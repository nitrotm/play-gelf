package ch.aducommun.gelf

import java.security.KeyStore

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Try, Success, Failure }

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{ ChannelInitializer, ChannelOption, ChannelFuture, ChannelFutureListener, ChannelPromise, ChannelOutboundHandlerAdapter, SimpleChannelInboundHandler, ChannelHandlerContext }
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.ssl.{ SslContextBuilder, SslProvider }

import org.slf4j.{ Logger, LoggerFactory }

import play.api.libs.json._



class SenderFactory(builder: => GELFSender)(implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  private val lock = new java.util.concurrent.locks.ReentrantReadWriteLock()
  private val rl = lock.readLock()
  private val wl = lock.writeLock()

  private var sender: Option[Future[GELFSender]] = None


  def current: Future[GELFSender] = {
    rl.lock()
    try {
      sender match {
        case Some(sender) =>
          for {
            currentSender <- sender
            active <- currentSender.isActive if active
          } yield currentSender
        case None =>
          Future.failed(new IllegalStateException("No sender"))
      }
    } finally {
      rl.unlock()
    }
  }

  def renew(): Future[GELFSender] = {
    wl.lock()
    try {
      val currentSender = sender

      sender = Some(
        for {
          newSender <- create()
          _ <- currentSender match {
            case Some(currentSender) =>
              currentSender.flatMap( _.shutdown ).recover({ case e: Exception => logger.warn(e.toString, e) })
            case None =>
              Future.successful()
          }
        } yield newSender
      )
      sender.get
    } finally {
      wl.unlock()
    }
  }

  def shutdown(): Future[Unit] = {
    wl.lock()
    try {
      val currentSender = sender

      sender = None
      (for {
        _ <- currentSender match {
          case Some(currentSender) =>
            currentSender.flatMap( _.shutdown ).recover({ case e: Exception => logger.warn(e.toString, e) })
          case None =>
            Future.successful()
        }
      } yield Unit)
    } finally {
      wl.unlock()
    }
  }

  private def create(): Future[GELFSender] = {
    val newSender = builder

    for {
      active <- newSender.isActive if active
    } yield newSender
  }
}

abstract class GELFSender {
  def isActive: Future[Boolean]

  def shutdown(): Future[Unit]

  def send(timestamp: Long, shortMessage: String, fullMessage: Option[String], fields: Option[JsObject], level: Option[Int], facility: Option[Int], file: Option[String], line: Option[Int]): Future[Unit]


  def send(t: Throwable): Future[Unit] = send(
    System.currentTimeMillis,
    throwableChainMessage(t),
    Some(throwableStackTrace(t)),
    None,
    None,
    None,
    None,
    None
  )

  def send(t: Throwable, fields: JsObject): Future[Unit] = send(
    System.currentTimeMillis,
    throwableChainMessage(t),
    Some(throwableStackTrace(t)),
    Some(fields),
    None,
    None,
    None,
    None
  )

  def send(shortMessage: String): Future[Unit] = send(
    System.currentTimeMillis,
    shortMessage,
    None,
    None,
    None,
    None,
    None,
    None
  )

  def send(shortMessage: String, fields: JsObject): Future[Unit] = send(
    System.currentTimeMillis,
    shortMessage,
    None,
    Some(fields),
    None,
    None,
    None,
    None
  )

  def send(shortMessage: String, fullMessage: String, fields: JsObject): Future[Unit] = send(
    System.currentTimeMillis,
    shortMessage,
    Some(fullMessage),
    Some(fields),
    None,
    None,
    None,
    None
  )


  private def throwableChain(t: Throwable): List[Throwable] = t.getCause match {
    case null =>
      Nil
    case cause =>
      t +: throwableChain(cause)
  }

  private def throwableChainMessage(t: Throwable): String = throwableChain(t).map( _.getMessage ).mkString(", caused by ")

  private def throwableStackTrace(t: Throwable): String = {
    val w = new java.io.StringWriter()

    t.printStackTrace(new java.io.PrintWriter(w))
    w.toString
  }
}

class GELFSenderWithRetry(factory: SenderFactory, retry: Int = 3, delay: Int = 1000)(implicit ec: ExecutionContext) extends GELFSender {
  private val logger = LoggerFactory.getLogger(getClass)


  def isActive: Future[Boolean] = Future.successful(true)

  def shutdown(): Future[Unit] = factory.shutdown()

  def send(timestamp: Long, shortMessage: String, fullMessage: Option[String], fields: Option[JsObject], level: Option[Int], facility: Option[Int], file: Option[String], line: Option[Int]): Future[Unit] = {
    def sendNext(trial: Int): Future[Unit] = {
      val sendFuture: Future[Unit] = for {
        sender <- factory.current.recoverWith({ case _ => factory.renew() })
        _ <- sender.send(timestamp, shortMessage, fullMessage, fields, level, facility, file, line)
      } yield Unit

      sendFuture.recoverWith({
        case e: Exception =>
          logger.warn(e.toString, e)

          if (trial >= retry) {
            throw new IllegalStateException("Sender failed " + trial + " times, dropping message (" + shortMessage + ")")
          }
          Thread.sleep(delay)
          sendNext(trial + 1)
      })
    }

    sendNext(1)
  }
}

class GELFSenderTCP(serverHostname: String = "localhost", serverPort: Int = 12201, clientName: String = "localhost", connectTimeout: Int = 1000, version: GELFVersion.Value = GELFVersion.V1_1, delimiter: Byte = '\0')(implicit ec: ExecutionContext) extends GELFSender {
  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val workerGroup = new NioEventLoopGroup()

  private lazy val bootstrap = new Bootstrap()
    .group(workerGroup)
    .channel(classOf[NioSocketChannel])
    .option(ChannelOption.AUTO_READ, Boolean.box(true))
    .option(ChannelOption.AUTO_CLOSE, Boolean.box(true))
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Int.box(connectTimeout))
    .option(ChannelOption.SO_KEEPALIVE, Boolean.box(true))
    .option(ChannelOption.TCP_NODELAY, Boolean.box(true))

  private lazy val channelFuture = bootstrap.clone()
    .handler(buildInitializer)
    .connect(serverHostname, serverPort)
  private lazy val channel = channelFuture.channel


  import NettyHelpers._

  def isActive: Future[Boolean] = for {
    _ <- channelFuture.asScala
  } yield channel.isActive

  def shutdown(): Future[Unit] = {
    channel.close()
    for {
      _ <- channel.closeFuture.asScala
      _ <- workerGroup.shutdownGracefully().asScala
    } yield Unit
  }

  def send(timestamp: Long, shortMessage: String, fullMessage: Option[String], fields: Option[JsObject], level: Option[Int], facility: Option[Int], file: Option[String], line: Option[Int]): Future[Unit] = for {
    _ <- channelFuture.asScala
    _ <- channel.writeAndFlush(
      GELFProtocol.build(version, clientName, timestamp, shortMessage, fullMessage, fields, level, facility, file, line)
    ).asScala
  } yield Unit

  protected def buildInitializer(): ChannelInitializer[SocketChannel] = new ChannelInitializer[SocketChannel] {
    def initChannel(ch: SocketChannel) {
      ch.pipeline()
        .addLast(new GELFEncoder(delimiter))
    }
  }
}

class GELFSenderSSLOverTCP(serverHostname: String = "localhost", serverPort: Int = 12201, keystore: KeyStore, keyAlias: String, keyPassword: String, clientName: String = "localhost", connectTimeout: Int = 1000, version: GELFVersion.Value = GELFVersion.V1_1, delimiter: Byte = '\0')(implicit ec: ExecutionContext) extends GELFSenderTCP(serverHostname, serverPort, clientName, connectTimeout, version, delimiter) {
  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val sslContext = SslContextBuilder.forClient()
    .sslProvider(SslProvider.JDK)
    .keyManager(
      keystore.getKey(keyAlias, keyPassword.toArray).asInstanceOf[java.security.PrivateKey],
      keystore.getCertificate(keyAlias).asInstanceOf[java.security.cert.X509Certificate]
    )
    .build()

  private def readData(src: java.io.InputStream): Array[Byte] = {
    val is = new java.io.BufferedInputStream(src)

    try {
      def readNext(result: List[Array[Byte]]): List[Array[Byte]] = {
        val buffer = Array.ofDim[Byte](8192)
        val br = is.read(buffer)

        if (br > 0) {
          readNext(result :+ buffer)
        } else if (br == 0) {
          readNext(result)
        } else {
          result
        }
      }

      readNext(Nil).flatten.toArray
    } finally {
      is.close()
    }
  }

  protected override def buildInitializer(): ChannelInitializer[SocketChannel] = new ChannelInitializer[SocketChannel] {
    def initChannel(ch: SocketChannel) {
      ch.pipeline()
        .addLast(sslContext.newHandler(ch.alloc(), serverHostname, serverPort))
        .addLast(new GELFEncoder(delimiter))
    }
  }
}

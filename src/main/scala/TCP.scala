package org.tmsrv.play.gelf

import scala.concurrent.{ ExecutionContext, Future }

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{ ChannelInitializer, ChannelOption }
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.ssl.SslContext

import play.api.libs.json._


class GELFSenderTCP(serverHostname: String = "localhost", serverPort: Int = 12201, clientName: String = "localhost", connectTimeout: Int = 1000, version: GELFVersion.Value = GELFVersion.V1_1, delimiter: Byte = 0)(implicit ec: ExecutionContext) extends GELFSender {
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

class GELFSenderSSLOverTCP(sslContext: SslContext, serverHostname: String = "localhost", serverPort: Int = 12201, clientName: String = "localhost", connectTimeout: Int = 1000, version: GELFVersion.Value = GELFVersion.V1_1, delimiter: Byte = 0)(implicit ec: ExecutionContext) extends GELFSenderTCP(serverHostname, serverPort, clientName, connectTimeout, version, delimiter) {
  protected override def buildInitializer(): ChannelInitializer[SocketChannel] = new ChannelInitializer[SocketChannel] {
    def initChannel(ch: SocketChannel) {
      ch.pipeline()
        .addLast(sslContext.newHandler(ch.alloc(), serverHostname, serverPort))
        .addLast(new GELFEncoder(delimiter))
    }
  }
}

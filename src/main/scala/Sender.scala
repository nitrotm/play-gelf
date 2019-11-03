package org.tmsrv.play.gelf

import java.util.concurrent.{ Executors, TimeUnit }

import scala.concurrent.{ ExecutionContext, Future, Promise }

import org.slf4j.LoggerFactory

import play.api.libs.json._


trait GELFSender {
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

class GELFSenderWithRetry(factory: GELFSenderFactory, retry: Int = 2, delay: Long = 1000)(implicit ec: ExecutionContext) extends GELFSender {
  private val logger = LoggerFactory.getLogger(getClass)

  private val scheduler = Executors.newSingleThreadScheduledExecutor()


  def isActive: Future[Boolean] = Future.successful(true)

  def shutdown(): Future[Unit] = factory.shutdown()

  def send(timestamp: Long, shortMessage: String, fullMessage: Option[String], fields: Option[JsObject], level: Option[Int], facility: Option[Int], file: Option[String], line: Option[Int]): Future[Unit] = {
    def sendNext: Future[Unit] = for {
      sender <- factory.current.recoverWith({ case _ => factory.renew() })
      _ <- sender.send(timestamp, shortMessage, fullMessage, fields, level, facility, file, line)
    } yield Unit

    def retryNext(result: Future[Unit], trial: Int = 0): Future[Unit] = result.recoverWith({
      case e: Exception =>
        if (trial >= retry) {
          logger.warn(e.toString, e)
          throw new IllegalStateException("Sender failed " + trial + " times, dropping message (" + shortMessage + ")", e)
        }

        val promise = Promise[Future[Unit]]()

        scheduler.schedule(
          new Runnable {
            def run() = promise.success(retryNext(sendNext, trial + 1))
          },
          delay,
          TimeUnit.MILLISECONDS
        )
        promise.future.flatten
    })

    retryNext(sendNext)
  }
}

class GELFSenderFactory(builder: => GELFSender)(implicit ec: ExecutionContext) {
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
              currentSender.flatMap( _.shutdown ).recover({ case e: Exception => logger.debug(e.toString, e) })
            case None =>
              Future.successful(Unit)
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
            currentSender.flatMap( _.shutdown ).recover({ case e: Exception => logger.debug(e.toString, e) })
          case None =>
            Future.successful(Unit)
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

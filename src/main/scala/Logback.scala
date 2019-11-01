package ch.aducommun.gelf

import java.net.InetAddress
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import ch.qos.logback.classic.spi.{ ILoggingEvent, IThrowableProxy }
import ch.qos.logback.core.AppenderBase

import play.api.libs.json._


class GELFLogbackAppender extends AppenderBase[ILoggingEvent] {
  private implicit val ec = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool())

  private var serverHostname: String = "localhost"
  private var serverPort: Int = 12201
  private var clientName: String = {
    InetAddress.getLocalHost().getHostName()
  }
  private var connectTimeout: Int = 1000
  private var version: GELFVersion.Value = GELFVersion.V1_1
  private var delimiter: Byte = '\0'
  private var useSsl: Boolean = false
  private var keystore: String = ""
  private var keystorePassword: String = ""
  private var keyAlias: String = ""
  private var keyAliasPassword: String = ""
  private var retry: Int = 5
  private var retryDelay: Int = 1000

  private var sender = new GELFSenderWithRetry(
    new SenderFactory(
      if (useSsl) {
        new GELFSenderSSLOverTCP(
          serverHostname,
          serverPort,
          GELFCryptography.loadKeyStore(new java.io.FileInputStream(keystore), keystorePassword),
          keyAlias,
          keyAliasPassword,
          clientName,
          connectTimeout,
          version,
          delimiter
        )
      } else {
        new GELFSenderTCP(
          serverHostname,
          serverPort,
          clientName,
          connectTimeout,
          version,
          delimiter
        )
      }
    ),
    retry,
    retryDelay
  )

  override def start() {
    super.start()
  }

  override def stop() {
    super.stop()

    sender.shutdown()
  }

  private val EMPTY = Json.obj()

  override protected def append(event: ILoggingEvent) {
    val caller = if (event.hasCallerData()) {
      Option(event.getCallerData()).map( _.toSeq ) match {
        case Some(data) if data.nonEmpty =>
          Json.obj(
            "logback_class" -> data(0).getClassName(),
            "logback_method" -> data(0).getMethodName(),
            "logback_file" -> data(0).getFileName(),
            "logback_line" -> data(0).getLineNumber()
          )
        case _ =>
          EMPTY
      }
    } else {
      EMPTY
    }
    val error = Option(event.getThrowableProxy()) match {
      case Some(t) =>
        Json.obj(
          "logback_error" -> throwableChainMessage(t),
          "logback_trace" -> throwableChain(t).map(t => throwableStackTrace(t))
        )
      case None =>
        EMPTY
    }

    sender.send(
      event.getTimeStamp(),
      event.getFormattedMessage(),
      None,
      Some(
        Json.obj(
          // "logback_seq" -> event.getSequenceNumber(),
          "logback_level" -> event.getLevel().toString(),
          "logback_logger" -> event.getLoggerName()
        ) ++ caller ++ error
      ),
      None,
      None,
      None,
      None
    )
  }

  private def throwableChain(t: IThrowableProxy): List[IThrowableProxy] = t.getCause match {
    case null =>
      Nil
    case cause =>
      t +: throwableChain(cause)
  }

  private def throwableChainMessage(t: IThrowableProxy): String = throwableChain(t).map( _.getMessage ).mkString(", caused by ")

  private def throwableStackTrace(t: IThrowableProxy): Seq[String] = {
    Option(t.getStackTraceElementProxyArray())
      .map(elements => elements.toSeq.map( _.getStackTraceElement().toString()))
      .getOrElse(Seq.empty)
  }

  private def afterConfigurationUpdate() {
    sender.shutdown()
  }

  def getHost(): String = serverHostname
  def setHost(value: String) {
    serverHostname = value
    afterConfigurationUpdate()
  }

  def getPort(): Int = serverPort
  def setPort(value: Int) {
    serverPort = value
    afterConfigurationUpdate()
  }

  def getClientName(): String = clientName
  def setClientName(value: String) {
    clientName = value
    afterConfigurationUpdate()
  }

  def getConnectTimeout(): Int = connectTimeout
  def setConnectTimeout(value: Int) {
    connectTimeout = value
    afterConfigurationUpdate()
  }

  def isSsl(): Boolean = useSsl
  def setSsl(value: Boolean) {
    useSsl = value
    afterConfigurationUpdate()
  }

  def getKeystore(): String = keystore
  def setKeystore(value: String) {
    keystore = value
    afterConfigurationUpdate()
  }

  def getKeystorePassword(): String = keystorePassword
  def setKeystorePassword(value: String) {
    keystorePassword = value
    afterConfigurationUpdate()
  }

  def getKeystoreAlias(): String = keyAlias
  def setKeystoreAlias(value: String) {
    keyAlias = value
    afterConfigurationUpdate()
  }

  def getKeystoreAliasPassword(): String = keyAliasPassword
  def setKeystoreAliasPassword(value: String) {
    keyAliasPassword = value
    afterConfigurationUpdate()
  }

  def getRetry(): Int = retry
  def setRetry(value: Int) {
    retry = value
    afterConfigurationUpdate()
  }

  def getRetryDelay(): Int = retryDelay
  def setRetryDelay(value: Int) {
    retryDelay = value
    afterConfigurationUpdate()
  }
}

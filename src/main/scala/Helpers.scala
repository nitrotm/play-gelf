package org.tmsrv.play.gelf

import java.security.KeyStore

import scala.concurrent.{ ExecutionContext, Future, Promise }

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.handler.ssl.{ SslContext, SslContextBuilder, SslProvider }
import io.netty.util.concurrent.{ Future => NettyFuture, GenericFutureListener }

import play.api.libs.json._


object GELFCryptography {
  def loadKeyStore(is: java.io.InputStream, password: String): KeyStore = {
    val ks = KeyStore.getInstance("JKS")

    ks.load(is, password.toArray)
    ks
  }

  def sslContext(): SslContext = SslContextBuilder.forClient()
    .sslProvider(SslProvider.JDK)
    .build()

  def sslContext(keystore: KeyStore, alias: String, password: String): SslContext = SslContextBuilder.forClient()
    .sslProvider(SslProvider.JDK)
    .keyManager(
      keystore.getKey(alias, password.toArray).asInstanceOf[java.security.PrivateKey],
      keystore.getCertificate(alias).asInstanceOf[java.security.cert.X509Certificate]
    )
    .build()
}

class GELFEncoder(delimiter: Byte) extends MessageToByteEncoder[JsObject] {
  protected def encode(ctx: ChannelHandlerContext, msg: JsObject, out: ByteBuf) {
    out.writeBytes(Json.toBytes(msg))
    out.writeByte(delimiter)
  }
}

object NettyHelpers {
  implicit class NettyFutureHelpers[T](src: NettyFuture[T]) {
    def asScala(implicit ec: ExecutionContext): Future[T] = {
      val promise = Promise[T]()
      val listener: GenericFutureListener[NettyFuture[T]] = new GenericFutureListener[NettyFuture[T]] {
        def operationComplete(result: NettyFuture[T]) {
          if (result.isSuccess) {
            promise.success(result.getNow)
          } else {
            promise.failure(result.cause)
          }
        }
      }

      src.addListener(listener)
      promise.future.andThen({ case _ => src.removeListener(listener) })
    }
  }
}

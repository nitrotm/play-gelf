package ch.aducommun.gelf

import scala.concurrent.{ ExecutionContext, Future, Promise }

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.util.concurrent.{ Future => NettyFuture, GenericFutureListener }

import play.api.libs.json._


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

class GELFEncoder(delimiter: Byte) extends MessageToByteEncoder[JsObject] {
  import NettyHelpers._

  protected def encode(ctx: ChannelHandlerContext, msg: JsObject, out: ByteBuf) {
    out.writeBytes(Json.toBytes(msg))
    out.writeByte(delimiter)
  }
}

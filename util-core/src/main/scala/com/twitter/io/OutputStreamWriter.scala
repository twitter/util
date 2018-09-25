package com.twitter.io

import com.twitter.util.{Future, FuturePool, Promise, Return, Throw, Time}
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * Construct a Writer from a given OutputStream.
 */
private[io] class OutputStreamWriter(out: OutputStream, bufsize: Int) extends Writer[Buf] {
  import com.twitter.io.OutputStreamWriter._

  private[this] val done = new Promise[Unit]
  private[this] val writeOp = new AtomicReference[Buf => Future[Unit]](doWrite)

  // Byte array reused on each write to avoid multiple allocations.
  private[this] val bytes = new Array[Byte](bufsize)

  @tailrec
  private[this] def drain(buf: Buf): Unit = {
    if (buf.isEmpty) out.flush()
    else {
      // The source length is min(buf.length, bytes.length).
      val b = buf.slice(0, bytes.length)
      // Copy from the source to byte array.
      b.write(bytes, 0)
      // Write the bytes that were copied.
      out.write(bytes, 0, b.length)
      // Recurse on the remainder.
      drain(buf.slice(bytes.length, Int.MaxValue))
    }
  }

  private[this] def doWrite: Buf => Future[Unit] =
    buf => FuturePool.interruptibleUnboundedPool { drain(buf) }

  def write(buf: Buf): Future[Unit] =
    if (done.isDefined) done
    else
      (
        done or writeOp.getAndSet(_ => Future.exception(WriteExc))(buf)
      ) transform {
        case Return(_) =>
          writeOp.set(doWrite)
          Future.Done

        case Throw(cause) =>
          // We don't need to wait for the close, we care only that it is called.
          if (cause != WriteExc) close()
          Future.exception(cause)
      }

  def fail(cause: Throwable): Unit =
    done.updateIfEmpty(Throw(cause))

  def close(deadline: Time): Future[Unit] =
    if (done.updateIfEmpty(Throw(CloseExc))) FuturePool.unboundedPool {
      out.close()
    } else Future.Done
}

private object OutputStreamWriter {
  val WriteExc = new IllegalStateException("write while writing")
  val CloseExc = new IllegalStateException("write after close")
}

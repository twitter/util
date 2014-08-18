package com.twitter.io

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

import com.twitter.util.{
  Future, FuturePool, Closable, Time, Throw, Return, Promise
}

/**
 * Construct a Writer from a given OutputStream.
 */
private[io]
class OutputStreamWriter(out: OutputStream, bufsize: Int) extends Writer with Closable {
  import OutputStreamWriter._

  private[this] val done = new Promise[Unit]
  private[this] val writeOp = new AtomicReference[Buf => Future[Unit]](doWrite)

  // Byte array reused on each write to avoid multiple allocations.
  private[this] val bytes = new Array[Byte](bufsize)

  @tailrec
  private[this] def drain(buf: Buf): Unit = {
    if (buf.isEmpty) out.flush() else {
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

  private[this] def doWrite: Buf => Future[Unit] = buf =>
    FuturePool.interruptibleUnboundedPool { drain(buf) }

  def write(buf: Buf): Future[Unit] =
    if (done.isDefined) done else (
      done or writeOp.getAndSet(_ => Future.exception(WriteExc))(buf)
    ) respond {
      case Return(_) => writeOp.set(doWrite)
      case Throw(cause) if cause != WriteExc => close()
      case Throw(_) =>
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

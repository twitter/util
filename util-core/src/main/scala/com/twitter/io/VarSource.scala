package com.twitter.io.exp

import com.twitter.conversions.time._
import com.twitter.io.{Buf, Reader}
import com.twitter.util._
import java.lang.ref.{ReferenceQueue, WeakReference}
import java.io.{ByteArrayOutputStream, File, FileInputStream}
import java.util.HashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A VarSource provides access to observerable named variables.
 */
trait VarSource[+T] {
  def get(varName: String): Var[VarSource.Result[T]]
}

object VarSource {
  sealed trait Result[+A] {
    def map[B](f: A => B): Result[B] = {
      this match {
        case Ok(a) =>
          try {
            Ok(f(a))
          } catch {
            case NonFatal(e) => Failed(e)
          }
        case f@Failed(_) => f
        case Pending => Pending
        case Empty => Empty
      }
    }
  }
  object Pending extends Result[Nothing]
  object Empty extends Result[Nothing]
  case class Failed(cause: Throwable) extends Result[Nothing]
  case class Ok[T](t: T) extends Result[T]

  /**
   * A VarSource for observing file contents. Once observed,
   * each file will be polled once per period.
   */
  def forFiles(period: Duration = 1.minute)(implicit timer: Timer) =
    new CachingVarSource[Buf](new FilePollingVarSource(period)(timer))

  /**
   * Create a VarSource for ClassLoader resources.
   */
  def forClassLoaderResources(cl: ClassLoader = ClassLoader.getSystemClassLoader) =
    new CachingVarSource[Buf](new ClassLoaderVarSource(cl))
}

/**
 * A convenient wrapper for caching the results returned by the
 * underlying VarSource.
 */
class CachingVarSource[T](underlying: VarSource[T]) extends VarSource[T] {
  import VarSource._

  private[this] val refq = new ReferenceQueue[Var[Result[T]]]
  private[this] val forward = new HashMap[String, WeakReference[Var[Result[T]]]]
  private[this] val reverse = new HashMap[WeakReference[Var[Result[T]]], String]

  /**
   * A caching proxy to the underlying VarSource. Vars are cached by
   * varName, and are tracked with WeakReferences.
   */
  def get(varName: String): Var[Result[T]] = synchronized {
    gc()
    Option(forward.get(varName)) flatMap { wr =>
      Option(wr.get())
    } match {
      case Some(v) => v
      case None =>
        val v = underlying.get(varName)
        val ref = new WeakReference(v, refq)
        forward.put(varName, ref)
        reverse.put(ref, varName)
        v
    }
  }

  /**
   * Remove garbage collected cache entries.
   */
  def gc() = synchronized {
    var ref = refq.poll()
    while (ref != null) {
      val key = reverse.remove(ref)
      if (key != null)
        forward.remove(key)

      ref = refq.poll()
    }
  }
}

/**
 * A VarSource for observing the contents of a file with periodic polling.
 *
 * Note: java.nio.file provides WatchService, but Unfortunately we
 * still need to be java 6 compatible.
 */
class FilePollingVarSource private[exp](period: Duration)(implicit timer: Timer) extends VarSource[Buf]
{
  import VarSource._

  def get(varName: String): Var[Result[Buf]] = {
    val v = Var.async[Result[Buf]](Pending) { v =>
      val f = () => {
        val file = new File(varName)
        if (file.exists()) {
          Future { Reader.fromFile(file) } flatMap { reader =>
            Reader.readAll(reader)
          } respond {
            case Return(buf) =>
              v() = Ok(buf)
            case Throw(cause) =>
              v() = Failed(cause)
          }
        } else {
          v() = Empty
        }
      }

      val timerTask = period match {
        case Duration.Top =>
          timer.schedule(Time.now) { f() }
        case _ =>
          timer.schedule(Time.now, period) { f() }
      }

      Closable make { _ =>
        Future { timerTask.cancel() }
      }
    }
    v
  }
}

/**
 * A VarSource for ClassLoader resources.
 */
class ClassLoaderVarSource private[exp](classLoader: ClassLoader) extends VarSource[Buf] {
  import VarSource._

  def get(varName: String): Var[Result[Buf]] = {
    // This Var is updated at most once since ClassLoader
    // resources don't change (do they?).
    val runOnce = new AtomicBoolean(false)
    val p = new Promise[Result[Buf]]()

    // Defer loading until the first observation
    val v = Var.async[Result[Buf]](Pending) { v =>
      if (runOnce.compareAndSet(false, true)) {
        FuturePool.unboundedPool {
          classLoader.getResourceAsStream(varName) match {
            case null => p.setValue(Empty)
            case stream =>
              val reader = Reader.fromStream(stream)
              Reader.readAll(reader) respond {
                case Return(buf) =>
                  p.setValue(Ok(buf))
                case Throw(cause) =>
                  p.setValue(Failed(cause))
              }
          }
        }
      }
      p onSuccess { v() = _ }
      Closable.nop
    }
    v
  }
}

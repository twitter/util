package com.twitter.io.exp

import java.io.{FileInputStream, File}
import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.HashMap
import java.util.concurrent.atomic.AtomicBoolean

import com.twitter.conversions.time._
import com.twitter.io.{InputStreamReader, Buf, Reader}
import com.twitter.util._

/**
 * A VarSource provides access to observerable named variables.
 */
trait VarSource[+T] { self =>
  def get(varName: String): Var[VarSource.Result[T]]

  def flatMap[U](f: T => VarSource.Result[U]): VarSource[U] = new VarSource[U] {
    def get(varName: String): Var[VarSource.Result[U]] =
      self.get(varName) map { result =>
        result flatMap f
      }
  }
}

object VarSource {
  sealed trait Result[+A] {
    /**
     * Returns a Result containing the result of applying `f` to this
     * Result's value if this Result is Ok.
     */
    def map[B](f: A => B): Result[B] = flatMap { a => Ok(f(a)) }

    /**
     * Returns the result of applying `f` to this Result's value if this
     * result is Ok.
     */
    def flatMap[B](f: A => Result[B]): Result[B] = {
      this match {
        case Ok(a) =>
          try {
            f(a)
          } catch {
            case NonFatal(e) => Failed(e)
          }
        case f@Failed(_) => f
        case Pending => Pending
        case Empty => Empty
      }
    }

    /**
     * Returns true if the result is Pending, false otherwise.
     */
    def isPending: Boolean = this == Pending

    /**
     * Returns true if the result is Empty, false otherwise.
     */
    def isEmpty: Boolean = this == Empty

    /**
     * Returns true if the result is Ok, false otherwise.
     */
    def isOk: Boolean = this match {
      case Ok(_) => true
      case _ => false
    }

    /**
     * Returns true if the result is Failed, false otherwise.
     */
    def isFailed: Boolean = this match {
      case Failed(_) => true
      case _ => false
    }

    /**
     * Returns the result's value.
     *
     * ''Note:'' The result must be Ok.
     */
    def get: A = this match {
      case Ok(a) => a
      case _ => throw new java.util.NoSuchElementException(toString)
    }
  }
  object Pending extends Result[Nothing]
  object Empty extends Result[Nothing]
  case class Failed(cause: Throwable) extends Result[Nothing]
  case class Ok[T](value: T) extends Result[T]

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
 * A VarSource which queries a primary underlying source, and queries
 * a failover source only when the primary result is VarSource.Failed.
 */
class FailoverVarSource[+T](primary: VarSource[T], failover: VarSource[T]) extends VarSource[T] {
  def get(varName: String): Var[VarSource.Result[T]] = {
    primary.get(varName) flatMap {
      case VarSource.Empty     => Var.value(VarSource.Empty)
      case VarSource.Pending   => Var.value(VarSource.Pending)
      case ok@VarSource.Ok(_)  => Var.value(ok)
      case VarSource.Failed(_) => failover.get(varName)
    }
  }
}

/**
 * A convenient wrapper for caching the results returned by the
 * underlying VarSource.
 */
class CachingVarSource[T](underlying: VarSource[T]) extends VarSource[T] {
  import com.twitter.io.exp.VarSource._

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
class FilePollingVarSource private[exp](
  period: Duration,
  pool: FuturePool
)(implicit timer: Timer) extends VarSource[Buf] {

  private[exp] def this(period: Duration)(implicit timer: Timer) =
    this(period, FuturePool.unboundedPool)

  import com.twitter.io.exp.VarSource._

  def get(varName: String): Var[Result[Buf]] = {
    val v = Var.async[Result[Buf]](Pending) { v =>
      val f = () => {
        val file = new File(varName)
        if (file.exists()) {
          pool {
            val reader = new InputStreamReader(new FileInputStream(file), InputStreamReader.DefaultMaxBufferSize, pool)
            Reader.readAll(reader) respond {
              case Return(buf) =>
                v() = Ok(buf)
              case Throw(cause) =>
                v() = Failed(cause)
            }
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
class ClassLoaderVarSource private[exp](classLoader: ClassLoader, pool: FuturePool) extends VarSource[Buf] {
  import com.twitter.io.exp.VarSource._

  private[exp] def this(classLoader: ClassLoader) = this(classLoader, FuturePool.unboundedPool)

  def get(varName: String): Var[Result[Buf]] = {
    // This Var is updated at most once since ClassLoader
    // resources don't change (do they?).
    val runOnce = new AtomicBoolean(false)
    val p = new Promise[Result[Buf]]()

    // Defer loading until the first observation
    val v = Var.async[Result[Buf]](Pending) { v =>
      if (runOnce.compareAndSet(false, true)) {
        pool {
          classLoader.getResourceAsStream(varName) match {
            case null => p.setValue(Empty)
            case stream =>
              val reader = new InputStreamReader(stream, InputStreamReader.DefaultMaxBufferSize, pool)
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

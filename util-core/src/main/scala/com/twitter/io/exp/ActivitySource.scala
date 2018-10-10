package com.twitter.io.exp

import com.twitter.io.{InputStreamReader, Buf, Reader}
import com.twitter.util._
import java.io.{FileInputStream, File}
import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.HashMap

/**
 * An ActivitySource provides access to observerable named variables.
 */
trait ActivitySource[+T] {

  /**
   * Returns an [[com.twitter.util.Activity]] for a named T-typed variable.
   */
  def get(name: String): Activity[T]

  /**
   * Produces an ActivitySource which queries this ActivitySource, falling back to
   * a secondary ActivitySource only when the primary result is ActivitySource.Failed.
   *
   * @param that the secondary ActivitySource
   */
  def orElse[U >: T](that: ActivitySource[U]): ActivitySource[U] =
    new ActivitySource.OrElse(this, that)
}

object ActivitySource {

  /**
   * A Singleton exception to indicate that an ActivitySource failed to find
   * a named variable.
   */
  object NotFound extends IllegalStateException

  /**
   * An ActivitySource for observing file contents. Once observed,
   * each file will be polled once per period.
   */
  def forFiles(
    period: Duration = Duration.fromSeconds(60)
  )(implicit timer: Timer): ActivitySource[Buf] =
    new CachingActivitySource(new FilePollingActivitySource(period)(timer))

  /**
   * Create an ActivitySource for ClassLoader resources.
   */
  def forClassLoaderResources(
    cl: ClassLoader = ClassLoader.getSystemClassLoader
  ): ActivitySource[Buf] =
    new CachingActivitySource(new ClassLoaderActivitySource(cl))

  private[ActivitySource] class OrElse[T, U >: T](
    primary: ActivitySource[T],
    failover: ActivitySource[U]
  ) extends ActivitySource[U] {
    def get(name: String): Activity[U] = {
      primary.get(name) transform {
        case Activity.Failed(_) => failover.get(name)
        case state => Activity(Var.value(state))
      }
    }
  }
}

/**
 * A convenient wrapper for caching the results returned by the
 * underlying ActivitySource.
 */
class CachingActivitySource[T](underlying: ActivitySource[T]) extends ActivitySource[T] {

  private[this] val refq = new ReferenceQueue[Activity[T]]
  private[this] val forward = new HashMap[String, WeakReference[Activity[T]]]
  private[this] val reverse = new HashMap[WeakReference[Activity[T]], String]

  /**
   * A caching proxy to the underlying ActivitySource. Vars are cached by
   * name, and are tracked with WeakReferences.
   */
  def get(name: String): Activity[T] = synchronized {
    gc()
    Option(forward.get(name)) flatMap { wr =>
      Option(wr.get())
    } match {
      case Some(v) => v
      case None =>
        val v = underlying.get(name)
        val ref = new WeakReference(v, refq)
        forward.put(name, ref)
        reverse.put(ref, name)
        v
    }
  }

  /**
   * Remove garbage collected cache entries.
   */
  def gc(): Unit = synchronized {
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
 * An ActivitySource for observing the contents of a file with periodic polling.
 */
class FilePollingActivitySource private[exp] (
  period: Duration,
  pool: FuturePool
)(implicit timer: Timer)
    extends ActivitySource[Buf] {

  private[exp] def this(period: Duration)(implicit timer: Timer) =
    this(period, FuturePool.unboundedPool)

  import com.twitter.io.exp.ActivitySource._

  def get(name: String): Activity[Buf] = {
    val v = Var.async[Activity.State[Buf]](Activity.Pending) { value =>
      val timerTask = timer.schedule(Time.now, period) {
        val file = new File(name)

        if (file.exists()) {
          pool {
            val reader = new InputStreamReader(
              new FileInputStream(file),
              InputStreamReader.DefaultMaxBufferSize,
              pool
            )
            Reader.readAll(reader) respond {
              case Return(buf) =>
                value() = Activity.Ok(buf)
              case Throw(cause) =>
                value() = Activity.Failed(cause)
            } ensure {
              // InputStreamReader ignores the deadline in close
              reader.close(Time.Undefined)
            }
          }
        } else {
          value() = Activity.Failed(NotFound)
        }
      }

      Closable.make { _ =>
        Future { timerTask.cancel() }
      }
    }

    Activity(v)
  }
}

/**
 * An ActivitySource for ClassLoader resources.
 */
class ClassLoaderActivitySource private[exp] (classLoader: ClassLoader, pool: FuturePool)
    extends ActivitySource[Buf] {

  import com.twitter.io.exp.ActivitySource._

  private[exp] def this(classLoader: ClassLoader) = this(classLoader, FuturePool.unboundedPool)

  def get(name: String): Activity[Buf] = {
    // This Var is updated at most once since ClassLoader
    // resources don't change (do they?).
    val runOnce = new AtomicBoolean(false)
    val p = new Promise[Activity.State[Buf]]

    // Defer loading until the first observation
    val v = Var.async[Activity.State[Buf]](Activity.Pending) { value =>
      if (runOnce.compareAndSet(false, true)) {
        pool {
          classLoader.getResourceAsStream(name) match {
            case null => p.setValue(Activity.Failed(NotFound))
            case stream =>
              val reader =
                new InputStreamReader(stream, InputStreamReader.DefaultMaxBufferSize, pool)
              Reader.readAll(reader) respond {
                case Return(buf) =>
                  p.setValue(Activity.Ok(buf))
                case Throw(cause) =>
                  p.setValue(Activity.Failed(cause))
              } ensure {
                // InputStreamReader ignores the deadline in close
                reader.close(Time.Undefined)
              }
          }
        }
      }
      p.onSuccess(value() = _)
      Closable.nop
    }

    Activity(v)
  }
}

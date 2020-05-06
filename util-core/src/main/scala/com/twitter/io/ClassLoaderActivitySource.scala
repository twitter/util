package com.twitter.io

import com.twitter.util._
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An ActivitySource for ClassLoader resources.
 */
class ClassLoaderActivitySource private[io] (classLoader: ClassLoader, pool: FuturePool)
    extends ActivitySource[Buf] {

  private[io] def this(classLoader: ClassLoader) = this(classLoader, FuturePool.unboundedPool)

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
            case null => p.setValue(Activity.Failed(ActivitySource.NotFound))
            case stream =>
              val reader = InputStreamReader(stream, pool)
              BufReader.readAll(reader) respond {
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

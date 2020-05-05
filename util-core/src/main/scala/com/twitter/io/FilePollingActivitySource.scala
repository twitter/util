package com.twitter.io

import com.twitter.util._
import java.io.{File, FileInputStream}

/**
 * An ActivitySource for observing the contents of a file with periodic polling.
 */
class FilePollingActivitySource private[io] (
  period: Duration,
  pool: FuturePool
)(
  implicit timer: Timer)
    extends ActivitySource[Buf] {

  private[io] def this(period: Duration)(implicit timer: Timer) =
    this(period, FuturePool.unboundedPool)

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
            BufReader.readAll(reader) respond {
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
          value() = Activity.Failed(ActivitySource.NotFound)
        }
      }

      Closable.make { _ => Future { timerTask.cancel() } }
    }

    Activity(v)
  }
}

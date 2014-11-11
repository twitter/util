package com.twitter.util.events

import com.twitter.util.Time
import com.twitter.util.events.Event.Type
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable.ArrayBuffer

object SizedSink {

  // use the next largest power of two for performance reasons.
  // eg: http://psy-lob-saw.blogspot.com/2014/11/the-mythical-modulo-mask.html
  private def nextPowOf2(n: Int): Int =
    math.min(
      1 << 30,
      math.max(1, Integer.highestOneBit(n - 1) * 2)
    )

  /**
   * An in-memory circular buffer of events. When `capacity` is reached,
   * new writes via `event` do not block, rather they overwrite the
   * oldest event.
   *
   * @param approxSize approximate for the max number of events to keep in-memory.
   * @param milliTime gets the current time in millis from the epoch.
   *          This is exposed to allow for more control in tests.
   */
  private[twitter] def apply(
    approxSize: Int,
    milliTime: () => Long = () => System.currentTimeMillis()
  ): Sink = {
    require(approxSize > 0, s"approxSize must be positive: $approxSize")
    new SizedSink(nextPowOf2(approxSize), milliTime)
  }

  private class MutableEvent(
      var etype: Type,
      var whenMillis: Long,
      var longVal: Long,
      var objectVal: Object,
      var doubleVal: Double)
  {
    def isDefined: Boolean = etype != null

    def toEvent: Event =
      Event(etype, Time.fromMilliseconds(whenMillis), longVal, objectVal, doubleVal)
  }

}

/**
 * An in-memory circular buffer of events. When `capacity` is reached,
 * new writes via `event` do not block, rather they overwrite the
 * oldest event.
 *
 * This class is thread-safe and effort is taken to
 * keep object allocations from calls to `event` to a minimum.
 *
 * @param capacity the max number of events to keep in-memory.
 *          Must be a positive power of 2.
 * @param milliTime gets the current time in millis from the epoch.
 *          This is exposed to allow for more control in tests.
 */
class SizedSink private[events](
    capacity: Int,
    milliTime: () => Long)
  extends Sink
{
  import SizedSink._

  require(capacity > 0, s"capacity must be positive: $capacity")

  // require capacity be a power of 2:
  // http://en.wikipedia.org/wiki/Power_of_two#Fast_algorithm_to_check_if_a_positive_number_is_a_power_of_two
  require((capacity & (capacity - 1)) == 0, s"capacity must be power of 2: $capacity")

  private[this] val pos = new AtomicLong(0)

  private[this] val evs = Array.fill(capacity) {
    new MutableEvent(
      etype = null,
      whenMillis = -1L,
      longVal = Event.NoLong,
      objectVal = Event.NoObject,
      doubleVal = Event.NoDouble
    )
  }

  override def event(
    etype: Type,
    longVal: Long = Event.NoLong,
    objectVal: Object = Event.NoObject,
    doubleVal: Double = Event.NoDouble
  ): Unit = {
    require(etype != null)

    // reserve our position where the write will go.
    val position = pos.getAndIncrement()
    val slot = (position & (capacity - 1)).toInt
    val millis = milliTime()

    // we need the lock to be exclusive to avoid the case of another writer
    // wrapping back around and trying to write back into this slot.
    val ev = evs(slot)
    ev.synchronized {
      ev.etype = etype
      ev.whenMillis = millis
      ev.longVal = longVal
      ev.objectVal = objectVal
      ev.doubleVal = doubleVal
    }
  }

  override def events: Iterator[Event] = {
    val out = new ArrayBuffer[Event](capacity)

    0.until(capacity).foreach { i =>
      val ev = evs(i)
      ev.synchronized {
        if (ev.isDefined) {
          out += ev.toEvent
        }
      }
    }
    out.iterator
  }

}

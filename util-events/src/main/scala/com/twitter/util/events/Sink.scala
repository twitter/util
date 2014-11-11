package com.twitter.util.events

import com.twitter.util.events.Event.Type

/**
 * Where runtime events such as logging, stats and tracing can be
 * sent to allow for analysis.
 *
 * '''Note:''' while the API is public it should be considered as experimental
 * and subject to changes.
 *
 * ===Design notes===
 *  - Implementations must be thread-safe.
 *  - Implementations should have very low runtime overhead such that as
 *    many events as possible can be sent here. In particular, object
 *    allocations should be kept to a minimum.
 *  - `event` is expected to be called many orders of magnitude
 *    more frequently than `events`.
 */
trait Sink {

  /**
   * Event input is captured as individual fields in service of
   * avoiding an allocation to wrap the event.
   */
  def event(
    etype: Event.Type,
    longVal: Long = Event.NoLong,
    objectVal: Object = Event.NoObject,
    doubleVal: Double = Event.NoDouble
  ): Unit

  /**
   * Returns all currently available events.
   *
   * '''Note:''' the events are not returned in any particular order.
   */
  def events: Iterator[Event]

}

object Sink {

  /**
   * A sink that ignores all input.
   */
  val Null: Sink = new Sink {
    override def event(
      etype: Type,
      longVal: Long,
      objectVal: Object,
      doubleVal: Double
    ): Unit = ()

    override def events: Iterator[Event] = Iterator.empty
  }

  // exposed for testing
  private[events] def newDefault: Sink = {
    if (!sinkEnabled.apply()) {
      Null
    } else if (approxNumEvents() <= 0) {
      Null
    } else {
      SizedSink(approxNumEvents())
    }
  }

  /**
   * The global default `Sink`.
   */
  val default: Sink = newDefault

  /**
   * Returns whether or not any event capture is enabled.
   */
  def enabled: Boolean = default ne Null

}

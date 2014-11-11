package com.twitter.util.events

import com.twitter.util.Time

object Event {

  val NoObject: AnyRef = new Object()
  val NoLong: Long = Long.MinValue
  val NoDouble: Double = Double.NegativeInfinity

  /**
   * Represents a type of event that can be recorded.
   *
   * A recommended usage pattern is to create a singleton
   * in the companion object and use that for all events
   * of that type.
   */
  abstract class Type

}

/**
 * A somewhat flexible schema representing various event types.
 *
 * @param when when the event happened.
 * @param longVal should be `Event.NoLong` if there is no supplied value.
 * @param objectVal should be `Event.NoObject` if there is no supplied value.
 * @param doubleVal should be `Event.NoDouble` if there is no supplied value.
 */
case class Event(
    etype: Event.Type,
    when: Time,
    longVal: Long = Event.NoLong,
    objectVal: Object = Event.NoObject,
    doubleVal: Double = Event.NoDouble)

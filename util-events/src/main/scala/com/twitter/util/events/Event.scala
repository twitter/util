package com.twitter.util.events

import com.twitter.util.{Time, Return, Throw, Try}
import com.twitter.io.Buf

object Event {

  val NoObject: AnyRef = new Object()
  val NoLong: Long = Long.MinValue
  val NoDouble: Double = Double.NegativeInfinity
  val NoTraceId: Long = Long.MinValue
  val NoSpanId: Long = Long.MinValue

  /**
   * Represents a type of event that can be recorded.
   *
   * A recommended usage pattern is to create a singleton
   * in the companion object and use that for all events
   * of that type.
   */
  abstract class Type {
    /**
     * An identifier for this Type construction. These should be unique across
     * types for any given sink, though no efforts are made to ensure this.
     *
     * @param id A name for this Type.
     */
    def id: String

    /**
     * A serializer for events of this type.
     */
    def serialize(event: Event): Try[Buf]

    /**
     * A deserializer for Events.
     */
    def deserialize(buf: Buf): Try[Event]

    protected def serializeTrace(traceId: Long, spanId: Long): (Option[Long], Option[Long]) = {
      val sid = if (spanId == NoSpanId) None else Some(spanId)
      val tid = if (traceId == NoTraceId) None else Some(traceId)
      (tid, sid)
    }

    override def toString() = id
  }

  // Note: Not a val so we can discriminate between constructions in tests.
  private[twitter] def nullType: Type = new Type {
    val id = "Null"
    def serialize(event: Event) = Return(Buf.Empty)
    def deserialize(buf: Buf) = Return(Event(this, Time.Bottom))
  }
}

/**
 * A somewhat flexible schema representing various event types.
 *
 * @param when when the event happened.
 * @param longVal should be `Event.NoLong` if there is no supplied value.
 * @param objectVal should be `Event.NoObject` if there is no supplied value.
 * @param doubleVal should be `Event.NoDouble` if there is no supplied value.
 * @param traceIdVal should be `Event.NoTraceId` if there is no supplied value.
 * @param spanIdVal should be `Event.NoSpanId` if there is no supplied value.
 */
case class Event(
    etype: Event.Type,
    when: Time,
    longVal: Long = Event.NoLong,
    objectVal: Object = Event.NoObject,
    doubleVal: Double = Event.NoDouble,
    traceIdVal: Long = Event.NoTraceId,
    spanIdVal: Long = Event.NoSpanId)

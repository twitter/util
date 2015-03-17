package com.twitter.util.events;

/**
 * Java friendly API for {@link com.twitter.util.events.Event}.
 */
public final class Events {
  private Events() { }

  public static Event.Type nullType() {
    return Event$.MODULE$.nullType();
  }

  public static final long NO_LONG = Event.NoLong();
  public static final Object NO_OBJECT = Event.NoObject();
  public static final double NO_DOUBLE = Event.NoDouble();
  public static final long NO_TRACE_ID = Event.NoTraceId();
  public static final long NO_SPAN_ID = Event.NoSpanId();
}

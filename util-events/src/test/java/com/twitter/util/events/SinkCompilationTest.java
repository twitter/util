package com.twitter.util.events;

import org.junit.Assert;
import org.junit.Test;

public class SinkCompilationTest {

  @Test
  public void testEvent() {
    Sinks.DEFAULT_SINK.event(
        Events.nullType(),
        Events.NO_LONG,
        Events.NO_OBJECT,
        Events.NO_DOUBLE,
        Events.NO_TRACE_ID,
        Events.NO_SPAN_ID);
  }
}

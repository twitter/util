/* Copyright 2016 Twitter, Inc. */
package com.twitter.util;

import scala.Function0;

import org.junit.Assert;
import org.junit.Test;

public class StopwatchCompilationTest {

  @Test
  public void testStart() {
    Function0<Duration> elapsed = Stopwatches.start();
    Assert.assertTrue(elapsed.apply().inNanoseconds() >= 0);
  }

  @Test
  public void testConstant() {
    Stopwatches.constant(Duration.Zero());
  }

  @Test
  public void testTimeX() {
    Stopwatches.timeNanos();
    Stopwatches.timeMicros();
    Stopwatches.timeMillis();
  }

  @Test
  public void testSystemX() {
    Stopwatches.systemNanos();
    Stopwatches.systemMicros();
    Stopwatches.systemMillis();
  }

  @Test
  public void testNilStopwatch() {
    Stopwatch nil = NilStopwatch.get();
    Assert.assertSame(Duration.Bottom(), nil.start().apply());
  }

}

/* Copyright 2015 Twitter, Inc. */
package com.twitter.concurrent;

import com.twitter.util.Duration;
import com.twitter.util.Future;
import com.twitter.util.MockTimer;
import org.junit.Test;

public class AsyncMeterCompilationTest {
  @Test
  public void testCreatePerSecond() {
    MockTimer timer = new MockTimer();
    AsyncMeter meter = AsyncMeter.perSecond(5, 5, timer);
  }

  @Test
  public void testCreateNewMeter() {
    MockTimer timer = new MockTimer();
    AsyncMeter meter = AsyncMeter.newMeter(5, Duration.fromMillisecondsJ(5), 5, timer);
  }

  @Test
  public void testAwaiting() {
    MockTimer timer = new MockTimer();
    AsyncMeter meter = AsyncMeter.perSecond(5, 5, timer);
    meter.await(0);
  }

  @Test
  public void testExtraWideAwaiting() {
    MockTimer timer = new MockTimer();
    AsyncMeter meter = AsyncMeter.perSecond(5, 5, timer);
    AsyncMeter.extraWideAwait(10, meter);
  }
}

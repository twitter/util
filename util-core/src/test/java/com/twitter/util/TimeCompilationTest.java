package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;

import scala.runtime.BoxedUnit;

public class TimeCompilationTest {

  @Test
  public void testTopAndBottom() {
    Time top = Time.Top();
    Time bottom = Time.Bottom();

    Assert.assertTrue(top.compareTo(bottom) > 0);
    Assert.assertEquals(Long.MAX_VALUE, top.nanos());
    Assert.assertEquals(Long.MIN_VALUE, bottom.nanos());
  }

  @Test
  public void testUndefined() {
    Time a = Time.Undefined();
    Time b = Time.fromSeconds(99);
    Time c = Time.fromSeconds(9999);

    Assert.assertEquals(a, b.max(a));
    Assert.assertEquals(c, c.min(a));
    Assert.assertEquals(Duration.Undefined(), b.diff(a));
  }

  @Test
  public void testFrom() {
    Time a = Time.fromSeconds(1);
    Time b = Time.fromMilliseconds(1000);
    Time c = Time.fromMicroseconds(1000000);
    Time d = Time.fromNanoseconds(1000000000);
    Time e = Time.fromFractionalSeconds(1.0);

    Assert.assertEquals(e, a);
    Assert.assertEquals(a, b);
    Assert.assertEquals(b, c);
    Assert.assertEquals(c, d);
  }

  @Test
  public void testMinAndMax() {
    Time a = Time.fromSeconds(9);
    Time b = Time.fromNanoseconds(9);

    Assert.assertEquals(a, a.max(Time.Bottom()));
    Assert.assertEquals(a, a.min(Time.Top()));
    Assert.assertEquals(a, a.max(b));
    Assert.assertEquals(b, b.min(a));
  }

  @Test
  public void testPlusAndMinus() {
    Time a = Time.fromMilliseconds(3333);
    Time b = a.plus(Duration.fromMilliseconds(2222));
    Time c = b.minus(Duration.fromMilliseconds(3333));

    Assert.assertEquals(a, a.plus(Duration.Zero()));
    Assert.assertEquals(a, a.minus(Duration.Zero()));
    Assert.assertEquals(5555, b.inMilliseconds());
    Assert.assertEquals(2222, c.inMilliseconds());
  }

  @Test
  public void testDiff() {
    Time a = Time.fromSeconds(6);
    Time b = Time.fromMilliseconds(2000);

    Assert.assertEquals(4000, a.diff(b).inMilliseconds());
  }

  @Test
  public void testFloor() {
    Time a = Time.fromNanoseconds(8888);
    Time b = a.floor(Duration.fromMicroseconds(1));

    Assert.assertEquals(8, b.inMicroseconds());
  }

  @Test
  public void testCeil() {
    Time a = Time.fromNanoseconds(6666);
    Time b = a.ceil(Duration.fromMicroseconds(1));

    Assert.assertEquals(7, b.inMicroseconds());
  }

  @Test
  public void testSince() {
    Time a = Time.now().plus(Duration.fromSeconds(10));
    Time b = Time.epoch().plus(Duration.fromSeconds(10));
    Time c = Time.fromMilliseconds(0);

    Assert.assertTrue(a.sinceNow().inSeconds() <= 10);
    Assert.assertTrue(b.sinceEpoch().inSeconds() <= 10);
    Assert.assertEquals(Long.MAX_VALUE, c.since(Time.Bottom()).inNanoseconds());
  }

  @Test
  public void testUntil() {
    Time a = Time.now().minus(Duration.fromMilliseconds(10));
    Time b = Time.epoch().minus(Duration.fromMilliseconds(10));
    Time c = Time.fromNanoseconds(0);

    Assert.assertTrue(a.untilNow().inSeconds() <= 10);
    Assert.assertTrue(b.untilEpoch().inSeconds() <= 10);
    Assert.assertEquals(Long.MAX_VALUE, c.until(Time.Top()).inNanoseconds());
  }

  @Test
  public void testWithTimeAt() {
    Time time = Time.fromMilliseconds(123456L);
    Time.withTimeAt(time, new Function<TimeControl, BoxedUnit>() {
      public BoxedUnit apply(TimeControl timeControl) {
        // Time.now() == time

        // you can control time via the `TimeControl` instance.
        timeControl.advance(Duration.fromSeconds(2));
        FuturePools.unboundedPool().apply(
          new Function0<BoxedUnit>() {
            public BoxedUnit apply() {
              // Time.now() == time + 2.seconds
              return BoxedUnit.UNIT;
            }
          }
        );
        return null;
      }
    });
  }

}

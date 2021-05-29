package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class DurationCompilationTest {

  @Test
  public void testTopAndBottom() {
    Duration top = Duration.Top();
    Duration bottom = Duration.Bottom();

    Assert.assertTrue(top.compareTo(bottom) > 0);
    Assert.assertEquals(Long.MAX_VALUE, top.nanos());
    Assert.assertEquals(Long.MIN_VALUE, bottom.nanos());
  }

  @Test
  public void testFrom() {
    Duration a = Duration.fromTimeUnit(1, TimeUnit.MINUTES);
    Duration b = Duration.fromSecondsJ(60);
    Duration c = Duration.fromMillisecondsJ(60000);
    Duration d = Duration.fromMicrosecondsJ(60000000);
    Duration e = Duration.fromNanoseconds(60000000000l);

    Assert.assertEquals(a, b);
    Assert.assertEquals(b, c);
    Assert.assertEquals(c, d);
    Assert.assertEquals(d, e);
  }

  @Test
  public void testConstructor() {
    Duration a = new Duration(1000000000);

    Assert.assertEquals(1, a.inSeconds());
  }

  @Test
  public void testPlusAndMinus() {
    Duration a = Duration.fromSecondsJ(1);
    Duration b = a.plus(Duration.fromMillisecondsJ(1000));
    Duration c = b.minus(Duration.fromMillisecondsJ(500));

    Assert.assertEquals(a, a.minus(Duration.Zero()));
    Assert.assertEquals(a, a.plus(Duration.Zero()));
    Assert.assertEquals(2000, b.inMillis());
    Assert.assertEquals(1500, c.inMillis());
    Assert.assertEquals(Duration.Top(), c.plus(Duration.Top()));
    Assert.assertEquals(Duration.Top(), c.minus(Duration.Bottom()));
  }

  @Test
  public void testMinMax() {
    Duration a = Duration.fromSecondsJ(4);
    Duration b = Duration.fromMicrosecondsJ(4);

    Assert.assertEquals(a, a.max(Duration.Zero()));
    Assert.assertEquals(a, a.max(b));
    Assert.assertEquals(b, a.min(b));
    Assert.assertEquals(a, a.min(Duration.Top()));
    Assert.assertEquals(b, b.max(Duration.Bottom()));
  }

  @Test
  public void testNeg() {
    Duration a = Duration.fromNanoseconds(1000000);
    Duration b = a.neg();
    Duration c = b.neg();

    Assert.assertEquals(-1, b.inMilliseconds());
    Assert.assertEquals(1, c.inMilliseconds());
  }

  @Test
  public void testAbs() {
    Duration a = Duration.fromMicrosecondsJ(-9999);
    Duration b = a.abs();
    Duration c = b.abs();

    Assert.assertEquals(9999000, b.inNanoseconds());
    Assert.assertEquals(9999000, c.inNanoseconds());
  }

  @Test
  public void testMulAndDiv() {
    Duration a = Duration.fromSecondsJ(2);
    Duration b = a.div(10);
    Duration c = b.mul(10);

    Assert.assertEquals(200, b.inMillis());
    Assert.assertEquals(2, c.inSeconds());
  }

  @Test
  public void testMulAndDivDouble() {
    Duration a = Duration.fromSecondsJ(9);
    Duration b = a.div(4.5);
    Duration c = b.mul(4.5);

    Assert.assertEquals(2, b.inSeconds());
    Assert.assertEquals(9, c.inSeconds());
  }

  @Test
  public void testRem() {
    Duration a = Duration.fromSecondsJ(5);
    Duration b = a.rem(Duration.fromSecondsJ(2));

    Assert.assertEquals(1000, b.inMilliseconds());
  }

  @Test
  public void testDiff() {
    Duration a = Duration.fromNanoseconds(999999);
    Duration b = Duration.fromNanoseconds(999);

    Assert.assertEquals(999000, a.diff(b).inNanoseconds());
  }

  @Test
  public void testFloor() {
    Duration a = Duration.fromMillisecondsJ(2222);
    Duration b = a.floor(Duration.fromSecondsJ(1));

    Assert.assertEquals(2, b.inSeconds());
  }

  @Test
  public void testCeil() {
    Duration a = Duration.fromMillisecondsJ(3333);
    Duration b = a.ceil(Duration.fromSecondsJ(1));

    Assert.assertEquals(4, b.inSeconds());
  }
}

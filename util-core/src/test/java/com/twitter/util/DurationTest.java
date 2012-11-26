package com.twitter.util;

// A compilation test for common Duration usage.

import junit.framework.TestCase;
import java.util.concurrent.TimeUnit;

// % gitg $sci 'Duration\.' -- '*.java' | drop 1 | 9 sed 's/.*(Duration.*)/\1/g' | 9 sed 's/(Duration\.[a-zA-Z0-9]+).*/\1/'|sort|uniq

public class DurationTest extends TestCase {
  public void testCommonUses() {
    Duration d;
    d = Duration.MaxValue();
    d = Duration.MinValue();
    d = Duration.apply(10, TimeUnit.MILLISECONDS);
    d = Duration.forever();
    d = Duration.fromTimeUnit(10, TimeUnit.SECONDS);
    d = Duration.zero();
  }
}
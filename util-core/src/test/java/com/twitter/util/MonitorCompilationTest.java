package com.twitter.util;

import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import org.junit.Test;

public class MonitorCompilationTest {

  @Test
  public void testSubclassingMonitor() {
    Monitor mon = new AbstractMonitor() {
      @Override
      public boolean handle(Throwable exc) {
        return false;
      }
    };
  }

  @Test
  public void testMonitors() {
    Monitor m1 = Monitors.instance();
    Monitor m2 = Monitors.get();
    String s1 = Monitors.using(NullMonitor.getInstance(), () -> "hello mon");
    String s2 = Monitors.restoring(() -> "yah mon");
    PartialFunction<Throwable, BoxedUnit> buster = Monitors.catcher();
    boolean b1 = Monitors.isActive();
  }

}

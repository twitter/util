package com.twitter.util;

import scala.runtime.BoxedUnit;

import org.junit.Test;

public class ClosableOnceCompilationTest {

  private static class ClosableOnceImpl extends AbstractClosableOnce {

    public int closeCount;

    @Override
    public Future<BoxedUnit> closeOnce(Time deadline) {
      closeCount++;
      return Future.Done();
    }

    @Override
    public Future<BoxedUnit> close(Time deadline) {
      return Future.Done();
    }
  }

  @Test
  public void closesOnce() {
    new ClosableOnceImpl();
  }
}

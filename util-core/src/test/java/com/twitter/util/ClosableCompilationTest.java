package com.twitter.util;

import org.junit.Test;
import scala.runtime.BoxedUnit;

public class ClosableCompilationTest {

  private static class ImplementsClosable implements Closable {
    @Override
    public Future<BoxedUnit> close(Time deadline) {
      return Future.Done();
    }

    @Override
    public Future<BoxedUnit> close(Duration after) {
      return Future.Done();
    }

    @Override
    public Future<BoxedUnit> close() {
      return Future.Done();
    }
  }

  private static class ExtendsClosable extends AbstractClosable {
    @Override
    public Future<BoxedUnit> close(Time deadline) {
      return Future.Done();
    }
  }

  @Test
  public void testClose() {
    Closable a = new ImplementsClosable();
    Closable b = new ExtendsClosable();

    a.close();
    b.close();
  }

  @Test
  public void testMake() {
    Closable closable = Closables.newClosable(
      new Function<Time, Future<BoxedUnit>>() {
        @Override
        public Future<BoxedUnit> apply(Time time) {
          return Future.Done();
        }
      }
    );

    closable.close();
  }

  @Test
  public void testAllAndSequence() {
    Closable a = new ImplementsClosable();
    Closable b = new ImplementsClosable();
    Closable c = new ImplementsClosable();

    Closables.all(a, b, c);
    Closables.sequence(a, b, c);
  }
}

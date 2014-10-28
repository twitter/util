package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class TimerCompilationTest {

  private final Callable<String> boom = new Callable<String>() {
    @Override
    public String call() {
      return "boom";
    }
  };

  @Test
  public void testDoAt() {
    Time.withCurrentTimeFrozen(new ExceptionalFunction<TimeControl, Void>() {
      @Override
      public Void applyE(TimeControl timeControl) throws Throwable {
        MockTimer timer = new MockTimer();
        Time at = Time.now().plus(Duration.fromMilliseconds(1));
        Future<String> f = timer.doAt(at, Function.ofCallable(boom));

        Assert.assertFalse(f.isDefined());
        timeControl.advance(Duration.fromMilliseconds(2));
        timer.tick();
        Assert.assertTrue(f.isDefined());
        Assert.assertEquals("boom", Await.result(f));
        return null;
      }
    });
  }

  @Test
  public void testDoLater() throws Exception {
    Time.withCurrentTimeFrozen(new ExceptionalFunction<TimeControl, Void>() {
      @Override
      public Void applyE(TimeControl timeControl) throws Throwable {
        MockTimer timer = new MockTimer();
        Future<String> f = timer.doLater(Duration.fromMilliseconds(1),
                                         Function.ofCallable(boom));

        Assert.assertFalse(f.isDefined());
        timeControl.advance(Duration.fromMilliseconds(2));
        timer.tick();
        Assert.assertTrue(f.isDefined());
        Assert.assertEquals("boom", Await.result(f));
        return null;
      }
    });
  }

  private static class MockCounter {
    private AtomicInteger underlying = new AtomicInteger(0);

    public Runnable incrementer() {
      return new Runnable() {
        @Override
        public void run() {
          underlying.incrementAndGet();
        }
      };
    }

    public int get() {
      return underlying.get();
    }
  }

  @Test
  public void testScheduleWhen() {
    Time.withCurrentTimeFrozen(new ExceptionalFunction<TimeControl, Void>() {
      @Override
      public Void applyE(TimeControl timeControl) throws Throwable {
        MockCounter counter = new MockCounter();
        MockTimer timer = new MockTimer();
        Time when = Time.now().plus(Duration.fromMilliseconds(1));
        timer.schedule(when, Function.ofRunnable(counter.incrementer()));

        timeControl.advance(Duration.fromMilliseconds(2));
        timer.tick();
        Assert.assertEquals(1, counter.get());
        return null;
      }
    });
  }

  @Test
  public void testCancelScheduleWhen() {
    Time.withCurrentTimeFrozen(new ExceptionalFunction<TimeControl, Void>() {
      @Override
      public Void applyE(TimeControl timeControl) throws Throwable {
        MockCounter counter = new MockCounter();
        MockTimer timer = new MockTimer();
        Time when = Time.now().plus(Duration.fromMilliseconds(1));
        TimerTask task = timer.schedule(when,
                                        Function.ofRunnable(counter.incrementer()));

        task.cancel();
        timeControl.advance(Duration.fromMilliseconds(2));
        timer.tick();
        Assert.assertEquals(0, counter.get());
        return null;
      }
    });
  }
}

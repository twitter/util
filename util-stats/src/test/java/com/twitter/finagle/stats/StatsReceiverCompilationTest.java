package com.twitter.finagle.stats;

import com.twitter.util.Future;

import org.junit.Test;

import java.lang.Integer;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import scala.Function0;
import scala.collection.immutable.Seq;

/**
 * Java compatibility layer for {@link com.twitter.finagle.stats.StatsReceiver}.
 */
public final class StatsReceiverCompilationTest {

  @Test
  public void testNullStatsReceiver() {
    StatsReceiver sr = NullStatsReceiver.get();
  }

  @Test
  public void testStatMethods() {
    InMemoryStatsReceiver sr = new InMemoryStatsReceiver(0);
    Stat stat = sr.stat("hello", "world");
    Callable<Integer> callable = new Callable<Integer>() {
      public Integer call() {
        return 3;
      }
    };
    Callable<Future<Integer>> fCallable = new Callable<Future<Integer>>() {
      public Future<Integer> call() {
        return Future.<Integer>value(new Integer(3));
      }
    };
    TimeUnit unit = TimeUnit.MILLISECONDS;

    JStats.time(stat, callable);
    JStats.time(stat, callable, unit);
    JStats.timeFuture(stat, fCallable);
    JStats.timeFuture(stat, fCallable, unit);
    stat.add(0);
  }

  @Test
  public void testGaugeMethods() {
    InMemoryStatsReceiver sr = new InMemoryStatsReceiver(0);
    Callable<Float> callable = new Callable<Float>() {
      public Float call() {
        return 3.0f;
      }
    };
    Gauge gauge = StatsReceivers.addGauge(sr, callable, "hello", "world");
    StatsReceivers.provideGauge(sr, callable, "hello", "world");
    gauge.remove();
  }

  @Test
  public void testCounterMethods() {
    InMemoryStatsReceiver sr = new InMemoryStatsReceiver(0);
    sr.isNull();
    Counter counter = sr.counter("hello", "world");
    counter.incr();
    counter.incr(100);
  }

  public void testScope() {
    InMemoryStatsReceiver sr = new InMemoryStatsReceiver(0);
    StatsReceiver sr2 = sr.scope();
    StatsReceiver sr3 = sr.scope("a");
    StatsReceiver sr4 = sr.scope("a", "s", "d");
    StatsReceiver sr5 = sr.scopeSuffix("bah").scope("foo");
  }

  @Test
  public void testAbstractStatsReceiver() {
    final StatsReceiver nullSr = NullStatsReceiver.get();
    StatsReceiver sr = new AbstractStatsReceiver() {

      @Override
      public Stat statImpl(Verbosity verbosity, scala.collection.Seq<String> name) {
        return nullSr.stat(verbosity, name.toSeq());
      }
      
      @Override
      public Object repr() {
        return this;
      }

      @Override
      public boolean isNull() {
        return false;
      }

      @Override
      public Counter counterImpl(Verbosity verbosity, scala.collection.Seq<String> name) {
        return nullSr.counter(name.toSeq());
      }

      @Override
      public Gauge addGaugeImpl(Verbosity verbosity, scala.collection.Seq<String> name, Function0<Object> f) {
        return nullSr.addGauge(verbosity, name.toSeq(), f);
      }
    };
  }
}

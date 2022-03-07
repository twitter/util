package com.twitter.compiletest;

import com.twitter.finagle.stats.AbstractStatsReceiver;
import com.twitter.finagle.stats.Counter;
import com.twitter.finagle.stats.Gauge;
import com.twitter.finagle.stats.InMemoryStatsReceiver;
import com.twitter.finagle.stats.JStats;
import com.twitter.finagle.stats.NullStatsReceiver;
import com.twitter.finagle.stats.Stat;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.stats.Verbosity;
import com.twitter.util.Future;

import org.junit.Test;

import java.lang.Integer;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import scala.Function0;

/**
 * Java compatibility test for {@link com.twitter.finagle.stats.StatsReceiver}.
 */
public final class StatsReceiverCompilationTest {

  @Test
  public void testNullStatsReceiver() {
    StatsReceiver sr = NullStatsReceiver.get();
  }

  @Test
  public void testStatMethods() {
    InMemoryStatsReceiver sr = new InMemoryStatsReceiver();
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
    InMemoryStatsReceiver sr = new InMemoryStatsReceiver();
    Gauge a = sr.addGauge(() -> 3.0f, "hello", "world");
    Gauge b = sr.addGauge(() -> 4.0f, Verbosity.Debug(), "foo", "bar");
    sr.provideGauge(() -> 3.0f, "hello", "world");
    a.remove();
    b.remove();
  }

  @Test
  public void testCounterMethods() {
    InMemoryStatsReceiver sr = new InMemoryStatsReceiver();
    sr.isNull();
    Counter counter = sr.counter("hello", "world");
    counter.incr();
    counter.incr(100);
  }

  @Test
  public void testScope() {
    InMemoryStatsReceiver sr = new InMemoryStatsReceiver();
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

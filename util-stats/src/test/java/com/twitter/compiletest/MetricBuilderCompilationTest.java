package com.twitter.compiletest;

import org.junit.Test;

import scala.Some;

import com.twitter.finagle.stats.Counter;
import com.twitter.finagle.stats.Gauge;
import com.twitter.finagle.stats.InMemoryStatsReceiver;
import com.twitter.finagle.stats.MetricBuilder;
import com.twitter.finagle.stats.MetricTypes;
import com.twitter.finagle.stats.Microseconds;
import com.twitter.finagle.stats.NoRoleSpecified;
import com.twitter.finagle.stats.Stat;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.stats.Verbosity;

/**
 * Java compatibility test for {@link com.twitter.finagle.stats.MetricBuilder}.
 */
public final class MetricBuilderCompilationTest {

  @Test
  public void testMetricBuilderConstruction() {
    StatsReceiver sr = new InMemoryStatsReceiver();
    sr.metricBuilder(MetricTypes.COUNTER_TYPE);
    sr.metricBuilder(MetricTypes.UNLATCHED_COUNTER_TYPE);
    sr.metricBuilder(MetricTypes.COUNTERISH_GAUGE_TYPE);
    sr.metricBuilder(MetricTypes.GAUGE_TYPE);
    sr.metricBuilder(MetricTypes.HISTOGRAM_TYPE);
  }

  @Test
  public void testWithMethods() {
    StatsReceiver sr = new InMemoryStatsReceiver();
    MetricBuilder mb = sr.metricBuilder(MetricTypes.COUNTER_TYPE)
      .withKeyIndicator(true)
      .withDescription("my cool metric")
      .withVerbosity(Verbosity.Debug())
      .withSourceClass(new Some<>("com.twitter.finagle.AwesomeClass"))
      .withIdentifier(new Some<>("/p/foo/bar"))
      .withUnits(Microseconds.getInstance())
      .withRole(NoRoleSpecified.getInstance())
      .withName("my", "very", "cool", "name")
      .withRelativeName("cool", "name")
      .withPercentiles(0.99, 0.88, 0.77);
  }

  @Test
  public void testConstructingMetrics() {
    StatsReceiver sr = new InMemoryStatsReceiver();
    MetricBuilder gmb = sr.metricBuilder(MetricTypes.GAUGE_TYPE);
    MetricBuilder smb = sr.metricBuilder(MetricTypes.HISTOGRAM_TYPE);
    MetricBuilder cmb = sr.metricBuilder(MetricTypes.COUNTER_TYPE);

    Gauge g = gmb.gauge(() -> 3.0f, "my", "cool", "gauge");
    Stat s = smb.histogram("my", "cool", "histogram");
    Counter c = cmb.counter("my", "cool", "counter");
  }
}

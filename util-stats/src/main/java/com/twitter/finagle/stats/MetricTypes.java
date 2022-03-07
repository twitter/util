package com.twitter.finagle.stats;

/**
 * Java APIs for {@link MetricBuilder.MetricType}
 */
public class MetricTypes {
  private MetricTypes() {
    throw new IllegalStateException();
  }

  public static final MetricBuilder.MetricType COUNTER_TYPE = MetricBuilder.CounterType$.MODULE$;

  public static final MetricBuilder.MetricType UNLATCHED_COUNTER_TYPE = MetricBuilder.UnlatchedCounter$.MODULE$;

  public static final MetricBuilder.MetricType COUNTERISH_GAUGE_TYPE = MetricBuilder.CounterishGaugeType$.MODULE$;

  public static final MetricBuilder.MetricType GAUGE_TYPE = MetricBuilder.GaugeType$.MODULE$;

  public static final MetricBuilder.MetricType HISTOGRAM_TYPE = MetricBuilder.HistogramType$.MODULE$;
}

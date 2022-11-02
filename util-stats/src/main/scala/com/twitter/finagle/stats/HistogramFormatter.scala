package com.twitter.finagle.stats

import com.twitter.app.GlobalFlag

object format
    extends GlobalFlag[String](
      "commonsmetrics",
      "Format style for metric names (ostrich|commonsmetrics|commonsstats)"
    ) {
  private[stats] val Ostrich = "ostrich"
  private[stats] val CommonsMetrics = "commonsmetrics"
  private[stats] val CommonsStats = "commonsstats"
}

private[twitter] object HistogramFormatter {
  def default: HistogramFormatter =
    format() match {
      case format.Ostrich => Ostrich
      case format.CommonsMetrics => CommonsMetrics
      case format.CommonsStats => CommonsStats
    }

  /**
   * The default behavior for formatting as done by Commons Metrics.
   *
   * See Commons Metrics' `Metrics.sample()`.
   */
  object CommonsMetrics extends HistogramFormatter {
    def labelPercentile(p: Double): String = {
      // this has a strange quirk that p999 gets formatted as p9990
      // Round for precision issues; e.g. 0.9998999... converts to "p9998" with a direct int cast.
      val gname: String = "p" + (p * 10000).round
      if (3 < gname.length && ("00" == gname.substring(3))) {
        gname.substring(0, 3)
      } else {
        gname
      }
    }

    override def labelMin: String = "min"

    override def labelMax: String = "max"

    override def labelAverage: String = "avg"
  }

  /**
   * Replicates the behavior for formatting Ostrich stats.
   *
   * See Ostrich's `Distribution.toMap`.
   */
  object Ostrich extends HistogramFormatter {
    def labelPercentile(p: Double): String = {
      p match {
        case 0.5d => "p50"
        case 0.9d => "p90"
        case 0.95d => "p95"
        case 0.99d => "p99"
        case 0.999d => "p999"
        case 0.9999d => "p9999"
        case _ =>
          val padded = (p * 10000).toInt
          s"p$padded"
      }
    }

    override def labelMin: String = "minimum"

    override def labelMax: String = "maximum"

    override def labelAverage: String = "average"
  }

  /**
   * Replicates the behavior for formatting Commons Stats stats.
   *
   * See Commons Stats' `Stats.getVariables()`.
   */
  object CommonsStats extends HistogramFormatter {
    def labelPercentile(p: Double): String =
      s"${p * 100}_percentile".replace(".", "_")

    override def labelMin: String = "min"

    override def labelMax: String = "max"

    override def labelAverage: String = "avg"
  }
}

sealed trait HistogramFormatter {
  def labelPercentile(p: Double): String

  def labelMin: String = "min"

  def labelMax: String = "max"

  def labelAverage: String = "avg"

  def labelCount: String = "count"

  def labelSum: String = "sum"

}

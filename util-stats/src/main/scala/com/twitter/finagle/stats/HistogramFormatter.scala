package com.twitter.finagle.stats

/**
 * The default behavior for formatting, align with Commons Metrics.
 */
private[twitter] object HistogramFormatter {
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

  val labelMin: String = "min"

  val labelMax: String = "max"

  val labelAverage: String = "avg"

  val labelCount: String = "count"

  val labelSum: String = "sum"
}

package com.twitter.finagle.stats.exp

private[twitter] object HistogramComponent {
  case object Min extends HistogramComponent
  case object Max extends HistogramComponent
  case object Avg extends HistogramComponent
  case object Sum extends HistogramComponent
  case object Count extends HistogramComponent
  case class Percentile(decimal: Double) extends HistogramComponent

  val DefaultPercentiles: Seq[HistogramComponent] =
    Seq(Percentile(0.50), Percentile(0.99), Percentile(0.999), Percentile(0.9999))
}

private[twitter] sealed trait HistogramComponent

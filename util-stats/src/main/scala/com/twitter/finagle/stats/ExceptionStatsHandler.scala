package com.twitter.finagle.stats

import com.twitter.util.Throwables

/**
 * API for deciding where request exceptions are reported in stats.
 * Typical implementations may report any cancellations or validation
 * errors separately so success rate can from valid non cancelled requests.
 */
object ExceptionStatsHandler {
  private[stats] val Failures = "failures"
  private[stats] val SourcedFailures = "sourcedfailures"

  /**
   * Returns the set of paths to record an exception against.
   *
   * @param t the exception being recorded
   * @param labels the category, usually 'failures' and 'sourcedfailures/service'
   * @param rollup whether to replicate RollupStatsReceiver
   * @return the complete set of stat paths to record against
   */
  private[stats] def statPaths(
    t: Throwable,
    labels: Seq[Seq[String]],
    rollup: Boolean
  ): Seq[Seq[String]] = {
    val exceptionChain = Throwables.mkString(t)

    val suffixes = if (rollup) {
      exceptionChain.inits.toSeq
    } else {
      Seq(exceptionChain, Nil)
    }

    labels.flatMap { prefix =>
      suffixes.map { suffix =>
        prefix ++ suffix
      }
    }
  }
}

/**
 * Exception Stats Recorder.
 */
trait ExceptionStatsHandler {
  def record(statsReceiver: StatsReceiver, t: Throwable): Unit
}

/**
 * Basic implementation of exception stat recording that
 * allows exceptions to be categorised and optional rollup.
 *
 * @param categorizer function for customer categories, None defaults to 'failures'
 * @param sourceFunction function for extracting a source when configured
 * @param rollup whether to report chained exceptions like RollupStatsReceiver
 */
class CategorizingExceptionStatsHandler(
    categorizer: Throwable => Option[String] = _ => None,
    sourceFunction: Throwable => Option[String] = _ => None,
    rollup: Boolean = true)
  extends ExceptionStatsHandler
{
  import ExceptionStatsHandler._

  def record(statsReceiver: StatsReceiver, t: Throwable): Unit = {
    val categoryLabel = Seq(categorizer(t).getOrElse(Failures))

    val labels = sourceFunction(t) match {
      case Some(service) => Seq(categoryLabel, Seq(SourcedFailures, service))
      case None => Seq(categoryLabel)
    }

    val paths = statPaths(t, labels, rollup)

    paths.foreach { path =>
      statsReceiver.counter(path: _*).incr()
    }
  }
}
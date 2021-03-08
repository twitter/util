package com.twitter.finagle.stats

import com.twitter.util.Throwables

/**
 * API for deciding where request exceptions are reported in stats.
 * Typical implementations may report any cancellations or validation
 * errors separately so success rate can from valid non cancelled requests.
 *
 * @see [[Null]] for a no-op handler.
 */
object ExceptionStatsHandler {
  private[finagle] val Failures = "failures"
  private[stats] val SourcedFailures = "sourcedfailures"

  /**
   * An [[ExceptionStatsHandler]] which does nothing on `record`.
   */
  val Null: ExceptionStatsHandler = new ExceptionStatsHandler {
    def record(statsReceiver: StatsReceiver, t: Throwable): Unit = ()
  }

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

    labels.flatMap { prefix => suffixes.map { suffix => prefix ++ suffix } }
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
    extends ExceptionStatsHandler {
  import ExceptionStatsHandler._

  private[this] val underlying: ExceptionStatsHandler = {
    val mkLabel: Throwable => String =
      t => categorizer(t).getOrElse(Failures)
    new MultiCategorizingExceptionStatsHandler(mkLabel, _ => Set.empty, sourceFunction, rollup)
  }

  def record(statsReceiver: StatsReceiver, t: Throwable): Unit =
    underlying.record(statsReceiver, t)
}

/**
 * Basic implementation of exception stat recording that
 * supports flags and source labeling, and rollup.
 *
 * For example,
 *
 * {{{
 *     import java.net.{ConnectException, InetSocketAddress}
 *     val f1 =
 *       Failure.adapt(
 *         new CancelledConnectionException(new ConnectException),
 *         FailureFlags.Retryable|FailureFlags.Interrupted)
 *     val f2 =
 *       new IndividualRequestTimeoutException(1.seconds)
 *     f2.serviceName = "myservice"
 *
 *     val handler =
 *       new MultiCategorizingExceptionStatsHandler(
 *         mkFlags = Failure.flagsOf,
 *         mkSource= SourcedException.unapply)
 *
 * }}}
 *
 * `f1` is recorded as:
 *   failures: 1
 *   failures/interrupted: 1
 *   failures/interrupted/com.twitter.finagle.Failure: 1
 *   failures/interrupted/com.twitter.finagle.Failure/com.twitter.finagle.CancelledConnectionException: 1
 *   failures/interrupted/com.twitter.finagle.Failure/com.twitter.finagle.CancelledConnectionException/java.net.ConnectException: 1
 *   failures/restartable: 1
 *   failures/restartable/com.twitter.finagle.Failure: 1
 *   failures/restartable/com.twitter.finagle.Failure/com.twitter.finagle.CancelledConnectionException: 1
 *   failures/restartable/com.twitter.finagle.Failure/com.twitter.finagle.CancelledConnectionException/java.net.ConnectException: 1
 *
 * `f2` is recorded as:
 *   failures
 *   failures/com.twitter.finagle.IndividualRequestTimeoutException
 *   sourcedfailures/myservice
 *   sourcedfailures/myservice/com.twitter.finagle.IndividualRequestTimeoutException
 *
 * @param mkLabel label prefix, default to 'failures'
 * @param mkFlags  extracting flags if the Throwable is a Failure
 * @param mkSource extracting source when configured
 * @param rollup whether to report chained exceptions like RollupStatsReceiver
 *
 */
private[finagle] class MultiCategorizingExceptionStatsHandler(
  mkLabel: Throwable => String = _ => ExceptionStatsHandler.Failures,
  mkFlags: Throwable => Set[String] = _ => Set.empty,
  mkSource: Throwable => Option[String] = _ => None,
  rollup: Boolean = true)
    extends ExceptionStatsHandler {
  import ExceptionStatsHandler._

  def record(statsReceiver: StatsReceiver, t: Throwable): Unit = {
    val parentLabel: String = mkLabel(t)

    val flags = mkFlags(t)
    val flagLabels: Seq[Seq[String]] =
      if (flags.isEmpty) Seq(Seq(parentLabel))
      else flags.toSeq.map(Seq(parentLabel, _))

    val labels: Seq[Seq[String]] = mkSource(t) match {
      case Some(service) => flagLabels :+ Seq(SourcedFailures, service)
      case None => flagLabels
    }

    val paths: Seq[Seq[String]] = statPaths(t, labels, rollup)

    if (flags.nonEmpty) statsReceiver.counter(parentLabel).incr()
    paths.foreach { path => statsReceiver.counter(path: _*).incr() }
  }
}

package com.twitter.util

import com.twitter.conversions.time._
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

/**
 * This uses `SingleShotTime` because scheduling work changes the
 * performance of future scheduling (particularly because this
 * does not wait for the tasks to be executed).
 */
// ./sbt 'project util-benchmark' 'run .*TimerBenchmark.*'
@State(Scope.Benchmark)
@Warmup(batchSize = 250)
@Measurement(batchSize = 1000)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(4)
class TimerBenchmark extends StdBenchAnnotations {

  private[this] val period = 5.minutes
  private[this] val wayLater = Time.now + 20.minutes

  private[this] val baseline = Timer.Nil
  @volatile private[this] var javaUtil: JavaTimer = _
  @volatile private[this] var executor: ScheduledThreadPoolTimer = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    javaUtil = new JavaTimer(true)
    executor = new ScheduledThreadPoolTimer(poolSize = 1, makeDaemons = true)
  }

  @TearDown(Level.Iteration)
  def tearDown(): Unit = {
    javaUtil.stop()
    // without this, the heap fills up with pending tasks.
    executor.stopWithPending()
  }

  /**
   * Note: this really just benchmarks how expensive it is to "enqueue"
   * the work, not expense of "dequeueing" and running.
   */
  private[this] def scheduleOnce(timer: Timer): TimerTask =
    timer.schedule(wayLater) { () }

  /**
   * Note: this really just benchmarks how expensive it is to "enqueue"
   * the work, not expense of "dequeueing" and running.
   */
  private[this] def schedulePeriodic(timer: Timer): TimerTask =
    timer.schedule(wayLater, period) { () }

  @Benchmark
  def scheduleOnceBaseline: TimerTask =
    scheduleOnce(baseline)

  @Benchmark
  def scheduleOnceJavaUtil: TimerTask =
    scheduleOnce(javaUtil)

  @Benchmark
  def scheduleOnceExecutor: TimerTask =
    scheduleOnce(executor)

  @Benchmark
  def schedulePeriodicBaseline: TimerTask =
    schedulePeriodic(baseline)

  @Benchmark
  def schedulePeriodicJavaUtil: TimerTask =
    schedulePeriodic(javaUtil)

  @Benchmark
  def schedulePeriodicExecutor: TimerTask =
    schedulePeriodic(executor)

}

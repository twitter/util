package com.twitter.jvm

import java.io.OutputStream
import java.lang.management.ManagementFactory
import java.nio.{ByteBuffer, ByteOrder}

import scala.collection.mutable

import com.twitter.conversions.time._
import com.twitter.util.{Duration, Future, Promise, Stopwatch, Time}

/**
 * A CPU profile.
 */
case class CpuProfile(
  // Counts of each observed stack.
  counts: Map[Seq[StackTraceElement], Long],
  // The amount of time over which the sample was taken.
  duration: Duration,
  // The number of samples taken.
  count: Int,
  // The number of samples missed.
  missed: Int
) {

  /**
   * Write a Google pprof-compatible profile to `out`. The format is
   * documented here:
   *
   *   http://google-perftools.googlecode.com/svn/trunk/doc/cpuprofile-fileformat.html
   */
  def writeGoogleProfile(out: OutputStream): Unit = {
    var next = 1
    val uniq = mutable.HashMap[StackTraceElement, Int]()
    val word = ByteBuffer.allocate(8)
    word.order(ByteOrder.LITTLE_ENDIAN)
    def putWord(n: Long): Unit = {
      word.clear()
      word.putLong(n)
      out.write(word.array())
    }

    def putString(s: String): Unit = {
      out.write(s.getBytes)
    }

    putString("--- symbol\nbinary=%s\n".format(Jvm().mainClassName))
    for ((stack, _) <- counts; frame <- stack if !uniq.contains(frame)) {
      putString("0x%016x %s\n".format(next, frame.toString))
      uniq(frame) = next
      next += 1
    }
    putString("---\n--- profile\n")
    for (w <- Seq(0, 3, 0, 1, 0))
      putWord(w)

    for ((stack, n) <- counts if stack.nonEmpty) {
      putWord(n)
      putWord(stack.size)
      for (frame <- stack)
        putWord(uniq(frame))
    }
    putWord(0)
    putWord(1)
    putWord(0)
    out.flush()
  }
}

object CpuProfile {

  // (class name, method names) that say they are runnable, but are actually doing nothing.
  private[this] val IdleClassAndMethod: Set[(String, String)] = Set(
    ("sun.nio.ch.EPollArrayWrapper", "epollWait"),
    ("sun.nio.ch.KQueueArrayWrapper", "kevent0"),
    ("java.net.SocketInputStream", "socketRead0"),
    ("java.net.SocketOutputStream", "socketWrite0"),
    ("java.net.PlainSocketImpl", "socketAvailable"),
    ("java.net.PlainSocketImpl", "socketAccept")
  )

  /**
   * When looking for RUNNABLEs, the JVM's notion of runnable differs from the
   * from kernel's definition and for some well known cases, we can filter
   * out threads that are actually asleep.
   * See http://www.brendangregg.com/blog/2014-06-09/java-cpu-sampling-using-hprof.html
   */
  private[jvm] def isRunnable(stackElem: StackTraceElement): Boolean =
    !IdleClassAndMethod.contains((stackElem.getClassName, stackElem.getMethodName))

  /**
   * Profile CPU usage of threads in `state` for `howlong`, sampling
   * stacks at `frequency` Hz.
   *
   * As an example, using Nyquist's sampling theorem, we see that
   * sampling at 100Hz will accurately represent components 50Hz or
   * less; ie. any stack that contributes 2% or more to the total CPU
   * time.
   *
   * Note that the maximum sampling frequency is set to 1000Hz.
   * Anything greater than this is likely to consume considerable
   * amounts of CPU while sampling.
   *
   * The profiler will discount its own stacks.
   *
   * TODO:
   *
   *   - Should we synthesize GC frames? GC has significant runtime
   *   impact, so it seems nonfaithful to exlude them.
   *   - Limit stack depth?
   */
  def record(howlong: Duration, frequency: Int, state: Thread.State): CpuProfile = {
    require(frequency < 1000)

    // TODO: it may make sense to write a custom hash function here
    // that needn't traverse the all stack trace elems. Usually, the
    // top handful of frames are distinguishing.
    val counts = mutable.HashMap[Seq[StackTraceElement], Long]()
    val bean = ManagementFactory.getThreadMXBean()
    val elapsed = Stopwatch.start()
    val end = howlong.fromNow
    val period = (1000000 / frequency).microseconds
    val myId = Thread.currentThread().getId()
    var next = Time.now

    var n = 0
    var nmissed = 0

    while (Time.now < end) {
      for (thread <- bean.dumpAllThreads(false, false)
        if thread.getThreadState() == state
          && thread.getThreadId() != myId) {
        val s = thread.getStackTrace().toSeq
        if (s.nonEmpty) {
          val include = state != Thread.State.RUNNABLE || isRunnable(s.head)
          if (include)
            counts(s) = counts.getOrElse(s, 0L) + 1L
        }
      }

      n += 1
      next += period

      while (next < Time.now && next < end) {
        nmissed += 1
        next += period
      }

      val sleep = math.max((next - Time.now).inMilliseconds, 0)
      Thread.sleep(sleep)
    }

    CpuProfile(counts.toMap, elapsed(), n, nmissed)
  }

  def record(howlong: Duration, frequency: Int): CpuProfile =
    record(howlong, frequency, Thread.State.RUNNABLE)

  /**
   * Call `record` in a thread with the given parameters, returning a
   * `Future` representing the completion of the profile.
   */
  def recordInThread(howlong: Duration, frequency: Int, state: Thread.State): Future[CpuProfile] = {
    val p = new Promise[CpuProfile]
    val thr = new Thread("CpuProfile") {
      override def run(): Unit = {
        p.setValue(record(howlong, frequency, state))
      }
    }
    thr.start()
    p
  }

  def recordInThread(howlong: Duration, frequency: Int): Future[CpuProfile] =
    recordInThread(howlong, frequency, Thread.State.RUNNABLE)
}

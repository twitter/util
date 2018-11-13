Basics
======

Gauges
------

`Gauges`_ measure discrete values at the time they are sampled. These are useful for measuring queue
depths and pool sizes.

.. code-block:: scala

    // scala
    import java.util.ArrayBlockingQueue

    val aQueue = new ArrayBlockingQueue[Job]()

    val queueSize = statsReceiver.addGauge("queue_size") {
      aQueue.size
    }

.. code-block:: java

    // java
    import com.twitter.finagle.stats.Gauge;
    import java.util.ArrayBlockingQueue;

    ArrayBlockingQueue<Job> aQueue =
      new ArrayBlockingQueue<Job>();

    Gauge queueSize = StatsReceivers.addGauge(
      statsReceiver,
      new Callable<Float>() {
        @Override public Float call() { return aQueue.size(); }
      },
      "queue_size"
    );

Counters
--------

`Counters`_ are used to, well, count how often something happens. Common examples include the number
of requests dispatched and failure counts.

.. code-block:: scala

    // scala
    val numRequests = statsReceiver.counter("num_requests")

    numRequests.incr()

.. code-block:: java

    // java
    import com.twitter.finagle.stats.Counter;

    Counter numRequests =
      statsReceiver.counter(statsReceiver, "num_requests");

    numRequests.incr();

Stats
-----

`Stats`_ provide a distribution of values that are seen, including percentiles, averages and maximums.
Stats are quite useful for measuring the n-th percentiles for latency of requests or capturing the
distribution of work batches.

.. code-block:: scala

    // scala
    import com.twitter.finagle.stats.Stat
    import java.util.concurrent.TimeUnit

    val latency = statsReceiver.stat("latency_ms")

    Stat.time(latency, TimeUnit.MILLISECONDS) {
      twiddleBits();
    }

.. code-block:: java

    // java
    import com.twitter.finagle.stats.Stat;
    import com.twitter.finagle.stats.JStat;
    import java.util.concurrent.TimeUnit;

    Stat latency = statsReceiver.stat(statsReceiver, "latency_ms");

    JStat.time(
      latency,
      new Callable<Void>() {
        @Override public void call() { twiddleBits(); }
      },
      TimeUnit.MILLISECONDS
    );


Verbosity Levels
----------------

Each metric created via a `StatsReceiver`_ has a **verbosity level** (i.e., "debug" or "default")
attached to it. Distinguishing verbosity levels for metrics is optional and is up to a concrete
implementation. Doing this, however, helps to separate debug metrics (only helpful in
troubleshooting) from their operationally-required counterparts (provide visibility into a healthy
process) thus potentially reducing the observability cost.

.. NOTE::

    Unless an explicit ``Verbosity`` is passed, ``Verbosity.Default`` is used.

.. code-block:: scala

    // scala
    import com.twitter.finagle.stats.Verbosity

    val someDebugEvent = statsReceiver.counter(Verbosity.Debug, "some_debug_event")

.. code-block:: java

    // java
    import java.util.concurrent.Counter;
    import java.util.concurrent.Verbosity;

    Counter someDebugEvent = statsReceiver.counter(Verbosity.Debug(), "some_debug_event")

.. _Gauges: https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/Gauge.scala
.. _Counters: https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/Counter.scala
.. _Stats: https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/Stat.scala
.. _StatsReceiver: https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/StatsReceiver.scala



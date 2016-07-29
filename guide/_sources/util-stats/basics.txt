Basics
======

Gauges
------

`Gauges <https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/Gauge.scala>`_
measure discrete values at the time they are sampled.
These are useful for measuring queue depths and pool sizes.

.. code-block:: scala

    // scala
    private val aQueue = new ArrayBlockingQueue[Job]()

    private val queueSize = statsReceiver.addGauge("queue_size") {
      aQueue.size
    }

.. code-block:: java

    // java
    private final ArrayBlockingQueue[Job] aQueue =
      new ArrayBlockingQueue[Job]();

    private final Gauge queueSize = StatsReceivers.addGauge(
      statsReceiver,
      new Callable<Float>() {
        @Override public Float call() { return aQueue.size(); }
      },
      "queue_size"
    );

Counters
--------

`Counters <https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/Counter.scala>`_
are used to, well, count how often something happens.
Common examples include the number of requests dispatched and failure counts.

.. code-block:: scala

    // scala
    private val numRequests = statsReceiver.counter("num_requests")

    numRequests.incr()

.. code-block:: java

    // java
    private final Counter numRequests =
      StatsReceivers.counter(statsReceiver, "num_requests");

    numRequests.incr();

Stats
-----

`Stats <https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/Stat.scala>`_
provide a distribution of values that are seen, including percentiles, averages and maximums.
Stats are quite useful for measuring the n-th percentiles for latency
of requests or capturing the distribution of work batches.

.. code-block:: scala

    // scala
    private val latency = statsReceiver.stat("latency_ms")

    Stat.time(latency, TimeUnit.MILLISECONDS) {
      twiddleBits();
    }

.. code-block:: java

    // java
    private final Stat latency = StatsReceivers.stat(statsReceiver, "latency_ms");

    JStat.time(
      latency,
      new Callable<Void>() {
        @Override public void call() { twiddleBits(); }
      },
      TimeUnit.MILLISECONDS
    );

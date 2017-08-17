User Guide
==========

.. _choosing_impl:

Choosing the stats implementation to use for Finagle
----------------------------------------------------

Finagle uses util-stats heavily throughout and it is important for a well-run service to have this
wired up properly. Finagle uses `LoadService`_ in order to discover implementations on the
classpath. The standard implementation comes via `finagle-stats`_.

If multiple implementations are found, metrics are reported to **all** of the implementations via a
`BroadcastStatsReceiver`_. This feature is useful when :ref:`transitioning from one library
implementation to another <migrating>`. Once you are done migrating, in the interests of efficiency
and memory pressure, it is wise to validate that only a single implementation is loaded via your
dependencies. This also implies that developers of libraries should not depend on a specific
implementation and only depend on util-stats.

If no implementations are found, metrics are reported to a `NullStatsReceiver`_ which is essentially
``/dev/null``.

Leveraging Verbosity Levels
---------------------------

Introducing a new application metric (i.e., a histogram or a counter) is always a trade-off between
its operational value and its cost within observability. Verbosity levels for StatsReceivers are
aiming to reduce the observability cost of Finagle-based services by allowing for a granular control
over which metrics are being exported in the application’s steady state.

Similar to log levels, verbosity levels provide means to mark a given metric as “debug” and
potentially (assuming supported in implementation) prevent it from being exported under standard
operations.

Limiting the number of exported metrics via verbosity levels can reduce applications' operational
cost. However taking this to extremes may drastically affect operability of your service. We
recommend using your judgment to make sure blacklisting a given metric will not reduce a process'
visibility.

Measuring the latency of operations
-----------------------------------
A common need for clients is to capture the latency distribution, often of asynchronous calls such
as those returning `Futures`_. This can be done easily via the `Stat.time`_ and `Stat.timeFuture`_
methods.

Prefer storing counters and stats in member variables
-----------------------------------------------------
While it may be tempting to create your metrics inline, whenever possible you should initialize
these up front, typically in a class constructor. This is because while capturing usage via
``Stat.add`` and ``Counter.incr`` is optimized for performance, the construction of a ``Stat`` and
``Counter`` is not.

.. code-block:: scala

    class CountingExample(statsReceiver: StatsReceiver) {
      // create the Counter once and store it in a member
      private val numSheep = statsReceiver.counter("sheep")

      // incr() called on the member variable many times
      // in the application's lifecycle.
      def bedtime(n: Int): Unit = {
        numSheep.incr(n)
        // nifty business logic here
      }
    }

Note that sometimes this is not possible due to business logic, for example, when a counter is named
based on current context. However, care should be taken to ensure that the cardinality of metrics
created is bounded or you will run the risk of a memory leak (for example by including application
ids in your metric names).

.. _gauges_in_members:

Store gauges in member variables
--------------------------------
While your application code is not likely to refer to the value returned from
``StatsReceiver.addGauge``, you should store it in a member variable. This is due to the common
implementation of ``Gauge`` being ``CumulativeGauge``, which uses `java.lang.ref.WeakReference`_.
If a ``CumulativeGauge`` is not held by a strong reference, *the gauge is eligible for garbage
collection*, which would cause your metric to disappear unexpectedly at runtime.

.. code-block:: scala

    // bad, don't do this:
    class BadExample(queue: BlockingQueue[Work], statsReceiver: StatsReceiver) {
      statsReceiver.addGauge("num_waiting") { queue.size }
      // code that may do incredible things but you'd never know
      // the size of the queue.
    }

    // do this instead:
    class GoodExample(queue: BlockingQueue[Work], statsReceiver: StatsReceiver) {
      private val queueDepth =
        statsReceiver.addGauge("num_waiting") { queue.size }
      // code that does incredible things AND you know the
      // size of the queue.
    }

Prefer `addGauge` over `provideGauge`
-------------------------------------
``StatsReceiver`` offers two similar methods, ``addGauge`` and ``provideGauge``, and whenever
possible ``addGauge`` should be preferred. ``provideGauge`` is basically a call to ``addGauge``
along with code that holds a strong reference to the gauge in a global linked list. Recall from the
:ref:`previous note <gauges_in_members>` that ``Gauges`` need to held in strong references and as
such you should only rely on ``provideGauge`` when you do not have a place to keep a strong
reference.

Testing code that use StatsReceivers
------------------------------------
If your tests do not need to verify the value of stats, you should use a `NullStatsReceiver`_
which provides a no-op implementation. If your tests need to verify the value of stats, you should
use an `InMemoryStatsReceiver`_ which provides ``ReadableCounters`` and ``ReadableStats`` that
enable simpler testing.

Usage from Java
---------------
There are Java-friendly mechanisms in the ``StatsReceivers`` object (note the trailing **s**) for
creating counters, gauges and stats. In addition ``JStats`` is available for measuring latency.

Thread-safety
-------------
It is expected that implementations of ``StatsReceivers`` and their associated counters/gauges/stats
themselves are thread-safe and safe to use across threads.

The caveat is that because ``Gauges`` run a function when they are read, the code you provide as the
function **must also** be thread-safe.

Access needed to a StatsReceiver in an inconvenient place
---------------------------------------------------------
Ideally classes would be passed a properly scoped ``StatsReceiver`` in their constructor but this
isn’t always simple or feasible. This may be due to various reasons such as legacy code, code in a
static initializer or a Scala object. In these cases, if you are depending on finagle-core, you
should consider using one of ``DefaultStatsReceiver``, ``ClientStatsReceiver`` or
``ServerStatsReceiver``. These are initialized via Finagle’s ``LoadService`` mechanism.

Viewing per-node metrics
------------------------
This is possible, however the mechanism varies depending on which “application” framework you are
using.

Via TwitterServer/finagle-stats — the `HTTP admin interface`_ responds with json at
``/admin/metrics.json`` and there is a web UI for watching them in real-time at ``/admin/metrics``.

.. _Load Service: https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/util/LoadService.scala
.. _finagle-stats: https://github.com/twitter/finagle/tree/master/finagle-stats
.. _BroadcastStatsReceiver: https://github.com/twitter/util/blob/master/util-stats/src/main/scala/com/twitter/finagle/stats/BroadcastStatsReceiver.scala
.. _NullStatsReceiver: https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/NullStatsReceiver.scala
.. _Futures: https://twitter.github.io/finagle/guide/Futures.html
.. _Stat.time: https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/Stat.scala
.. _Stat.timeFuture: https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/Stat.scala
.. _java.lang.ref.WeakReference: http://docs.oracle.com/javase/8/docs/api/java/lang/ref/WeakReference.html
.. _InMemoryStatsReceiver: https://github.com/twitter/util/blob/master/util-stats/src/main/scala/com/twitter/finagle/stats/InMemoryStatsReceiver.scala
.. _HTTP admin interface: https://twitter.github.io/twitter-server/Features.html#http-admin-interface

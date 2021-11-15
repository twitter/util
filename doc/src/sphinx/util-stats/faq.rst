FAQ
===

Why are util-stats in the com.twitter.finagle.stats package?
------------------------------------------------------------
This is a historical artifact that reflects how the code was
originally written as part of `Finagle <https://twitter.github.io/finagle/>`_
but turned out to not actually depend on anything Finagle-specific.
The code was moved to `util <https://twitter.github.io/util/>`_
in order to allow usage without depending on finagle-core.
To ease the transition, the package namespace was left as is.

Why were there two(!) different implementations?
--------------------------------------------------
This is somewhat historical, as early on in Twitter, Ostrich was created for Scala developers.
`finagle-stats` was written quite a bit later in order to
improve upon its implementation in terms of
allocation efficiency and histogram precision.

Why is `NullStatsReceiver` being used?
--------------------------------------
`NullStatsReceiver` is used when no metrics implementation is found on the classpath.
The section on :ref:`choosing the stats implementation <choosing_impl>` covers how to
pick an implementation for your application. Twitter runs its production
services with `finagle-stats` and it is available on
`Github <https://github.com/twitter/finagle/tree/master/finagle-stats>`_
and `Maven Central <https://search.maven.org/#search%7Cga%7C1%7Cfinagle-stats>`_.

.. _why_finagle_stats:

Why migrate to finagle-stats?
-----------------------------
The `finagle-stats` library is designed to replace Ostrich which was
formerly supported by Twitter. The comparison table below
illustrates the advantages of `finagle-stats` over Ostrich.

+------------------------------+----------+-----------------+
|                              | Ostrich  | finagle-stats   |
+==============================+==========+=================+
| Allocations per `Stat.add`   | 0 bytes  | 0 bytes         |
+------------------------------+----------+-----------------+
| Highest histogram resolution | p9999    | p9999           |
+------------------------------+----------+-----------------+
| Sampling error               |  ±5%     | ±0.5%           |
+------------------------------+----------+-----------------+

`finagle-stats` allows you to visualize and download the full details of `Stat`
histograms. This is available on the
`/admin/histogram <https://twitter.github.io/twitter-server/Admin.html#admin-histograms>`_
admin page.

Are metric values reset or latched after an observability collection?
---------------------------------------------------------------------
``Stats`` are latched by all two implementations.
This means that for each observability collection window,
you will see that window’s distribution of values.

There is no notion of latching/resetting a ``Gauge``, as it is
always a discrete point in time value. This is the case across
all three implementations, except for CounterishGauges, which 
creates a gauge that models an unlatched counter.

``Counters`` have different behavior depending on which implementation
is being used. This in turn has an impact in how you write your
queries for your dashboards and alerts. Both Commons Metrics :sup:`[1]` and Commons Stats
are **not** “latched”, which means that their values are monotonically
increasing (assuming you never call ``incr()`` with a negative value).
This implies that you use `CQLs rate function <http://go/cql>`_
if you want to see per minute counts. Ostrich’s ``Counters`` are latched
when they are collected (think of this as being a “reset”)
which means that you should not need to use ``rate`` in order
to see per minute values.

:sup:`[1]` Note that by using ``finagle-stats`` and setting the flag
``com.twitter.finagle.stats.useCounterDeltas=true`` you can
get per-minute deltas in Commons Metrics.

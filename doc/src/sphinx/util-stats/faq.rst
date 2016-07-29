FAQ
===

Why are util-stats in the com.twitter.finagle.stats package?
------------------------------------------------------------
This is a historical artifact that reflects how the code was
originally written as part of `Finagle <http://twitter.github.io/finagle/>`_
but turned out to not actually depend on anything Finagle-specific.
The code was moved to `util <http://twitter.github.io/util/>`_
in order to allow usage without depending on finagle-core.
To ease the transition, the package namespace was left as is.

Why are there three(!) different implementations?
-------------------------------------------------
This is somewhat historical, as early on in Twitter, Commons Stats
was created for Java developers and Ostrich was created for Scala developers.
Code initially was written directly to the implementations and there were
(and still are) some subtle differences in their behavior.
``StatsReceiver`` was written in order to bridge the gap between the
two implementations. Commons Metrics was written quite a bit later in order to
improve upon both of these implementations in terms of
allocation efficiency and histogram precision.

Why migrate to Commons Metrics?
-------------------------------
The Commons Metrics library is designed to replace the other two libraries
supported by Twitter: Ostrich and Commons Stats. The comparison table below
illustrates the advantages of Commons Metrics over the older libraries.

+------------------------------+---------------+----------+-----------------+
|                              | Commons Stats | Ostrich  | Commons Metrics |
+==============================+===============+==========+=================+
| Allocations per `Stat.add`   | 16 bytes      | 0 bytes  | 0 bytes         |
+------------------------------+---------------+----------+-----------------+
| Highest histogram resolution | p99           | p9999    | p9999           |
+------------------------------+---------------+----------+-----------------+
| Sampling error               | N/A           |  ±5%     | ±0.5%           |
+------------------------------+---------------+----------+-----------------+

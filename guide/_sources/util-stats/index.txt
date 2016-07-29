util-stats Guide
================

`util-stats <https://github.com/twitter/util/tree/master/util-stats/src/main/scala/com/twitter/finagle/stats>`_
is Twitter’s library for instrumenting Scala and Java code so that you know
what your application is doing in production. The API surface is small, with
the `StatsReceiver interface <https://github.com/twitter/util/blob/develop/util-stats/src/main/scala/com/twitter/finagle/stats/StatsReceiver.scala>`_
you get counters, gauges (value at a point in time) and stats (histograms).


.. toctree::
   :maxdepth: 1

   overview
   basics
   user_guide
   faq
   filter_stats

Contact
-------

For questions email the `finaglers@twitter.com <finaglers@twitter.com>`_ group.
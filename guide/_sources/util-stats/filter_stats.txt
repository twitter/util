Filter Stats
============

Why do you need stats filter?
-----------------------------
It is usually too costly to store every single metric exposed by the application code. Therefore, a filter can be applied
yet everything is exposed by the server when queried directly. The goal for observability is to store all important metrics that are being read back,
and not store metrics that never get utilized by either dashboards or alerts.

Filtering with TwitterServer
-----------------------------

See the `TwitterServer documentation <https://twitter.github.io/twitter-server/Features.html#filtering-stats-out>`_ for information about filtering stats with TwitterServer.

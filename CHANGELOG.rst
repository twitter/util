.. Author notes: this file is formatted with restructured text
   (https://docutils.sourceforge.net/docs/user/rst/quickstart.html).
   The changelog style is adapted from Apache Lucene.

Note that ``PHAB_ID=#`` and ``RB_ID=#`` correspond to associated messages in commits.

Unreleased
----------

New Features
~~~~~~~~~~~~

* util-core: Add `ClasspathResource`, a utility for loading classpath resources as an
  optional `InputStream`. ``PHAB_ID=D687324``

* util-jackson: Introduce a new library for JSON serialization and deserialization based on the
  Jackson integration in `Finatra <https://twitter.github.io/finatra/user-guide/json/index.html>`__.

  This includes a custom case class deserializer which "fails slow" to collect all mapping failures
  for error reporting. This deserializer is also natively integrated with the util-validator library
  to provide for performing case class validations during deserialization. ``PHAB_ID=D664962``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-stats: Removed MetricSchema trait (CounterSchema, GaugeSchema and HistogramSchema).
  StatReceiver derived classes use MetricBuilder directly to create counters, gauges and stats.
  ``PHAB_ID=D668739``

21.5.0
------

New Features
~~~~~~~~~~~~

* util-validator: Introduce new library for case class validations (akin to Java bean validation)
  which follows the Jakarta Bean Validation specification (https://beanvalidation.org/) by wrapping
  the Hibernate Validator library and thus supports `jakarta.validation.Constraint` annotations and
  validators for annotating and validating fields of Scala case classes. ``PHAB_ID=D638603``

* util-app: Introduce a Java-friendly API `c.t.app.App#runOnExit(Runnable)` and 
  `c.t.app.App#runOnExitLast(Runnable)` to allow Java 8 users to call `c.t.app.App#runOnExit`
  and `c.t.app.App#runOnExitLast` with lambda expressions. ``PHAB_ID=D511536``

21.4.0
------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-reflect: Memoize `c.t.util.reflect.Types#isCaseClass` computation. ``PHAB_ID=D657748``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-stats: Added a methods `c.t.f.stats.Counter#metadata: Metadata`,
  `c.t.f.stats.Stat#metadata: Metadata`, and `c.t.f.stats.Gauge#metadata:
  Metadata` to make it easier to introspect the constructed metric.  In
  particular, this will enable constructing `Expression`s based on the full name
  of the metric.  If you don't have access to a concrete `Metadata` instance
  (like `MetricBuilder`) for constructing a Counter, Stat, or Gauge, you can
  instead supply `NoMetadata`.  ``PHAB_ID=D653751``

New Features
~~~~~~~~~~~~

* util-stats: Added a `com.twitter.finagle.stats.Metadata` abstraction, that can
  be either many `com.twitter.finagle.stats.Metadata`, a `MetricBuilder`, or a
  `NoMetadata`, which is the null `Metadata`.  This enabled constructing
  metadata for counters that represent multiple counters under the hood.
  ``PHAB_ID=D653751``

21.3.0
------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util: Revert to scala version 2.12.12 due to https://github.com/scoverage/sbt-scoverage/issues/319
  ``PHAB_ID=D635917``

* util: Bump scala version to 2.12.13 ``PHAB_ID=D632567``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util: Rename `c.t.util.reflect.Annotations#annotationEquals` to `c.t.util.reflect.Annotations#equals`
  and `c.t.util.reflect.Types.eq` to `c.t.util.reflect.Types.equals`. ``PHAB_ID=D640386``

* util: Builds are now only supported for Scala 2.12+ ``PHAB_ID=D631091``

* util-reflect: Remove deprecated `c.t.util.reflect.Proxy`. There is no library replacement.
  ``PHAB_ID=D630143``

* util-security: Renamed `com.twitter.util.security.PemFile` to `c.t.u.security.PemBytes`, and
  changed its constructor to accept a string and a name.  The main change here is that we assume
  the PEM-encoded text has been fully buffered.  To migrate, please use the helper method on the
  companion object, `PemBytes#fromFile`.  Note that unlike before with construction, we read from
  the file, so it's possible for it to throw.  ``PHAB_ID=D641088``

* util-security: Make KeyManager, TrustManager Factory helper methods public. The main change here
  is that we expose util methods that are used to generate  TrustManagerFactory, KeyManagerFactory
  inside `c.t.u.security` for public usage.  ``PHAB_ID=D644502``

New Features
~~~~~~~~~~~~

* util-reflect: Add `c.t.util.reflect.Annotations` a utility for finding annotations on a class and
  `c.t.util.reflect.Classes` which has a utility for obtaining the `simpleName` of a given class
  across JDK versions and while handling mangled names (those with non-supported Java identifier
  characters). Also add utilities to determine if a given class is a case class in
  `c.t.util.reflect.Types`. ``PHAB_ID=D638655``

* util-reflect: Add `c.t.util.reflect.Types`, a utility for some limited reflection based
  operations. ``PHAB_ID=D631819``

* util-core: `c.t.io` now supports creating and deconstructing unsigned 128-bit buffers
  in Buf. ``PHAB_ID=D606905``

* util-core: `c.t.io.ProxyByteReader` and `c.t.io.ProxyByteWriter` are now public. They are
  useful for wrapping an existing `ByteReader` or `ByteWriter` and extending its functionality
  without modifying the underlying instance. ``PHAB_ID=D622705``

* util-core: Provided `c.t.u.security.X509CertificateDeserializer` to make it possible to directly
  deserialize an `X509Certificate` even if you don't have a file on disk. Also provided
  `c.t.u.security.X509TrustManagerFactory#buildTrustManager` to make it possible to directly
  construct an `X509TrustManager` with an `X509Certificate` instead of passing in a `File`.
  ``PHAB_ID=D641088``

21.2.0
------

No Changes

21.1.0
------

No Changes

20.12.0
-------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~
* util-core: removed `com.twitter.util.Config.` ``PHAB_ID=D580444``

* util-core: removed `com.twitter.util.Future.isDone` method. The semantics of this method
  are surprising in that `Future.exception(throwable).isDone == false`. Replace usages with
  `Future.isDefined` or `Future.poll` ``PHAB_ID=D585700``

* util-stats: Changed `com.twitter.finagle.stats.MethodBuilder#withName`,
  `com.twitter.finagle.stats.MethodBuilder#withRelativeName`,
  `com.twitter.finagle.stats.MethodBuilder#withPercentiles`,
  `com.twitter.finagle.stats.MethodBuilder#counter`, and
  `com.twitter.finagle.stats.MethodBuilder#histogram`, to accept varargs as parameters,
  rather than a `Seq`.  Added a Java-friendly `com.twitter.finagle.stats.MethodBuilder#gauge`.
  ``PHAB_ID=D620425``

New Features
~~~~~~~~~~~~

* util-core: `c.t.conversions` now includes conversion methods for maps (under `MapOps`)
  that were moved from Finatra. ``PHAB_ID=D578819``

* util-core: `c.t.conversions` now includes conversion methods for tuples (under `TupleOps`)
  that were moved from Finatra. ``PHAB_ID=D578804``

* util-core: `c.t.conversions` now includes conversion methods for seqs (under `SeqOps`)
  that were moved from Finatra. ``PHAB_ID=D578605``

* util-core: `c.t.conversions` now includes conversion methods `toOption`, and `getOrElse`
  under `StringOps`. ``PHAB_ID=D578549``

* util-core: `c.t.util.Duration` now includes `fromJava` and `asJava` conversions to
  `java.time.Duration` types. ``PHAB_ID=D571885``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: `Activity.apply(Event)` will now propagate registry events to the underlying
  `Event` instead of registering once and deregistering on garbage collection.  This means
  that if the underlying `Event` is "notified" while the derived `Activity` is not actively
  being observed, it will not pick up the notification.  Furthermore, the derived `Activity`
  will revert to the `Activity.Pending` state while it is not under observation. ``PHAB_ID=D574843``

* util-core: `Activity#stabilize` will now propagate registry events to the underlying
  `Activity` instead of registering once and deregistering on garbage collection.  This means
  that if the underlying `Activity` is changed to a new state while the derived `Activity` is not actively
  being observed, it will not update its own state.  The derived `Activity` will maintain its last
  "stable" state when it's next observed, unless the underlying `Activity` was updated to a new "stable"
  state, in which case it will pick that up instead. ``PHAB_ID=D574843``

* util-stats: `c.t.finagle.stats.DenylistStatsReceiver` now includes methods for creating
  `DenyListStatsReceiver` from partial functions. ``PHAB_ID=D576833``

* util-core: `c.t.util.FuturePool` now supports exporting the number of its pending tasks via
  `numPendingTasks`. ``PHAB_ID=D583030``

20.10.0
-------

Bug Fixes
~~~~~~~~~
* util-stat: `MetricBuilder` now uses a configurable metadataScopeSeparator to align
  more closely with the metrics.json api. Services with an overridden scopeSeparator will
  now see that reflected in metric_metadata.json where previously it was erroneously using
  / in all cases. ``PHAB_ID=D557994``

* util-slf4j-api: Better Java interop. Deprecate `c.t.util.logging.Loggers` as Java users should be
  able to use the `c.t.util.logging.Logger` companion object with less verbosity required.
  ``PHAB_ID=D558605``

20.9.0
------

New Features
~~~~~~~~~~~~

* util-app: `Seq`/`Tuple2`/`Map` flags can now operate on booleans. For example,
  `Flag[Seq[Boolean]]` now works as expected instead of throwing an assert exception (previous
  behaviour). ``PHAB_ID=D549196``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-app: `Flaggable.mandatory` now takes implicit `ClassTag[T]` as an argument. This is change is
  source-compatible in Scala but requires Java users to pass argument explicitly via 
  `ClassTag$.MODULE$.apply(clazz)`. ``PHAB_ID=D542135``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util: Bump version of Jackson to 2.11.2. ``PHAB_ID=D538440``

20.8.1
------

New Features
~~~~~~~~~~~~

* util-mock: Introduce `mockito-scala <https://github.com/mockito/mockito-scala>`__ based mocking
  integration. Fix up and update mockito testing dependencies:
  - `mockito-all:1.10.19` to `mockito-core:3.3.3`
  - `scalatestplus:mockito-1-10:3.1.0.0` to `scalatestplus:mockito-3-2:3.1.2.0`  ``PHAB_ID=D530995``

* util-app: Add support for flags of Java Enum types. ``PHAB_ID=D530205``

20.8.0 (DO NOT USE)
-------------------

New Features
~~~~~~~~~~~~
* util-stats: Store MetricSchemas in InMemoryStatsReceiver to enable further testing. ``PHAB_ID=D518195``

* util-core: c.t.u.Var.Observer is now public. This allows scala users to extend the Var trait
  as has been the case for Java users. ``PHAB_ID=D520237``

* util-core: Added two new methods to c.t.u.Duration and c.t.u.Time: `.fromHours` and `.fromDays`. ``PHAB_ID=D522734``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~
* util-app: Treat empty strings as empty collections in `Flag[Seq[_]]`, `Flag[Set[_]]`,
  `Flag[java.util.List[_]]`, and `Flag[java.util.Set[_]]`. They were treated as collections
  with single element, empty string, before. ``PHAB_ID=D516724``

20.7.0
------

New Features
~~~~~~~~~~~~
* util-app: Add ability to observe App lifecycle events. ``PHAB_ID=D508145``

20.6.0
------

New Features
~~~~~~~~~~~~
* util-stats: Add two new Java-friendly methods to `StatsReceiver` (`addGauge` and `provideGauge`)
  that take java.util.function.Supplier as well as list vararg argument last to enable better
  developers' experience. ``PHAB_ID=D497885``

* util-app: Add a `Flaggable` instance for `java.time.LocalTime`. ``PHAB_ID=D499606``

* util-app: Add two new methods to retrieve flag's unparsed value (as string): `Flag.getUnparsed`
  and `Flag.getWithDefaultUnparsed`. ``PHAB_ID=D499628``

20.5.0
------

New Features
~~~~~~~~~~~~
* util-security: Moved Credentials from util-core
   `c.t.util.Credentials` => `c.t.util.security.Credentials`.
  ``PHAB_ID=D477984``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~
* util-core: Move Credentials to util-security
   `c.t.util.Credentials` => `c.t.util.security.Credentials`.
  ``PHAB_ID=D477984``

* util-core: Change the namespace of `ActivitySource` and its derivatives to
  `com.twitter.io` as its no longer considered experimental since the code has
  changed minimally in the past 5 years. ``PHAB_ID=D478498``

20.4.1
------

New Features
~~~~~~~~~~~~
* util-tunable: ConfigurationLinter accepts a relative path. ``PHAB_ID=D468528``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util: Bump jackson version to 2.11.0. ``PHAB_ID=D457496``

20.4.0 (DO NOT USE)
-------------------

New Features
~~~~~~~~~~~~
* util-core: When looking to add idempotent close behavior, users should mix in `CloseOnce` to
  classes which already extend (or implement) `Closable`, as mixing in `ClosableOnce` leads to
  compiler errors. `ClosableOnce.of` can still be used to create a `ClosableOnce` proxy of an
  already instantiated `Closable`. Classes which do not extend `Closable` can still
  mix in `ClosableOnce`. ``PHAB_ID=D455819``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~
* util-hashing: Rename
   `c.t.hashing.KetamaNode` => `HashNode`,
   `c.t.hashing.KetamaDistributor` => `ConsistentHashingDistributor`.
  ``PHAB_ID=D449929``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-stats: Provide CachedRegex, a function that filters a
  Map[String, Number] => Map[String, Number] and caches which keys to filter on
  based on a regex.  Useful for filtering down metrics in the style that Finagle
  typically recommends ``PHAB_ID=D459391``.

20.3.0
------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: The system property `com.twitter.util.UseLocalInInterruptible` no longer
  can be used to modulate which Local state is present when a Promise is interrupted.
  ``PHAB_ID=D442444``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: Promises now exclusively use the state local to setting the interrupt
  handler when raising on a Promise. ``PHAB_ID=D442444``

20.2.1
------

New Features
~~~~~~~~~~~~

* util-app: Add `c.t.util.app.App#onExitLast` to be able to provide better Java
  ergonomics for designating a final exit function. ``PHAB_ID=D433874``

* util-core: Add `c.t.io.Reader.concat` to conveniently concatenate a collection
  of Reader to a single Reader. ``PHAB_ID=D434448``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `Future.unapply` has been removed. Use `Future.poll` to retrieve Future's
  state. ``PHAB_ID=D427429``

Bug Fixes
~~~~~~~~~

* util-logging: Add a missing `_*` that could result in exceptions when using the
  `Logger.apply(Level, Throwable, String, Any*)` signature. ``PHAB_ID=D430122``

20.1.0
------

No Changes

19.12.0
-------

New Features
~~~~~~~~~~~~

* util-stats: Introduces `c.t.f.stats.LazyStatsReceiver` which ensures that counters and histograms
  don't export metrics until after they have been `incr`ed or `add`ed at least once. ``PHAB_ID=D398898``

* util-core: Introduce `Time#nowNanoPrecision` to produce nanosecond resolution timestamps in JDK9
  or later. ``PHAB_ID=D400661``

* util-core: Introduce `Future#toCompletableFuture`, which derives a `CompletableFuture` from
  a `com.twitter.util.Future` to make integrating with Java APIs simpler. ``PHAB_ID=D408656``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util: Upgrade to jackson 2.9.10 and jackson-databind 2.9.10.1 ``PHAB_ID=D410846``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: The lightly used `com.twitter.util.JavaSingleton` trait has been removed. It
  did not work as intended. Users should provide Java friendly objects, classes, and methods
  instead. ``PHAB_ID=D399947``

Deprecations
~~~~~~~~~~~~

* util-test: The `c.t.logging.TestLogging` mixin has been deprecated. Users are encouraged to
  move to slf4j for logging and minimize dependencies on `com.twitter.logging` in general, as
  it is intended to be replaced entirely by slf4j. ``PHAB_ID=D403574``

Bug Fixes
~~~~~~~~~

* util-core: `Future#toJavaFuture` incorrectly threw the exception responsible for failing it,
  instead of a `j.u.c.ExecutionException` wrapping the exception responsible for failing it.
  ``PHAB_ID=D408656``

19.11.0
-------

New Features
~~~~~~~~~~~~

* util: Add initial support for JDK 11 compatibility. ``PHAB_ID=D365075``

* util-core: Created public method Closable.stopCollectClosablesThread that stops CollectClosables
thread. ``PHAB_ID=D382800``

* util-core: Introduced `Reader.fromIterator` to create a Reader from an iterator. It is not
recommended to call `iterator.next()` after creating a `Reader` from it. Doing so will affect the
behavior of `Reader.read()` because it will skip the value returned from `iterator.next`.
``PHAB_ID=D391769``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util: Upgrade to caffeine 2.8.0 ``PHAB_ID=D384592``

* util-jvm: Stop double-exporting `postGC` stats under both `jvm` and `jvm/mem`. These are now
  only exported under `jvm/mem/postGC`. ``PHAB_ID=D392230``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-stats: abstract methods of StatsReceiver now take Schemas. The old APIs
  are now final and cannot be overriden. For custom implementations, define
  schema based methods (eg, counter(verbosity, name) is now
  counter(CounterSchema)). NB: users can continue to call the old interface;
  only implementors must migrate.``PHAB_ID=D385068``

* util-core: Removed `c.t.io.Pipe.copyMany` (was `Reader.copyMany`). Use `AsyncStream.foreachF`
  link to `Pipe.copy` for substitution. ``PHAB_ID=D396590``

* util-core: Add `c.t.io.BufReader.readAll` to consume a `Reader[Buf]` and concat values to a Buf.
  Replace `c.t.io.Reader.readAll` with `Reader.readAllItems`, the new API consumes a generic Reader[T],
  and return a Seq of items. ``PHAB_ID=D391346``

* util-core: Moved `c.t.io.Reader.chunked` to `c.t.io.BufReader.chunked`, and `Reader.framed` to
  `BufReader.framed`. ``PHAB_ID=D392198``

* util-core: Moved `c.t.io.Reader.copy` to `c.t.io.Pipe.copy`, and `Reader.copyMany` to
  `Pipe.copyMany`. ``PHAB_ID=D393650``

Deprecations
~~~~~~~~~~~~

* util-core: Mark `c.t.io.BufReaders`, `c.t.io.Bufs`, `c.t.io.Readers`, and `c.t.io.Writers` as
  Deprecated. These classes will no longer be needed, and will be removed, after 2.11 support is
  dropped. ``PHAB_ID=D393913``

* util-stats: Removed deprecated methods `stat0` and `counter0` from `StatsReceiver`. ``PHAB_ID=D393063``

19.10.0
-------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: When a computation from FuturePool is interrupted, its promise is
  set to the interrupt, wrapped in a j.u.c.CancellationException. This wrapper
  was introduced because, all interrupts were once CancellationExceptions. In
  RB_ID=98612, this changed to allow the user to raise specific exceptions as
  interrupts, and in the aid of compatibility, we wrapped this raised exception
  in a CancellationException. This change removes the wrapper and fails the
  promise directly with the raised exception. This will affect users that
  explicitly handle CancellationException. ``PHAB_ID=D371872``

Bug Fixes
~~~~~~~~~

* util-core: Fixed bug in `c.t.io.Reader.framed` where if the `framer` didn't emit a `List` the
  emitted frames were skipped. ``PHAB_ID=D378048``

* util-hashing: Fix a bug where `partitionIdForHash` was returning incosistent values w.r.t
  `entryForHash` in `KetamaDistributor`. ``PHAB_ID=D381128``

19.9.0
------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-app: Better handling of exceptions when awaiting on the `c.t.app.App` to close at
  the end of the main function. We `Await.ready` on `this` as the last step of
  `App#nonExitingMain` which can potentially throw a `TimeoutException` which was previously
  unhandled. We have updated the logic to ensure that `TimeoutException`s are handled accordingly.
  ``PHAB_ID=D356846``

* util: Upgrade to Scala Collections Compat 2.1.2. ``PHAB_ID=D364013``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core:  BoundedStack is unused and really old code. Delete it. ``PHAB_ID=D357338``

* util-logging: `com.twitter.logging.ScribeHandler` and `com.twitter.logging.ScribeHandlers` have
  been removed. Users are encouraged to use slf4j for logging. However, if a util-logging integrated
  ScribeHandler is still required, users can either build their own Finagle-based scribe client as
  in `ScribeRawZipkinTracer` in finagle-zipkin-scribe, or copy the old `ScribeHandler`
  implementation directly into their code. ``PHAB_ID=D357008``

19.8.1
------

New Features
~~~~~~~~~~~~

* util: Enables cross-build for 2.13.0. ``PHAB_ID=D333021``

Java Compatibility
~~~~~~~~~~~~~~~~~~

* util-stats: In `c.t.finagle.stats.AbstractStatsReceiver`, the `counter`, `stat` and
  `addGauge` become final, override `counterImpl`, `statImpl` and `addGaugeImpl` instead.
  ``PHAB_ID=D333021``

* util-core:
   `c.t.concurrent.Offer.choose`,
   `c.t.concurrent.AsyncStream.apply`,
   `c.t.util.Await.all`,
   `c.t.util.Closable.sequence` become available to java for passing varargs. ``PHAB_ID=D333021``

* util-stats:
   `c.t.finagle.stats.StatsReceiver.provideGauge` and `addGauge` become available to java for
   passing varags. ``PHAB_ID=D333021``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: (not breaking) `c.t.util.Future.join` and `c.t.util.Future.collect` now take
  `Iterable[Future[A]]` other than Seq. ``PHAB_ID=D333021``

* util-core:  Revert the change above, in `c.t.util.Future`, `collect`, `collectToTry` and `join`
  take `scala.collection.Seq[Future[A]]`. ``PHAB_ID=D355403``

* util-core: `com.twitter.util.Event#build` now builds a Seq of events. `Event#buildAny` builds
  against any collection of events. ``PHAB_ID=D333021``

19.8.0
------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-logging: The namespace forwarders for `Level` and `Policy` in `com.twitter.logging.config`
  have been removed. Code should be updated to use `com.twitter.logging.Level` and
  `com.twitter.logging.Policy` where necessary. Users are encouraged to use 'util-slf4j-api' though
  where possible. ``PHAB_ID=D344439``

* util-logging: The deprecated `com.twitter.logging.config.LoggerConfig` and associated
  classes have been removed. These have been deprecated since 2012. Code should be updated
  to use `com.twitter.logging.LoggerFactory` where necessary. Users are encouraged to use
  'util-slf4j-api' though where possible. ``PHAB_ID=D345381``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util: Upgrade to Jackson 2.9.9. ``PHAB_ID=D345969``

* util-app: It is now illegal to define GlobalFlags enclosed in package objects. ``PHAB_ID=D353045``

19.7.0
------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Removed deprecated `c.t.concurrent.Scheduler` methods `usrTime`,
  `cpuTime`, and `wallTime`. These were deprecated in 2015 and have no
  replacement. ``PHAB_ID=D330386``

* util-core: Removed deprecated `com.twitter.logging.config` classes `SyslogFormatterConfig`,
  `ThrottledHandlerConfig`, `SyslogHandlerConfig`. These were deprecated in 2012 and have
  no replacement. Users are encouraged to use 'util-slf4j-api' where possible. ``PHAB_ID=D339563``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: Remove experimental toggle `com.twitter.util.BypassScheduler` used
  for speeding up `ConstFuture.map` (`transformTry`). Now, we always run map
  operations immediately instead of via the Scheduler, where they may be queued
  and potentially reordered. ``PHAB_ID=D338487``

19.6.0
------

Bug Fixes
~~~~~~~~~

* util-core: Fixed the behavior in `c.t.io.Reader` where reading from `Reader#empty` fails to return
  a `ReaderDiscardedException` after it's discarded. ``PHAB_ID=D325465``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: Use Local at callback creation for Future's interrupt handler rather than
  raiser's locals so that it is consistent with other callbacks. This functionality is
  currently disabled and can be enabled by a toggle (com.twitter.util.UseLocalInInterruptible)
  by setting it to 1.0 if you would like to try it out. ``PHAB_ID=D324315``

19.5.1
------

No Changes

19.5.0
------

New Features
~~~~~~~~~~~~

* util-app: Track the registration of duplicated Flag names. Currently, we print a warning to
  `stderr` but do not track the duplicated Flag names. Tracking them allows us to inspect and
  warn over the entire set. ``PHAB_ID=D314410``

19.4.0
------

New Features
~~~~~~~~~~~~

* util-app: Improve usage of `Flag.let` by providing a `Flag.letParse` method
  ``PHAB_ID=D288549``

19.3.0
------

New Features
~~~~~~~~~~~~

* util-core: Discard parent reader from `Reader.flatten` when child reader encounters an exception.
  ``PHAB_ID=D281830``

* util-core: Added `c.t.conversions.StringOps#toSnakeCase,toCamelCase,toPascalCase` implementations.
  ``PHAB_ID=D280886``

19.2.0
------

New Features
~~~~~~~~~~~~

* util-core: updated `Reader#fromFuture` to resolve its `onClose` when reading of end-of-stream.
  ``PHAB_ID=D269413``

* util-core: Added `Reader.flatten` to flatten a `Reader[Reader[_]]` to `Reader[_]`,
  and `Reader.fromSeq` to create a new Reader from a Seq. ``PHAB_ID=D255424``

* util-core: Added `Duration.fromMinutes` to return a `Duration` from a given number of minutes.
  ``PHAB_ID=D259795``

* util-core: If given a `Timer` upon construction, `c.t.io.Pipe` will respect the close
  deadline and wait the given amount of time for any pending writes to be read. ``PHAB_ID=D229728``

* util-core: Optimized `ConstFuture.proxyTo` which brings the performance of
  `flatMap` and `transform` of a `ConstFuture` in line with `map`. ``PHAB_ID=D271358``

* util-core: Experimental toggle (com.twitter.util.BypassScheduler) for speeding up
  `ConstFuture.map` (`transformTry`). The mechanism, when turned on, runs map operations
  immediately (why not when we have a concrete value), instead of via the Scheduler, where it may
  be queued and potentially reordered, e.g.:
  `f.flatMap { _ => println(1); g.map { _ => println(2) }; println(3) }` will print `1 2 3`,
  where it would have printed `1 3 2`. ``PHAB_ID=D271962``

* util-security: `Pkcs8KeyManagerFactory` now supports a certificates file which contains multiple
  certificates that are part of the same certificate chain. ``PHAB_ID=D263190``

Bug Fixes
~~~~~~~~~

* util-core: Fixed the behavior in `c.t.io.Reader` where `Reader#flatMap` fails to propagate
  parent reader's `onClose`. ``PHAB_ID=D269413``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: Closing a `c.t.io.Pipe` will notify `onClose` when the deadline has passed whereas
  before the pipe would wait indefinitely for a read before transitioning to the Closed state.
  ``PHAB_ID=D229728``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Remove `c.t.u.CountDownLatch` which is an extremely thin shim around
  `j.u.c.CountDownLatch` that provides pretty limited value.  To migrate to `j.u.c.CountDownLatch`,
  instead of `c.t.u.CountDownLatch#await(Duration)`, please use
  `j.u.c.CountDownLatch#await(int, TimeUnit)`, and instead of
  `c.t.u.CountDownLatch#within(Duration)`, please throw an exception yourself after awaiting.
  ``PHAB_ID=D269404``

* util-core: Deprecated conversions in `c.t.conversions` have new implementations
  that follow a naming scheme of `SomethingOps`. ``PHAB_ID=D272206``

  - `percent` is now `PercentOps`
  - `storage` is now `StorageUnitOps`
  - `string` is now `StringOps`
  - `thread` is now `ThreadOps`
  - `time` is now `DurationOps`
  - `u64` is now `U64Ops`

* util-collection: Delete util-collection.  We deleted `GenerationalQueue`, `MapToSetAdapter`, and
  `ImmutableLRU`, because we found that they were of little utility.  We deleted `LruMap` because it
  was a very thin shim around a `j.u.LinkedHashMap`, where you override `removeEldestEntry`.  If you
  need `SynchronizedLruMap`, you can wrap your `LinkedHashMap` with
  `j.u.Collection.synchronizedMap`.  We moved `RecordSchema` into finagle-base-http because it was
  basically only used for HTTP messages, so its new package name is `c.t.f.http.collection`.
  ``PHAB_ID=D270548``

* util-core: Rename `BlacklistStatsReceiver` to `DenylistStatsReceiver`. ``PHAB_ID=D270526``

* util-core: `Buf.Composite` is now private. Program against more generic, `Buf` interface instead.
  ``PHAB_ID=D270916``

19.1.0
------

New Features
~~~~~~~~~~~~

* util-core: Added Reader.map/flatMap to transform Reader[A] to Reader[B]. Added `fromFuture()`
  and `value()` in the Reader object to construct a new Reader. ``PHAB_ID=D252165``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: The implicit conversions classes in `c.t.conversions.SomethingOps` have been
  renamed to have unique names. This allows them to be used together with wildcard imports.
  See Github issue (https://github.com/twitter/util/issues/239). ``PHAB_ID=D252462``

* util-core: Both `c.t.io.Writer.FailingWriter` and `c.t.io.Writer.fail` were removed. Build your
  own instance should you need to.  ``PHAB_ID=D256615``

18.12.0
-------

New Features
~~~~~~~~~~~~

* util-core: Provide a way to listen for stream termination to `c.t.util.Reader`, `Reader#onClose`
  which is satisfied when the stream is discarded or read until the end. ``PHAB_ID=D236311``

* util-core: Conversions in `c.t.conversions` have new implementations
  that follow a naming scheme of `SomethingOps`. Where possible the implementations
  are `AnyVal` based avoiding allocations for the common usage pattern.
  ``PHAB_ID=D249403``

  - `percent` is now `PercentOps`
  - `storage` is now `StorageUnitOps`
  - `string` is now `StringOps`
  - `thread` is now `ThreadOps`
  - `time` is now `DurationOps`
  - `u64` is now `U64Ops`

Bug Fixes
~~~~~~~~~

* util-core: Fixed a bug where tail would sometimes return Some empty AsyncStream instead of None.
  ``PHAB_ID=D241513``

Deprecations
~~~~~~~~~~~~

* util-core: Conversions in `c.t.conversions` have been deprecated in favor of `SomethingOps`
  versions. Where possible the implementations are `AnyVal` based and use implicit classes
  instead of implicit conversions. ``PHAB_ID=D249403``

  - `percent` is now `PercentOps`
  - `storage` is now `StorageUnitOps`
  - `string` is now `StringOps`
  - `thread` is now `ThreadOps`
  - `time` is now `DurationOps`
  - `u64` is now `U64Ops`

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Experimental `c.t.io.exp.MinThroughput` utilities were removed.  ``PHAB_ID=D240944``

* util-core: Deleted `c.t.io.Reader.Null`, which was incompatible with `Reader#onClose` semantics.
  `c.t.io.Reader#empty[Nothing]` is a drop-in replacement. ``PHAB_ID=D236311``

* util-core: Removed `c.t.util.U64` bits. Use `c.t.converters.u64._` instead.  ``PHAB_ID=D244723``

18.11.0
-------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `c.t.u.Future.raiseWithin` methods now take the timeout exception as a call-by-name
  parameter instead of a strict exception. While Scala programs should compile as usual, Java
  users will need to use a `scala.Function0` as the second parameter. The helper
  `c.t.u.Function.func0` can be helpful. ``PHAB_ID=D229559``

* util-core: Rename `c.t.io.Reader.ReaderDiscarded` to `c.t.io.ReaderDiscardedException`.
  ``PHAB_ID=D231969``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: Made Stopwatch.timeNanos monotone. ``PHAB_ID=D236629``

18.10.0
-------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `c.t.io.Reader.Writable` and `c.t.Reader.writable()` are removed. Use `c.t.io.Pipe`
  instead. ``PHAB_ID=D226603``

* util-core: `c.t.util.TempFolder` has been moved to `c.t.io.TempFolder`. ``PHAB_ID=D226940``

* util-core: Removed the forwarding types `c.t.util.TimeConversions` and
  `c.t.util.StorageUnitConversions`. Use `c.t.conversions.time` and
  `c.t.conversions.storage` directly. ``PHAB_ID=D227363``

* util-core: `c.t.concurrent.AsyncStream.fromReader` has been moved to
  `c.t.io.Reader.toAsyncStream`. ``PHAB_ID=D228277``

* util-core: `c.t.io.Reader.read()` no longer takes `n`, the maximum number of bytes to read off a
  stream.  ``PHAB_ID=D228385``

New Features
~~~~~~~~~~~~

* util-core: `c.t.io.Reader.fromBuf` (`BufReader`), `c.t.io.Reader.fromFile`,
  `c.t.io.Reader.fromInputStream` (`InputStreamReader`) now take an additional parameter,
  `chunkSize`, the upper bound of the number of bytes that a given reader emits at each read.
  ``PHAB_ID=D203154``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: `c.t.u.Duration.inTimeUnit` can now return
  `j.u.c.TimeUnit.MINUTES`. ``PHAB_ID=D225115``

18.9.1
-------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `c.t.io.Writer` now extends `c.t.util.Closable`. `c.t.io.Writer.ClosableWriter`
  is no longer exist. ``PHAB_ID=D218453``

* util-core: Add `onClose` into `c.t.io.Writer`, it exposes a `Future` that is satisfied when
  the stream is closed. ``PHAB_ID=D226319``

Bug Fixes
~~~~~~~~~

* util-slf4j-api: Moved slf4j-simple dependency to be a 'test' dependency, instead of a
  compile dependency, which was inaccurate. ``PHAB_ID=D220718``

New Features
~~~~~~~~~~~~

* util-core: Added a `contramap` function into `c.t.io.Writer`, `Writer` is now a contravariant
  functor. Added the `AbstractWriter` for Java compatibility ``PHAB_ID=D225686``

18.9.0
-------

New Features
~~~~~~~~~~~~

* util-logging: New way to construct `ScribeHandler` for java interoperability.
  ``PHAB_ID=D208928``

* util-core: Added Reader#fromAsyncStream for consuming an `AsyncStream` as a `Reader`.
  ``PHAB_ID=D202334``

* util-core: Introducing `Reader.chunked` that chunks the output of a given reader.
  ``PHAB_ID=D206676``

* util-core: Added Reader#framed for consuming data framed by a user supplied function.
  ``PHAB_ID=D212396``

* util-security: Add `NullSslSession` related objects for use with non-existent
  `SSLSession`s.  ``PHAB_ID=D201421``

* util-tunable: Introducing `Tunable.asVar` that allows observing changes to tunables.
  ``PHAB_ID=D211622``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `c.t.io.Reader` and `c.t.io.Writer` are now abstracted over the type
  they produce/consume (`Reader[A]` and `Writer[A]`) and are no longer fixed to `Buf`.
  ``PHAB_ID=D195638``

* util-core: `InMemoryStatsReceiver` now eagerly creates the mappings for `Counters`
  and `Stats` instead of waiting for the first call to `Counter.incr` and `Stat.add`.
  ``PHAB_ID=D205760``

* util-core: `c.t.io.Reader.Writable` is now `c.t.io.Pipe`. Both `Writable` type and
  its factory method are deprecated; use `new Pipe[A]` instead.  ``PHAB_ID=D199536``

* util-slf4j-api: Ensure that marker information is used when determining if log
  level is enabled for methods which support markers. ``PHAB_ID=D202387``

* util-slfj4-api: Finalize the underlying logger def in the Logging trait as it is not
  expected that this be overridable. If users wish to change how the underlying logger is
  constructed they should simply use the Logger and its methods directly rather than
  configuring the the underlying logger of the Logging trait.

  Add better Java compatibility for constructing a Logger. ``PHAB_ID=D204330``

18.8.0
-------

Bug Fixes
~~~~~~~~~

* util-core: Fixed an issue with Future.joinWith where it waits for
  completion of both futures even if one has failed. This also affects
  the join method, which is implemented in terms of joinWith. ``PHAB_ID=D191342``

18.7.0
-------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Local.Context used to be a type alias for Array[Option[_]], now it is
  a new key-value liked structure. ``PHAB_ID=D182478``

18.6.0
-------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-app: Allow users a way to override the argument parsing behavior in
  `c.t.app.App#nonExitingMain` which was inlined. Users can override `parseArgs`
  to define custom behavior. ``PHAB_ID=D181660``

* util-core: Removed `c.t.u.NonFatal`, use `scala.util.control.NonFatal`
  instead. ``PHAB_ID=D181918``

* util-class-preloader: This library has been removed since it deprecated. We
  no longer recommend that people do this. ``PHAB_ID=D174250``

Bug Fixes
~~~~~~~~~

* util-app: Fix issue where in some environments, `URLClassLoader#getURLs` can
  return null, failing LoadService from initializing properly
  (see: https://github.com/google/guava/issues/2239). The `URLClassLoader` javadoc
  is not clear if a null can be returned when calling `URLClassLoader#getURLs` and for
  at least one application server, the default returned is null, thus we should be more
  resilient against this possibility. Fixes Finagle #695. ``PHAB_ID=D181152``

Deprecations
~~~~~~~~~~~~

* util-reflect: This library has been deprecated since it is legacy code and shouldn't
  be used for new services. We no longer think this facility is the right way to do it
  and encourage you to provide your own forwarders. ``PHAB_ID=D174250``

New Features
~~~~~~~~~~~~

* util-app: added #suppressGracefulShutdownErrors method to optionally suppress exceptions
  during graceful shutdown from bubbling up. ``PHAB_ID=D176970``

18.5.0
-------

Bug Fixes
~~~~~~~~~

* util-core: `c.t.concurrent.AsyncSemaphore` no longer completes promises while holding
  its intrinsic lock. ``PHAB_ID=D167434``

* util-logging: Fix incorrect `loggerName` in `c.t.logging.ScribeHandler` which
  prevented the short-circuiting of publishing messages emitted from the ScribeHandler.
  ``PHAB_ID=D161552``

* util-hashing: Add murmur3, a fast, non-cryptographic hashing function that
  is missing from hashing.
  ``PHAB_ID=D164915``

18.4.0
-------

New Features
~~~~~~~~~~~~

* util-app: Add the ability to bind specific implementations for `LoadService.apply`
  via `App.loadServiceBindings`. ``PHAB_ID=D146554``

* util-core: Introduce the `ClosableOnce` trait which extends the guarantees of
  `Closable` to include idempotency of the `close` method. ``PHAB_ID=D152000``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-app: Add visibility for NonFatal exceptions during exiting of `c.t.app.App`.
  Added visibility into any NonFatal exceptions which occur during the closing of
  resources during `App#close`. ``PHAB_ID=D146029``

* util-core: Ensure the `Awaitable.CloseAwaitably0.closeAwaitably` Future returns.
  Because the `closed` AtomicBoolean is flipped, we want to make sure that executing
  the passed in `f` function satisfies the `onClose` Promise even the cases of thrown
  exceptions. ``PHAB_ID=D146565``

* util-stats: Alphabetically sort stats printed to the given `PrintStream` in the
  `c.t.finagle.stats.InMemoryStatsReceiver#print(PrintStream)` function.

  To include stats headers which provide better visual separation for the different
  types of stats being printedm, set `includeHeaders` to true. E.g.,
  ```
  InMemoryStatsReceiver#print(PrintStream, includeHeaders = true)
  ```
  ``PHAB_ID=D144091``

18.3.0
-------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-app: Ensure that any flag parsing error reason is written to `System.err`
  before attempting to print flag usage. In the event that collecting flags for
  the printing the usage message fails, users will still receive a useful message
  as to why flag parsing failed. ``PHAB_ID=D137629``

* util-core: Promises/Futures now use LIFO execution order for their callbacks
  (was depth-based algorithm before).  ``PHAB_ID=D135407``

* util-core: Wrap the function passed to `Closable.make` in a try/catch and return
  a `Future.exception` over any NonFatal exception. ``PHAB_ID=D142086``

Deprecations
~~~~~~~~~~~~

* util-core: RichU64* APIs are deprecated. Use Java 8 Unsigned Long API instead:
  ``PHAB_ID=D137893``

  - `new RichU64String("123").toU64Long` -> `Long.parseUnsignedInt`
  - `new RichU64Long(123L).toU64HexString` -> `Long.toHexString` (no leading zeros)

18.2.0
-------

New Features
~~~~~~~~~~~~

* util-core: Added implicit conversion for percentage specified as "x.percent"
  to a fractional Double in `c.t.conversions.percent`. ``PHAB_ID=D128792``

* util-tunable: Add deserializer for `c.t.u.StorageUnit` to JsonTunableMapper
  ``PHAB_ID=D132368``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-app: When `c.t.a.App.exitOnError` is called, it now gives `close`
  an opportunity to clean up resources before exiting with an error.
  ``PHAB_ID=D129437``

18.1.0
-------

New Features
~~~~~~~~~~~~

* util-security: Added `c.t.util.security.X509CrlFile` for reading
  Certificate Revocation List PEM formatted `X509CRL` files.
  ``PHAB_ID=D127700``

17.12.0
-------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-collection: `c.t.util.SetMaker` has been removed.
  Direct usage of Guava is recommended if needed. ``PHAB_ID=D116852``

17.11.0
-------

Dependencies
~~~~~~~~~~~~

* Guava has been removed as dependency from all modules except the
  'util-cache-guava' module. ``PHAB_ID=D117039``

New Features
~~~~~~~~~~~~

* util-security: Added `c.t.util.security.PrivateKeyFile` for reading PKCS#8
  PEM formatted `PrivateKey` files. ``PHAB_ID=D105266``

Bug Fixes
~~~~~~~~~

* util-core: `c.t.io.BufByteWriter.fixed(size).owned()` will only represent bytes
  explicitly written instead of the full size of the backing array, `size`.
  ``PHAB_ID=D112938``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-cache: The Guava dependency and its associated implementations have been
  moved to a new module, 'util-cache-guava'. ``PHAB_ID=D117039``

* util-cache: `c.t.cache.EvictingCache.lazily` now takes a `FutureCache`
  instead of an implementation specific cache. ``PHAB_ID=D117039``

17.10.0
-------

Release Version Changes:
~~~~~~~~~~~~~~~~~~~~~~~~

* From now on, release versions will be based on release date in the format of
  YY.MM.x where x is a patch number. ``PHAB_ID=D101244``

New Features
~~~~~~~~~~~~

* util-intellij: Create util-intellij project and publish IntelliJ capture
  points plugin for debugging asynchronous stack traces of code using Twitter
  Futures in Scala 2.11.11. ``PHAB_ID=D96782``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-app: c.t.app.Flag.let and letClear are now generic in their return type.
  ``PHAB_ID=D93951``

Bug Fixes
~~~~~~~~~
* util-core: Fix Buf.ByteArray.Shared.apply(bytes,begin,end) constructor function.
  ``PHAB_ID=D100648``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: c.t.io.Buf.ByteArray.[Owned.Shared](Array[Byte], begin, end) now
  validates its input arguments. ``PHAB_ID=D100648``

* util-jvm: The `jvm/mem/postGC/{poolName}/max` metric has been removed
  because it is the same as the `jvm/mem/current/{poolName}/max` metric.
  ``PHAB_ID=D95291``

* util-security: Assert validity of X.509 certificates when read from a file.
  Attempting to read a `c.t.util.security.X509CeritificateFile` will now assert
  that the certificate is valid, i.e., if the current date and time are within
  the validity period given in the certificate. ``PHAB_ID=D88745``

7.1.0  2017-09-06
------------------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-events: Module has been removed. ``PHAB_ID=D82346``

* util-lint: Add GlobalRules#withRules for testing. Allow for the ability to
  specify a global rules set for use in testing. ``PHAB_ID=D83506``

7.0.0  2017-08-15
------------------

New Features
~~~~~~~~~~~~

* util-core: Added `c.t.util.SlowProbeProxyTimer` for monitoring the duration
  of execution for timer tasks. ``PHAB_ID=D70279``

* util-core: Introduced RootMonitor#set to set custom Monitor to RootMonitor.
  ``PHAB_ID=D70876``

* util-jvm: `JvmStats` has been moved here from TwitterServer allowing broader
  access to many metrics including GC, allocations, memory, and more.
  ``PHAB_ID=D80883``

* util-stats: Introducing Verbosity Levels for StatsReceivers (see docs on `StatsReceiver`
  for more info). ``PHAB_ID=D70112``

* util-tunable: `c.t.u.tunable.Tunable`, `c.t.u.tunable.TunableMap`,
  `c.t.u.tunable.JsonTunableMapper`, and `c.t.u.tunable.ServiceLoadedTunableMap` are now public.
  This allows users to create and use Tunables, a mechanism for accessing dynamically
  configured values. See https://twitter.github.io/finagle/guide/Configuration.html#tunables
  for details on how these can be used in Finagle. ``PHAB_ID=D80751``.

Bug Fixes
~~~~~~~~~

* util-core: Fix some consistency issues with `c.t.util.ByteReaderImpl`. Advance its
  cursor by the number of bytes consumed via `readBytes(Int)`, not the number specified
  as a method argument. `readString` will now throw an UnderflowException if the number
  of bytes specified exceeds the remaining buffer length instead of silently making due
  with the rest of the buffer's contents. ``PHAB_ID=D78301``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: `c.t.util.Closable.sequence` now continues processing
  the `Closables` should any of their closes result in a failed `Future`
  and will return the first failure. Synchronous exceptions are now
  handled by lifting them into failed `Futures`. ``PHAB_ID=D62418``

* util-events: `com.twitter.util.events.sinkEnabled` now defaults to false
  in preparation for removal in an upcoming release. ``PHAB_ID=D64437``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: ByteWriter has been transformed into a true trait which can now
  be implemented outside of the com.twitter.io package. ``PHAB_ID=D59996``

* util-core: The method ByteWriter.owned() has been moved to a sub trait,
  BufByteWriter, to separate the notion of the target buffer representation from the
  writer methods in order to make it easier to target different buffer representations.
  ``PHAB_ID=D61215``

* util-stats: ``PHAB_ID=D59762``

 - `ProxyStatsReceiver.self` is now protected (was public before).
 - `StatsReceiver.repr` is now `def` (was `val` before).

* util-stats: `Counter#add` now takes a `Long` instead of an `Integer` as an argument.
  ``PHAB_ID=D69064``

* util-stats: `StatsReceiver#counter`, `StatsReceiver#stat`, and `StatsReceiver.addGauge`
  now may optionally take `c.t.f.stats.Verbosity` as a first argument. ``PHAB_ID=D70112``


Deprecations
~~~~~~~~~~~~

* util-events: This module is deprecated and will be removed in an upcoming
  release. ``PHAB_ID=D64437``

* util-stats: ``PHAB_ID=D62611``

  - `StatsReceiver.counter0` is deprecated in favour of vararg `StatsReceiver.counter`
  - `StatsReceiver.stat0` is deprecated in favour of vararg `StatsReceiver.stat`


6.45.0  2017-06-06
------------------

New Features
~~~~~~~~~~~~

* util-app: Optional resource shutdown sequencing for registered closables
  via `c.t.app.App#closeOnExitLast`. See longer note there for usage.
  ``RB_ID=916120``

* util-core: Added `writeBytes(Buf)` to the ByteWriter abstract class to allow
  for efficient writing of the `c.t.io.Buf` type. ``RB_ID=917094``

* util-core: Added `writeString(CharSequence, Charset)` and readString(Int, Charset)`
  to ByteWriter and ByteReader respectively to facilitate for more efficient String
  encoding and decoding. ``PHAB_ID=D63987``

* util-core: Added `ByteReader.readUnsignedLongBE` and `ByteReader.readUnsignedLongLE`.
  ``RB_ID=917289``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-collection: Removed deprecated `c.t.u.JMapWrapper`. Use
  `scala.collection.JavaConverters` instead. ``RB_ID=915544``

* util-core: ByteReader extends the AutoClosable interface to provide
  a notion of resource management. Users should ensure that instances
  of the ByteReader interface are closed after they are no longer
  needed. ``RB_ID=916086``

* util-core: Removed deprecated methods from `c.t.u.Future`:
    - `rawException`; use `exception` instead
    - `cancel`; use `raise` instead

  Removed deprecated `c.t.u.Futures.select`; use `Future.select` instead.
  Remove deprecated `flatten` method on `c.t.u.Future`; use `Futures.flatten` instead.
  ``RB_ID=915500``

* util-core: Removed deprecated `c.t.u.LongOverflowException`. Use
  `java.lang.ArithmeticException` instead. Removed deprecated
  `c.t.u.LongOverflowArith` and all methods on it:
  - `add`; use Java 8's `Math.addExact` instead
  - `sub`; use Java 8's `Math.subtractExact` instead
  - `mul`; use Java 8's `Math.multiplyExact` instead
  ``RB_ID=915545``

* util-core: Removed deprecated `c.t.concurrent.exp.AsyncStream`. Use
  `c.t.concurrent.AsyncStream` instead. ``RB_ID=916422``

* util-eval: Removed from the project. ``RB_ID=915430``
  https://finagle.github.io/blog/2017/04/06/announce-removals/

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: All `Timers` now handle negative or undefined times/durations in uniform way:
  treat them as zeros (i.e., `Time.epoch`, `Duration.Zero`). ``RB_ID=916008``

6.43.0  2017-04-20
------------------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: `Closable.all(..)` will now catch synchronous exceptions thrown
  by any `Closable.close(..)` invocations, and wrap them in a failed Future.
  ``RB_ID=914859``

* util-stats: InMemoryStatsReceiver's `gauges` member is now safe for
  concurrent iteration but now holds strong references to gauge instances.
  ``RB_ID=911951``

New Features
~~~~~~~~~~~~

* util-core: `c.t.f.u.BufReader` and `c.t.f.u.BufWriter` have been
  moved from finagle-core to util-core and renamed to
  `c.t.io.ByteReader` and `c.t.io.ByteWriter` respectively. They
  are now also exposed publicly. ``RB_ID=911639``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util: util-zk-commons was removed, since it was only a connector between
  util and commons, which was not widely used.  ``RB_ID=910721``

* util-core: AsyncQueue's `size` method is now final while `offer` and `fail`
  are no longer final. ``RB_ID=914191``

6.42.0  2017-03-10
------------------

New Features
~~~~~~~~~~~~

* util-core: Promoted the positional `Buf.Indexed` API to be a first-class
  part of `c.t.io.Buf`. If you have a custom implementation of `Buf` it
  will require some effort to become compatible. ``RB_ID=907231``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-app: Set failFastUntilParsed on created flag added to `c.t.app.Flags`
  via `c.t.app.Flags#add`. ``RB_ID=908804``

* util-core: Remove deprecated `c.t.io.ConcatBuf` which is replaced by
  `c.t.io.Buf.apply(Iterable[Buf])`. ``RB_ID=907180``

* util-core: Remove deprecated `c.t.util.RingBuffer`. Use Guava's
  `EvictingQueue`. ``RB_ID=907516``

* util-core: Remove deprecated `c.t.concurrent.ConcurrentPool`. Prefer
  Finagle's `c.t.f.pool.BufferingPool`. ``RB_ID=907516``

* util-core: Remove deprecated `c.t.concurrent.ConcurrentMultiMap`. Prefer
  Guava's Multimap. ``RB_ID=907516``

Dependencies
~~~~~~~~~~~~

* util: Bump guava to 19.0. ``RB_ID=907807``

6.41.0  2017-02-03
------------------

New Features
~~~~~~~~~~~~

* util-app: App now exposes `closeOnExit` publicly. ``RB_ID=906890``

* util-core: Add method to `Buf` to efficiently write to a nio `ByteBuffer`.
  ``RB_ID=910152``

* util-core: Add Java-friendly API to Scala.java for converting from
  a Java 8 `Optional` to a Scala `Option`. ``RB_ID=906512``

* util-core: Introduced a positional `Buf` API, `Buf.Indexed`, and retrofitted
  all existing implementations in util and finagle to adopt it. It is now used
  throughout for a reductions in allocation and latency. In two services at
  Twitter we saw a 1-2% reduction in allocations. We plan to open the API to
  the public and make it a part of `Buf` once we are confident in the APIs.
  ``RB_ID=904559`` ``RB_ID=905253`` ``RB_ID=906201``

* util-slf4j-api: Introduce slf4j-api support into util. This includes a
  small scala wrapper over the `org.slf4j.Logger` and a scala-friendly
  `Logging` trait. Changes also include the util-slf4j-jul-bridge module which
  is a library that provides a utility to "smartly" install the
  Slf4jBridgeHandler. ``RB_ID=900815``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: Improved performance and allocation rates of some "random access"
  `Buf` operations. ``RB_ID=905253``

* util-core: Standardized argument checking in implementations of
  `c.t.io.Buf.write` and `c.t.io.Buf.slice`. ``RB_ID=899935``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Deprecated `c.t.io.ConcatBuf` which is replaced by
  `c.t.io.Buf.apply(Iterable[Buf])`. ``RB_ID=899623``

6.40.0  2016-12-20
------------------

Bug Fixes
~~~~~~~~~

* util-core: Fix issue with c.t.concurrent.AsyncStream.mapConcurrent which
  will cause the stream head to be held for life of operation. ``RB_ID=896168``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Deprecated charset constants in `c.t.io.Charsets` have been
  removed. Use java.nio.charset.StandardCharsets instead. ``RB_ID=893542``

* util-core: `com.twitter.util.NonFatal` is deprecated, use
  `scala.util.control.NonFatal` instead. ``RB_ID=892475``

* util-core: `FactoryPool`/`SimplePool` now inherits `scala.collection.mutable.Queue[A]`
  not deprecated `scala.collection.mutable.QueueProxy[A]` ``RB_ID=896485``

* util-core: `Buf` has been promoted from a trait to an abstract class to facilitate
  memoization of the `Buf` hash code. This also removes the need for the Java friendly
  abstract class: `AbstractBuf`. ``RB_ID=897476``

6.39.0  2016-11-22
------------------

No Changes

6.38.0  2016-10-10
------------------

New Features
~~~~~~~~~~~~

* util-app: Java developers can now declare instances of `GlobalFlag`
  from Java. See `c.t.app.JavaGlobalFlag` for details. ``RB_ID=874073``

* util-thrift: We now depend on a fork of libthrift hosted in the Central Repository.
  The new package lives in the 'com.twitter' organization. This removes the necessity of
  depending on maven.twttr.com. This also means that eviction will not be automatic and
  using a newer libthrift library requires manual eviction if artifacts are being pulled
  in transitively. ``RB_ID=885879``

* util-logging: Allow users to override `c.t.util.logging.Logger` installation,
  making it easier to work with SLF4J bridges. ``RB_ID=870684``

* util: No longer need to add an additional resolver that points to maven.twttr.com.
  ``RB_ID=878967``

Bug Fixes
~~~~~~~~~

* util-core: `c.t.io.InputStreamReader` and `Readers` created by
  `c.t.io.Reader.fromFile` and `fromStream` now close the underlying
  `InputStream` on reading of EOF and on calls to `Reader.discard`.
  ``RB_ID=873319``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `c.t.app.GlobalFlag` is now `abstract` to reflect how the class
  is intended to be used. ``RB_ID=875409``


6.37.0  2016-09-07
------------------

New Features
~~~~~~~~~~~~

* util-app: Introduce `c.t.app.Flag.letClear` allowing flags to be unset.
  ``RB_ID=868177``

6.36.0  2016-08-25
------------------

New Features
~~~~~~~~~~~~

* util-core: `c.t.util.FuturePool` now optionally exposes metrics on
  their internal state such as active tasks, and completed tasks.
  ``RB_ID=850652``

* util-core: Add a system property
  `com.twitter.concurrent.schedulerSampleBlockingFraction` that can be
  set to a value between 0.0 and 1.0 (inclusive). When the Scheduler
  runs blocking code, it will log the stacktrace for that fraction of
  the calls. ``RB_ID=861892``

* util-core: Add Java-friendly API for `StorageUnit`. See `StorageUnit.fromX`
  and `StorageUnit.{times, plus, minus, divide}` methods. ``RB_ID=864546``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-eval: The compiler reporter is now reset between code check invocations.
  This means that when there is a failure that it is no longer required to reset
  the entire state to recover and that already compiled and loaded classes can still
  be used. ``RB_ID=859878``

6.35.0  2016-07-07
------------------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-codec: StringEncoder no longer depends on apache commons-codec, and
  decode will now throw an exception when it fails to decode a byte, instead
  of failing silently. ``RB_ID=833478``

* util-collection: LruMap is now backed by jdk LinkedHashMap instead of apache
  collections LRUMap. ``RB_ID=833515``

* util-core: `com.twitter.util.NonFatal` is now implemented by Scala's
  `scala.util.control.NonFatal`. This changes behavior such that
  `java.lang.StackOverflowError` is considered fatal and
  `java.lang.NoSuchMethodException` is considered non-fatal.
  ``RB_ID=835671``

New Features
~~~~~~~~~~~~

* util-app: `com.twitter.finagle.util.LoadService` has been moved to
  `c.t.app.LoadService` and can now be used without needing a finagle-core
  dependency. ``RB_ID=829897``

* util-cache: Adds support for Caffeine-style caches. ``RB_ID=833848``

* util-core: Add `c.t.concurrent.Scheduler.blockingTimeNanos` which tracks time spent doing
  blocking operations. ``RB_ID=828289``

* util-core: Reduced allocations by 40% and latency by 18% of satisfying `Promises`.
  ``RB_ID=832816``

* util-core: `c.t.util.NoStacktrace` is removed. Use `scala.util.control.NoStackTrace` instead.
  ``RB_ID=833188``

* util-core: Add `Future.joinWith` that also accepts a function `(A, B) => C` for mapping
  a joined result. ``RB_ID=838169``

* util-core: Add `Future.by(Time)`, complementary to the existing `Future.within(Duration)`
  ``RB_ID=838169``

* util-core: Add `c.t.util.ProxyTimer` which allows for creating proxy based
  `Timers` outside of the `com.twitter.util` package. ``RB_ID=846194``

* util-core: Add `AsyncStream.merge` merge potentially inifite streams
  ``RB_ID=846681``

* util-security: Added new project. ``RB_ID=843070``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* Builds are now only for Java 8 and Scala 2.11. See the
  `blog post <https://finagle.github.io/blog/2016/04/20/scala-210-and-java7/>`_
  for details. ``RB_ID=828898``

* util-core: `c.t.u.Bijection` is removed. use `c.t.bijection.Bijection`
  (https://github.com/twitter/bijection) instead. ``RB_ID=834383``

* util-core: Deprecated method `Future.get()` has been removed because it made it
  too easy to hide blocking code. Replaced usage with the more explicit
  `com.twitter.util.Await.result(Future)`. ``RB_ID=833579``

* util-core: Deprecated method `Future.get(Duration): Try` has been removed because it
  made it too easy to hide blocking code. Replaced usage with the more explicit
  `com.twitter.util.Await.result(Future.liftToTry)`. ``RB_ID=836066``

* util-core: Deprecated methods `Future.isReturn` and `Future.isThrow` have been
  removed because they made it too easy to hide blocking code. Replaced usage with
  the more explicit `Await.result(Future.liftToTry).isReturn` and
  `Await.result(Future.liftToTry).isThrow`. ``RB_ID=837329``

* util-lint: Added methods `com.twitter.util.lint.Rules.removeById(String)` and
  `com.twitter.util.lint.RulesImpl.removeById(String)` so that it is now possible
  to remove a `com.twitter.util.lint.Rule` from the `com.twitter.util.lint.GlobalRules`
  set. ``RB_ID=840753``

Bug Fixes
~~~~~~~~~

* util-core: AsyncMeter had a bug where if the burst size was smaller than
  the number of disbursed tokens, it would discard all of the tokens over
  the disbursal limit.  Changed to instead process tokens in the wait queue
  with leftover tokens.  This improves behavior where the actual period is
  smaller than can actually be simulated with the given timer.  ``RB_ID=836742``

* util-core: Once didn't actually provide the guarantee it tried to, because
  of an issue with the scala compiler,
  https://issues.scala-lang.org/browse/SI-9814.  It should now actually be
  synchronized. ``RB_ID=842245``

* util-zk: Fixed race when an existing permit is released between the time
  the list was gotten and the data was checked. ``RB_ID=835856``

* util-core: Memoize apply now throws IllegalStateException if a thread
  re-enters with identical input parameters instead of deadlocking.

6.34.0  2016-04-26
------------------

New Features
~~~~~~~~~~~~

* util-core: Add `Throwables.unchecked` to help Java users deal with checked
  exceptions. ``RB_ID=811441``

* util-stats: Can now get from a `com.twitter.finagle.stats.StatsReceiver`` to all "leaf"
  StatsReceivers that don't delegate to another StatsReceiver with
  `com.twitter.finagle.stats.DelegatingStatsReceiver.all`.  ``RB_ID=819519``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Removed deprecated methods from Buf.scala ``RB_ID=809948``
  - Removed `c.t.io.Buf.ByteArray.apply`, replace usage with `Buf.ByteArray.Owned.apply`.
  - Removed `c.t.io.Buf.ByteArray.unapply`, replace usage with `Buf.ByteArray.Owned.unapply`.
  - Removed `c.t.io.Buf.ByteBuffer.apply`, replace usage with `Buf.ByteBuffer.Owned.apply`.
  - Removed `c.t.io.Buf.toByteBuffer`, replace usage with `Buf.ByteBuffer.Owned.extract`.

* util-core: Removed deprecated `Future.apply` methods ``RB_ID=811617``

* util-stats: Removed `com.twitter.finagle.stats.BroadcastStatsReceiver` marker trait in favor of
  `com.twiter.finagle.stats.DelegatingStatsReceiver` marker trait, which lets us specify that we
  only delegate to a single `com.twitter.finagle.stats.StatsReceiver`.  ``RB_ID=819519``

* util-zk-common: Removed `com.twitter.zk.ServerSet`. Use implementations of ServerSets in the
  finagle-serversets project. ``RB_ID=821355``

Bug Fixes
~~~~~~~~~

* util-core: Fix memory leak in `Var.apply(T, Event[T])` and `Var.patch`.
  ``RB_ID=809100``

6.33.0  2016-03-10
------------------

New Features
~~~~~~~~~~~~

* util-core: AsyncSemaphore supports closing and draining of waiters via `fail`. ``RB_ID=807590``

* util-core: Add convenience methods `force`, `size`, `sum`, and `withEffect` to `AsyncStream`.
  ``RB_ID=808411``

Bug Fixes
~~~~~~~~~

* util-core: Fix nested functions `AsyncStream.++` to avoid stack overflow. ``RB_ID=804408``

Deprecations
~~~~~~~~~~~~

* util-core: `Future.rawException` is deprecated in favor of `Future.exception`.
  ``RB_ID=798223``

6.32.0  2016-02-03
------------------

New Features
~~~~~~~~~~~~

* util-core: Add `Future.traverseSequentially`. Take a sequence and sequentially apply a function
  A => Future[B] to each item. ``RB_ID=785091``

6.31.0  2016-02-02
------------------

NOT RELEASED

6.30.0  2015-12-03
------------------

New Features
~~~~~~~~~~~~

* util-core: Introduce an `AsyncMeter` for asynchronously rate limiting to a fixed rate over time.
  It can be used for smoothing out bursty traffic, or for slowing down access to a
  resource. ``RB_ID=756333``

* util-core: Introduce a `TokenBucket` for helping to control the relative rates of two processes,
  or for smoothing out the rate of a single process. ``RB_ID=756333``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `Timer` now has final implementations for `schedule` which delegate
  to new protected `scheduleOnce` and `schedulePeriodically` methods. This is
  done to ensure that `Locals` are captured when the task is scheduled and
  then used when the task is run. Existing `Timer` implementations should rename
  their existing `schedule` methods to work with the new interface. ``RB_ID=755387``

* util-core: Remove deprecated `FuturePool.defaultPool`, callers should
  use `FuturePool.unboundedPool` instead. ``RB_ID=757499``

* util-stats: Remove deprecated methods on `com.twitter.finagle.stats.StatsReceiver`.
  ``RB_ID=757414``

* util-core: `AsyncStream` graduates out of `com.twitter.concurrent.exp` into
  `com.twitter.concurrent`. Backwards compatibility aliases remain for Scala
  users, but Java users will need to update their imports. ``RB_ID=758061``

* util-codec: Add a new encoder `com.twitter.util.Base64UrlSafeStringEncoder`
  which extends from `com.twitter.util.Base64StringEncoder`. Both the url-safe
  and non-url-safe encoders can decode all strings generated by either. ``RB_ID=765189``

* util-core: Remove unnecessary `invalidate` method from util-cache's
  `com.twitter.cache.guava.LoadingFutureCache`, and change the `remove` semantic
  to match the `com.twitter.cache.FutureCache` contract. ``RB_ID=766988``

* util-core: Remove protected `Timer.monitor` (overrides a monitor to use by a
  timer implementation) because any possible implementation rather than `Monitor.get`
  promotes memory leaks when timer is used to schedule recursive tasks (tasks that
  reschedules themselves). ``RB_ID=771736``

6.29.0  2015-10-15
------------------

New Features
~~~~~~~~~~~~

* util-core: Introduce an optional max capacity to `AsyncQueue`.
  Modified `AsyncQueue.offer` to return a boolean indicating whether or not the
  item was accepted. Added `AsyncQueue.drain(): Try[Queue]`. ``RB_ID=745567``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Remove deprecated methods from `com.twitter.util.Time` and
  `com.twitter.util.Duration`. ``RB_ID=751771``

* util-core: Provide methods on `Stopwatch` so that users can take advantage of
  `Time` manipulation tools in latency-sensitive code when measuring elapsed
  time. ``RB_ID=75268``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: The Scheduler clock stats were decommissioned as they only make sense
  relative to `wallTime` and the tracking error we have experienced `wallTime` and
  `*Time` make it impossible to use them reliably. It is not worth the performance
  and code complexity to support them. ``RB_ID=750239``

* util-core: `JavaTimer` and `ScheduledThreadPoolTimer` now capture the `Local`
  state when scheduled and is used along with that `Monitor` when the `TimerTask`
  is run. ``RB_ID=755387``

* util-logging: `QueueingHandler` does not create a separate thread per instance.
  ``RB_ID=745567``

6.28.0  2015-09-25
------------------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Remove deprecated methods from `com.twitter.util.Var`.

  To migrate `observe` and `foreach`, given `aVar.observe { t => somethingWith(t) }`
  you would write `aVar.changes.register(Witness({ t => somethingWith(t) }))`.

  To migrate `observeUntil`, given `aVar.observeUntil(_ == something)`,
  you would write `aVar.changes.filter(_ == something).toFuture()`.

  To migrate `observeTo`, given `aVar.observeTo(anAtomicReference)`,
  you would write `aVar.changes.register(Witness(anAtomicReference))`.

  ``RB_ID=744282``

6.27.0  2015-08-28
------------------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `TimeFormat` optionally takes a `TimeZone` in the constructor.
  If not provided, it uses UTC.

6.26.0  2015-07-27
------------------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `Activity`, `BoundedStack`, `RingBuffer` and `Var` migrated
  off of deprecated `ClassManifest` to `ClassTag`. ``RB_ID=720455``

* util-core: Added Spool#zip

* util-core: Removed deprecated methods `Future.void` and `Future$.void()`.
  Use `Future.voided` and `Future$.Void` instead. ``RB_ID=720427``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: `Promise.forwardInterruptsTo(other)` is a no-op if the
              other future is fulfilled. ``RB_ID=714420``

* util-events: Recording of events is disabled by default and can be updated
               at runtime via TwitterServer's `/admin/events` page or
               `/admin/events/{recordOn,recordOff}`. ``RB_ID=715712``

6.25.0  2015-06-22
------------------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~
* util-events: Enable event sink by default.

6.24.0  2015-04-12
------------------

New Features
~~~~~~~~~~~~

* util-core: Introduce AsyncStream, an experimental replacement for Spool.

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: `Future.willEqual()` now returns `Future[Boolean]` instead of
             `Promise[Boolean]`.

* util-core: rename VarSource to ActivitySource. remove
             com.twitter.io.exp.VarSource.Result, return Activity[T]
             instead of Var[VarSource.Result[T]]. Remove FailoverVarSource in
             favor of ActivitySource.orElse.

* util-core: `TimeFormat` now throws IllegalArgumentException if the pattern
             uses the week year ('Y') without the week number ('w')
* util-core: `Spool.++` used to force its argument, but now it is evaluated
             only if `this` Spool is empty. To revert to existing behavior,
             simply force the argument before passing it to ++.

* util-core: `Reader.writable()` returns a new type, `Reader.Writable`, which
             combines `Reader`, `Writer` and `Closable`.
* util-core: `Reader.concat` and `Reader.copyMany` now take an AsyncStream
             argument instead of Spool.

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-core: Futures still rethrow on fatals, but now also Monitor.handle on
             them.

* util-core: `Future.onFailure` now only applies a `PartialFunction` if
             `PartialFunction.isDefinedAt` returns true.

* util-core: `AsyncSemaphore` now requires that `initialPermits` be positive.

* util-core: The `Reader` and `Writer` from `Reader.Writable.close()` are now
             synchronized on `close`.

6.23.0 2014-12-12
------------------

New Features
~~~~~~~~~~~~

* util-core: Add method .flushBatch() to batched future returned by Future.batched()
             that immediately initiates processing of all remaining queued requests

* util-core: Add Future.collect() method that collects over Map's values

* util-stats: Create a new module, `util-stats` to move `finagle-core`
              StatsReceivers to.  They retain the `com.twitter.finagle`
              namespace to ease the transition.

Deprecation:

* util-stats: Deprecate `com.twitter.finagle.stats.StatsReceiver#time{,TimeFuture}`.
              Instead, please use the `com.twitter.finagle.stats.Stat` helpers
              from scala, and the `com.twitter.finagle.stats.JStats` helpers
              from java.

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~
* util-cache: Remove unused com.twitter.cache.Mod trait and object

* util-core: Rename Buf._.Unsafe to Buf._.Owned and Buf._.Copied to Buf._.Shared

* util-core: Remove the com.twitter.util.repository package

* util-core: Change return type of Future.batched() to com.twitter.util.Batcher

Java Compatibility
~~~~~~~~~~~~~~~~~~

* util-app: Flaggable is now an abstract class for Java compatibility

* util-core: Make Futures an API entry point for Java users
             (even for methods that take Scala collections)

* util-core: Add compilation tests to track Java compatibility of new API

6.22.2  2014-10-29
------------------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~
* util-core: Removed `Sieve` example.

* util-core: Introduce new constructors and extractors for Buf types to
             support more efficient, correct uses.  Buf types now come with
             Copied and Direct management interfaces -- Direct tries to
             provide direct access to the Buf's backing byte array, while
             Copied ensures that the caller cannot accidentally mutate a Buf's
             data. Additionally, helpers to support Buf-type coersion have
             been added.

New Features
~~~~~~~~~~~~

* util-app: add an option so that we can let apps fail fast if reading
            argument before args are parsed.

Bug Fixes
~~~~~~~~~

* util: add missing @RunWith annotation

* util-core: Java tests for Duration, Time and Timer

* util-core: Reader.writable.fail: make reentrant

Optimizations
~~~~~~~~~~~~~

* util-core: Slurry of PartialFunction micro-optimizations

6.22.1  2014-10-23
------------------

Bug Fixes
~~~~~~~~~

* util and finagle: fix compiler warnings

Deprecation:

* util-core: Add deprecation of RingBuffer to changelog

* util-core: Removed IVar and IVarField

Documentation
~~~~~~~~~~~~~

* util-core: Clarify Scaladoc of `Promise.attached`

* util-core: Add self-type to `Promise.Detachable` and augment Promise Scaladocs

* util-io: Better names for Buf.slice() paramters.

New Features
~~~~~~~~~~~~

* util-app: Add App registration

* util-cache Add asynchronous cache with TTL

* util-core: Add `Activity.future`

Package factoring
~~~~~~~~~~~~~~~~~

* util-logging: factor out testing code into new package util-test

6.22.0  2014-10-13
------------------

System Requirements
~~~~~~~~~~~~~~~~~~~

* util-core: prefer Await.result(future.liftToTry) to deprecated methods

* c.t.util.Time: Scope Locals with `Local.let` instead of `save`+`restore`

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* util-logging:
  * Logging's default handler is now async by default via `com.twitter.logging.QueueingHandler`.
  * Two Flags allowing for customization:
    * `com.twitter.logging.log.async`: Default true, turns this functionality on/off.
    * `com.twitter.logging.log.async.maxsize`: Default 4096, max size of the async buffer.

* util.RingBuffer: fix buffer size on drops

* util-io: Fix Buf.ByteBuffer.slice

* util-core: Future.sleep: short-circuit when duration <= 0

* util-core: IVar and IVarField were removed. Use com.twitter.util.Promise instead because it provides a superset of IVar behavior.

New Features
~~~~~~~~~~~~

* util-core: introduce Memoize.snappable

* util-app: add Flaggable.ofSet

* util-app: introduce Flag.let

Optimizations
~~~~~~~~~~~~~

* util-core: Perf improvement to ConcatBuf#slice

* util-core: Avoid accumulation of listeners in Future.select

* util-core: Event.filter only 1 call to filter predicate

Bug Fixes
~~~~~~~~~

* util-jvm: Fix logging in Jvm.foreachGc

* util-core: document StorageUnit can overflow

* util-core: check Future.proxyTo and Promise.become preconditions

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: remove Leaky and FutureBenchmark

Documentation
~~~~~~~~~~~~~

* util, ostrich, finagle, twitter-server: Remove all trailing spaces

Package factoring
~~~~~~~~~~~~~~~~~

* Test classes from util-logging were factored into its own package, util-test.

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* util-core: Deprecate `RingBuffer` in favor of Guava's `com.google.common.collect.EvictingQueue`.

6.21.2  2014-09-08
------------------

* util-cache: Adds a Guava-backed asynchronous cache

* util-core: Fixed FuturePool for NLRCK

* util-core: Improve java friendliness of futures

* util-core: Make register/close on Event() work atomically

* util-core: Reimplement Buf.Utf8 encoder/extractor using io.Charsets

* util-core: storage parse() should be able to handle Long

* util-logging: make Logger immutable & break cyclic dependency on Level

* util: Upgrade to scala_2.10

6.20.0  2014-08-22
------------------

* util: Enables cross-publishing for 2.11
* util-app: Log severely if a flag is read at the wrong time
* util-core: Changes transform to fail Futures if you return inside the passed closure
* util-core: Copy bytes from Reader to Writer and OutputStream
* util-core: Fix RichU64String to throw for negative input Problem
* util-core: Optimizations in Buf
* util-core: Remove some unnecessary implicit conversions
* util-doc: Fix updatedocs.bash to update new util docs

6.19.0  2014-08-05
------------------

* util: smattering of minor cleanups in util and finagle
* util-core: Reader and getContent symmetry

6.18.4  2014-07-31
------------------

* util-core: Remove confusing NOOP 0.until(5) in Future.collect().
* util-app: Fix a bug in global flag parsing

6.18.2  2014-07-23
------------------

* util-core: Fixes a broken sbt test
* util-core: Log exceptions caught by ChannelStatsHandler
* util-core: Satisfy promise on fatal exception in FuturePool task
* util-core: small perf improvements to Future.collect, Throw, Flag
* util-logging: java-friendly LoggerFactory API

6.18.1  2014-07-08
------------------

* util: Update README to reflect correct storage units.
* util: Convert all tests in util to scalatest
* util-app: Simplifies the logic to get the appname
* util-io: Buf, Reader: remove Buf.Eof; end-of-stream is None
* util-io: Create Buf.ByteBuffer to wrap java.nio.ByteBuffer

6.18.0  2014-06-23
------------------

* util-app: Don't kill the JVM on flag-parsing failure
* util-app: Improve the Scaladocs for com.twitter.app.Flag and friends
* util-core: Add U(32|64)(BE|LE) to Buf
* util-core: Add com.twitter.util.NilStopwatch
* util-core: Add src/main/java dependency on src/main/scala
* util-core: Catch InterruptedException in Closable collector thread
* util-core: Fix MockTimer#schedule(Duration)(=> Unit)'s cancel
* util-core: Fix update-after-interrupt race condition in AsyncSemaphore
* util-core: Signal the deprecation of com.twitter.util.Bijection.
* util-logging: Add additional handlers to Logging trait

6.17.0  2014-06-04
------------------

* util: Upgrade dependency versions
* util-core: Scheduler productivity = cpuTime/wallTime
* util-core: Add a `take` method to `Spool`
* util-core: Introduce `ConcatBuf`
* util-core: add `Spool.collectFuture`

6.16.0  2014-05-13
------------------

* util-app: Add flag for configuring acceptance of undefined flags
* util-app: Minor c.t.app.ClassPath/c.t.f.util.LoadService cleanup
* util-core: Adds Time.sleep for testing sleeping code

6.15.0  2014-04-29
------------------

* util-app: enforce close grace period
* util-core: special case buf.slice(0, buf.length)
* util-core: add LIFO option to LocalScheduler
* util-core: improves usability of Var and VarSource from java
* util-core: Make spool lazier
* util-core: Fixes detachable semantics with ConstFuture
* util-core: make LocalScheduler non-private for custom schedulers

6.14.0  2014-04-09
------------------

* util-benchmark: Fix caliper failures due to new guava
* util-core: Add Local.let
* util-core: Add com.twitter.io.Charsets and replace the use of org.jboss.netty.util.CharsetUtil
* util-core: Bump objectsize dependency to 0.0.10
* util-core: Comprehensive Scaladocs for Scheduler-related classes and traits
* util-core: Create a static Exception for use in `Future.raiseWithin`
* util-core: Future.select: fix inaccurate comments
* util-core: Make Function classes covariant
* util-core: Parse names into trees; introduce separate evaluation.
* util-core: Short-circuit `within` and `raiseWithin` if Future is already satisfied

6.13.2  2014-03-24
------------------

* util-core: Add `StorageUnit.hashCode`
* util-core: Event.mergeMap: fix Closable
* util: Update 3rdparty library versions
* util: Upgrade to guava 16

6.13.1  2014-03-20
------------------

* util: Update zk libraries

6.13.0  2014-03-14
------------------

* util-app: add usage string, printed before flags
* util-app: Handle comma-separated values in `Flaggable.ofMap`
* util-app: Implement application-level shutdown handling in App.
* util-app: Remove hardcoded ports in FlagTest
* util-app: sort global flags in usage
* util-core/Offer: Don't do indexed lookups in prepare()
* util-core: Add support for interrupting Future.sleep
* util-core: Check whether JVM supports thread measurement before measuring
* util-core: Create daemon threads in all stock `com.twitter.util.FuturePool`\s
* util-core: Event: mergeMap, not flatMap
* util-core: Performance optimizations for Future.collect
* util-core: TimeLike inSeconds should not truncate
* util-core: Var.collect, Fix deadlock caused by oversynchronizing
* util-core: Var: prevent stale updates
* util: ForkJoin scheduler: first draft

6.12.1  2014-02-18
------------------

* Upgrade everyone to the new c.t.common.server-set

6.12.0  2014-02-14
------------------

* LocalScheduler: improve concurrency by sampling less
* Option to enable thread pool scheduler in finagle, and fix the shutting down RejectedExecutionException's.
* re-write Future.unit in terms of Future.Unit
* Revert "Option to enable thread pool scheduler in finagle, and fix the shutting down RejectedExecutionException's." (It's breaking the build on JDK6 machines)
* twitter-server: Report on deadlock conditions in admin/contentions
* Update 3rdpaty zookeeper client
* Update version of com.twitter.common*
* util-core: Add a Scaladoc for com.twitter.util.RandomSocket
* util-core: State[+A] => State[A]
* util-logging: Increase richness of file-logging flags
* util-zk: scalatest as test dep
* util-{app,jvm}: various small improvements from gcflow
* util: Drop util-eval dep from util-zk-common, which pulls in scala-compiler unnecessarily
* Var: fix an iatrogenic concurrency bug

6.11.1  2014-01-16
------------------

* util-collection: Depend on jsr305.
* util-core: Add `Promise.attached` and Detachable.
* util-core: Add `Future.batched`.
* util-common: Fix a race condition in ExecutorServiceFuturePool.

6.11.0  2014-01-14
------------------

* util-core: Add BridgedThreadPoolScheduler.
* util-core: Add Events, discrete-time values.
* util-core: Add Future.delayed, Timer.Nil.
* util-core: Add Var.join.
* util-core: Add utilities for composing Future side effects.
* util-core: Allocation improvements to Future.isDefined, Promise.isDefined, Promise.interrupts.
* util-core: Fix forcing issues with Spool.*::.
* util-core: Future.followedBy->Future.before
* util-core: s/setValue(())/setDone()/g
* util-logging: Allocation improvements to Formatter.formatMessageLines.
* util-logging: Get correct method and class name in c.t.u.LogRecord
* util-zk-common: Fix finagle-serversets dependencies discrepancy.

6.10.0  2013-12-12
------------------

* `util-core`: Add functionality to AsyncSemaphore for executing functions as permits become available.
* `util-core`: Fine-grained locking to prevent deadlocks in Var.
* `util-core`: Introduce com.twitter.io.BufInputStream - wraps a Buf and exposes a java.io.InputStream interface.
* `util-core`: Introduce com.twitter.util.Memoize - thread-safe memoization of a function.

6.9.0  2013-12-02
------------------

* util-core: 2.10 pattern matching strictness
* util-core: Gives Var single-owner semantics
* util-core: Seq[Future[A]] => Future[Seq[Try[A]]]
* util-core: Adds a comment explicitly describing synchronous callback on observe for Var
* util-core: async semaphore cancellation
* util: sbt version in `build.properties`

6.8.1  2013-11-15
------------------

* util-core: Break apart interruptible FuturePool for java backcompat

6.8.0  2013-11-12
------------------

* util-app: Fix null error for Flaggable[InetSocketAddress].
* util-app: Flag, easier usage of default.
* util-core: adds closable.close(Duration)
* util-core: Adds com.twitter.io.exp.VarSource
* util-core: adds comment re using FuturePool from java.
* util-core: buffers requests until Var[Addr] is in a ready state
* util-core: Fix Promise update race when interrupting FuturePool threads.
* util-core: improve allocation/perf in Offer.choose and Future.select
* util-core: Var: remove Var.apply; introduce Var.sample
* util-zk-common: update pom com.twitter.common.zookeeper dependencies
* util: scaladoc warning cleanup.

6.7.0  2013-10-18
------------------

* util-core: Introduce Try.collect(), analagous to Future.collect
* util-core: Add some empirically useful add-ons to Var
* util-logging: Use ConsoleHandler when outputFlag is /dev/null
* util-core: Fix broken string-deserialization in Buf.Utf8.unapply
* util-core: Improve gc profile around Var

6.6.0  2013-10-09
------------------

* util-app: Properly propagate underlying exceptions.
* util-core: Add a `Var.value` function. (835a043)
* util-core: Augment Var and Local in support of Finagle's request context feature. (b2d689a)
* util-core: Avoid instantiating TimeoutException until it is needed (CSL-592)
* util-core: Make Future.never a val instead of a def
* util-core: Move Var to core util, add Var.unapply
* util-core: Testing function Time.withTimeAt now uses Locals.
* util-core: Throw AlreadyNackd on nack-ack.
* util-core: raiseWithin, alternative to within, that raise interrupt.
* util-jvm: Add a GlobalFlag for a machine's number of logical cores. (dc20fbf1)
* util-logging: Add a NullLogger object.
* util-logging: makes Logging more flexible for easy extension of twitter-server
* util-zk: Add ShardCoordinator and ZkAsyncSemaphore classes. (c57b2a9)

6.5.0  2013-09-10
------------------

* util-hashing: removed dependency on util-core
* util-core: Introduce swappable schedulers, ThreadPool scheduler.
* util-core: Scheduler - "productivity" stats, dispatches.
* util-core: Add Future.when
* util-core: introduced Var - composable variables
* util-core: adding short note on Future 'within'

6.4.0  2013-08-28
------------------

* util-core: Add Return constants
* util-core: Make ConstFuture.transform consistent with Promise.transform
* util-core: Make it possible to explicitly set a locale on TimeFormat
* util-logging: Refactored formatter to decrease coupling
* util-core: Add NoSuchMethodException as fatal exception in NonFatal
* util-app: Add some logging helpers to Flags
* util-core: Introduce Buf, Reader, and Writer: Zerocopy, buffered I/O

6.3.8  2013-07-22
------------------

* util-core: Add Future.True and Future.False constants
* util-app: Treat '--' as end of flags indicator
* util-app: Add support for long flags

6.3.7  2013-06-24
------------------

* util-app: flags use by-name default values
* util-app: Make the global flag test idempotent
* util-collection: guard against missing element exception in BGQ
* util: Deal with UnknownHostException thrown by InetAddress.getLocalHost
* util: update version in README

6.3.6  2013-06-11
------------------

* util: Update owners files
* util-jvm: CpuProfile: sleep the right amount of time for the recording thread
* util-jvm: always try to construct hotspot instance Detection by VM name is unreliable.
* util: util/* compiling, testing and benchmarking with pants.
* util-eval: Gizzard: Some followup deps alignment to fix deployment classpath issues

6.3.5  2013-05-31
------------------

* util-core: add Time.fromMicroseconds to util.Time
* util-core: NullMonitor takes itself out when composed
* util-core: deprecate Config
* util-hashing: add entryForHash api to Distributor
* util-app: Flag: clarify usage and hide all Flag constructors.
* util-core: Added reduceLeft and foldLeft to the Spool class
* util: Update sbt project for (util, ostrich, finagle)

6.3.4  2013-05-16
------------------

* util-core: Convenience method to await all
* util-core: RootMonitor never propagates non fatal exception

6.3.3  2013-05-13
------------------

* util-collection: When growing chain only grow the chain. This addresses a NoSuchElementException.
* util-eval: fix for when class files are on the classpath directly
* util: Generate build.properties from sbt
* util-core:Time, Duration: implement Java serialization
* util-thrift: Bump Jackson to 1.9.11
* util-core: Add withFilter to Future and Try
* util: Remove zookeeper dependency ivyXML and replace with ExclusionRules

6.3.2  2013-04-18
------------------

* util-core: create less garbage in AsyncSemaphore.acquire()
* util-core: deprecate com.twitter.util.concurrent.Concurrent{Pool, MultiMap}
* util-core: restore prior Future.get behavior
* util-core: Spool error propagation
* util-core: Use futures for schema detection to avoid blocking finagle threads
* util-refect: test: use sys.error
* util-zk: ZNode("/path").parentPath should be "/", not an empty string

6.3.0  2013-04-05
------------------

* util-core: flag a bug with U64 truncation
* util-core: Future.get: include fatal exceptions
* util-core: deprecate Future#apply, get.
* util-core: special-case Duration.Zero to avoid allocation

6.2.5  2013-03-27
------------------

* util-zk: Improvements to util-zk NativeConnector
* util: Update sbt project definition
* util: launching test in all scala version of the project

6.2.4  2013-03-21
------------------

* util-core: Add Future.Nil, it can be used anytime you need a Future[Seq[_]] with an empty sequence.
* util-core: fix VM test error by ensuring reset
* util-core: Move Disposable/Managed to util
* util-logging: scribe binary thrift for tbird add/remove/scrub ops:
* util: upgrade com.twitter.common.objectsize to 0.0.7

6.2.3  2013-03-08
------------------

* util-core: Remove StreamHelper
* Flag: create Map flag type

6.2.2  2013-02-25
------------------

* Flag: introduce global flags

6.2.1  2013-02-20
------------------

* HttpMux: provide visibility into available handlers
* Flag: add Time type
* Spool: encode exceptions
* Closable: use Time.Bottom for close()
* Future.within: bypass timer entirely if we're passed Duration.Top
* Awaitable: introduce Await
* util-jvm: GC predictor
* io.Files: don't overallocate buffers
* Future: use .nonEmpty instead of != Nil

6.1.0  2013-01-30
------------------

* preliminary 2.10 port/build
* Add Closable trait
* Add contention snapshot

6.0.6  2013-01-22
------------------

* util-core: concurrent.SpoolSource utility for creating Spools
* util-core: Spool.flatMap, Spool.++
* util-app: add shutdown hooks
* util-logging: Make the logging work properly for Scala and
  mixed Scala/Java

6.0.4  2012-12-18
------------------

* Broker: more efficient dequeueing of offers
* Duration: parse all output of Duration.toString
* ScheduledThreadPoolTimer: aggressively remove runnables
  to avoid space leak
* util-core documentation: fix some parentheses, backticks
* util-hashing: add Hashable type class

6.0.3  2012-12-11
------------------

* Promise: remove future tracing, add explicit transforming state to
  avoid extraneous allocation
* update zk client
* com.twitter.app: composable apps & flags

6.0.1  2012-11-26
------------------

* Use java.util.ArrayDeque in place of mutable.Queue due to
  https://issues.scala-lang.org/browse/SI-6690

6.0.0  2012-11-26
------------------

* Removed future cancellation, which is now replaced with one-shot
  interrupts. These also carry a ``cause`` which will be used
  profitably in finagle.
* A new, leaner Promise implemetnation
* New implementations for Time and Duration with true sentinels
* Promise, Try combinators no longer attempt to catch fatal
  exceptions

5.3.14  2012-11-20
------------------

* fix compiler warnings
* Future.join: support up to 22-tupled futures
* com.twitter.concurrent.Serialized: explicit docs
* util-logging: concurrent enqueue support for ScribeHandler, add stats

5.3.13  2012-10-16
------------------

* AsyncSemaphore: Use volatile vars for the active number and the waiters
* util-logging: fix ThrottledHandler to not leak memory
* util-logging: for file handlers, default to append=true since that was the default with FileHandlerConfig and is safer behavior
* upgrading slf4j dependent projects (1.6.1).
* sbt: robust MD5 checking.
* Fix Spool.foreachElem crashing on resoved spool with error
* FuturePool.defaultPool: use cached threadpool by default.
* util-logging: Correctly handle files with a shared prefix.

5.3.10  2012-09-06
------------------

* Improve ZNode.name and ZNode.parentPath to not use Regexes
* Fix ScheduledThreadPoolTimer.schedule(...).cancel()
* Upgrade guava dependency to v13
* Add a ZkClient Connector that dispatches requests across several zookeeper connections
* Support prefix-less sequential nodes in util-zk
* util-logging: Add Logger.withLoggers.
* Clean up equals and hashCode for Time and Duration

5.3.7  2012-08-21
------------------

* Disable log handler purging
* Added ThriftCodec
* Add a Time.hashCode method
* GC monitor: be more quiet about missed GCs
* patch public release of OSS libraries; catch up sbt

5.3.6  2012-07-26
------------------

* Fix temporary file name generation

5.3.0  2012-06-25
------------------

* util-jvm: start timer thread in 'daemon' mode

5.2.0  2012-06-14
------------------

* JVM CPU profiler
* util-jvm: fix for JDK 7

5.1.2  2012-06-07
------------------

* fix documentation
* util-jvm: gc monitoring
* Kill com.twitter.concurrent.Channel

5.0.4  2012-06-01
------------------

* Upgrade scala to 2.9.2
* Java compatibility: void -> voided

4.0.1
------------------

* added AsyncQueue
* config:validate optional subconfigs
* util-zk: allow multiple session event listeners, fix AsyncCallbackPromise
  exception handling, misc fixes
* offer: deprecate apply()
* propagate cancellation exception when Timer.doAt future is cancelled
* KetamaDistributor optionally preserves a floating point truncation
* Timer uses daemon thread by default
* Future.monitor: release reference to promise when it's satisfied
* Future: misc Java compatibility fixes
* Eval.scala: Allow @deprecated
* util-logging: Add LoggerFactory
* Util: Add util-class-preloader (classfile preloading), util-jvm
  (access to performance counters)
* Future: divorce from TryLike hierarchy
* LogRecord: use MessageFormat
* Time: Treat MaxValue specially in TimeMod.{add,sub}

3.0.0  2012-03-14
------------------

* AsyncSemaphore: allow parameterizing maximum queue size
* Logging: scribe handlers may now be named "scribe"
* Logging: Always make sure Level is initialized before being
  able to refer to Logger.
* Offer/Broker: simpler, more flexible implementation
* Config: Config.optional results in lazy evaluation

2.0.0  2012-02-27
------------------

* NetUtil: optimize ipv4 address parsing
* upgrade to Guava r11

1.12.13  2012-02-13
-------------------

* NetUtil: Add inetAddressToInt, isInetAddressInBlock, isInetAddressInBlocks
* Future tracer: fix bug where double proxied exceptions fail
* add "ExceptionalFunction0" for easier use from Java
* Locals: many optimizations to reduce allocations caused by saving
  and restoring contexts

1.12.12  2012-01-24
-------------------

* util-zk-common: Asynchronous wrappers for common ServerSets.
* IVar.unget: only remove closures by object equality
* Offer.choose: use nanoseconds for random seed
* Future.const - builds a constant Future from an existing Try

1.12.9  2012-01-05
------------------

* ThreadPoolFactories are named by default
* Offer: ensure ObjectOrder is independent of Object#hashCode
* new package: util-zk: asynchronous bindings to ZooKeeper

1.12.7  2011-12-02
------------------

* Future: temporarily disabling default usage of the AsmTracer

1.12.6  2011-12-01
------------------

* Future: all helper methods now have Java-friendly equivalents
  that take Lists.

1.12.5  2011-11-29
------------------

* Config: recompile configs based on hash instead of timestamp, add
  memoization
* Timer: make JavaTimer more resilient, log errors
* FuturePool: Fixed race condition in FuturePool where work that was
  cancelled would not clean up after itself
* Function: Add ExceptionalFunction type to allow Java to throw
  checked exceptions.
* Futures: trace dispatch "stack", supplying it as a stack trace for
  exceptions, implement "transform", "transformedBy" to allow for a
  more imperative control flow when used from Java.
* Monitors: composable widgets for handling exceptions

1.12.4  2011-11-09
------------------

* Files.delete has to follow symlinks because jdk6 support
  for symlinks is weaksauce
* properly handle cancellation in FuturePool
* Locals: ensure ``Local`` is fully initialized before registering

1.12.3  2011-11-08
------------------

* add some docs to Offer, Time
* util.io.Files: file utilities, documentation for TempFile
* Offer/Broker: explicit return types for Java compat.

1.12.2  2011-10-28
------------------

* Json thrift deserializer
* Finagle: count pending timeouts
* Fix eval precompile bug

1.12.0  2011-10-21
------------------

* util.Config.Specified now delays evaluation of specified value, to
  ensure evaluation happens in correct dependency order, rather than
  in class-hierarchy order.  This change is mostly source compatible,
  unless you have directly used the Specified class.

1.11.9  2011-10-14
------------------

* ivar/future: provide "TCE", per-thread scheduling, and
  promise squashing
* logger: restore original logging level after modifying them
* u64: fix
* filehandler: thread-visibility
* eval: fix mtime invalidation
* base64 encoder: make it threadsafe

1.11.8  2011-10-04
------------------

* Back out TCE for ivar/futures. This introduced a space
  leak and will be fixed momentarily.
* FuturePool: Catch any exception thrown by executor.submit()
  and return as a Future.exception

1.11.7  2011-09-28
------------------

* ivar/future: provide "TCE", per-thread scheduling, and
  promise squashing
* util-core: add bijection
* util: Time.now is now measured at nanosecond granularity
  instead of millisecond.
* futurepool: don't attempt to perform work for Futures
  that are cancelled

1.11.2  2011-08-12
------------------

* offer: use Int.compare instead of subtraction to avoid
  integer overflow in ObjectOrder
* offer: accept an empty offer list.  this is just Offer.never
* Eval: persistent compilation targets

1.11.1  2011-08-05
------------------

* offer/broker: fixes, simplifications - gets rid of thunked
  values on sends.  removing the infrastructure required to
  support this led to significant simplification.  lock the
  correct objects for broker events.  don't try to resolve
  identical objects in lock order.
* offer: java support
* hashing: actually return 64bit values from the 64bit hash
  functions; tests

1.11.0  2011-08-02
------------------

* Introduce new util-codec module to contain various codecs.
  Primarily so that it can depend on apache commons-codec 1.5
  for base64 improvements over the sun one.

1.10.4  2011-07-29
------------------

* Added TestLogging specs helper to util-logging.
* Spools: like scala streams, but with deferred tails.

1.10.3  2011-07-27
------------------

* add GZip string encoder

1.10.2  2011-07-18
------------------

* Maintain a map of already visited objects incase someone
  creates a circular of config objects.
* Make Duration hashable.
* Promise.on{Success, Failure}: returned chained future.

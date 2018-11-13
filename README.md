# Twitter Util

[![Build status](https://travis-ci.org/twitter/util.svg?branch=develop)](https://travis-ci.org/twitter/util)
[![Codecov](https://codecov.io/gh/twitter/util/branch/develop/graph/badge.svg)](https://codecov.io/gh/twitter/util)
[![Project status](https://img.shields.io/badge/status-active-brightgreen.svg)](#status)
[![Gitter](https://badges.gitter.im/twitter/finagle.svg)](https://gitter.im/twitter/finagle?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.twitter/util-core_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.twitter/util-core_2.12)

A bunch of idiomatic, small, general purpose tools.

See the Scaladoc [here](https://twitter.github.com/util).

## Status

This project is used in production at Twitter (and many other organizations),
and is being actively developed and maintained.

## Releases

[Releases](https://maven-badges.herokuapp.com/maven-central/com.twitter/util_2.12)
are done on an approximately monthly schedule. While [semver](http://semver.org/)
is not followed, the [changelogs](CHANGELOG.rst) are detailed and include sections on
public API breaks and changes in runtime behavior.

## Contributing

The `master` branch of this repository contains the latest stable release of
Util, and weekly snapshots are published to the `develop` branch. In general
pull requests should be submitted against `develop`. See
[CONTRIBUTING.md](https://github.com/twitter/util/blob/master/CONTRIBUTING.md)
for more details about how to contribute.

# Using in your project

An example SBT dependency string for the `util-collection` tools would look like this:

```scala
val collUtils = "com.twitter" %% "util-collection" % "18.11.0"
```

# Units

## Time

```scala
import com.twitter.conversions.time._

val duration1 = 1.second
val duration2 = 2.minutes
duration1.inMillis // => 1000L
```

## Space

```scala
import com.twitter.conversions.storage._
val amount = 8.megabytes
amount.inBytes // => 8388608L
amount.inKilobytes // => 8192L
```

# Futures

A Non-actor re-implementation of Scala Futures.

```scala
import com.twitter.conversions.time._
import com.twitter.util.{Await, Future, Promise}

val f = new Promise[Int]
val g = f.map { result => result + 1 }
f.setValue(1)
Await.result(g, 1.second) // => this blocks for the futures result (and eventually returns 2)

// Another option:
g.onSuccess { result =>
  println(result) // => prints "2"
}

// Using for expressions:
val xFuture = Future(1)
val yFuture = Future(2)

for {
  x <- xFuture
  y <- yFuture
} {
  println(x + y) // => prints "3"
}
```

# Collections

## LruMap

The LruMap is an LRU with a maximum size passed in. If the map is full it expires items in FIFO order. Reading a value will move an item to the top of the stack.

```scala
import com.twitter.util.LruMap

val map = new LruMap[String, String](15) // this is of type mutable.Map[String, String]
```

# Object Pool

The pool order is FIFO.

## A pool of constants

```scala
import scala.collection.mutable
import com.twitter.util.{Await, SimplePool}

val queue = new mutable.Queue[Int] ++ List(1, 2, 3)
val pool = new SimplePool(queue)

// Note that the pool returns Futures, it doesn't block on exhaustion.
assert(Await.result(pool.reserve()) == 1)
pool.reserve().onSuccess { item =>
  println(item) // prints "2"
}
```

## A pool of dynamically created objects

Here is a pool of even-number generators. It stores 4 numbers at a time:

```scala
import com.twitter.util.{Future, FactoryPool}

val pool = new FactoryPool[Int](4) {
  var count = 0
  def makeItem() = { count += 1; Future(count) }
  def isHealthy(i: Int) = i % 2 == 0
}
```

It checks the health when you successfully reserve an object (i.e., when the Future yields).

# Hashing

`util-hashing` is a collection of hash functions and hashing distributors (eg. ketama).

To use one of the available hash functions:

```scala
import com.twitter.hashing.KeyHasher

KeyHasher.FNV1_32.hashKey("string".getBytes)
```

Available hash functions are:

```
FNV1_32
FNV1A_32
FNV1_64
FNV1A_64
KETAMA
CRC32_ITU
HSIEH
```

To use `KetamaDistributor`:

```scala
import com.twitter.hashing.{KetamaDistributor, KetamaNode, KeyHasher}

val nodes = List(KetamaNode("host:port", 1 /* weight */, "foo" /* handle */))
val distributor = new KetamaDistributor(nodes, 1 /* num reps */)
distributor.nodeForHash("abc".##) // => client
```

# Logging

`util-logging` is a small wrapper around Java's built-in logging to make it more
Scala-friendly.

## Using

To access logging, you can usually just use:

```scala
import com.twitter.logging.Logger
private val log = Logger.get(getClass)
```

This creates a `Logger` object that uses the current class or object's
package name as the logging node, so class "com.example.foo.Lamp" will log to
node `com.example.foo` (generally showing "foo" as the name in the logfile).
You can also get a logger explicitly by name:

```scala
private val log = Logger.get("com.example.foo")
```

Logger objects wrap everything useful from `java.util.logging.Logger`, as well
as adding some convenience methods:

```scala
// log a string with sprintf conversion:
log.info("Starting compaction on zone %d...", zoneId)

try {
  ...
} catch {
  // log an exception backtrace with the message:
  case e: IOException =>
    log.error(e, "I/O exception: %s", e.getMessage)
}
```

Each of the log levels (from "fatal" to "trace") has these two convenience
methods. You may also use `log` directly:

```scala
import com.twitter.logging.Level
log(Level.DEBUG, "Logging %s at debug level.", name)
```

An advantage to using sprintf ("%s", etc) conversion, as opposed to:

```scala
log(Level.DEBUG, s"Logging $name at debug level.")
```

is that Java & Scala perform string concatenation at runtime, even if nothing
will be logged because the log file isn't writing debug messages right now.
With `sprintf` parameters, the arguments are just bundled up and passed directly
to the logging level before formatting. If no log message would be written to
any file or device, then no formatting is done and the arguments are thrown
away. That makes it very inexpensive to include verbose debug logging which
can be turned off without recompiling and re-deploying.

If you prefer, there are also variants that take lazily evaluated parameters,
and only evaluate them if logging is active at that level:

```scala
log.ifDebug(s"Login from $name at $date.")
```

The logging classes are done as an extension to the `java.util.logging` API,
and so you can use the Java interface directly, if you want to. Each of the
Java classes (Logger, Handler, Formatter) is just wrapped by a Scala class.


## Configuring

In the Java style, log nodes are in a tree, with the root node being "" (the
empty string). If a node has a filter level set, only log messages of that
priority or higher are passed up to the parent. Handlers are attached to nodes
for sending log messages to files or logging services, and may have formatters
attached to them.

Logging levels are, in priority order of highest to lowest:

- `FATAL` - the server is about to exit
- `CRITICAL` - an event occurred that is bad enough to warrant paging someone
- `ERROR` - a user-visible error occurred (though it may be limited in scope)
- `WARNING` - a coder may want to be notified, but the error was probably not
  user-visible
- `INFO` - normal informational messages
- `DEBUG` - coder-level debugging information
- `TRACE` - intensive debugging information

Each node may also optionally choose to *not* pass messages up to the parent
node.

The `LoggerFactory` builder is used to configure individual log nodes, by
filling in fields and calling the `apply` method. For example, to configure
the root logger to filter at `INFO` level and write to a file:

```scala
import com.twitter.logging._

val factory = LoggerFactory(
  node = "",
  level = Some(Level.INFO),
  handlers = List(
    FileHandler(
      filename = "/var/log/example/example.log",
      rollPolicy = Policy.SigHup
    )
  )
)

val logger = factory()
```

As many `LoggerFactory`s can be configured as you want, so you can attach to
several nodes if you like. To remove all previous configurations, use:

```scala
Logger.clearHandlers()
```

## Handlers

- `QueueingHandler`

  Queues log records and publishes them in another thread thereby enabling "async logging".

- `ConsoleHandler`

  Logs to the console.

- `FileHandler`

  Logs to a file, with an optional file rotation policy. The policies are:

  - `Policy.Never` - always use the same logfile (default)
  - `Policy.Hourly` - roll to a new logfile at the top of every hour
  - `Policy.Daily` - roll to a new logfile at midnight every night
  - `Policy.Weekly(n)` - roll to a new logfile at midnight on day N (0 = Sunday)
  - `Policy.SigHup` - reopen the logfile on SIGHUP (for logrotate and similar services)

  When a logfile is rolled, the current logfile is renamed to have the date
  (and hour, if rolling hourly) attached, and a new one is started. So, for
  example, `test.log` may become `test-20080425.log`, and `test.log` will be
  reopened as a new file.

- `SyslogHandler`

  Log to a syslog server, by host and port.

- `ScribeHandler`

  Log to a scribe server, by host, port, and category. Buffering and backoff
  can also be configured: You can specify how long to collect log lines
  before sending them in a single burst, the maximum burst size, and how long
  to backoff if the server seems to be offline.

- `ThrottledHandler`

  Wraps another handler, tracking (and squelching) duplicate messages. If you
  use a format string like `"Error %d at %s"`, the log messages will be
  de-duped based on the format string, even if they have different parameters.


## Formatters

Handlers usually have a formatter attached to them, and these formatters
generally just add a prefix containing the date, log level, and logger name.

- `Formatter`

  A standard log prefix like `"ERR [20080315-18:39:05.033] jobs: "`, which
  can be configured to truncate log lines to a certain length, limit the lines
  of an exception stack trace, and use a special time zone.

  You can override the format string used to generate the prefix, also.

- `BareFormatterConfig`

  No prefix at all. May be useful for logging info destined for scripts.

- `SyslogFormatterConfig`

  A formatter required by the syslog protocol, with configurable syslog
  priority and date format.

## Future interrupts

Method `raise` on `Future` (`def raise(cause: Throwable)`) raises the interrupt described by `cause` to the producer of this `Future`. Interrupt handlers are installed on a `Promise` using `setInterruptHandler`, which takes a partial function:

```scala
val p = new Promise[T]
p.setInterruptHandler {
  case exc: MyException =>
    // deal with interrupt..
}
```

Interrupts differ in semantics from cancellation in important ways: there can only be one interrupt handler per promise, and interrupts are only delivered if the promise is not yet complete.

## Time and Duration

Like arithmetic on doubles, `Time` and `Duration` arithmetic is now free of overflows. Instead, they overflow to `Top` and `Bottom` values, which are analogous to positive and negative infinity.

Since the resolution of `Time.now` has been reduced (and is also more expensive due to its use of system time), a new `Stopwatch` API has been introduced in order to calculate durations of time.

It's used simply:

```scala
import com.twitter.util.{Duration, Stopwatch}
val elapsed: () => Duration = Stopwatch.start()
```

which is read by applying `elapsed`:

```scala
val duration: Duration = elapsed()
```

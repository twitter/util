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
are done on an approximately monthly schedule. While [semver](https://semver.org/)
is not followed, the [changelogs](CHANGELOG.rst) are detailed and include sections on
public API breaks and changes in runtime behavior.

## Contributing

The `master` branch of this repository contains the latest stable release of
Util, and weekly snapshots are published to the `develop` branch. In general
pull requests should be submitted against `develop`. See
[CONTRIBUTING.md](https://github.com/twitter/util/blob/master/CONTRIBUTING.md)
for more details about how to contribute.

# Using in your project

An example SBT dependency string for the `util-core` library would look like this:

```scala
val utilCore = "com.twitter" %% "util-core" % "20.4.1"
```

# Units

## Time

```scala
import com.twitter.conversions.DurationOps._

val duration1 = 1.second
val duration2 = 2.minutes
duration1.inMillis // => 1000L
```

## Space

```scala
import com.twitter.conversions.StorageUnitOps._
val amount = 8.megabytes
amount.inBytes // => 8388608L
amount.inKilobytes // => 8192L
```

# Futures

A Non-actor re-implementation of Scala Futures.

```scala
import com.twitter.conversions.DurationOps._
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

## Future interrupts

Method `raise` on `Future` (`def raise(cause: Throwable)`) raises the interrupt described by
`cause` to the producer of this `Future`. Interrupt handlers are installed on a `Promise`
using `setInterruptHandler`, which takes a partial function:

```scala
val p = new Promise[T]
p.setInterruptHandler {
  case exc: MyException =>
    // deal with interrupt..
}
```

Interrupts differ in semantics from cancellation in important ways: there can only be one
interrupt handler per promise, and interrupts are only delivered if the promise is not yet
complete.

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

# Time and Duration

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

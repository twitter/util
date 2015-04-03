A bunch of idiomatic, small General Purpose tools.

[![Build Status](https://secure.travis-ci.org/twitter/util.png?branch=master)](https://travis-ci.org/twitter/util)

See the Scaladoc [here](https://twitter.github.com/util)

# Using in your Project

Pre-compiled jars for each set of tools (`util-core`, `util-collection` etc) are available in the Twitter Maven repository, here: http://maven.twttr.com/

We use [Semantic Versioning](http://semver.org/) for published artifacts.

An example SBT dependency string for the `util-collection` tools would look like this:

```scala
val collUtils = "com.twitter" %% "util-collection" % "6.23.0"
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
import com.twitter.util.{Future, Promise}

val f = new Promise[Int]
val g = f map { result => result + 1 }
f.setValue(1)
g.get(1.second) // => This blocks for the futures result (and eventually returns 2)

// Another option:
g respond { result =>
  println(result) // => prints "2"
}

// Using for expressions:
val xFuture = Future(1)
val yFuture = Future(2)

for (
  x <- xFuture
  y <- yFuture
) {
  println(x + y) // => prints "3"
}
```

# Collections

## LruMap

The LruMap is an LRU with a maximum size passed in. If the map is full it expires items in FIFO order. Reading a value will move an item to the top of the stack.

```scala
import com.twitter.util.LruMap

val f = new LruMap[Key, Value](15) // this is of type mutable.Map[Key, Value]
```

## Google MapMaker

```scala
import com.twitter.util.MapMaker

val map = MapMaker[Key, Value] { config =>
  config.weakKeys()
  config.weakValues()
} // this is of type mutable.Map[Key, Value]
```

# Object Pool

The pool order is FIFO

## A pool of constants

```scala
val queue = new mutable.Queue[Int] ++ List(1, 2, 3)
val pool = new SimplePool(queue)
// Note that the pool returns Futures, it doesn't block on exhaustion.
pool.reserve()() mustEqual 1
pool.reserve { item =>
  println(item) // prints "2"
}
```

## A pool of dynamically created objects

Here is a pool of even-number generators. It stores 4 numbers at a time:

```scala
val pool = new FactoryPool[Int](4) {
  var count = 0
  def makeItem() = { count += 1; Future(count) }
  def isHealthy(i: Int) = i % 2 == 0
}
```

It checks the health when you successfully reserve an object (i.e., when the Future yields).

# Eval

Dynamically evaluates Scala strings and files.

This is motivated by the desire to have a type-safe alternative to textual configuration formats such as
YAML, JSON, or .properties files.  Its advantages over these text
formats are

*   Strong typing and compiler checking.  If it doesn't compile and
    doesn't conform to the type you expect, you get an exception
*   The full power of Scala in your config.  You don't have to use
    it.  But you can.

## in config/Development.scala

```scala
import com.xxx.MyConfig

new MyConfig {
  val myValue = 1
  val myTime = 2.seconds
  val myStorage = 14.kilobytes
}
```

## in Main.scala

```scala
import com.xxx.MyConfig

val config = Eval[MyConfig](new File("config/Development.scala"))
```


# Version 6.x

Major version 6 introduced some breaking changes:

* Futures are no longer `Cancellable`; cancellation is replaced with a simpler interrupt mechanism.
* Time and duration implement true sentinels (similar to infinities in doubles). `Time.now` uses system time instead of nanotime + offset.
* The (dangerous) implicit conversion from a `Duration` to a `Long` was removed.
* `Try`s and `Future`s no longer handle fatal exceptions: these are propagated to the dispatching thread.

## Future interrupts

Method `raise` on `Future` (`def raise(cause: Throwable)`) raises the interrupt described by `cause` to the producer of this `Future`. Interrupt handlers are installed on a `Promise` using `setInterruptHandler`, which takes a partial function:

	val p = new Promise[T]
	p.setInterruptHandler {
	  case exc: MyException =>
	    // deal with interrupt..
	}

Interrupts differ in semantics from cancellation in important ways: there can only be one interrupt handler per promise, and interrupts are only delivered if the promise is not yet complete.

## Time and Duration

Like arithmetic on doubles, `Time` and `Duration` arithmetic is now free of overflows. Instead, they overflow to `Top` and `Bottom` values, which are analogous to positive and negative infinity.

Since the resolution of `Time.now` has been reduced (and is also more expensive due to its use of system time), a new `Stopwatch` API has been introduced in order to calculate durations of time.

It's used simply:

	val elapsed: () => Duration = Stopwatch.start()

which is read by applying `elapsed`:

	val duration: Duration = elapsed()

## Contributing

The `master` branch of this repository contains the latest stable release of
Util, and weekly snapshots are published to the `develop` branch. In general
pull requests should be submitted against `develop`. See
[CONTRIBUTING.md](https://github.com/twitter/util/blob/master/CONTRIBUTING.md)
for more details about how to contribute.

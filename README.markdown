A bunch of idiomatic, small General Purpose tools.

See the Scaladoc [here](http://twitter.github.com/util)

# Units

## Time

    import com.twitter.conversions.time._

    val duration1 = 1.second
    val duration2 = 2.minutes
    duration1.inMillis // => 1000L

## Space

    import com.twitter.conversions.storage._
    val amount = 8.megabytes
    amount.inBytes // => 8192L
    amount.inGigabytes // => 0.0078125

# Futures

A Non-actor re-implementation of Scala Futures.

    import com.twitter.util.{Future, Promise}

    val f = new Promise[Int]
    val g = f map { result => result + 1 }
    f.setValue(1
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

# Collections

## LruMap

The LruMap is an LRU with a maximum size passed in. If the map is full it expires items in FIFO order. Reading a value will move an item to the top of the stack.

    import com.twitter.util.LruMap

    val f = new LruMap[Key, Value](15) // this is of type mutable.Map[Key, Value]

## Google MapMaker

    import com.twitter.util.MapMaker

    val map = MapMaker[Key, Value] { config =>
      config.weakKeys()
      config.weakValues()
    } // this is of type mutable.Map[Key, Value]

# Object Pool

The pool order is FIFO

## A pool of constants

      val queue = new mutable.Queue[Int] ++ List(1, 2, 3)
      val pool = new SimplePool(queue)
      // Note that the pool returns Futures, it doesn't block on exhaustion.
      pool.reserve()() mustEqual 1
      pool.reserve { item =>
        println(item) // prints "2"
      }

## A pool of dynamically created objects

Here is a pool of even-number generators. It stores 4 numbers at a time:

    val pool = new FactoryPool[Int](4) {
      var count = 0
      def makeItem() = { count += 1; Future(count) }
      def isHealthy(i: Int) = i % 2 == 0
    }

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

    import com.xxx.MyConfig

    new MyConfig {
      val myValue = 1
      val myTime = 2.seconds
      val myStorage = 14.kilobytes
    }

## in Main.scala

    import com.xxx.MyConfig

    val config = Eval[MyConfig](new File("config/Development.scala"))


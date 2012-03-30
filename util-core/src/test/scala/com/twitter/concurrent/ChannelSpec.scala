package com.twitter.concurrent

import org.specs.SpecificationWithJUnit
import com.twitter.conversions.time._
import collection.mutable.ArrayBuffer
import com.twitter.util.{Future, Promise}
import java.util.concurrent.ConcurrentLinkedQueue
import com.twitter.conversions.time._

class ChannelSpec extends SpecificationWithJUnit {
  "ChannelSource" should {
    "send & receive" in {
      val source = new ChannelSource[Int]
      val results = new ArrayBuffer[Int]

      "respond before any sent messages" in {
        source.respond { i =>
          Future { results += i }
        }
        source send 1
        source send 2
        results.toList mustEqual List(1, 2)
      }

      "close" in {
        source.isOpen must beTrue
        source.close()
        source.isOpen must beFalse
      }

      "receive is triggered when there is a subscriber" in {
        source.responds.first.respond { o =>
          source.send(1)
          source.send(2)
        }
        source.respond { i =>
          Future { results += i }
        }
        results.toList mustEqual List(1, 2)
      }

      "map" in {
        val channel = source map(_.toString)
        var result = ""
        channel.respond { i =>
          Future { result += i }
        }
        source send(1)
        source send(2)
        result mustEqual "12"
      }

      "filter" in {
        val channel = source filter(_ % 2 != 0)
        channel.respond { i =>
          Future { results += i }
        }
        source send(1)
        source send(2)
        source send(3)
        source send(4)
        results.toList mustEqual List(1, 3)
      }

      "merge" in {
        val source2 = new ChannelSource[Int]
        val channel = source merge source2
        channel.respond { i =>
          Future { results += i }
        }
        source send(1)
        source2 send(2)
        results.toList mustEqual List(1, 2)
      }

      "pipe" in {
        val source2 = new ChannelSource[Int]
        source pipe(source2)
        source2.respond { i =>
          Future { results += i }
        }
        source send(1)
        source send(2)
        results.toList mustEqual List(1, 2)
      }

      "first" in {
        val first = source.first
        source.send(1)
        source.send(2)
        source.send(3)
        first(1.second) mustEqual 1
      }

      "backpressure" in {
        val promise = new Promise[Unit]
        val observer = source.respond { i =>
          promise
        }
        Future.join(source.send(1)) respond { _ =>
          results += 1
        }
        results.toList mustEqual Nil
        promise.setValue(())
        results.toList mustEqual List(1)
      }

      "numObservers" in {
        val numObservers = new ConcurrentLinkedQueue[Int]
        source.numObservers.respond { i =>
          numObservers add(i)
          Future.Done
        }
        val o1 = source.respond { i =>
          Future.Done
        }
        val o2 = source.respond { i =>
          Future.Done
        }
        o1.dispose()
        val o3 = source.respond { i =>
          Future.Done
        }
        o3.dispose()
        o2.dispose()
        numObservers.toArray.toList mustEqual List(1, 2, 1, 2, 1, 0)
      }
    }
  }
}

package com.twitter.concurrent

import org.specs.Specification
import com.twitter.conversions.time._
import com.twitter.util.MapMaker
import collection.mutable.{Queue, ArrayBuffer}

object ChannelSpec extends Specification {
  "ChannelSource" should {
    "send & receive" in {
      val source = new ChannelSource[Int]
      val results = new ArrayBuffer[Int]

      "respond before any sent messages" in {
        source.respond(this) { i =>
          results += i
        }
        source send 1
        source send 2
        results.toList mustEqual List(1, 2)
      }

      "receive is triggered when there is a subscriber" in {
        source.responds.first.respond { o =>
          source.send(1)
          source.send(2)
        }
        source.respond(this) { i =>
          results += i
        }
        results.toList mustEqual List(1, 2)
      }

      "map" in {
        val channel = source map(_.toString)
        var result = ""
        channel.respond(this) { i =>
          result += i
        }
        source send(1)
        source send(2)
        result mustEqual "12"
      }

      "filter" in {
        val channel = source filter(_ % 2 == 0)
        channel.respond(this) { i =>
          results += i
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
        channel.respond(this) { i =>
          results += i
        }
        source send(1)
        source2 send(2)
        results.toList mustEqual List(1, 2)
      }

      "pipe" in {
        val source2 = new ChannelSource[Int]
        source pipe(source2)
        source2.respond(this) { i =>
          results += i
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
        first() mustEqual 1
      }

      "backpressure" in {
        "listen for pauses" in {
          source.resumes.respond(this) { i =>
            source.send(1)
            source.send(2)
          }
          val observer = source.respond(this) { i =>
            results += i
          }
          observer.pause()
          results.toList mustEqual Nil
          observer.resume()
          results.toList mustEqual List(1, 2)
        }

        "discard messages to paused observers" in {
          val o1out = new ArrayBuffer[Int]
          val o2out = new ArrayBuffer[Int]
          val source = new ChannelSource[Int] {
            override protected def deliver(a: Int, f: ObserverSource[Int]) {
              if (!f.isPaused) f(a)
            }
          }
          val o1 = source.respond(this) { i =>
            o1out += i
          }
          val o2 = source.respond(this) { i =>
            o2out += i
          }
          o1.pause()
          source.send(1)
          source.send(2)
          o1out.toList mustEqual Nil
          o2out.toList mustEqual List(1, 2)
        }
      }
    }
  }
}
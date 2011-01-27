package com.twitter.concurrent

import org.specs.Specification
import collection.mutable.ArrayBuffer

object ChannelSpec extends Specification {
  "Topic" should {
    "send & receive" in {
      "receiving before any sent messages" in {
        val topic = new Topic[Int]
        val results = new ArrayBuffer[Int]
        topic receive {
          case Value(i) =>
            results += i
          case End =>
            results += 0
        }
        topic send 1
        topic send 2
        topic close()
        results.toList mustEqual List(1, 2, 0)
      }

      "initializer is called when there is a subscriber" in {
        val topic = new Topic[Int]
        topic.onReceive {
          topic.send(1)
        }
        var result = 0
        topic foreach {
          case i =>
            result = i
        }
        topic.close()
        result mustEqual 1
      }
    }
  }
}
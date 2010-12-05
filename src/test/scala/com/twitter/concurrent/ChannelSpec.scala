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

      "messages are queued until a receiver" in {
        val topic = new Topic[Int]
        val results = new ArrayBuffer[Int]
        topic send 1
        topic send 2
        topic close()
        topic receive {
          case Value(i) =>
            results += i
          case End =>
            results += 0
        }
        results.toList mustEqual List(1, 2, 0)
      }
    }
  }
}
package com.twitter.util

import org.specs.Specification
import org.specs.mock.Mockito

object TimerSpec extends Specification with Mockito {
  "ReferenceCountedTimer" should {
    val underlying = mock[Timer]
    val factory = mock[() => Timer]
    factory() returns underlying

    val refcounted = new ReferenceCountedTimer(factory)
    there was no(factory)()

    "call the factory when it is first acquired" in {
      refcounted.acquire()
      there was one(factory)()
    }

    "stop the underlying timer when acquire count reaches 0" in {
      refcounted.acquire()
      refcounted.acquire()
      refcounted.acquire()
      there was one(factory)()

      refcounted.stop()
      there was no(underlying).stop()
      refcounted.stop()
      there was no(underlying).stop()
      refcounted.stop()
      there was one(underlying).stop()
    }
  }
}

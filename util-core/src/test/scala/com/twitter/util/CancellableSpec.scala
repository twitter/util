package com.twitter.util

import org.specs.Specification
import org.specs.mock.Mockito
import com.twitter.conversions.time._

object CancellableSpec extends Specification with Mockito {
  "Cancellable, on cancellation" should {
    class TestCancellable extends Cancellable
    val c = spy(new TestCancellable)
    val c1 = spy(new TestCancellable)

    "cancel a linked cancellable (after cancellation)" in {
      c.cancel()
      there was no(c1).cancel()
      c.linkTo(c1)
      there was one(c1).cancel()
    }

    "cancel a linked cancellable (before cancellation)" in {
      c.linkTo(c1)
      there was no(c1).cancel()
      c.cancel()
      there was one(c1).cancel()
    }
  }
}

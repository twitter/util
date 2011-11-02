package com.twitter.util

import org.specs.Specification
import com.twitter.util._

object BijectionSpec extends Specification {
  case class Foo(i: Int)

  val fooject = new Bijection[Foo, Int] {
    def apply(f: Foo) = f.i
    def invert(i: Int) = if (i % 2 == 0) Foo(i) else error("not really a bijection, natch")
  }

  def isAFoo(i: Int) = i match {
    case fooject(f) => "a foo! "+ f.toString
    case _          => "not a foo"
  }

  "Bijection" should {
    "return the original when inverting the inverse" in {
      fooject.inverse.inverse mustBe fooject
    }

    "can be used for pattern-match" in {
      isAFoo(2) mustEqual "a foo! Foo(2)"
      isAFoo(1) mustEqual "not a foo"
    }
  }
}

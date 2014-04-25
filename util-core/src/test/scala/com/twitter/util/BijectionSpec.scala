package com.twitter.util

import com.twitter.util._
import org.scalatest.{WordSpec, Matchers}

class BijectionSpec extends WordSpec with Matchers {
  case class Foo(i: Int)

  val fooject = new Bijection[Foo, Int] {
    def apply(f: Foo) = f.i
    def invert(i: Int) = if (i % 2 == 0) Foo(i) else fail("not really a bijection, natch")
  }

  def isAFoo(i: Int) = i match {
    case fooject(f) => "a foo! "+ f.toString
    case _          => "not a foo"
  }

  "Bijection" should  {
    "return the original when inverting the inverse" in {
      assert(fooject.inverse.inverse == fooject)
    }

    "can be used for pattern-match" in {
      assert(isAFoo(2) == "a foo! Foo(2)")
      assert(isAFoo(1) == "not a foo")
    }
  }
}

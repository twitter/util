package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TryTest extends FunSuite {
  class MyException extends Exception
  val e = new Exception("this is an exception")

  test("Try.apply(): should catch exceptions and lift into the Try type") {
    assert(Try[Int](1) == Return(1))
    assert(Try[Int] { throw e } == Throw(e))
  }

  test("Try.apply(): should propagate fatal exceptions") {
    intercept[AbstractMethodError] {
      Try[Int] { throw new AbstractMethodError }
    }
  }

  test("Try.withFatals works like Try.apply, but can handle fatals") {
    val nonFatal = new Exception
    val fatal = new AbstractMethodError
    val handler: PartialFunction[Throwable, Try[Int]] = {
      case e: AbstractMethodError => Throw(e)
      case e: Exception => Return(1)
    }

    // Works like Try.apply for non fatal errors.
    assert(Try.withFatals(1)(handler) == Return(1))
    assert(Try.withFatals(throw nonFatal)(handler) == Throw(nonFatal))

    // Handles fatal errors
    assert(Try.withFatals(throw fatal)(handler) == Throw(fatal))

    // Unhandled fatals are propagated
    intercept[NoClassDefFoundError] {
      Try.withFatals(throw new NoClassDefFoundError)(handler)
    }
  }

  test("Try.throwable: should return e for Throw:s") {
    assert(Throw(e).throwable == e)
  }

  test("Try.throwable: should throw IllegalStateException for Return:s") {
    intercept[IllegalStateException] {
      Return(1).throwable
    }
  }

  test("Try.rescue") {
    val result1 = Return(1) rescue { case _ => Return(2) }
    val result2 = Throw(e) rescue { case _ => Return(2) }
    val result3 = Throw(e) rescue { case _ => Throw(e) }

    assert(result1 == Return(1))
    assert(result2 == Return(2))
    assert(result3 == Throw(e))
  }

  test("Try.getOrElse") {
    assert(Return(1).getOrElse(2) == 1)
    assert(Throw(e).getOrElse(2) == 2)
  }

  test("Try.apply") {
    assert(Return(1)() == 1)
    intercept[Exception] { Throw[Int](e)() }
  }

  test("Try.map: when there is no exception") {
    val result1 = Return(1).map(1 + _)
    val result2 = Throw[Int](e).map(1 + _)

    assert(result1 == Return(2))
    assert(result2 == Throw(e))
  }

  test("Try.map: when there is an exception") {
    val result1 = Return(1) map(_ => throw e)
    assert(result1 == Throw(e))

    val e2 = new Exception
    val result2 = Throw[Int](e) map(_ => throw e2)
    assert(result2 == Throw(e))
  }

  test("Try.flatMap: when there is no exception") {
    val result1 = Return(1) flatMap(x => Return(1 + x))
    val result2 = Throw[Int](e) flatMap(x => Return(1 + x))

    assert(result1 == Return(2))
    assert(result2 == Throw(e))
  }

  test("Try.flatMap: when there is an exception") {
    val result1 = Return(1).flatMap[Int](_ => throw e)
    assert(result1 == Throw(e))

    val e2 = new Exception
    val result2 = Throw[Int](e).flatMap[Int](_ => throw e2)
    assert(result2 == Throw(e))
  }

  test("Try.exists: should return true when predicate passes for a Return value") {
    val t = Return(4)
    assert(t.exists(_ > 0) == true)
  }

  test("Try.exists: should return false when predicate doesn't pass for a Return value") {
    val t = Return(4)
    assert(t.exists(_ < 0) == false)
  }

  test("Try.exists: should return false for Throw") {
    val t = Throw(new Exception)
    assert(t.exists(_ => true) == false)
  }

  test("Try.flatten: is a Return(Return)") {
    assert(Return(Return(1)).flatten == Return(1))
  }

  test("Try.flatten: is a Return(Throw)") {
    val e = new Exception
    assert(Return(Throw(e)).flatten == Throw(e))
  }

  test("Try.flatten: is a Throw") {
    val e = new Exception
    assert(Throw[Try[Int]](e).flatten == Throw(e))
  }

  test("Try in for comprehension with no Throw values") {
    val result = for {
      i <- Return(1)
      j <- Return(1)
    } yield (i + j)
    assert(result == Return(2))
  }

  test("Try in for comprehension with Throw values throws before") {
    val result = for {
      i <- Throw[Int](e)
      j <- Return(1)
    } yield (i + j)
    assert(result == Throw(e))
  }

  test("Try in for comprehension with Throw values throws after") {
    val result = for {
      i <- Return(1)
      j <- Throw[Int](e)
    } yield (i + j)
    assert(result == Throw(e))
  }

  test("Try in for comprehension with Throw values returns the FIRST Throw") {
    val e2 = new Exception
    val result = for {
      i <- Throw[Int](e)
      j <- Throw[Int](e2)
    } yield (i + j)
    assert(result == Throw(e))
  }

  test("Try.collect: with an empty Seq") {
    assert(Try.collect(Seq.empty) == Return(Seq.empty))
  }

  test("Try.collect: with a Throw") {
    assert(Try.collect(Seq(Return(1), Throw(e))) == Throw(e))
  }

  test("Try.collect: with Returns") {
    assert(Try.collect(Seq(Return(1), Return(2))) == Return(Seq(1, 2)))
  }

  test("Try.orThrow: returns on Some") {
    val exc = new Exception("boom!")
    assert(Try.orThrow(Some("OK")) { () => exc } == Return("OK"))
  }

  test("Try.orThrow: fails on empty on Some") {
    val exc = new Exception("boom!")
    assert(Try.orThrow(None) { () => exc } == Throw(exc))
  }

  test("Try.orThrow: OK if you throw") {
    val exc = new Exception("boom!")
    assert(Try.orThrow(None) { () => throw exc } == Throw(exc))
  }

  test("OrThrow implicits in nicely") {
    import Try._
    val exc = new Exception("boom!")
    assert(Some("OK").orThrow { exc } == Return("OK"))
  }
}

package com.twitter.util

import org.specs.Specification

object TrySpec extends Specification {
  class MyException extends Exception
  val e = new Exception("this is an exception")

  "Try()" should {
    "catch exceptions and lift into the Try type" in {
      Try(1) mustEqual Return(1)
      Try { throw e } mustEqual Throw(e)
    }
  }

  "Try" should {
    "rescue" in {
      val myException = new MyException
      Return(1) rescue { case _ => Return(2) } mustEqual Return(1)
      Throw(e) rescue { case _ => Return(2) } mustEqual Return(2)
      Throw(e) rescue { case _ => Throw(e) } mustEqual Throw(e)
    }

    "getOrElse" in {
      Return(1) getOrElse 2 mustEqual 1
      Throw(e) getOrElse 2 mustEqual 2
    }

    "apply" in {
      Return(1)() mustEqual 1
      Throw[Exception, Int](e)() must throwA(e)
    }

    "map" in {
      "when there is no exception" in {
        Return(1) map(1+) mustEqual Return(2)
        Throw[Exception, Int](e) map(1+) mustEqual Throw(e)
      }

      "when there is an exception" in {
        Return(1) map(_ => throw e) mustEqual Throw(e)

        val e2 = new Exception
        Throw[Exception, Int](e) map(_ => throw e2) mustEqual Throw(e)
      }
    }

    "for" in {
      "with no Throw values" in {
        val result = for {
          i <- Return(1)
          j <- Return(1)
        } yield (i + j)
        result mustEqual Return(2)
      }

      "with Throw values" in {
        "throws before" in {
          val result = for {
            i <- Throw[Exception, Int](e)
            j <- Return(1)
          } yield (i + j)
          result mustEqual Throw(e)
        }

        "throws after" in {
          val result = for {
            i <- Return(1)
            j <- Throw[Exception, Int](e)
          } yield (i + j)
          result mustEqual Throw(e)
        }

        "returns the FIRST Throw" in {
          val e2 = new Exception
          val result = for {
            i <- Throw[Exception, Int](e)
            j <- Throw[Exception, Int](e2)
          } yield (i + j)
          result mustEqual Throw(e)
        }
      }
    }
  }
}
package com.twitter.util

import org.specs.Specification

object TrySpec extends Specification {
  class MyException extends Exception
  val e = new Exception

  "Try()" should {
    "catch exceptions and lift into the Try type" in {
      Try(1) mustEqual Return(1)
      Try { throw e } mustEqual Throw(e)
    }
  }

  "Try" should {
    "rescue" in {
      "when the exception is caught" in {
        Return(1) rescue { case _ => 2 } mustEqual 1
        Throw(e) rescue { case _ => 2 } mustEqual 2
      }

      "when the exception is uncaught" in {
        Throw(e) rescue { case e: MyException => 2 } must throwA(e)
      }
    }

    "handle" in {
      Return(1) handle { case _ => Return(2) } mustEqual Return(1)
      Throw(e) handle { case e: MyException => Return(2) } mustEqual Throw(e)
      Throw(new MyException) handle { case e: MyException => Return(2) } mustEqual Return(2)
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
      Return(1) map(1+) mustEqual Return(2)
      Throw[Exception, Int](e) map(1+) mustEqual Throw(e)
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
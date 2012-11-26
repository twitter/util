package com.twitter.util

import org.specs.SpecificationWithJUnit

class TrySpec extends SpecificationWithJUnit {
  class MyException extends Exception
  val e = new Exception("this is an exception")

  "Try()" should {
    "catch exceptions and lift into the Try type" in {
      Try[Int](1) mustEqual Return(1)
      Try[Int] { throw e } mustEqual Throw(e)
    }

    "not catch fatal exceptions" in {
      val ex = new AbstractMethodError
      Try[Int] { throw ex } must throwA(ex)
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
      Throw[Int](e)() must throwA(e)
    }

    "map" in {
      "when there is no exception" in {
        Return(1) map(1+) mustEqual Return(2)
        Throw[Int](e) map(1+) mustEqual Throw(e)
      }

      "when there is an exception" in {
        Return(1) map(_ => throw e) mustEqual Throw(e)

        val e2 = new Exception
        Throw[Int](e) map(_ => throw e2) mustEqual Throw(e)
      }
    }

    "flatMap" in {
      "when there is no exception" in {
        Return(1) flatMap(x => Return(1 + x)) mustEqual Return(2)
        Throw[Int](e) flatMap(x => Return(1 + x)) mustEqual Throw(e)
      }

      "when there is an exception" in {
        Return(1).flatMap[Int](_ => throw e) mustEqual Throw(e)

        val e2 = new Exception
        Throw[Int](e).flatMap[Int](_ => throw e2) mustEqual Throw(e)
      }
    }

    "flatten" in {
      "is a Return(Return)" in {
        Return(Return(1)).flatten mustEqual Return(1)
      }

      "is a Return(Throw)" in {
        val e = new Exception
        Return(Throw(e)).flatten mustEqual Throw(e)
      }

      "is a Throw" in {
        val e = new Exception
        Throw[Try[Int]](e).flatten mustEqual Throw(e)
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
            i <- Throw[Int](e)
            j <- Return(1)
          } yield (i + j)
          result mustEqual Throw(e)
        }

        "throws after" in {
          val result = for {
            i <- Return(1)
            j <- Throw[Int](e)
          } yield (i + j)
          result mustEqual Throw(e)
        }

        "returns the FIRST Throw" in {
          val e2 = new Exception
          val result = for {
            i <- Throw[Int](e)
            j <- Throw[Int](e2)
          } yield (i + j)
          result mustEqual Throw(e)
        }
      }
    }
  }
}

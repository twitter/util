package com.twitter.concurrent

import org.specs.SpecificationWithJUnit

class SchedulerSpec extends SpecificationWithJUnit {
  def submit(f: => Unit) = Scheduler.submit(new Runnable {
    def run() = f
  })

  "Scheduler" should {
    val N = 100

    "run the first submitter immediately" in {
      var ok = false
      submit {
        ok = true
      }
      ok must beTrue
    }
    
    "run subsequent submits serially" in {
      var n = 0
      submit {
        n must be_==(0)
        submit {
          n must be_==(1)
          submit {
            n must be_==(2)
            n += 1
          }
          n += 1
        }
        n += 1
      }
      
      n must be_==(3)
    }
    
    "handle many submits" in {
      var ran = Nil: List[Int]
      submit {
        for (which <- 0 until N)
          submit {
            ran must beLike {
              case Nil if which == 0 => true
              case hd :: _ => hd == which - 1
            }
            ran ::= which
          }
      }
      ran must be_==((0 until N).reverse)
    }
  }
}

package com.twitter.examples
package concurrent

import com.twitter.concurrent._

object Sieve {
  def integers(from: Int): Offer[Int] = {
    val b = new Broker[Int]
    def gen(n: Int): Unit = b.send(n) andThen gen(n + 1)
    gen(from)
    b.recv
  }

  def filter(in: Offer[Int], prime: Int): Offer[Int] = {
    val b = new Broker[Int]
    def loop() {
      in.sync() onSuccess { i =>
        if (i % prime != 0)
          b.send(i) andThen loop()
        else
          loop()
      }
    }
    loop()

    b.recv
  }

  def sieve = {
    val b = new Broker[Int]
    def loop(of: Offer[Int]) {
      for (prime <- of?; _ <- b ! prime)
        loop(filter(of, prime))
    }
    loop(integers(2))
    b.recv
  }

  def main(args: Array[String]) {
    val primes = sieve
    0 until 100 foreach { _ =>
      println(primes??)
    }
  }
}

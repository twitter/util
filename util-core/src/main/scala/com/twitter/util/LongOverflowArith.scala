package com.twitter.util

class LongOverflowException(msg: String) extends Exception(msg)

object LongOverflowArith {
  def add(a: Long, b: Long) = {
    val c = a + b
    if (((a ^ c) & (b ^ c)) < 0)
      throw new LongOverflowException(a + " + " + b)
    else
      c
  }

  def sub(a: Long, b: Long) = {
    val c = a - b
    if (((a ^ c) & (-b ^ c)) < 0)
      throw new LongOverflowException(a + " - " + b)
    else
      c
  }

  def mul(a: Long, b: Long): Long = {
    if (a > b) {
      // normalize so that a <= b to keep conditionals to a minimum
      mul(b, a)
    } else if (a < 0L) {
      if (b < 0L) {
        if (a < Long.MaxValue / b) throw new LongOverflowException(a + " * " + b)
      } else if (b > 0L) {
        if (Long.MinValue / b > a) throw new LongOverflowException(a + " * " + b)
      }
    } else if (a > 0L) {
      // and b > 0L
      if (a > Long.MaxValue / b) throw new LongOverflowException(a + " * " + b)
    }

    a * b
  }
}

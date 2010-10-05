package com.twitter.util

import scala.reflect.Manifest

/**
 * The Try type represents a computation that may either result in an exception
 * or return a value. It is analogous to the Either type but encodes common idioms
 * for handling exceptional cases.
 */
object Try {
  case class PredicateDoesNotObtain() extends Exception()

  def apply[R](r: => R): Try[R] = {
    try { Return(r) } catch {
      case e => Throw(e)
    }
  }
}

trait Try[+R] {
  /**
   * Returns true if the Try is a Throw, false otherwise.
   */
  def isThrow: Boolean

  /**
   * Returns true if the Try is a Return, false otherwise.
   */
  def isReturn: Boolean

  /**
   * Returns the value from this Return or the given argument if this is a Throw.
   */
  def getOrElse[R2 >: R](default: => R2) = if (isReturn) apply() else default

  /**
   * Calls the exceptionHandler with the exception if this is a Throw. This is like flatMap for the exception.
   */
  def rescue[R2 >: R](rescueException: Throwable => Try[R2]): Try[R2]

  /**
   * Returns the value from this Return or throws the exception if this is a Throw
   */
  def apply(): R

  /**
   * Applies the given function f if this is a Result.
   */
  def foreach(f: R => Unit) { if (isReturn) f(apply()) }

  /**
   * Returns the given function applied to the value from this Return or returns this if this is a Throw
   */
  def flatMap[R2](f: R => Try[R2]): Try[R2]

  /**
   * Maps the given function to the value from this Return or returns this if this is a Throw
   */
  def map[X](f: R => X): Try[X]

  /**
   * Returns None if this is a Throw or the given predicate does not obtain. Returns some(this) otherwise.
   */
  def filter(p: R => Boolean): Try[R]

  /**
   * Returns None if this is a Throw or a Some containing the value if this is a Return
   */
  def toOption = if (isReturn) Some(apply()) else None

  /**
   * Returns this object. This is overridden by subclasses.
   */
  def respond(k: Try[R] => Unit) = k(this)
}

final case class Throw[+R](e: Throwableq ) extends Try[R] { 
  def isThrow = true
  def isReturn = false
  def rescue[R2 >: R](rescueException: Throwable => Try[R2]) = rescueException(e)
  def apply(): R = throw e
  def flatMap[R2](f: R => Try[R2]) = Throw[R2](e)
  def map[X](f: R => X) = Throw(e)
  def filter(p: R => Boolean) = this
}

final case class Return[+R](r: R) extends Try[R] {
  def isThrow = false
  def isReturn = true
  def rescue[R2 >: R](rescueException: Throwable => Try[R2]) = Return(r)
  def apply() = r
  def flatMap[R2](f: R => Try[R2]) = {
    try {
      f(r)
    } catch {
      case e => Throw(e)
    }
  }  
  def map[X](f: R => X) = Try[X](f(r))
  def filter(p: R => Boolean) = if (p(apply())) this else Throw(new Try.PredicateDoesNotObtain)
}
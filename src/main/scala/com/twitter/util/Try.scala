package com.twitter.util

import scala.reflect.Manifest

/**
 * The Try type represents a computation that may either result in an exception
 * or return a value. It is analogous to the Either type but encodes common idioms
 * for handling exceptional cases.
 */
object Try {
  case class PredicateDoesNotObtain() extends Exception()

  def apply[E <: Throwable, R](r: => R): Try[E, R] = {
    try { Return(r) } catch {
      case e: E => Throw(e)
    }
  }
}

trait Try[+E <: Throwable, +R] {
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
  def rescue[E2 >: E <: Throwable, R2 >: R](rescueException: PartialFunction[E, Try[E2, R2]]): Try[E2, R2]

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
  def flatMap[E2 >: E <: Throwable, R2](f: R => Try[E2, R2]): Try[E2, R2]

  /**
   * Maps the given function to the value from this Return or returns this if this is a Throw
   */
  def map[X](f: R => X): Try[E, X]

  /**
   * Returns None if this is a Throw or the given predicate does not obtain. Returns some(this) otherwise.
   */
  def filter(p: R => Boolean): Try[Throwable, R]

  /**
   * Returns None if this is a Throw or a Some containing the value if this is a Return
   */
  def toOption = if (isReturn) Some(apply()) else None
}

final case class Throw[+E <: Throwable, +R](e: E) extends Try[E, R] { 
  def isThrow = true
  def isReturn = false
  def rescue[E2 >: E <: Throwable, R2 >: R](rescueException: PartialFunction[E, Try[E2, R2]]) =
    if (rescueException.isDefinedAt(e)) rescueException(e) else this
  def apply(): R = throw e
  def flatMap[E2 >: E <: Throwable, R2](f: R => Try[E2, R2]) = Throw[E2, R2](e)
  def map[X](f: R => X) = Throw(e)
  def filter(p: R => Boolean) = this
}

final case class Return[+E <: Throwable, +R](r: R) extends Try[E, R] {
  def isThrow = false
  def isReturn = true
  def rescue[E2 >: E <: Throwable, R2 >: R](rescueException: PartialFunction[E, Try[E2, R2]]) = Return(r)
  def apply() = r
  def flatMap[E2 >: E <: Throwable, R2](f: R => Try[E2, R2]) = f(r)
  def map[X](f: R => X) = Return[E, X](f(r))
  def filter(p: R => Boolean) = if (p(apply())) this else Throw(new Try.PredicateDoesNotObtain)
}
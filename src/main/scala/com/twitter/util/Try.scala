package com.twitter.util

/**
 * The Try type represents a computation that may either result in an exception
 * or return a success value. It is analogous to the Either type but encodes
 * common idioms for handling exceptional cases (such as rescue/ensure which
 * is analogous to try/finally).
 */
object Try {
  case class PredicateDoesNotObtain() extends Exception()

  def apply[R](r: => R): Try[R] = {
    try { Return(r) } catch {
      case e => Throw(e)
    }
  }
}

trait TryLike[+R, Repr[R]] {
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
   * Returns the value from this Return or throws the exception if this is a Throw
   */
  def apply(): R

  /**
   * Returns the value from this Return or throws the exception if this is a Throw.
   * Alias for apply()
   */
  def get() = apply()

  /**
   * Applies the given function f if this is a Result.
   */
  def foreach(f: R => Unit) { onSuccess(f) }

  /**
   * Returns the given function applied to the value from this Return or returns this if this is a Throw
   */
  def flatMap[R2](f: R => Repr[R2]): Repr[R2]

  /**
   * Returns the given function applied to the value from this Return or returns this if this is a Throw.
   * Alias for flatMap
   */
  def andThen[R2](f: R => Repr[R2]) = flatMap(f)

  /**
   * Maps the given function to the value from this Return or returns this if this is a Throw
   */
  def map[X](f: R => X): Repr[X]

  /**
   * Returns None if this is a Throw or the given predicate does not obtain. Returns some(this) otherwise.
   */
  def filter(p: R => Boolean): Repr[R]

  /**
   * Calls the exceptionHandler with the exception if this is a Throw. This is like flatMap for the exception.
   */
  def rescue[R2 >: R](rescueException: PartialFunction[Throwable, Repr[R2]]): Repr[R2]

  /**
   * Calls the exceptionHandler with the exception if this is a Throw. This is like map for the exception.
   */
  def handle[R2 >: R](rescueException: Throwable => R2): Repr[R2]

  /**
   * Invoked only if the computation was successful. Returns `this`
   */
  def onSuccess(f: R => Unit): Repr[R]

  /**
   * Invoked only if the computation was successful. Returns `this`
   */
  def onFailure(rescueException: Throwable => Unit): Repr[R]

  /**
   * Invoked regardless of whether the computation completed successfully or unsuccessfully.
   * Implemented in terms of `respond` so that subclasses control evaluation order. Returns `this`.
   */
  def ensure(f: => Unit): Repr[R] = {
    respond { _ => f }
    this.asInstanceOf[Repr[R]]
  }

  /**
   * Returns None if this is a Throw or a Some containing the value if this is a Return
   */
  def toOption = if (isReturn) Some(apply()) else None

  /**
   * Returns this object. This is overridden by subclasses.
   */
  def respond(k: Repr[R] => Unit) = k(this.asInstanceOf[Repr[R]])
}

sealed abstract class Try[+R] extends TryLike[R, Try]

final case class Throw[+R](e: Throwable) extends Try[R] {
  def isThrow = true
  def isReturn = false
  def rescue[R2 >: R](rescueException: PartialFunction[Throwable, Try[R2]]) = {
    try {
      if (rescueException.isDefinedAt(e)) rescueException(e) else this
    } catch {
      case e2 => Throw(e2)
    }
  }
  def apply(): R = throw e
  def flatMap[R2](f: R => Try[R2]) = Throw[R2](e)
  def map[X](f: R => X) = Throw(e)
  def filter(p: R => Boolean) = this
  def onFailure(rescueException: Throwable => Unit) = { rescueException(e); this }
  def onSuccess(f: R => Unit) = this
  def handle[R2 >: R](rescueException: Throwable => R2) = Try{ rescueException(e) }
}

final case class Return[+R](r: R) extends Try[R] {
  def isThrow = false
  def isReturn = true
  def rescue[R2 >: R](rescueException: PartialFunction[Throwable, Try[R2]]) = Return(r)
  def apply() = r
  def flatMap[R2](f: R => Try[R2]) = try f(r) catch { case e => Throw(e) }
  def map[X](f: R => X) = Try[X](f(r))
  def filter(p: R => Boolean) = if (p(apply())) this else Throw(new Try.PredicateDoesNotObtain)
  def onFailure(rescueException: Throwable => Unit) = this
  def onSuccess(f: R => Unit) = { f(r); this }
  def handle[R2 >: R](rescueException: Throwable => R2) = this
}

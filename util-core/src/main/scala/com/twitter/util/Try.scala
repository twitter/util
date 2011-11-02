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

/**
 * This trait represents a computation that can succeed or fail. Its two current
 * specializations are Try (a computation that has succeeded or failed) and Future
 * (a computation that will succeed or fail).
 */
trait TryLike[+R, This[+R] <: TryLike[R, This]] {
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
   * Returns the given function applied to the value from this Return or returns this if this is a Throw.
   *
   * ''Note'' The gnarly type parameterization is there for Java compatibility, since Java
   * does not support higher-kinded types.
   */
  def flatMap[R2, AlsoThis[R2] >: This[R2] <: This[R2]](f: R => AlsoThis[R2]): AlsoThis[R2]

  /**
   * Maps the given function to the value from this Return or returns this if this is a Throw
   */
  def map[X](f: R => X): This[X]

  /**
   * Converts this to a Throw if the predicate does not obtain.
   */
  def filter(p: R => Boolean): This[R]

  /**
   * Calls the exceptionHandler with the exception if this is a Throw. This is like flatMap for the exception.
   *
   * ''Note'' The gnarly type parameterization is there for Java compatibility, since Java
   * does not support higher-kinded types.
   */
  def rescue[R2 >: R, AlsoThis[R] >: This[R] <: This[R]](rescueException: PartialFunction[Throwable, AlsoThis[R2]]): AlsoThis[R2]

  /**
   * Calls the exceptionHandler with the exception if this is a Throw. This is like map for the exception.
   */
  def handle[R2 >: R](rescueException: PartialFunction[Throwable, R2]): This[R2]

  /**
   * Invoked only if the computation was successful.  Returns a
   * chained `this` as in `respond`.
   */
  def onSuccess(f: R => Unit): This[R]

  /**
   * Invoked only if the computation was successful.  Returns a
   * chained `this` as in `respond`.
   */
  def onFailure(rescueException: Throwable => Unit): This[R]

  /**
   * Invoked regardless of whether the computation completed
   * successfully or unsuccessfully.  Implemented in terms of
   * `respond` so that subclasses control evaluation order.  Returns a
   * chained `this` as in `respond`.
   */
  def ensure(f: => Unit): This[R] =
    respond { _ => f }

  /**
   * Returns None if this is a Throw or a Some containing the value if this is a Return
   */
  def toOption = if (isReturn) Some(apply()) else None

  /**
   * Invokes the given closure when the value is available.  Returns
   * another 'This[R]' that is guaranteed to be available only *after*
   * 'k' has run.  This enables the enforcement of invocation ordering.
   *
   * This is overridden by subclasses.
   */
  def respond(k: Try[R] => Unit): This[R]

  /**
   * Returns the given function applied to the value from this Return or returns this if this is a Throw.
   * Alias for flatMap
   */
  def andThen[R2](f: R => This[R2]) = flatMap(f)
}

sealed abstract class Try[+R] extends TryLike[R, Try] {
  def respond(k: Try[R] => Unit) = { k(this); this }

  /**
   * Converts a Try[Try[T]] into a Try[T]
   */
  def flatten[T](implicit ev: R <:< Try[T]): Try[T]
}

final case class Throw[+R](e: Throwable) extends Try[R] {
  def isThrow = true
  def isReturn = false
  def rescue[R2 >: R, AlsoTry[R2] >: Try[R2] <: Try[R2]](rescueException: PartialFunction[Throwable, AlsoTry[R2]]) = {
    try {
      if (rescueException.isDefinedAt(e)) rescueException(e) else this
    } catch {
      case e2 => Throw(e2)
    }
  }
  def apply(): R = throw e
  def flatMap[R2, That[R2] >: Try[R2] <: Try[R2]](f: R => That[R2]) = Throw[R2](e)
  def flatten[T](implicit ev: R <:< Try[T]): Try[T] = Throw[T](e)
  def map[X](f: R => X) = Throw(e)
  def filter(p: R => Boolean) = this
  def onFailure(rescueException: Throwable => Unit) = { rescueException(e); this }
  def onSuccess(f: R => Unit) = this
  def handle[R2 >: R](rescueException: PartialFunction[Throwable, R2]) =
    if (rescueException.isDefinedAt(e)) {
      Try(rescueException(e))
    } else {
      this
    }
}

final case class Return[+R](r: R) extends Try[R] {
  def isThrow = false
  def isReturn = true
  def rescue[R2 >: R, AlsoTry[R2] >: Try[R2] <: Try[R2]](rescueException: PartialFunction[Throwable, AlsoTry[R2]]) = Return(r)
  def apply() = r
  def flatMap[R2, That[R2] >: Try[R2] <: Try[R2]](f: R => That[R2]) = try f(r) catch { case e => Throw(e) }
  def flatten[T](implicit ev: R <:< Try[T]): Try[T] = r
  def map[X](f: R => X) = Try[X](f(r))
  def filter(p: R => Boolean) = if (p(apply())) this else Throw(new Try.PredicateDoesNotObtain)
  def onFailure(rescueException: Throwable => Unit) = this
  def onSuccess(f: R => Unit) = { f(r); this }
  def handle[R2 >: R](rescueException: PartialFunction[Throwable, R2]) = this
}

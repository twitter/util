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
      case NonFatal(e) => Throw(e)
    }
  }

  /**
   * Collect the results from the given Trys into a new Try. The result will be a Throw if any of
   * the argument Trys are Throws. The first Throw in the Seq is the one which is surfaced.
   */
  def collect[A](ts: Seq[Try[A]]): Try[Seq[A]] = {
    if (ts.isEmpty) Return(Seq.empty[A]) else Try { ts map { t => t() } }
  }
}

/**
 * This class represents a computation that can succeed or fail. It has two
 * concrete implementations, Return (for success) and Throw (for failure)
 */
sealed abstract class Try[+R] {
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
  def flatMap[R2](f: R => Try[R2]): Try[R2]

  /**
   * Maps the given function to the value from this Return or returns this if this is a Throw
   */
  def map[X](f: R => X): Try[X]

  /**
   * Converts this to a Throw if the predicate does not obtain.
   */
  def filter(p: R => Boolean): Try[R]

  /**
   * Converts this to a Throw if the predicate does not obtain.
   */
  def withFilter(p: R => Boolean): Try[R]

  /**
   * Calls the exceptionHandler with the exception if this is a Throw. This is like flatMap for the exception.
   *
   * ''Note'' The gnarly type parameterization is there for Java compatibility, since Java
   * does not support higher-kinded types.
   */
  def rescue[R2 >: R](rescueException: PartialFunction[Throwable, Try[R2]]): Try[R2]

  /**
   * Calls the exceptionHandler with the exception if this is a Throw. This is like map for the exception.
   */
  def handle[R2 >: R](rescueException: PartialFunction[Throwable, R2]): Try[R2]

  /**
   * Invoked only if the computation was successful.  Returns a
   * chained `this` as in `respond`.
   */
  def onSuccess(f: R => Unit): Try[R]

  /**
   * Invoke the function on the error, if the computation was
   * unsuccessful.  Returns a chained `this` as in `respond`.
   */
  def onFailure(rescueException: Throwable => Unit): Try[R]

  /**
   * Invoked regardless of whether the computation completed
   * successfully or unsuccessfully.  Implemented in terms of
   * `respond` so that subclasses control evaluation order.  Returns a
   * chained `this` as in `respond`.
   */
  def ensure(f: => Unit): Try[R] =
    respond { _ => f }

  /**
   * Returns None if this is a Throw or a Some containing the value if this is a Return
   */
  def toOption = if (isReturn) Some(apply()) else None

  /**
   * Invokes the given closure when the value is available.  Returns
   * another 'This[R]' that is guaranteed to be available only *after*
   * 'k' has run.  This enables the enforcement of invocation ordering.
   */
  def respond(k: Try[R] => Unit): Try[R] = { k(this); this }

  /**
   * Invokes the given transformation when the value is available,
   * returning the transformed value. This method is like a combination
   * of flatMap and rescue. This method is typically used for more
   * imperative control-flow than flatMap/rescue which often exploits
   * the Null Object Pattern.
   */
  def transform[R2](f: Try[R] => Try[R2]): Try[R2] = f(this)

  /**
   * Returns the given function applied to the value from this Return or returns this if this is a Throw.
   * Alias for flatMap
   */
  def andThen[R2](f: R => Try[R2]) = flatMap(f)

  def flatten[T](implicit ev: R <:< Try[T]): Try[T]
}

final case class Throw[+R](e: Throwable) extends Try[R] {
  def isThrow = true
  def isReturn = false
  def rescue[R2 >: R](rescueException: PartialFunction[Throwable, Try[R2]]) = {
    try {
      if (rescueException.isDefinedAt(e)) rescueException(e) else this
    } catch {
      case NonFatal(e2) => Throw(e2)
    }
  }
  def apply(): R = throw e
  def flatMap[R2](f: R => Try[R2]) = Throw[R2](e)
  def flatten[T](implicit ev: R <:< Try[T]): Try[T] = Throw[T](e)
  def map[X](f: R => X) = Throw(e)
  def filter(p: R => Boolean) = this
  def withFilter(p: R => Boolean) = this
  def onFailure(rescueException: Throwable => Unit) = { rescueException(e); this }
  def onSuccess(f: R => Unit) = this
  def handle[R2 >: R](rescueException: PartialFunction[Throwable, R2]) =
    if (rescueException.isDefinedAt(e)) {
      Try(rescueException(e))
    } else {
      this
    }
}

object Return {
  val Unit = Return(())
  val Void = Return[Void](null)
  val None: Return[Option[Nothing]] = Return(Option.empty)
  val Nil: Return[Seq[Nothing]] = Return(Seq.empty)
  val True: Return[Boolean] = Return(true)
  val False: Return[Boolean] = Return(false)
}

final case class Return[+R](r: R) extends Try[R] {
  def isThrow = false
  def isReturn = true
  def rescue[R2 >: R](rescueException: PartialFunction[Throwable, Try[R2]]) = Return(r)
  def apply() = r
  def flatMap[R2](f: R => Try[R2]) = try f(r) catch { case NonFatal(e) => Throw(e) }
  def flatten[T](implicit ev: R <:< Try[T]): Try[T] = r
  def map[X](f: R => X) = Try[X](f(r))
  def filter(p: R => Boolean) = if (p(apply())) this else Throw(new Try.PredicateDoesNotObtain)
  def withFilter(p: R => Boolean) = filter(p)
  def onFailure(rescueException: Throwable => Unit) = this
  def onSuccess(f: R => Unit) = { f(r); this }
  def handle[R2 >: R](rescueException: PartialFunction[Throwable, R2]) = this
}

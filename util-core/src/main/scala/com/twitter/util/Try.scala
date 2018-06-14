package com.twitter.util

import scala.util.control.NonFatal
import scala.util.{Success, Failure}

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
   * Build a Try from a scala.util.Try. This does nothing
   * more than pattern match and translate Success and Failure
   * to Return and Throw respectively.
   */
  def fromScala[R](tr: scala.util.Try[R]): Try[R] =
    tr match {
      case Success(r) => Return(r)
      case Failure(e) => Throw(e)
    }

  /**
   * Like [[Try.apply]] but allows the caller to specify a handler for fatal
   * errors.
   */
  def withFatals[R](r: => R)(f: PartialFunction[Throwable, Try[R]]): Try[R] =
    try Try(r)
    catch {
      case e: Throwable if f.isDefinedAt(e) => f(e)
    }

  /**
   * Collect the results from the given Trys into a new Try. The result will be a Throw if any of
   * the argument Trys are Throws. The first Throw in the Seq is the one which is surfaced.
   */
  def collect[A](ts: Seq[Try[A]]): Try[Seq[A]] = {
    if (ts.isEmpty) Return(Seq.empty[A])
    else
      Try {
        ts map { t =>
          t()
        }
      }
  }

  /**
   * Convert an [[scala.Option]] to a [[Try]].
   *
   * For users from scala, there's also the implicit class [[OrThrow]] which
   * allows
   *
   * {{{
   * import Try._
   * Option(null).orThrow { new Exception("boom!") }
   * }}}
   *
   * @param o the Option to convert to a Try
   * @param failure a function that returns the Throwable that should be
   * returned if the option is None
   */
  def orThrow[A](o: Option[A])(failure: () => Throwable): Try[A] =
    try {
      o match {
        case Some(item) => Return(item)
        case None => Throw(failure())
      }
    } catch {
      case NonFatal(e) => Throw(e)
    }

  implicit class OrThrow[A](val option: Option[A]) extends AnyVal {
    def orThrow(failure: => Throwable): Try[A] = Try.orThrow(option)(() => failure)
  }
}

/**
 * This class represents a computation that can succeed or fail. It has two
 * concrete implementations, Return (for success) and Throw (for failure)
 */
sealed abstract class Try[+R] {

  /**
   * Convert to a scala.util.Try
   */
  def asScala: scala.util.Try[R]

  /**
   * Returns true if the Try is a Throw, false otherwise.
   */
  def isThrow: Boolean

  /**
   * Returns true if the Try is a Return, false otherwise.
   */
  def isReturn: Boolean

  /**
   * Returns the throwable if this is a Throw, else raises IllegalStateException.
   *
   * Callers should consult isThrow() prior to calling this method to determine whether
   * or not this is a Throw.
   *
   * This method is intended for Java compatibility. Scala consumers are encouraged to
   * pattern match for Throw(t).
   */
  def throwable: Throwable

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
  def foreach(f: R => Unit): Unit = { onSuccess(f) }

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
   * Returns true if this Try is a Return and the predicate p returns true when
   * applied to its value.
   */
  def exists(p: R => Boolean): Boolean

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
    respond { _ =>
      f
    }

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

object Throw {
  private val NotApplied: Throw[Nothing] = Throw[Nothing](null)
  private val AlwaysNotApplied: Any => Throw[Nothing] = scala.Function.const(NotApplied) _
}

final case class Throw[+R](e: Throwable) extends Try[R] {
  def asScala: scala.util.Try[R] = Failure(e)
  def isThrow = true
  def isReturn = false
  def throwable: Throwable = e
  def rescue[R2 >: R](rescueException: PartialFunction[Throwable, Try[R2]]) = {
    try {
      val result = rescueException.applyOrElse(e, Throw.AlwaysNotApplied)
      if (result eq Throw.NotApplied) this else result
    } catch {
      case NonFatal(e2) => Throw(e2)
    }
  }
  def apply(): R = throw e
  def flatMap[R2](f: R => Try[R2]) = this.asInstanceOf[Throw[R2]]
  def flatten[T](implicit ev: R <:< Try[T]): Try[T] = this.asInstanceOf[Throw[T]]
  def map[X](f: R => X): Try[X] = this.asInstanceOf[Throw[X]]
  def cast[X]: Try[X] = this.asInstanceOf[Throw[X]]
  def exists(p: R => Boolean) = false
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
  def asScala: scala.util.Try[R] = Success(r)
  def isThrow: Boolean = false

  def isReturn: Boolean = true

  def throwable: Throwable =
    throw new IllegalStateException("this Try is not a Throw; did you fail to check isThrow?")

  def rescue[R2 >: R](rescueException: PartialFunction[Throwable, Try[R2]]): Try[R2] =
    this

  def apply(): R = r

  def flatMap[R2](f: R => Try[R2]): Try[R2] =
    try f(r)
    catch { case NonFatal(e) => Throw(e) }

  def flatten[T](implicit ev: R <:< Try[T]): Try[T] = r

  def map[X](f: R => X): Try[X] =
    try Return(f(r))
    catch { case NonFatal(e) => Throw(e) }

  def exists(p: R => Boolean): Boolean = p(r)

  def filter(p: R => Boolean): Try[R] =
    if (p(apply())) this else Throw(new Try.PredicateDoesNotObtain)

  def withFilter(p: R => Boolean): Try[R] = filter(p)

  def onFailure(rescueException: Throwable => Unit): Try[R] = this

  def onSuccess(f: R => Unit): Try[R] = { f(r); this }

  def handle[R2 >: R](rescueException: PartialFunction[Throwable, R2]): Try[R2] = this
}

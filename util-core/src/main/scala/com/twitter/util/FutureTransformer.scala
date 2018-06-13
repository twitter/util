package com.twitter.util

/**
 * An alternative interface for performing Future transformations;
 * that is, converting a Future[A] to a Future[B]. This interface is
 * designed to be friendly to Java users since it does not require
 * creating many small Function objects. It is used in conjunction
 * with `transformedBy`.
 *
 * You must override one of `{map, flatMap}`. If you override both
 * `map` and `flatMap`, `flatMap` takes precedence. If you fail to
 * override one of `{map, flatMap}`, an `AbstractMethodError` will be
 * thrown at Runtime.
 *
 * '''Note:''' to end a result with a failure, we encourage you to use either
 * `flatMap` or `rescue` and return a failed Future, instead of throwing an
 * exception.  A failed future can be used by returning
 * `Future.exception(throwable)` instead of throwing an exception.
 *
 * @see [[Future.transform]] which is the equivalent Scala API for further details.
 * @see [[Future.transformedBy]] for using it with a `Future`.
 * @see [[FutureEventListener]] for a Java API for side-effects.
 */
abstract class FutureTransformer[-A, +B] {

  /**
   * Invoked if the computation completes successfully. Returns the
   * new transformed value in a Future.
   */
  def flatMap(value: A): Future[B] = Future.value(map(value))

  /**
   * Invoked if the computation completes successfully. Returns the
   * new transformed value.
   *
   * ''Note'': this method will throw an `AbstractMethodError` if it is not overridden.
   */
  def map(value: A): B =
    throw new AbstractMethodError(s"`map` must be implemented by $this")

  /**
   * Invoked if the computation completes unsuccessfully. Returns the
   * new Future value.
   */
  def rescue(throwable: Throwable): Future[B] = Future.value(handle(throwable))

  /**
   * Invoked if the computation fails. Returns the new transformed
   * value.
   */
  def handle(throwable: Throwable): B = throw throwable
}


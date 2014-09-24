package com.twitter.util

abstract class Function0[+R] extends (() => R)

abstract class ExceptionalFunction0[+R] extends Function0[R] {
   /**
    * Implements apply in terms of abstract applyE, to allow Java code to throw checked exceptions.
    */
   final override def apply(): R = applyE()

   @throws(classOf[Throwable])
   def applyE(): R
}

/**
 * This class is for Java friendliness. Any Scala method that takes Function1[A, B]
 * can now take a Function[A, B]. Because Function1 is a trait, it is very difficult
 * to instantiate directly in Java.
 */
abstract class Function[-T1, +R] extends PartialFunction[T1, R] {
  /**
   * These overrides do nothing but delegate to super. They are necessary for Java
   * compatibility.
   */
  override def compose[A](g: A => T1): A => R = super.compose(g)
  override def andThen[A](g: R => A): PartialFunction[T1, A] = super.andThen(g)

  override def isDefinedAt(x: T1) = true
}

object Function {
  /**
   * Compose a function with a monitor; all invocations of the
   * returned function are synchronized with the given monitor `m`.
   */
  def synchronizeWith[T, R](m: Object)(f: T => R): T => R =
    t => m.synchronized { f(t) }
}

abstract class ExceptionalFunction[-T1, +R] extends Function[T1, R] {
  /**
   * Implements apply in terms of abstract applyE, to allow Java code to throw checked exceptions.
   */
  final override def apply(in: T1): R = applyE(in)
  @throws(classOf[Throwable])
  def applyE(in: T1): R
}

abstract class Function2[-T1, -T2, +R] extends ((T1, T2) => R)

abstract class Command[-T1] extends (T1 => Unit) {
  /**
   * These overrides do nothing but delegate to super. They are necessary for Java
   * compatibility.
   */
  override def andThen[A](g: (Unit) => A) = super.andThen(g)
  override def compose[A](g: (A) => T1) = super.compose(g)
}

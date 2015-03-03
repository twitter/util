package com.twitter.util

import com.twitter.function._
import java.util.concurrent.Callable

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

  /**
   * Creates `() => Unit` function from given `Runnable`.
   */
  def ofRunnable(r: Runnable): () => Unit = () => r.run()

  /**
   * Creates `() => A` function from given `Callable`.
   */
  def ofCallable[A](c: Callable[A]): () => A = () => c.call()

  /**
   * Creates a T => R from a JavaFunction. Used for easier interop
   * between Java 8 and Twitter Util libraries.
   */
  def func[T, R](f: JavaFunction[T, R]) = new Function[T, R] {
    override def apply(value: T): R = f(value)
  }

  /**
   * Creates a T => Unit from a JavaConsumer.
   * Useful for e.g. future.onSuccess
   */
  def cons[T](f: JavaConsumer[T]) = new Function[T, Unit] {
    override def apply(value: T): Unit = f(value)
  }

  /**
   * like `func`, but deals with checked exceptions as well
   */
  def exfunc[T, R](f: ExceptionalJavaFunction[T, R]) = new ExceptionalFunction[T, R] {
    @throws(classOf[Throwable])
    override def applyE(value: T): R = f(value)
  }

  /**
   * like `cons`, but deals with checked exceptions as well
   */
  def excons[T](f: ExceptionalJavaConsumer[T]) = new ExceptionalFunction[T, Unit] {
    @throws(classOf[Throwable])
    override def applyE(value: T): Unit = f(value)
  }
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

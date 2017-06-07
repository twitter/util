package com.twitter.util

import com.twitter.function._
import java.util.concurrent.Callable
import java.util.{function => juf}

abstract class Function0[+R] extends (() => R)

abstract class ExceptionalFunction0[+R] extends Function0[R] {
   /**
    * Implements apply in terms of abstract applyE, to allow Java code to throw checked exceptions.
    */
   final def apply(): R = applyE()

   @throws(classOf[Throwable])
   def applyE(): R
}

/**
 * This class is for Java friendliness. Any Scala method that takes Function1[A, B]
 * can now take a Function[A, B]. Because Function1 is a trait, it is very difficult
 * to instantiate directly in Java.
 */
abstract class Function[-T1, +R] extends PartialFunction[T1, R] {
  // These overrides do nothing but delegate to super. They are necessary for Java
  // compatibility.
  override def compose[A](g: A => T1): A => R = super.compose(g)
  override def andThen[A](g: R => A): PartialFunction[T1, A] = super.andThen(g)

  def isDefinedAt(x: T1): Boolean = true
}

object Function {
  /**
   * Compose a function with a monitor; all invocations of the
   * returned function are synchronized with the given monitor `m`.
   */
  def synchronizeWith[T, R](m: Object)(f: T => R): T => R =
    t => m.synchronized { f(t) }

  /**
   * Creates `() => Unit` function (`scala.Function0`) from given `Runnable`.
   */
  def ofRunnable(r: Runnable): () => Unit = () => r.run()

  /**
   * Creates `() => A` function (`scala.Function0`) from given `Callable`.
   */
  def ofCallable[A](c: Callable[A]): () => A = () => c.call()

  /**
   * Creates a [[Function]] of `T` to `R` from a [[JavaFunction]].
   *
   * Allows for better interop with Scala from Java 8 using lambdas.
   *
   * For example:
   * {{{
   * import com.twitter.util.Future;
   * import static com.twitter.util.Function.func;
   *
   * Future<String> fs = Future.value("example");
   * Future<Integer> fi = fs.map(func(s -> s.length()));
   * // or using method references:
   * Future<Integer> fi2 = fs.map(func(String::length));
   * }}}
   *
   * @see [[exfunc]] if your function throws checked exceptions.
   * @see [[func0]] if you have a function which takes no input.
   * @see [[exfunc0]] if your function throws checked exceptions and takes no input.
   * @see [[cons]] if you have a side-effecting function (return type is `Unit` or `void`)
   */
  def func[T, R](f: JavaFunction[T, R]): Function[T, R] = new Function[T, R] {
    def apply(value: T): R = f(value)
  }

  /**
   * Creates a [[Function0]] of type-`T` from a `java.util.function.JavaSupplier`.
   *
   * Allows for better interop with Scala from Java 8 using lambdas.
   *
   * For example:
   * {{{
   * import com.twitter.util.Future;
   * import static com.twitter.util.Function.func0;
   *
   * Future<String> fs = Future.apply(func0(() -> "example"));
   * }}}
   *
   * @see [[func]] if you have a function which returns a type, `R`.
   * @see [[cons]] if you have a side-effecting function (return type is `Unit` or `void`).
   *
   * @note as this was introduced after util dropped support for versions of Java
   *       below 8, there was no need for a `JavaSupplier` like class.
   */
  def func0[T](f: juf.Supplier[T]): Function0[T] = new Function0[T] {
    def apply(): T = f.get()
  }

  /**
   * Creates a [[Function]] of `T` to `Unit` from a [[JavaConsumer]].
   *
   * Allows for better interop with Scala from Java 8 using lambdas.
   *
   * For example:
   * {{{
   * import com.twitter.util.Future;
   * import static com.twitter.util.Function.cons;
   *
   * Future<String> fs = Future.value("example");
   * Future<String> f2 = fs.onSuccess(cons(s -> System.out.println(s)));
   * // or using method references:
   * fs.onSuccess(cons(System.out::println));
   * }}}
   *
   * @see [[excons]] if your function throws checked exceptions.
   * @see [[func]] if you have a function which returns a type, `R`.
   * @see [[func0]] if you have a function which takes no input.
   */
  def cons[T](f: JavaConsumer[T]): Function[T, Unit] = new Function[T, Unit] {
    def apply(value: T): Unit = f(value)
  }

  /**
   * Creates an [[ExceptionalFunction]] of `T` to `R` from an [[ExceptionalJavaFunction]].
   *
   * Allows for better interop with Scala from Java 8 using lambdas.
   *
   * For example:
   * {{{
   * import com.twitter.util.Future;
   * import static com.twitter.util.Function.exfunc;
   *
   * Future<String> fs = Future.value("example");
   * Future<Integer> fi = fs.map(exfunc(s -> { throw new Exception(s); }));
   * }}}
   */
  def exfunc[T, R](f: ExceptionalJavaFunction[T, R]): ExceptionalFunction[T, R] =
    new ExceptionalFunction[T, R] {
      @throws(classOf[Throwable])
      def applyE(value: T): R = f(value)
    }

  /**
   * Creates an [[ExceptionalFunction]] of `T` to `Unit` from an [[ExceptionalJavaConsumer]].
   *
   * Allows for better interop with Scala from Java 8 using lambdas.
   *
   * For example:
   * {{{
   * import com.twitter.util.Future;
   * import static com.twitter.util.Function.excons;
   *
   * Future<String> fs = Future.value("example");
   * Future<String> f2 = fs.onSuccess(excons(s -> { throw new Exception(s); }));
   * }}}
   */
  def excons[T](f: ExceptionalJavaConsumer[T]): ExceptionalFunction[T, Unit] =
    new ExceptionalFunction[T, Unit] {
      @throws(classOf[Throwable])
      def applyE(value: T): Unit = f(value)
    }

  /**
    * Creates an [[ExceptionalFunction0]] to `T` from an [[ExceptionalSupplier]].
    *
    * Allows for better interop with Scala from Java 8 using lambdas.
    *
    * For example:
    * {{{
    * import com.twitter.util.Future;
    * import static com.twitter.util.Function.exRunnable;
    *
    * FuturePool futurePool = FuturePools.immediatePool();
    * Future<Unit> fu = futurePool.apply(exfunc0(() -> { throw new Exception("blah"); }));
    * }}}
    */
  def exfunc0[T](f: ExceptionalSupplier[T]): ExceptionalFunction0[T] =
    new ExceptionalFunction0[T] {
      @throws(classOf[Throwable])
      def applyE(): T = f()
    }
}

abstract class ExceptionalFunction[-T1, +R] extends Function[T1, R] {
  /**
   * Implements apply in terms of abstract applyE, to allow Java code to throw checked exceptions.
   */
  final def apply(in: T1): R = applyE(in)
  @throws(classOf[Throwable])
  def applyE(in: T1): R
}

abstract class Function2[-T1, -T2, +R] extends ((T1, T2) => R)

abstract class Function3[-T1, -T2, -T3, +R] extends ((T1, T2, T3) => R)

abstract class Command[-T1] extends (T1 => Unit) {
  // These overrides do nothing but delegate to super. They are necessary for Java
  // compatibility.
  override def andThen[A](g: Unit => A): T1 => A = super.andThen(g)
  override def compose[A](g: A => T1): A => Unit = super.compose(g)
}

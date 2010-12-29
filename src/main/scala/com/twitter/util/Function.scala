package com.twitter.util

/**
 * This class is for Java friendliness. Any Scala method that takes Function1[A, B]
 * can now take a Function[A, B]. Because Function1 is a trait, it is very difficult
 * to instantiate directly in Java.
 */
abstract class Function[-T1, R] extends (T1 => R) {
  /**
   * These overrides do nothing but delegate to super. They are necessary for Java
   * compatibility.
   */
  override def compose[A](g: A => T1): A => R = super.compose(g)
  override def andThen[A](g: R => A): T1 => A = super.andThen(g)
}
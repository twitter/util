package com.twitter.util

object Bijection {
  implicit def identity[A]: Bijection[A, A] = new Bijection[A, A] {
    def apply(a: A)  = a
    def invert(a: A) = a

    override def inverse = this
    override def andThen[T](g: Bijection[A, T]) = g
    override def compose[T](g: Bijection[T, A]) = g
  }
}

/**
 * A bijection is a function for which every value in its codomain
 * (set of all possible results) is equivalent to application of the
 * function on a unique value in its domain (all possible
 * inputs). This trait in particular provides an interface for
 * functions that can be 'unapplied' as well as applied. A codec that
 * can convert to and from a set of objects and their serialized form
 * is an example of a bijection.
 */
trait Bijection[A, B] extends (A => B) { self =>
  def apply(a: A): B
  def invert(b: B): A
  def unapply(b: B) = Try(Some(invert(b))).getOrElse(None)

  /**
   * Return a Bijection that is the inverse of this one.
   */
  def inverse: Bijection[B, A] = _inverse

  private lazy val _inverse = {
    new Bijection[B, A] {
      def apply(b: B)  = self.invert(b)
      def invert(a: A) = self.apply(a)
      override def inverse = self
    }
  }

  /**
   * Composes two instances of Bijection in a new Bijection, with this one applied first.
   */
  def andThen[C](g: Bijection[B, C]) = {
    new Bijection[A, C] {
      def apply(a: A)  = g(self(a))
      def invert(c: C) = self.invert(g.invert(c))
    }
  }

  /**
   * Composes two instances of Bijection in a new Bijection, with this one applied last.
   */
  def compose[T](g: Bijection[T, A]) = g andThen this
}

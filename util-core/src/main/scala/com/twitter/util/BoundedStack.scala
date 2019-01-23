package com.twitter.util

import scala.reflect.ClassTag

/**
 * A "stack" with a bounded size.  If you push a new element on the top
 * when the stack is full, the oldest element gets dropped off the bottom.
 */
class BoundedStack[A: ClassTag](val maxSize: Int) extends Seq[A] {
  private val array = new Array[A](maxSize)
  private var top = 0
  private var count_ = 0

  def length: Int = count_
  override def size: Int = count_

  def clear(): Unit = {
    top = 0
    count_ = 0
  }

  /**
   * Gets the element from the specified index in constant time.
   */
  def apply(i: Int): A = {
    if (i >= count_) throw new IndexOutOfBoundsException(i.toString)
    else array((top + i) % maxSize)
  }

  /**
   * Pushes an element, possibly forcing out the oldest element in the stack.
   */
  def +=(elem: A): Unit = {
    top = if (top == 0) maxSize - 1 else top - 1
    array(top) = elem
    if (count_ < maxSize) count_ += 1
  }

  /**
   * Inserts an element 'i' positions down in the stack.  An 'i' value
   * of 0 is the same as calling this += elem.  This is a O(n) operation
   * as elements need to be shifted around.
   */
  def insert(i: Int, elem: A): Unit = {
    if (i == 0) this += elem
    else if (i > count_) throw new IndexOutOfBoundsException(i.toString)
    else if (i == count_) {
      array((top + i) % maxSize) = elem
      count_ += 1
    } else {
      val swapped = this(i)
      this(i) = elem
      insert(i - 1, swapped)
    }
  }

  /**
   * Replaces an element in the stack.
   */
  def update(index: Int, elem: A): Unit = {
    array((top + index) % maxSize) = elem
  }

  /**
   * Adds multiple elements, possibly overwriting the oldest elements in
   * the stack.  If the given iterable contains more elements that this
   * stack can hold, then only the last maxSize elements will end up in
   * the stack.
   */
  def ++=(iter: Iterable[A]): Unit = {
    for (elem <- iter) this += elem
  }

  /**
   * Removes the top element in the stack.
   */
  def pop: A = {
    if (count_ == 0) throw new NoSuchElementException
    else {
      val res = array(top)
      top = (top + 1) % maxSize
      count_ -= 1
      res
    }
  }

  override def iterator: Iterator[A] = new Iterator[A] {
    var idx = 0
    def hasNext = idx != count_
    def next = {
      val res = apply(idx)
      idx += 1
      res
    }
  }
}

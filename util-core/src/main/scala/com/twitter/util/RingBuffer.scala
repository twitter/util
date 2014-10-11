package com.twitter.util

@deprecated("Use com.google.common.collect.EvictingQueue instead")
class RingBuffer[A: ClassManifest](val maxSize: Int) extends Seq[A] {
  private val array = new Array[A](maxSize)
  private var read = 0
  private var write = 0
  private var count_ = 0

  def length = count_
  override def size = count_

  def clear() {
    read = 0
    write = 0
    count_ = 0
  }

  /**
   * Gets the element from the specified index in constant time.
   */
  def apply(i: Int): A = {
    if (i >= count_) throw new IndexOutOfBoundsException(i.toString)
    else array((read + i) % maxSize)
  }

  /**
   * Overwrites an element with a new value
   */
  def update(i: Int, elem: A) {
    if (i >= count_) throw new IndexOutOfBoundsException(i.toString)
    else array((read + i) % maxSize) = elem
  }

  /**
   * Adds an element, possibly overwriting the oldest elements in the buffer
   * if the buffer is at capacity.
   */
  def +=(elem: A) {
    array(write) = elem
    write = (write + 1) % maxSize
    if (count_ == maxSize) read = (read + 1) % maxSize
    else count_ += 1
  }

  /**
   * Adds multiple elements, possibly overwriting the oldest elements in
   * the buffer.  If the given iterable contains more elements that this
   * buffer can hold, then only the last maxSize elements will end up in
   * the buffer.
   */
  def ++=(iter: Iterable[A]) {
    for (elem <- iter) this += elem
  }

  /**
   * Removes the next element from the buffer
   */
  def next: A = {
    if (read == write) throw new NoSuchElementException
    else {
      val res = array(read)
      read = (read + 1) % maxSize
      count_ -= 1
      res
    }
  }

  override def iterator = new Iterator[A] {
    var idx = 0
    def hasNext = idx != count_
    def next = {
      val res = apply(idx)
      idx += 1
      res
    }
  }

  override def drop(n: Int): RingBuffer[A] = {
    if (n >= maxSize) clear()
    else {
      read = (read + n) % maxSize
      count_ = math.max(0, count_ - n)
    }
    this
  }

  def removeWhere(fn: A=>Boolean): Int = {
    var rmCount_ = 0
    var j = 0
    for (i <- 0 until count_) {
      val elem = apply(i)
      if (fn(elem)) rmCount_ += 1
      else {
        if (j < i) update(j, elem)
        j += 1
      }
    }
    count_ -= rmCount_
    write = (read + count_) % maxSize
    rmCount_
  }
}

package com.twitter.util

class RingBuffer[A](val maxSize: Int) extends Seq[A] {
  private val array = new Array[A](maxSize)
  private var read = 0
  private var write = 0
  private var count = 0

  def length = count
  override def size = count

  def clear() {
    read = 0
    write = 0
    count = 0
  }

  /**
   * Gets the element from the specified index in constant time.
   */
  def apply(i: Int): A = {
    if (i >= count) throw new IndexOutOfBoundsException(i.toString)
    else array((read + i) % maxSize)
  }
  
  /**
   * Overwrites an element with a new value
   */
  def update(i: Int, elem: A) {
    if (i >= count) throw new IndexOutOfBoundsException(i.toString)
    else array((read + i) % maxSize) = elem
  }

  /**
   * Adds an element, possibly overwriting the oldest elements in the buffer
   * if the buffer is at capacity.
   */
  def +=(elem: A) {
    array(write) = elem
    write = (write + 1) % maxSize
    if (count == maxSize) read = (read + 1) % maxSize
    else count += 1
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
      count -= 1
      res
    }
  }

  def elements = new Iterator[A] {
    var idx = 0
    def hasNext = idx != count
    def next = {
      val res = apply(idx)
      idx += 1
      res
    }
  }

  override def drop(n: Int): RingBuffer[A] = {
    if (n >= maxSize) clear()
    else read = (read + n) % maxSize
    this
  }
  
  def removeWhere(fn: A=>Boolean): Int = {
    var rmCount = 0
    var j = 0
    for (i <- 0 until count) {
      val elem = apply(i)
      if (fn(elem)) rmCount += 1
      else {
        if (j < i) update(j, elem)
        j += 1
      }
    }
    count -= rmCount
    write = (read + count) % maxSize
    rmCount
  }
}

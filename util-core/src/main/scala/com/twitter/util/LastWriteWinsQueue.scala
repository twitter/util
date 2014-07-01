package com.twitter.util
import java.util.Collection
import java.util.concurrent.atomic.AtomicReference

/**
 * An implementation of java.util.Queue that has LIFO order and a maximum capacity of 1
 * When the Queue is full, a push replaces the item.
 */
class LastWriteWinsQueue[A] extends java.util.Queue[A] {
  val item = new AtomicReference[Option[A]](None)

  def clear() {
    item.set(None)
  }

  def retainAll(p1: Collection[_]) = throw new UnsupportedOperationException

  def removeAll(p1: Collection[_]) = throw new UnsupportedOperationException

  def addAll(p1: Collection[_ <: A]) = throw new UnsupportedOperationException

  def containsAll(p1: Collection[_]) =
    p1.size == 1 && item.get == p1.iterator.next()

  def remove(candidate: AnyRef) = {
    val contained = item.get
    val containsCandidate = contained.map(_ == candidate).getOrElse(false)
    if (containsCandidate) {
      item.compareAndSet(contained, None)
    }
    containsCandidate
  }

  def toArray[T](array: Array[T with java.lang.Object]): Array[T with java.lang.Object] = {
    val contained = item.get
    if (contained.isDefined && array.size > 0) {
      array(0) = contained.get.asInstanceOf[T with java.lang.Object]
      array
    } else if (contained.isDefined) {
      Array[Any](contained.get).asInstanceOf[Array[T with java.lang.Object]]
    } else Array[Any]().asInstanceOf[Array[T with java.lang.Object]]
  }

  def toArray = toArray(new Array[AnyRef](0))

  def iterator = null

  def contains(p1: AnyRef) = false

  def isEmpty = item.get.isDefined

  def size = if (item.get.isDefined) 1 else 0

  def peek = item.get.getOrElse(null.asInstanceOf[A])

  def element = item.get.getOrElse(throw new NoSuchElementException)

  def poll = item.getAndSet(None).getOrElse(null.asInstanceOf[A])

  def remove = item.getAndSet(None).getOrElse(throw new NoSuchElementException)

  def offer(p1: A) = {
    item.set(Some(p1))
    true
  }

  def add(p1: A) = {
    item.set(Some(p1))
    true
  }
}

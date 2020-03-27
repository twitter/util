package com.twitter.util

import scala.collection.mutable

private[util] class HealthyQueue[A](
  makeItem: () => Future[A],
  numItems: Int,
  isHealthy: A => Boolean)
    extends scala.collection.mutable.Queue[Future[A]] {

  0.until(numItems) foreach { _ => this += makeItem() }

  override def addOne(elem: Future[A]): HealthyQueue.this.type = synchronized {
    super.addOne(elem)
  }

  override def prepend(elem: Future[A]): HealthyQueue.this.type = synchronized {
    super.prepend(elem)
  }

  override def enqueue(elem: Future[A]) = synchronized {
    super.enqueue(elem)
  }
  override def enqueue(elem1: Future[A], elem2: Future[A], elems: Future[A]*) = synchronized {
    super.enqueue(elem1, elem2, elems: _*)
  }

  override def dequeue(): Future[A] = synchronized {
    if (isEmpty) throw new NoSuchElementException("queue empty")

    super.dequeue() flatMap { item =>
      if (isHealthy(item)) {
        Future(item)
      } else {
        val item = makeItem()
        synchronized {
          super.enqueue(item)
          dequeue()
        }
      }
    }
  }
}

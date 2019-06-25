package com.twitter.util

import scala.collection.mutable

private[util] class HealthyQueue[A](makeItem: () => Future[A], numItems: Int, isHealthy: A => Boolean)
    extends mutable.Queue[Future[A]] {

  0.until(numItems) foreach { _ =>
    this += makeItem()
  }

  override def +=(elem: Future[A]): HealthyQueue.this.type = synchronized {
    super.+=(elem)
  }

  override def +=:(elem: Future[A]): HealthyQueue.this.type = synchronized {
    super.+=:(elem)
  }

  override def enqueue(elems: Future[A]*): Unit = synchronized {
    super.enqueue(elems: _*)
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
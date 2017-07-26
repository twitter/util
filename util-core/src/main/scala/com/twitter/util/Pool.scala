package com.twitter.util

import scala.collection.mutable

trait Pool[A] {
  def reserve(): Future[A]
  def release(a: A): Unit
}

class SimplePool[A](items: mutable.Queue[Future[A]]) extends Pool[A] {
  def this(initialItems: Seq[A]) = this {
    val queue = new mutable.Queue[Future[A]]
    queue ++= initialItems.map(Future(_))
    queue
  }

  private val requests = new mutable.Queue[Promise[A]]

  def reserve(): Future[A] = synchronized {
    if (items.isEmpty) {
      val future = new Promise[A]
      requests += future
      future
    } else {
      items.dequeue()
    }
  }

  def release(item: A): Unit = {
    items += Future[A](item)
    synchronized {
      if (requests.nonEmpty && items.nonEmpty)
        Some((requests.dequeue(), items.dequeue()))
      else
        None
    } map {
      case (request, currItem) =>
        currItem.respond(request() = _)
    }
  }
}

abstract class FactoryPool[A](numItems: Int) extends Pool[A] {
  private val healthyQueue = new HealthyQueue[A](makeItem, numItems, isHealthy)
  private val simplePool = new SimplePool[A](healthyQueue)

  def reserve(): Future[A] = simplePool.reserve()
  def release(a: A): Unit = simplePool.release(a)
  def dispose(a: A): Unit = {
    healthyQueue += makeItem()
  }

  protected def makeItem(): Future[A]
  protected def isHealthy(a: A): Boolean
}

private class HealthyQueue[A](makeItem: () => Future[A], numItems: Int, isHealthy: A => Boolean)
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
          enqueue(item)
          dequeue()
        }
      }
    }
  }
}

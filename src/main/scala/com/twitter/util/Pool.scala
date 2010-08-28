package com.twitter.util

import scala.collection.mutable

trait Pool[A] {
  def reserve(): Future[A]
  def release(a: A)
}

class SimplePool[A](items: mutable.Queue[Future[A]]) extends Pool[A] {
  def this(items: Seq[A]) = this {
    val queue = new mutable.Queue[Future[A]]
    queue ++= items map { item => Future.constant(item) }
    queue
  }

  private val requests = new mutable.Queue[Promise[A]]

  def reserve() = synchronized {
    if (items.isEmpty) {
      val future = new Promise[A]
      requests += future
      future
    } else {
      items.dequeue()
    }
  }

  def release(item: A) {
    items += Future.constant(item)
    synchronized {
      if (!requests.isEmpty && !items.isEmpty)
        Some((requests.dequeue(), items.dequeue()))
      else
        None
    } map { case (request, item) =>
      item respond(request() = _)
    }
  }
}

abstract class FactoryPool[A](numItems: Int) extends Pool[A] {
  private val healthyQueue = new HealthyQueue[A](makeItem _, numItems, isHealthy(_))
  private val simplePool = new SimplePool[A](healthyQueue)

  def reserve() = simplePool.reserve()
  def release(a: A) = simplePool.release(a)
  def dispose(a: A) {
    healthyQueue += makeItem()
  }

  protected def makeItem(): Future[A]
  protected def isHealthy(a: A): Boolean
}

private class HealthyQueue[A](
  makeItem: () => Future[A],
  numItems: Int,
  isHealthy: A => Boolean)
  extends mutable.QueueProxy[Future[A]]
{
  val self = new mutable.Queue[Future[A]]
  0.until(numItems) foreach { _ => self += makeItem() }

  override def +=(item: Future[A]) = synchronized { self += item }

  override def dequeue() = synchronized {
    if (isEmpty) throw new Predef.NoSuchElementException

    self.dequeue() flatMap { item =>
      if (isHealthy(item)) {
        Future.constant(item)
      } else {
        val item = makeItem()
        synchronized {
          this += item
          dequeue()
        }
      }
    }
  }
}
package com.twitter.util

import scala.collection.mutable

trait Pool[E <: Throwable, A] {
  def reserve(): Future[E, A]
  def release(a: A)
}

class SimplePool[E <: Throwable, A](items: mutable.Queue[Future[E, A]]) extends Pool[E, A] {
  def this(items: Seq[A]) = this {
    val queue = new mutable.Queue[Future[E, A]]
    queue ++= items map { item => Future(item) }
    queue
  }

  private val requests = new mutable.Queue[Promise[E, A]]

  def reserve() = synchronized {
    if (items.isEmpty) {
      val future = new Promise[E, A]
      requests += future
      future
    } else {
      items.dequeue()
    }
  }

  def release(item: A) {
    items += Future[E, A](item)
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

abstract class FactoryPool[E <: Throwable, A](numItems: Int) extends Pool[E, A] {
  private val healthyQueue = new HealthyQueue[E, A](makeItem _, numItems, isHealthy(_))
  private val simplePool = new SimplePool[E, A](healthyQueue)

  def reserve() = simplePool.reserve()
  def release(a: A) = simplePool.release(a)
  def dispose(a: A) {
    healthyQueue += makeItem()
  }

  protected def makeItem(): Future[E, A]
  protected def isHealthy(a: A): Boolean
}

private class HealthyQueue[E <: Throwable, A](
  makeItem: () => Future[E, A],
  numItems: Int,
  isHealthy: A => Boolean)
  extends mutable.QueueProxy[Future[E, A]]
{
  val self = new mutable.Queue[Future[E, A]]
  0.until(numItems) foreach { _ => self += makeItem() }

  override def +=(item: Future[E, A]) = synchronized { self += item }

  override def dequeue() = synchronized {
    if (isEmpty) throw new Predef.NoSuchElementException

    self.dequeue() flatMap { item =>
      if (isHealthy(item)) {
        Future(item)
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
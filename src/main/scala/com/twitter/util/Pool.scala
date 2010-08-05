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

  private val requests = new mutable.Queue[NotifyingFuture[A]]

  def reserve() = synchronized {
    if (items.isEmpty) {
      val future = new NotifyingFuture[A]
      requests += future
      future
    } else {
      items.dequeue()
    }
  }

  def release(item: A) {
    synchronized {
      items += Future.constant(item)
      if (!requests.isEmpty) {
        val request = requests.dequeue()
        items.dequeue() respond(request.setResult(_))
      }
    }
  }
}

abstract class FactoryPool[A](numItems: Int) extends Pool[A] {
  private val healthyQueue = new HealthyQueue[A](makeItem _, numItems, isHealthy(_))
  private val simplePool = new SimplePool[A](healthyQueue)

  def reserve() = simplePool.reserve()
  def release(a: A) = simplePool.release(a)

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

  override def dequeue() = {
    if (isEmpty) throw new Predef.NoSuchElementException

    self.dequeue() flatMap { item =>
      if (isHealthy(item)) {
        Future.constant(item)
      } else {
        makeItem()
      }
    }
  }
}
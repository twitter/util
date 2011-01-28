package com.twitter.concurrent

import java.util.concurrent.ConcurrentLinkedQueue
import com.twitter.util.CountDownLatch

trait Channel[+A] {
  def receive(k: Message[A] => Unit)
  def foreach(f: A => Unit) {
    receive {
      case Value(a) => f(a)
      case End =>
    }
  }
  def join()
  def close()
  def isOpen: Boolean
}

sealed abstract class Message[+A]
case class Value[A](get: A) extends Message[A]
case object End extends Message[Nothing]

class Topic[A] extends Channel[A] with Serialized {
  private[this] var open = true
  private[this] var subscriber: Option[Message[A] => Unit] = None
  private[this] val latch = new CountDownLatch(1)
  private[this] var onReceive: Option[() => Unit] = None

  def isOpen = open
  def hasSubscriber = subscriber.isDefined

  def send(a: A) {
    require(open, "Channel is closed")

    if (open && subscriber.isDefined) subscriber.foreach(_.apply(Value(a)))
  }

  def onReceive(f: => Unit) {
    require(!onReceive.isDefined)
    serialized {
      if (!onReceive.isDefined) {
        onReceive = Some(() => f)
      }
    }
  }

  def close() {
    serialized {
      if (open) {
        open = false
        subscriber foreach(_(End))
        latch.countDown()
      }
    }
  }

  def receive(listener: Message[A] => Unit) {
    require(!subscriber.isDefined,
      "Only one subscriber to the topic is supported for now")
    serialized {
      if (!subscriber.isDefined) {
        subscriber = Some(listener)
        if (!open) listener(End)
        else onReceive.foreach(_.apply)
      }
    }
  }

  def join() {
    latch.await()
  }
}
package com.twitter.concurrent

import java.util.concurrent.ConcurrentLinkedQueue

trait Channel[+A] {
  def receive(k: Message[A] => Unit)
}

sealed abstract class Message[+A]
case class Value[A](get: A) extends Message[A]
case object End extends Message[Nothing]

class Topic[A] extends Channel[A] with Serialized {
  private[this] var open = true
  private[this] val queue = new ConcurrentLinkedQueue[A]
  private[this] var subscriber: Option[Message[A] => Unit] = None

  def send(a: A) {
    serialized {
      require(open, "Channel is closed")

      if (!subscriber.isDefined) queue.offer(a)
      else subscriber.get(Value(a))
    }
  }

  def close() {
    serialized {
      require(open, "Channel closed twice")
      open = false
      subscriber foreach(_(End))
    }
  }

  def receive(listener: Message[A] => Unit) {
    serialized {
      require(!subscriber.isDefined,
        "Only one subscriber to the topic is supported for now")
      subscriber = Some(listener)
      while (!queue.isEmpty) listener(Value(queue.poll()))
      if (!open) listener(End)
    }
  }

  private[this] def emptyQueue() {
    while (!queue.isEmpty) queue.poll()
  }
}
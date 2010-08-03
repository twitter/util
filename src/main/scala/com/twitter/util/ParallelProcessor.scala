/** Copyright 2009 Twitter, Inc. */
package com.twitter.util

import java.util.concurrent.{BlockingQueue, TimeUnit}

/**
 * Abstract class for handling scatter-gather processing across a fixed
 * number of threads, like a parser pool.
 */
abstract class ParallelProcessor[A,B] {
  private val runningLatch = new CountDownLatch(1)
  private lazy val doneLatch = new CountDownLatch(threadCount)

  /**
   * Values to be processed should be placed in this queue.
   */
  def inputQueue: BlockingQueue[A]
  
  /**
   * Values that have been processed are placed in this queue.
   */
  def outputQueue: BlockingQueue[B]

  /**
   * Number of threads to use.
   */
  def threadCount: Int

  /**
   * Maximum time for a worker thread to wait on the queue before timeing out,
   * and checking if the pool has been stopped.
   */
  def pollTimeout: Long

  /**
   * Processes the input in one of the worker threads.  If this method
   * returns None, no result is placed in the outputQueue.
   */
  protected def process(input: A): Option[B]

  def start(): Unit = this.synchronized {
    if (runningLatch.count == 0)
      throw new IllegalStateException("Already started!")
    else {
      for (i <- 0 until threadCount) {
        new Thread(new Worker, "ParallelProcessor-" + i).start()
      }
    }
  }

  /**
   * Stops all worker threads, optionally waiting until
   * all threads have stopped
   */
  def stop(wait: Boolean): Unit = this.synchronized {
    runningLatch.countDown()
    if (wait) {
      doneLatch.await()
    }
  }

  private class Worker extends Runnable {
    def run() {
      while (runningLatch.count > 0) {
        inputQueue.poll(pollTimeout, TimeUnit.MILLISECONDS) match {
          case null => // timeout?
          case input =>
            try {
              process(input).foreach(outputQueue offer _)
            } catch {
              case ex => println("error")
            }
        }
      }
      doneLatch.countDown()
    }
  }
}

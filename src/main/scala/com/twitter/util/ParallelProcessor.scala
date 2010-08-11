/** Copyright 2010 Twitter, Inc. */
package com.twitter.util

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{BlockingQueue, Executors, TimeUnit}

/**
 * Abstract class for handling scatter-gather processing across a fixed
 * number of threads.
 */
abstract class ParallelProcessor[A, B] {
  private val continue = new AtomicBoolean(true)
  private val executor = Executors.newFixedThreadPool(threadCount)

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

  def start() {
    for (i <- 0 until threadCount) {
      executor.submit(new Worker)
    }
  }

  /**
   * Stops all worker threads, optionally waiting until
   * all threads have stopped
   */
  def stop() {
    continue.set(false)
    executor.shutdown()
  }

  private class Worker extends Runnable {
    def run() {
      while (continue.get) {
        inputQueue.poll(pollTimeout, TimeUnit.MILLISECONDS) match {
          case null => // timeout?
          case input => {
            try {
              process(input).foreach(outputQueue offer _)
            } catch {
              case ex => println("error")
            }
          }
        }
      }
    }
  }
}

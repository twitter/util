package com.twitter.io

/**
 * Trait that indicates what kind of stream termination a stream saw.
 */
sealed abstract class StreamTermination {
  def isFullyRead: Boolean
}

object StreamTermination {

  /**
   * Indicates a stream terminated when it was read all the way until the end.
   */
  case object FullyRead extends StreamTermination {
    val Return: com.twitter.util.Return[FullyRead.type] = com.twitter.util.Return(this)

    def isFullyRead: Boolean = true
  }

  /**
   * Indicates a stream terminated when it was discarded by the reader before it
   * could be read all the way until the end.
   */
  case object Discarded extends StreamTermination {
    val Return: com.twitter.util.Return[Discarded.type] = com.twitter.util.Return(this)

    def isFullyRead: Boolean = false
  }
}

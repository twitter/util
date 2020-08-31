package com.twitter.inject

import com.twitter.app.Flag

/** Transforms a [[com.twitter.app.Flags]] collection into a map look up */
case class Flags private[inject] (private[inject] val flgs: com.twitter.app.Flags) {
  private[this] val underlying: Map[String, Flag[_]] = {
    val flags: Seq[Flag[_]] = flgs.getAll(includeGlobal = false).toSeq
    flags.map(f => f.name -> f).toMap
  }

  /** gets Flag from [com.twitter.app.Flags] map look up */
  def get(name: String): Option[Flag[_]] = underlying.get(name)

  /**  gets set of defined Flags */
  def getAll: Seq[Flag[_]] = underlying.values.toSeq
}

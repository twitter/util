package com.twitter.util.tunable

import com.twitter.app.LoadService

/**
 * [[TunableMap]] loaded through `com.twitter.app.LoadService`.
 */
trait ServiceLoadedTunableMap extends TunableMap {

  /**
   * Unique identifier for this [[TunableMap]]. A service may have multiple
   * [[TunableMap]]s loaded through `com.twitter.app.LoadService`.
   */
  def id: String
}

object ServiceLoadedTunableMap {

  /**
   * Uses `com.twitter.app.LoadService` to load the [[TunableMap]] for a given `id`.
   *
   * If no matches are found, a [[NullTunableMap]] is returned.
   *
   * If more than one match is found, an `IllegalStateException` is thrown.
   */
  def apply(id: String): TunableMap = {

    val tunableMaps = LoadService[ServiceLoadedTunableMap]().filter(_.id == id).toList

    tunableMaps match {
      case Nil =>
        NullTunableMap
      case tunableMap :: Nil =>
        tunableMap
      case _ =>
        throw new IllegalStateException(
          s"Found multiple `ServiceLoadedTunableMap`s for $id: ${tunableMaps.mkString(", ")}"
        )
    }
  }
}

package com.twitter.util.tunable

import com.twitter.app.LoadService
import com.twitter.concurrent.Once
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

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

  private[this] val loadedMaps = new ConcurrentHashMap[String, TunableMap]()

  /**
   * Uses `com.twitter.app.LoadService` to load the [[TunableMap]] for a given `id`.
   *
   * If no matches are found, a [[NullTunableMap]] is returned.
   *
   * If more than one match is found, an `IllegalStateException` is thrown.
   */
  def apply(id: String): TunableMap = {
    init()
    loadedMaps.getOrDefault(id, NullTunableMap)
  }

  private[this] val init = Once {
    val serviceLoadedTunableMap = LoadService[ServiceLoadedTunableMap]().toList
    serviceLoadedTunableMap.foreach(setLoadedMap)
  }

  private[this] def setLoadedMap(serviceLoadedTunableMap: ServiceLoadedTunableMap): Unit = {
    loadedMaps.compute(
      serviceLoadedTunableMap.id,
      new BiFunction[String, TunableMap, TunableMap] {
        def apply(
          id: String,
          curr: TunableMap
        ): TunableMap = {
          if (curr != null) throw new IllegalStateException(
            s"Found multiple `ServiceLoadedTunableMap`s for $id: $curr, $serviceLoadedTunableMap"
          )
          else serviceLoadedTunableMap
        }
      }
    )
  }

  /**
   * Uses `com.twitter.app.LoadService` to load all [[ServiceLoadedTunableMap]]s,
   * The subtype `com.twitter.util.tunable.ConfigbusTunableMap`s would re-construct
   * and re-subscribe to the ConfigBus.
   *
   * @note this should be called after ServerInfo.initialized().
   */
  private[twitter] def reloadAll(): Unit = {
    val serviceLoadedTunableMaps = LoadService[ServiceLoadedTunableMap]().toList
    serviceLoadedTunableMaps.foreach { serviceLoadedTunableMap =>
      loadedMaps.computeIfPresent(
        serviceLoadedTunableMap.id,
        new BiFunction[String, TunableMap, TunableMap] {
          def apply(
            id: String,
            curr: TunableMap
          ): TunableMap = serviceLoadedTunableMap
        }
      )
    }
  }
}

package com.twitter.util.tunable

/**
 * Object used for getting the Configbus-backed [[TunableMap]] for a given `id`.
 * This is currently under construction and will compose [[TunableMap]]s from different sources
 * (local file-based, in-memory) in the future.
 */
private[twitter] object StandardTunableMap {

  def apply(id: String): TunableMap =
    ServiceLoadedTunableMap(id)
}

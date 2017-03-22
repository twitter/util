package com.twitter.util.tunable

/**
 * Object used for getting the [[TunableMap]] for a given `id`. This [[TunableMap]] is composed
 * from 3 sources, in order of priority:
 *
 *  i. A mutable, in-process [[TunableMap.Mutable]].
 *  i. The dynamically loaded [[TunableMap]], provided via [[ServiceLoadedTunableMap.apply]].
 *  i. The JSON file-based [[TunableMap]], provided via [[JsonTunableMapper.loadJsonTunables]].
 *
 *  A new composed [[TunableMap]] is returned on each `apply`.
 */
private[twitter] object StandardTunableMap {

  def apply(id: String): TunableMap =
    apply(id, TunableMap.newMutable())

  // Exposed for testing
  private[tunable] def apply(
    id: String,
    mutable: TunableMap
  ): TunableMap = {
    val json = JsonTunableMapper.loadJsonTunables(id)
    TunableMap.of(
      mutable,
      ServiceLoadedTunableMap(id),
      json
    )
  }
}

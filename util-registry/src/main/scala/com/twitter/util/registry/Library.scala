package com.twitter.util.registry

import java.util.logging.Logger

/**
 * Utility for library owners to register information about their libraries in
 * the registry.
 */
object Library {
  private[this] val log = Logger.getLogger(getClass.getName)
  private[registry] val Registered = "__registered"

  /**
   * Registers your library with util-registry under the "library" namespace.
   *
   * May only be called once with a given `name`.  `params` and `name` must abide
   * by the guidelines for keys and values set in [[Registry]].
   *
   * @returns None if a library has already been registered with the given `name`,
   * or a [[Roster]] for resetting existing fields in the map otherwise.
   */
  def register(name: String, params: Map[String, String]): Option[Roster] = {
    val registry = GlobalRegistry.get
    val prefix = Seq("library", name)
    val old = registry.put(prefix, Registered)
    old match {
      case Some(oldValue) =>
        registry.put(prefix, oldValue)
        log.warning(s"""Tried to register a second library named "$name"""")
        None
      case None =>
        params.foreach {
          case (key, value) =>
            registry.put(prefix :+ key, value)
        }
        Some(new Roster(prefix, params.keySet, log))
    }
  }
}

/**
 * Can change the value of params that were already set in the registry, but cannot
 * add new ones.
 */
class Roster private[registry] (scope: Seq[String], keys: Set[String], log: Logger) {
  private[this] val registry = GlobalRegistry.get

  /**
   * Changes a key's value if it already exists in the registry.
   *
   * Only works in the existing `scope`, and only works for
   *
   * `key` and `value` must abide by the guidelines for keys and values set
   * in [[Registry]].
   */
  def update(key: String, value: String): Boolean =
    keys(key) && {
      val newKey = scope :+ key
      val result = registry.put(newKey, value)

      // TODO: it's impossible to remove bad entries with the current registry API
      // but we should be OK because this is impossible in theory
      if (result.isEmpty) {
        val serialized = s""""(${newKey.mkString(",")})""""
        log.warning(
          s"expected there to be a value at key $serialized in registry but it was empty."
        )
      }
      result.isDefined
    }
}

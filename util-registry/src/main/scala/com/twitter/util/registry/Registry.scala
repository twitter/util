package com.twitter.util.registry

import java.util.NoSuchElementException

private[registry] final case class Entry(key: Seq[String], value: String)

private[registry] object Entry {
  val TupledMethod: ((Seq[String], String)) => Entry = (Entry.apply _).tupled
}

/**
 * This is an expert-level API; it is not meant for end-users.
 *
 * The registry is a hierarchical key/value store, where all keys are sequences
 * of Strings, and values are Strings.
 *
 * Keys and values must be non-control ascii, and must not contain the '/'
 * character.  If you pass in a key or value with an invalid character, it will
 * silently be removed.  If this makes your key clash with another key, it will
 * overwrite.
 */
private[registry] trait Registry extends Iterable[Entry] {
  /**
   * Provides an iterator over the registry.
   *
   * It is the responsibility of the caller to synchronize if they would like to
   * iterate in multiple threads, but the iterator is guaranteed not to change as
   * it is called.
   */
  def iterator(): Iterator[Entry]

  /**
   * Registers a value in the registry, and returns the old value (if any).
   */
  def put(key: Seq[String], value: String): Option[String]
}

private[registry] class NaiveRegistry extends Registry {
  private[this] var registry = Map.empty[Seq[String], String]

  def iterator(): Iterator[Entry] = synchronized(registry).iterator.map(Entry.TupledMethod)

  def put(key: Seq[String], value: String): Option[String] = {
    val sanitizedKey = key.map(sanitize)
    val sanitizedValue = sanitize(value)
    synchronized {
      val result = registry.get(sanitizedKey)
      registry += sanitizedKey -> sanitizedValue
      result
    }
  }

  private[this] def sanitize(key: String): String =
    key.filter { char => char > 31 && char <= 127 && char != '/' }
}

private[registry] object GlobalRegistry {
  private[this] val registry = new NaiveRegistry

  def get: Registry = registry
}

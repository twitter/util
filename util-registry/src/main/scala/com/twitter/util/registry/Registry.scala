package com.twitter.util.registry

import com.twitter.util.Local
import scala.annotation.varargs

/**
 * This is an expert-level API; it is not meant for end-users.
 */
final case class Entry(key: Seq[String], value: String)

/**
 * This is an expert-level API; it is not meant for end-users.
 */
object Entry {
  val TupledMethod: ((Seq[String], String)) => Entry = (Entry.apply _).tupled
}

/**
 * This is an expert-level API; it is not meant for end-users.
 *
 * The registry is a hierarchical key/value store, where all keys are sequences
 * of Strings, and values are Strings.
 *
 * Keys and values must be non-control ascii.  If you pass in a key or value
 * with an invalid character, the character will silently be removed.  If this
 * makes your key clash with another key, it will overwrite.
 */
trait Registry extends Iterable[Entry] {
  /**
   * Provides an iterator over the registry.
   *
   * It is the responsibility of the caller to synchronize if they would like to
   * iterate in multiple threads, but the iterator is guaranteed not to change as
   * it is called.
   */
  def iterator: Iterator[Entry]

  /**
   * Registers a  value in the registry, and returns the old value (if any).
   *
   * See `Registry.put(String*)` for the Java usage.
   */
  def put(key: Seq[String], value: String): Option[String]

  /**
   * Registers a value (a non-empty sequence of strings) in the registry, and returns the old
   * value (if any).
   *
   * Note: This is a Java-friendly version of `Registry.put(Seq[String, String])`.
   */
  @varargs
  def put(value: String*): Option[String] = {
    require(value.nonEmpty)
    put(value.init, value.last)
  }

  /**
   * Removes a key from the registry, if it was registered, and returns the old value (if any).
   */
  def remove(key: Seq[String]): Option[String]
}

/**
 * This is an expert-level API; it is not meant for end-users.
 */
class SimpleRegistry extends Registry {
  private[this] var registry = Map.empty[Seq[String], String]

  def iterator: Iterator[Entry] = synchronized(registry).iterator.map(Entry.TupledMethod)

  def put(key: Seq[String], value: String): Option[String] = {
    val sanitizedKey = key.map(sanitize)
    val sanitizedValue = sanitize(value)
    synchronized {
      val result = registry.get(sanitizedKey)
      registry += sanitizedKey -> sanitizedValue
      result
    }
  }

  def remove(key: Seq[String]): Option[String] = {
    val sanitizedKey = key.map(sanitize)
    synchronized {
      val result = registry.get(sanitizedKey)
      registry -= sanitizedKey
      result
    }
  }

  private[this] def sanitize(key: String): String =
    key.filter { char => char > 31 && char < 127 }
}

/**
 * This is an expert-level API; it is not meant for end-users.
 */
object GlobalRegistry {
  private[this] val registry: Registry = new SimpleRegistry

  /**
   * Gets the global registry.
   *
   * If it's call inside of a `withRegistry` context then it's a temporary
   * registry, useful for writing isolated tests.
   */
  def get: Registry = localRegistry() match {
    case None => registry
    case Some(local) => local
  }

  /**
   * Note, this should only ever be updated by methods used for testing.
   */
  private[this] val localRegistry = new Local[Registry]

  /**
   * Changes the global registry to instead return a local one.
   *
   * Takes the registry context with it when moved to a different thread via
   * Twitter concurrency primitives, like `flatMap` on a
   * [[com.twitter.util.Future]].
   */
  def withRegistry[A](replacement: Registry)(fn: => A): A = localRegistry.let(replacement) {
    fn
  }
}

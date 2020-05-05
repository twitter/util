package com.twitter.io

import com.twitter.util._
import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.HashMap

/**
 * A convenient wrapper for caching the results returned by the
 * underlying ActivitySource.
 */
class CachingActivitySource[T](underlying: ActivitySource[T]) extends ActivitySource[T] {

  private[this] val refq = new ReferenceQueue[Activity[T]]
  private[this] val forward = new HashMap[String, WeakReference[Activity[T]]]
  private[this] val reverse = new HashMap[WeakReference[Activity[T]], String]

  /**
   * A caching proxy to the underlying ActivitySource. Vars are cached by
   * name, and are tracked with WeakReferences.
   */
  def get(name: String): Activity[T] = synchronized {
    gc()
    Option(forward.get(name)) flatMap { wr => Option(wr.get()) } match {
      case Some(v) => v
      case None =>
        val v = underlying.get(name)
        val ref = new WeakReference(v, refq)
        forward.put(name, ref)
        reverse.put(ref, name)
        v
    }
  }

  /**
   * Remove garbage collected cache entries.
   */
  def gc(): Unit = synchronized {
    var ref = refq.poll()
    while (ref != null) {
      val key = reverse.remove(ref)
      if (key != null)
        forward.remove(key)

      ref = refq.poll()
    }
  }
}

package com.twitter.util.reflect

import com.twitter.util.Memoize

object Classes {
  private val PRODUCT: Class[Product] = classOf[Product]
  private val OPTION: Class[Option[_]] = classOf[Option[_]]
  private val LIST: Class[List[_]] = classOf[List[_]]

  /**
   * Safely compute the `clazz.getSimpleName` of a given [[Class]] handling malformed
   * names with a best-effort to parse the final segment after the last `.` after
   * transforming all `$` to `.`.
   */
  val simpleName: Class[_] => String = Memoize { clazz: Class[_] =>
    // class.getSimpleName can fail with an java.lan.InternalError (for malformed class/package names),
    // so we attempt to deal with this with a manual translation if necessary
    try {
      clazz.getSimpleName
    } catch {
      case _: InternalError =>
        // replace all $ with . and return the last element
        clazz.getName.replace('$', '.').split('.').last
    }
  }
}

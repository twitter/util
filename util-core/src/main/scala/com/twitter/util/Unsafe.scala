package com.twitter.util

object Unsafe {
  private lazy val instance: sun.misc.Unsafe = {
    val fld = classOf[sun.misc.Unsafe].getDeclaredField("theUnsafe")
    fld.setAccessible(true)
    fld.get(null).asInstanceOf[sun.misc.Unsafe]
  }

  def apply(): sun.misc.Unsafe = instance
}

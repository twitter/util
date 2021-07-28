package com.twitter.conversions

object SeqUtil {
  def hasKnownSize[A](self: Seq[A]): Boolean =
    self.knownSize != -1
}

package com.twitter.io

import java.nio.charset.{Charset, CharsetDecoder, CharsetEncoder, CodingErrorAction}
import java.util

/**
 * A set of `java.nio.charset.Charset` utilities.
 */
object Charsets {

  private[this] val encoders = new ThreadLocal[util.Map[Charset, CharsetEncoder]] {
    protected override def initialValue: util.HashMap[Charset, CharsetEncoder] = new util.HashMap()
  }

  private[this] val decoders = new ThreadLocal[util.Map[Charset, CharsetDecoder]] {
    protected override def initialValue: util.Map[Charset, CharsetDecoder] = new util.HashMap()
  }

  /**
   * Returns a cached thread-local `java.nio.charset.CharsetEncoder` for
   * the specified `charset`.
   * @note Malformed and unmappable input is silently replaced
   *       see `java.nio.charset.CodingErrorAction.REPLACE`
   */
  def encoder(charset: Charset): CharsetEncoder = {
    var e = encoders.get.get(charset)
    if (e == null) {
      e = charset.newEncoder()
      encoders.get.put(charset, e)
    }
    e.reset()
    e.onMalformedInput(CodingErrorAction.REPLACE)
    e.onUnmappableCharacter(CodingErrorAction.REPLACE)
  }

  /**
   * Returns a cached thread-local `java.nio.charset.CharsetDecoder` for
   * the specified `charset`.
   * @note Malformed and unmappable input is silently replaced
   *       see `java.nio.charset.CodingErrorAction.REPLACE`
   */
  def decoder(charset: Charset): CharsetDecoder = {
    var d = decoders.get.get(charset)
    if (d == null) {
      d = charset.newDecoder()
      decoders.get.put(charset, d)
    }
    d.reset()
    d.onMalformedInput(CodingErrorAction.REPLACE)
    d.onUnmappableCharacter(CodingErrorAction.REPLACE)
  }
}

package com.twitter.util

import sun.misc.{BASE64Decoder, BASE64Encoder}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

trait StringEncoder {
  def encode(bytes: Array[Byte]): String = new String(bytes)
  def decode(str: String): Array[Byte] = str.getBytes
}

trait Base64StringEncoder extends StringEncoder {
  private[this] lazy val encoder = new BASE64Encoder
  private[this] lazy val decoder = new BASE64Decoder

  override def encode(bytes: Array[Byte]): String =
    encoder.encode(bytes)

  override def decode(str: String): Array[Byte] =
    decoder.decodeBuffer(str)

}

object Base64StringEncoder extends Base64StringEncoder

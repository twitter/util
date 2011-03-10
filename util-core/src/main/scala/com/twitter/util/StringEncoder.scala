package com.twitter.util

import org.apache.commons.codec.binary.Base64

trait StringEncoder {
  def encode(bytes: Array[Byte]): String = new String(bytes)
  def decode(str: String):   Array[Byte] = str.getBytes
}

trait Base64StringEncoder extends StringEncoder {
  override def encode(bytes: Array[Byte]): String = super.encode(Base64.encodeBase64(bytes))
  override def decode(str: String):   Array[Byte] = Base64.decodeBase64(super.decode(str))
}

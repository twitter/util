package com.twitter.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import org.apache.commons.codec.binary.Base64

import com.twitter.io.StreamIO

trait StringEncoder {
  def encode(bytes: Array[Byte]): String = new String(bytes)
  def decode(str: String): Array[Byte] = str.getBytes
}

trait Base64StringEncoder extends StringEncoder {
  private[this] def codec = new Base64()

  override def encode(bytes: Array[Byte]): String = {
    codec.encodeToString(bytes)
  }

  override def decode(str: String): Array[Byte] =
    codec.decode(str)
}

object StringEncoder extends StringEncoder
object Base64StringEncoder extends Base64StringEncoder

/**
 * A collection of utilities for encoding strings and byte arrays to and decoding from strings
 * compressed from with gzip.
 *
 * This trait is thread-safe because there are no streams shared outside of method scope, and
 * therefore no contention for shared byte arrays.
 *
 * The encoding for strings is UTF-8.
 *
 * gzipping inherently includes base64 encoding (the GZIP utilities from java will complain
 * otherwise!)
 */
trait GZIPStringEncoder extends StringEncoder {
  override def encode(bytes: Array[Byte]): String = {
    val baos = new ByteArrayOutputStream
    val gos = new GZIPOutputStream(baos)
    gos.write(bytes)
    gos.finish()
    Base64StringEncoder.encode(baos.toByteArray)
  }

  def encodeString(str: String) = encode(str.getBytes("UTF-8"))

  override def decode(str: String): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    StreamIO.copy(new GZIPInputStream(new ByteArrayInputStream(Base64StringEncoder.decode(str))), baos)

    baos.toByteArray
  }

  def decodeString(str: String): String = new String(decode(str), "UTF-8")
}

object GZIPStringEncoder extends GZIPStringEncoder

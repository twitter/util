package com.twitter.io

import java.nio.charset.Charset

/**
 * A simple proxy ByteWriter that forwards all calls to another ByteWriter.
 * This is useful if you want to wrap-but-modify an existing ByteWriter.
 */
private[twitter] abstract class ProxyByteWriter(underlying: ByteWriter) extends AbstractByteWriter {

  def writeString(string: CharSequence, charset: Charset): ProxyByteWriter.this.type = {
    underlying.writeString(string, charset)
    this
  }

  def writeBytes(bs: Array[Byte]): this.type = {
    underlying.writeBytes(bs)
    this
  }

  def writeBytes(buf: Buf): this.type = {
    underlying.writeBytes(buf)
    this
  }

  def writeByte(b: Int): this.type = {
    underlying.writeByte(b)
    this
  }

  def writeShortBE(s: Int): this.type = {
    underlying.writeShortBE(s)
    this
  }

  def writeShortLE(s: Int): this.type = {
    underlying.writeShortLE(s)
    this
  }

  def writeMediumBE(m: Int): this.type = {
    underlying.writeMediumBE(m)
    this
  }

  def writeMediumLE(m: Int): this.type = {
    underlying.writeMediumLE(m)
    this
  }

  def writeIntBE(i: Long): this.type = {
    underlying.writeIntBE(i)
    this
  }

  def writeIntLE(i: Long): this.type = {
    underlying.writeIntLE(i)
    this
  }

  def writeLongBE(l: Long): this.type = {
    underlying.writeLongBE(l)
    this
  }

  def writeLongLE(l: Long): this.type = {
    underlying.writeLongLE(l)
    this
  }
}

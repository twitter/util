package com.twitter.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import org.apache.commons.codec.binary.Base64
import org.apache.thrift.TBase
import org.apache.thrift.protocol.{TBinaryProtocol, TCompactProtocol, TProtocolFactory,
  TSimpleJSONProtocol}
import org.apache.thrift.transport.TIOStreamTransport

trait StringEncoder {
  def encode(bytes: Array[Byte]): String
  def decode(str: String): Array[Byte]
}

trait ThriftSerializer extends StringEncoder {
  def protocolFactory: TProtocolFactory

  def toBytes(obj: TBase[_, _]): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    obj.write(protocolFactory.getProtocol(new TIOStreamTransport(baos)))
    baos.toByteArray
  }

  def fromBytes(obj: TBase[_, _], bytes: Array[Byte]): Unit =
    obj.read(protocolFactory.getProtocol(new TIOStreamTransport(new ByteArrayInputStream(bytes))))

  def toString(obj: TBase[_, _]): String = encode(toBytes(obj))

  def fromString(obj: TBase[_, _], str: String): Unit = fromBytes(obj, decode(str))

  override def encode(bytes: Array[Byte]): String = new String(bytes)
  override def decode(str: String): Array[Byte] = str.getBytes
}

class JsonThriftSerializer extends ThriftSerializer {
  override def protocolFactory = new TSimpleJSONProtocol.Factory
}

class BinaryThriftSerializer extends ThriftSerializer {
  override def protocolFactory = new TBinaryProtocol.Factory
}

class CompactThriftSerializer extends ThriftSerializer {
  override def protocolFactory = new TCompactProtocol.Factory
}

trait Base64StringEncoder extends StringEncoder {
  override def encode(bytes: Array[Byte]) = new String(Base64.encodeBase64(bytes))
  override def decode(str: String) = Base64.decodeBase64(str.getBytes)
}

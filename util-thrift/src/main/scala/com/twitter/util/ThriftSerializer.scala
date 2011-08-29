package com.twitter.util

import java.io.{InputStream, ByteArrayInputStream, ByteArrayOutputStream}
import org.apache.thrift.TBase
import org.apache.thrift.protocol.{TBinaryProtocol, TCompactProtocol, TProtocolFactory,
  TSimpleJSONProtocol}
import org.apache.thrift.transport.TIOStreamTransport

trait ThriftSerializer extends StringEncoder {
  def protocolFactory: TProtocolFactory

  def toBytes(obj: TBase[_, _]): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    obj.write(protocolFactory.getProtocol(new TIOStreamTransport(baos)))
    baos.toByteArray
  }

  def fromInputStream(obj: TBase[_, _], stream: InputStream): Unit =
    obj.read(protocolFactory.getProtocol(new TIOStreamTransport(stream)))

  def fromBytes(obj: TBase[_, _], bytes: Array[Byte]): Unit =
    fromInputStream(obj, new ByteArrayInputStream(bytes))

  def toString(obj: TBase[_, _]): String = encode(toBytes(obj))

  def fromString(obj: TBase[_, _], str: String): Unit = fromBytes(obj, decode(str))
}

class JsonThriftSerializer extends ThriftSerializer {
  override def protocolFactory = new TSimpleJSONProtocol.Factory

  /**
   * Thrift does not properly deserialize the JSON it serializes ;/
   */
  override def fromBytes(obj: TBase[_, _], bytes: Array[Byte]): Unit =
    throw new UnsupportedOperationException("Thrift does not properly deserialize the JSON")
}

class BinaryThriftSerializer extends ThriftSerializer with Base64StringEncoder {
  override def protocolFactory = new TBinaryProtocol.Factory
}

class CompactThriftSerializer extends ThriftSerializer with Base64StringEncoder {
  override def protocolFactory = new TCompactProtocol.Factory
}

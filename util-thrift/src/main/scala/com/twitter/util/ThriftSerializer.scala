package com.twitter.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}

import com.fasterxml.jackson.databind.MappingJsonFactory
import org.apache.thrift.TBase
import org.apache.thrift.protocol.{TBinaryProtocol, TCompactProtocol, TProtocolFactory, TSimpleJSONProtocol}
import org.apache.thrift.transport.TIOStreamTransport

trait ThriftSerializer extends StringEncoder {
  def protocolFactory: TProtocolFactory

  def toBytes(obj: TBase[_, _], bufSize: Int): Array[Byte] = {
    val baos = new ByteArrayOutputStream(bufSize)
    obj.write(protocolFactory.getProtocol(new TIOStreamTransport(baos)))
    baos.toByteArray
  }

  def toBytes(obj: TBase[_, _]): Array[Byte] = {
    toBytes(obj, 32) // default initial size of ByteArrayOutputStream
  }

  def fromInputStream(obj: TBase[_, _], stream: InputStream): Unit =
    obj.read(protocolFactory.getProtocol(new TIOStreamTransport(stream)))

  def fromBytes(obj: TBase[_, _], bytes: Array[Byte]): Unit =
    fromInputStream(obj, new ByteArrayInputStream(bytes))

  def toString(obj: TBase[_, _], bufSize: Int): String = encode(toBytes(obj, bufSize))

  def toString(obj: TBase[_, _]): String = encode(toBytes(obj))

  def fromString(obj: TBase[_, _], str: String): Unit = fromBytes(obj, decode(str))
}

class JsonThriftSerializer extends ThriftSerializer {
  override def protocolFactory = new TSimpleJSONProtocol.Factory

  override def fromBytes(obj: TBase[_, _], bytes: Array[Byte]): Unit = {
    val binarySerializer = new BinaryThriftSerializer
    val newObj = new MappingJsonFactory().createJsonParser(bytes).readValueAs(obj.getClass)
    binarySerializer.fromBytes(obj, binarySerializer.toBytes(newObj.asInstanceOf[TBase[_, _]]))
  }
}

class BinaryThriftSerializer extends ThriftSerializer with Base64StringEncoder {
  override def protocolFactory = new TBinaryProtocol.Factory
}

class CompactThriftSerializer extends ThriftSerializer with Base64StringEncoder {
  override def protocolFactory = new TCompactProtocol.Factory
}

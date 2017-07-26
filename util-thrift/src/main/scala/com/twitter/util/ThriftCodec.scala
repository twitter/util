package com.twitter.util

import org.apache.thrift.TBase
import org.apache.thrift.protocol.{TBinaryProtocol, TCompactProtocol, TProtocolFactory}

object ThriftCodec {
  def apply[T <: TBase[_, _]: Manifest, P <: TProtocolFactory: Manifest]: ThriftCodec[T, P] =
    new ThriftCodec[T, P]
}

class ThriftCodec[T <: TBase[_, _]: Manifest, P <: TProtocolFactory: Manifest]
    extends Codec[T, Array[Byte]]
    with ThriftSerializer {

  protected lazy val prototype: T =
    manifest[T].runtimeClass.asInstanceOf[Class[T]].newInstance

  override lazy val protocolFactory: TProtocolFactory =
    manifest[P].runtimeClass.asInstanceOf[Class[P]].newInstance

  override def encode(item: T): Array[Byte] = toBytes(item)

  override def decode(bytes: Array[Byte]): T = {
    val obj = prototype.deepCopy
    fromBytes(obj, bytes)
    obj.asInstanceOf[T]
  }
}

class BinaryThriftCodec[T <: TBase[_, _]: Manifest] extends ThriftCodec[T, TBinaryProtocol.Factory]

class CompactThriftCodec[T <: TBase[_, _]: Manifest]
    extends ThriftCodec[T, TCompactProtocol.Factory]

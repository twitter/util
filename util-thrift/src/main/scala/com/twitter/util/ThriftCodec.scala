package com.twitter.util

import org.apache.thrift.TBase
import org.apache.thrift.protocol.{TBinaryProtocol, TCompactProtocol, TProtocolFactory}

import scala.reflect.{ClassTag, classTag} // TODO Scala3 Is ClassTag ok here?

object ThriftCodec {
  def apply[T <: TBase[_, _]: ClassTag, P <: TProtocolFactory: ClassTag]: ThriftCodec[T, P] =
    new ThriftCodec[T, P]
}

class ThriftCodec[T <: TBase[_, _]: ClassTag, P <: TProtocolFactory: ClassTag]
    extends Codec[T, Array[Byte]]
    with ThriftSerializer {

  protected lazy val prototype: T =
    classTag[T].runtimeClass.asInstanceOf[Class[T]].newInstance

  lazy val protocolFactory: TProtocolFactory =
    classTag[P].runtimeClass.asInstanceOf[Class[P]].newInstance

  def encode(item: T): Array[Byte] = toBytes(item)

  def decode(bytes: Array[Byte]): T = {
    val obj: TBase[_, _] = prototype.deepCopy.asInstanceOf[TBase[_, _]]
    fromBytes(obj, bytes)
    obj.asInstanceOf[T]
  }
}

class BinaryThriftCodec[T <: TBase[_, _]: ClassTag] extends ThriftCodec[T, TBinaryProtocol.Factory]

class CompactThriftCodec[T <: TBase[_, _]: ClassTag]
    extends ThriftCodec[T, TCompactProtocol.Factory]

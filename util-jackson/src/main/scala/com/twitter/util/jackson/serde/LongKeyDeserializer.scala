package com.twitter.util.jackson.serde

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.deser.KeyDeserializers
import com.fasterxml.jackson.module.scala.JacksonModule
import com.twitter.util.WrappedValue
import org.json4s.reflect.{ClassDescriptor, Reflector, classDescribable}

private[jackson] class LongKeyDeserializer(clazz: Class[_]) extends KeyDeserializer {
  private val constructor = clazz.getConstructor(classOf[Long])

  override def deserializeKey(key: String, ctxt: DeserializationContext): Object = {
    val long = key.toLong.asInstanceOf[Object]
    constructor.newInstance(long).asInstanceOf[Object]
  }
}

private[jackson] object LongKeyDeserializers extends JacksonModule {
  override def getModuleName = "LongKeyDeserializers"

  private val keyDeserializers = new KeyDeserializers {
    override def findKeyDeserializer(
      `type`: JavaType,
      config: DeserializationConfig,
      beanDesc: BeanDescription
    ): KeyDeserializer = {
      val clazz = beanDesc.getBeanClass
      if (isJsonWrappedLong(clazz))
        new LongKeyDeserializer(clazz)
      else
        null
    }
  }

  private def isJsonWrappedLong(clazz: Class[_]): Boolean = {
    classOf[WrappedValue[_]].isAssignableFrom(clazz) &&
    isWrappedLong(clazz)
  }

  private def isWrappedLong(clazz: Class[_]): Boolean = {
    val reflector =
      Reflector
        .describe(classDescribable(clazz))
        .asInstanceOf[ClassDescriptor]
        .constructors
        .head
    val erased = reflector.params.head.argType.erasure
    erased == classOf[Long] || erased.getName == "long"
  }

  this += { _.addKeyDeserializers(keyDeserializers) }
}

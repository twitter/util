package com.twitter.util.inject.module

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.twitter.util.inject.{MyServiceImpl, MyServiceInterface, TestBindingAnnotation}
import java.util.Properties
import javax.inject.Singleton
import net.codingwell.scalaguice.ScalaModule

trait TestModule extends AbstractModule with ScalaModule {

  override protected def configure(): Unit = {
    bind[String].annotatedWith(Names.named("str1")).toInstance("string1")
    bind[String].annotatedWith(Names.named("str2")).toInstance("string2")

    bind[Int].toInstance(11)
    bind[String].toInstance("default string")
    bind[String].annotatedWith[TestBindingAnnotation].toInstance("prod string")

    bind[MyServiceInterface].to[MyServiceImpl].in[Singleton]
    val properties = new Properties()
    properties.setProperty("name", "Steve")
    Names.bindProperties(TestModule.super.binder(), properties)
  }
}

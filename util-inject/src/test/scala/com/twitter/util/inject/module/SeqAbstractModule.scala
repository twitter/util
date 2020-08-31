package com.twitter.inject.module

import com.google.inject.{AbstractModule, Provides}
import javax.inject.Singleton
import net.codingwell.scalaguice.ScalaModule

object SeqAbstractModule extends AbstractModule with ScalaModule {
  @Singleton
  @Provides
  def provideSeq: Seq[Array[Byte]] =
    Seq.empty[Array[Byte]]
}

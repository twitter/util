package com.twitter.inject

import com.google.inject.assistedinject.Assisted
import javax.inject.{Inject, Named, Singleton}

object servicefactory {

  @Singleton
  class DoEverythingService {

    def doit = {
      "done"
    }
  }

  class ComplexService @Inject() (
    exampleService: DoEverythingService,
    defaultString: String,
    @Named("str1") string1: String,
    @Named("str2") string2: String,
    defaultInt: Int,
    @Assisted name: String) {

    def execute = {
      exampleService.doit + " " + name
    }
  }

  trait ComplexServiceFactory {
    def create(name: String): ComplexService
  }
}

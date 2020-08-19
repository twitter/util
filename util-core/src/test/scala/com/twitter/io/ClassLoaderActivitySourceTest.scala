package com.twitter.io

import com.twitter.util.FuturePool
import java.io.ByteArrayInputStream
import org.scalatest.funsuite.AnyFunSuite

class ClassLoaderActivitySourceTest extends AnyFunSuite {

  test("ClassLoaderActivitySource") {
    val classLoader = new ClassLoader() {
      override def getResourceAsStream(name: String) =
        new ByteArrayInputStream(name.getBytes("UTF-8"))
    }

    val loader = new ClassLoaderActivitySource(classLoader, FuturePool.immediatePool)
    val bufAct = loader.get("bar baz")

    assert("bar baz" == ActivitySourceTest.bufToString(bufAct.sample()))
  }

}

package com.twitter.app

import java.net.URLClassLoader
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.mockito.MockitoSugar

class ClassPathTest extends FunSuite with MockitoSugar {

  test("Null URL[] URLClassloader") {
    val classLoader = mock[URLClassLoader]
    when(classLoader.getURLs).thenReturn(null)

    val classPath = new LoadServiceClassPath()
    val entries = classPath.getEntries(classLoader)
    assert(entries.isEmpty)
  }

  test("Null entry in URL[] from URLClassloader") {
    val urls = Array[java.net.URL](null)

    val classLoader = mock[URLClassLoader]
    when(classLoader.getURLs).thenReturn(urls)

    val classPath = new LoadServiceClassPath()
    val entries = classPath.getEntries(classLoader)
    assert(entries.isEmpty)
  }
}

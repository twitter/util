package com.twitter.io

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ClasspathResourceTest extends AnyFunSuite with Matchers {

  test("ClasspathResource#load absolute path") {
    ClasspathResource.load("/test.txt") match {
      case Some(inputStream) =>
        try {
          inputStream.available() > 0 should be(true)
        } finally {
          inputStream.close()
        }
      case _ => fail()
    }
  }

  test("ClasspathResource#load path interpreted as absolute") {
    ClasspathResource.load("test.txt") match {
      case Some(inputStream) =>
        try {
          inputStream.available() > 0 should be(true)
        } finally {
          inputStream.close()
        }
      case _ => fail()
    }
  }

  test("ClasspathResource#load absolute path multiple directories 1") {
    // loads from /com/twitter/io/resource-test-file.txt
    ClasspathResource.load("/com/twitter/io/resource-test-file.txt") match {
      case Some(inputStream) =>
        try {
          inputStream.available() > 0 should be(true)
        } finally {
          inputStream.close()
        }
      case _ => fail()
    }
  }

  test("ClasspathResource#load absolute path multiple directories 2") {
    // loads from /foo/bar/test-file.txt
    ClasspathResource.load("/foo/bar/test-file.txt") match {
      case Some(inputStream) =>
        try {
          inputStream.available() > 0 should be(true)
        } finally {
          inputStream.close()
        }
      case _ => fail()
    }
  }

  test("ClasspathResource#load path interpreted as absolute multiple directories 1") {
    // loads from /com/twitter/io/resource-test-file.txt
    ClasspathResource.load("com/twitter/io/resource-test-file.txt") match {
      case Some(inputStream) =>
        try {
          inputStream.available() > 0 should be(true)
        } finally {
          inputStream.close()
        }
      case _ => fail()
    }
  }

  test("ClasspathResource#load path interpreted as absolute multiple directories 2") {
    // loads from /foo/bar/test-file.txt
    ClasspathResource.load("foo/bar/test-file.txt") match {
      case Some(inputStream) =>
        try {
          inputStream.available() > 0 should be(true)
        } finally {
          inputStream.close()
        }
      case _ => fail()
    }
  }

  test("ClasspathResource#load does not exist 1") {
    ClasspathResource.load("/does-not-exist.txt") match {
      case Some(_) => fail()
      case _ =>
      // should not return an inputstream
    }
  }

  test("ClasspathResource#load does not exist 2") {
    ClasspathResource.load("does-not-exist.txt") match {
      case Some(_) => fail()
      case _ =>
      // should not return an inputstream
    }
  }

  test("ClasspathResource#load empty file 1") {
    ClasspathResource.load("/empty-file.txt") match {
      case Some(_) => fail()
      case _ =>
      // should not return an inputstream
    }
  }

  test("ClasspathResource#load empty file 2") {
    // loads from /empty-file.txt
    ClasspathResource.load("empty-file.txt") match {
      case Some(_) => fail()
      case _ =>
      // should not return an inputstream
    }
  }
}

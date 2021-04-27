package com.twitter.util.validation.conversions

import com.twitter.util.validation.conversions.PathOps._
import jakarta.validation.Path
import org.hibernate.validator.internal.engine.path.PathImpl
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PathOpsTest extends AnyFunSuite with Matchers {

  test("PathOps#root") {
    val path: Path = PathImpl.createRootPath()

    val leafNode = path.getLeafNode
    leafNode should not be (null)
    leafNode.toString should equal("")
  }

  test("PathOps#without leaf node") {
    val path = PathImpl.createRootPath()

    // only the root exists, so this fails
    intercept[IndexOutOfBoundsException] {
      PathImpl.createCopyWithoutLeafNode(path)
    }

    // add nodes
    path.addBeanNode()
    path.addPropertyNode("foo")

    val pathWithLeaf: Path = PathImpl.createCopy(path)
    val leafNode = pathWithLeaf.getLeafNode
    leafNode should not be (null)
    leafNode.toString should equal("foo")
  }

  test("PathOps#propertyNode") {
    val path = PathImpl.createRootPath()
    path.addPropertyNode("leaf")

    val propertyPath: Path = PathImpl.createCopy(path)

    val leafNode = propertyPath.getLeafNode
    leafNode should not be (null)
    leafNode.toString should equal("leaf")
  }

  test("PathOps#fromString") {
    val path: Path = PathImpl.createPathFromString("foo.bar.baz")

    val leafNode = path.getLeafNode
    leafNode should not be (null)
    leafNode.toString should equal("baz")
  }
}

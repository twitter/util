package com.twitter.zk

/**
 * @author ver@twitter.com
 */
import org.scalatest.WordSpec
import org.scalatestplus.mockito.MockitoSugar

class ZNodeTest extends WordSpec with MockitoSugar {
  "ZNode" should {
    class ZNodeSpecHelper {
      val zk = mock[ZkClient]
    }
    def pathTest(path: String, parent: String, name: String): Unit = {
      val h = new ZNodeSpecHelper
      import h._

      val znode = ZNode(zk, path)
      path should {
        "parentPath" in { assert(znode.parentPath == parent) }
        "name" in { assert(znode.name == name) }
      }
    }

    pathTest("/", "/", "")
    pathTest("/some/long/path/to/a/znode", "/some/long/path/to/a", "znode")
    pathTest("/path", "/", "path")

    "hash together" in {
      val h = new ZNodeSpecHelper
      import h._

      val zs = (0 to 1) map { _ => ZNode(zk, "/some/path") }
      val table = Map(zs(0) -> true)
      assert(table.keys.toList.contains(zs(0)))
      assert(table.keys.toList.contains(zs(1)))
    }
  }
}

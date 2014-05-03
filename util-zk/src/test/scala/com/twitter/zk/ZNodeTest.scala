package com.twitter.zk

/**
 * @author ver@twitter.com
 */


import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.mock.MockitoSugar
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ZNodeTest extends WordSpec with ShouldMatchers with MockitoSugar {
  "ZNode" should {
    class ZNodeSpecHelper {
      val zk = mock[ZkClient]
    }
    def pathTest(path: String, parent: String, name: String) {
      val h = new ZNodeSpecHelper
      import h._

      val znode = ZNode(zk, path)
      path should {
        "parentPath" in { znode.parentPath shouldEqual parent }
        "name"       in { znode.name       shouldEqual name   }
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
      table should contain key (zs(0))
      table should contain key (zs(1))
    }
  }
}

package com.twitter.zk

/**
 * @author ver@twitter.com
 */

import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito

class ZNodeSpec extends SpecificationWithJUnit with Mockito {
  "ZNode" should {
    val zk = mock[ZkClient]
    def pathTest(path: String, parent: String, name: String) {
      val znode = ZNode(zk, path)
      path in {
        "parentPath" in { znode.parentPath mustEqual parent }
        "name"       in { znode.name       mustEqual name   }
      }
    }

    pathTest("/", "/", "")
    pathTest("/some/long/path/to/a/znode", "/some/long/path/to/a", "znode")

    "hash together" in {
      val zs = (0 to 1) map { _ => ZNode(zk, "/some/path") }
      val table = Map(zs(0) -> true)
      table must haveKey(zs(0))
      table must haveKey(zs(1))
    }
  }
}

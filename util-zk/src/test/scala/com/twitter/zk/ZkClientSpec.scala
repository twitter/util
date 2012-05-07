package com.twitter.zk

import com.twitter.conversions.time._
import com.twitter.logging.{Level, Logger}
import org.apache.zookeeper._
import org.apache.zookeeper.data.{ACL, Stat}
import org.specs.SpecificationWithJUnit
import org.specs.mock._
import scala.collection.Set
import scala.collection.JavaConverters._
import com.twitter.util._

class ZkClientSpec extends SpecificationWithJUnit with JMocker with ClassMocker {
  Logger.get("").setLevel(Level.TRACE)

  val zk = mock[ZooKeeper]
  class TestZkClient extends ZkClient {
    val connector = new Connector {
      def apply() = Future(zk)
      def release() = Future.Done
    }
  }
  def zkClient = new TestZkClient

  implicit val javaTimer = new JavaTimer(true)

  /*
   * ZooKeeper expectation wrappers
   */

  def create(path: String,
             data: Array[Byte] = "".getBytes,
             acls: Seq[ACL] = zkClient.acl,
             mode: CreateMode = zkClient.mode)
            (wait: => Future[String]) {
    val cb = capturingParam[AsyncCallback.StringCallback]
    one(zk).create(equal(path),
        equal(data), equal(acls.asJava),
        equal(mode), cb.capture(4),
        equal(null)) willReturn cb.map { cb =>
      wait onSuccess { newPath =>
        cb.processResult(0, path, null, newPath)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null, null)
      }
      null // explicitly void
    }
  }

  def delete(path: String, version: Int)(wait: => Future[Unit]) {
    val cb = capturingParam[AsyncCallback.VoidCallback]
    one(zk).delete(equal(path), equal(version), cb.capture(2), same(null)) willReturn cb.map { cb =>
      wait onSuccess { _ =>
        cb.processResult(0, path, null)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null)
      }
      null // explicitly void
    }
  }

  def exists(path: String)(stat: => Future[Stat]) {
    val cb = capturingParam[AsyncCallback.StatCallback]
    one(zk).exists(equal(path), equal(false), cb.capture(2), equal(null)) willReturn cb.map { cb =>
      stat onSuccess {
        cb.processResult(0, path, null, _)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null, null)
      }
      null // explicitly void
    }
  }

  def watch(path: String)(stat: => Future[Stat])(update: => Future[WatchedEvent]) {
    val watcher = capturingParam[Watcher]
    val cb = capturingParam[AsyncCallback.StatCallback]
    one(zk).exists(equal(path), watcher.capture(1), cb.capture(2),
        equal(null)) willReturn cb.map { cb =>
      stat onSuccess {
        cb.processResult(0, path, null, _)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null, null)
      }
      update onSuccess { watcher.captured.process(_) }
      null // explicitly void
    }
  }

  def getChildren(path: String)(children: => Future[ZNode.Children]) {
    val cb = capturingParam[AsyncCallback.Children2Callback]
    one(zk).getChildren(equal(path),
        equal(false), cb.capture(2),
        equal(null)) willReturn cb.map { cb =>
      children onSuccess { znode =>
        cb.processResult(0, path, null, znode.children.map { _.name }.toList.asJava, znode.stat)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null, null, null)
      }
      null // explicitly void
    }
  }

  def watchChildren(path: String)
                   (children: => Future[ZNode.Children])
                   (update: => Future[WatchedEvent]) {
    val w = capturingParam[Watcher]
    val cb = capturingParam[AsyncCallback.Children2Callback]
    one(zk).getChildren(equal(path), w.capture(1), cb.capture(2),
        equal(null)) willReturn cb.map { cb =>
      children onSuccess { case ZNode.Children(znode, stat, children) =>
        cb.processResult(0, path, null, children.map { _.name }.toList.asJava, stat)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null, null, null)
      }
      update onSuccess { w.captured.process(_) }
      null // explicitly void
    }
  }

  def getData(path: String)(result: => Future[ZNode.Data]) {
    val cb = capturingParam[AsyncCallback.DataCallback]
    one(zk).getData(equal(path), equal(false), cb.capture(2), equal(null)) willReturn cb.map { cb =>
      result onSuccess { z =>
        cb.processResult(0, path, null, z.bytes, z.stat)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null, null, null)
      }
      null // explicitly void
    }
  }
  def watchData(path: String)(result: => Future[ZNode.Data])(update: => Future[WatchedEvent]) {
    val w = capturingParam[Watcher]
    val cb = capturingParam[AsyncCallback.DataCallback]
    one(zk).getData(equal(path),
        w.capture(1), cb.capture(2),
        equal(null)) willReturn cb.map { cb =>
      result onSuccess { z =>
        cb.processResult(0, path, null, z.bytes, z.stat)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null, null, null)
      }
      update onSuccess { w.captured.process(_) }
      null // explicitly void
    }
  }

  def setData(path: String, data: Array[Byte], version: Int)
             (wait: => Future[Stat]) {
    val cb = capturingParam[AsyncCallback.StatCallback]
    one(zk).setData(equal(path), equal(data), equal(version),
        cb.capture(3), equal(null)) willReturn cb.map { cb =>
      wait onSuccess { stat =>
        cb.processResult(0, path, null, stat)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null, null)
      }
      null // explicitly void
    }
  }

  def sync(path: String)
          (wait: Future[Unit]) {
    val cb = capturingParam[AsyncCallback.VoidCallback]
    one(zk).sync(equal(path), cb.capture(1), equal(null)) willReturn cb.map { cb =>
      wait onSuccess { _ =>
        cb.processResult(0, path, null)
      } onFailure { case ke: KeeperException =>
        cb.processResult(ke.code.intValue, path, null)
      }
      null // explicitly void
    }
  }

  "ZkClient" should {
    "apply" in {
      val path = "/root/path/to/a/node"
      val znode = zkClient(path)
      znode must be[ZNode]
      znode.path mustEqual path
    }

    "retry" in {
      val connectionLoss = new KeeperException.ConnectionLossException

      "retry KeeperException.ConnectionLossException until completion" in {
        var i = 0
        zkClient.withRetries(3).retrying { _ =>
          i += 1
          Future.exception(connectionLoss)
        }.onSuccess { _ =>
          fail("Unexpected success")
        }.handle { case e: KeeperException.ConnectionLossException =>
          e mustBe connectionLoss
          i mustEqual 4
        }.apply()
      }

      "not retry on success" in {
        var i = 0
        zkClient.withRetries(3).retrying { _ =>
          i += 1
          Future.Done
        }.onSuccess { _ =>
          i mustEqual 1
        }.apply()
      }

      "convert exceptions to Futures" in {
        val rex = new RuntimeException
        var i = 0
        zkClient.withRetries(3).retrying { _ =>
          i += 1
          throw rex
        }.onSuccess { _ =>
          fail("Unexpected success")
        }.handle { case e: RuntimeException =>
          e mustBe rex
          i mustEqual 1
        }.apply()
      }

      "only retry when instructed to" in {
        var i = 0
        zkClient.retrying { _ =>
          i += 1
          Future.exception(connectionLoss)
        }.onSuccess { _ =>
          fail("Shouldn't have succeeded")
        }.handle { case e: KeeperException.ConnectionLossException =>
          i mustEqual 1
        }.apply()
      }
    }

    "transform" in {
      val acl = ZooDefs.Ids.OPEN_ACL_UNSAFE.asScala
      val mode = CreateMode.EPHEMERAL_SEQUENTIAL
      val retries = 37
      val retryPolicy = new RetryPolicy.Basic(retries)

      "withAcl" in {
        val transformed = zkClient.withAcl(acl)
        transformed must be[ZkClient]
        transformed.acl mustEqual acl
      }

      "withMode" in {
        val transformed = zkClient.withMode(mode)
        transformed must be[ZkClient]
        transformed.mode mustEqual mode
      }

      "withRetries" in {
        val transformed = zkClient.withRetries(retries)
        transformed must be[ZkClient]
        transformed.retryPolicy must beLike { case RetryPolicy.Basic(r) => (r == retries) }
      }

      "withRetryPolicy" in {
        val transformed = zkClient.withRetryPolicy(retryPolicy)
        transformed must be[ZkClient]
        transformed.retryPolicy mustBe retryPolicy
      }

      "chained" in {
        val transformed = zkClient.withAcl(acl).withMode(mode).withRetryPolicy(retryPolicy)
        transformed must be[ZkClient]
        transformed.acl mustEqual acl
        transformed.mode mustEqual mode
        transformed.retryPolicy mustBe retryPolicy
      }
    }
  }

  "ZNode" should {
    "apply a relative path to a ZNode" in {
      val znode = zkClient("/some/path")
      znode("to/child").path mustEqual "/some/path/to/child"
    }

    "create" in {
      val path = "/root/path/to/a/node"

      "ok" in {
        val data = "blah".getBytes
        expect {
          create(path, data)(Future(path))
        }
        zkClient(path).create(data).apply() must beLike { case ZNode(p) => p == path }
      }

      "error" in {
        val data = null
        expect {
          create(path, data)(Future.exception(new KeeperException.NodeExistsException(path)))
        }
        zkClient(path).create(data) map { _ =>
          fail("Unexpected success")
        } handle { case e: KeeperException.NodeExistsException =>
          e.getPath mustEqual path
        } apply()
      }

      "new name" in {
        val data = null

        expect {
          create(path, data, mode = CreateMode.EPHEMERAL_SEQUENTIAL)(Future(path + "0000"))
        }

        val newPath = zkClient(path).create(data, mode = CreateMode.EPHEMERAL_SEQUENTIAL).apply()
        newPath.name mustEqual "node0000"
      }
    }

    "delete" in {
      val version = 0
      val path = "/path"
      "ok" in {
        expect {
          delete(path, version)(Future.Done)
        }
        zkClient(path).delete(version).apply() must beLike { case ZNode(p) => p == path }
      }

      "error" in {
        expect {
          delete(path, version)(Future.exception(new KeeperException.NoNodeException(path)))
        }
        zkClient(path).delete(version) map { _ =>
          fail("Unexpected success")
        } handle { case e: KeeperException.NoNodeException =>
          e.getPath mustEqual path
        } apply()
      }
    }

    "exist" in {
      val znode = zkClient("/maybe/exists")
      val result = ZNode.Exists(znode, new Stat)

      "apply" in {
        "ok" in {
          expect {
            exists(znode.path)(Future(result.stat))
          }
          znode.exists().apply() mustEqual result
        }

        "error" in {
          expect {
            exists(znode.path)(Future.exception(new KeeperException.NoNodeException(znode.path)))
          }
          znode.exists() map { _ =>
            fail("Unexpected success")
          } handle { case e: KeeperException.NoNodeException =>
            e.getPath mustEqual znode.path
          } apply()
        }
      }

      "watch" in {
        val event = NodeEvent.Deleted(znode.path)
        expect {
          watch(znode.path)(Future(result.stat))(Future(event))
        }
        znode.exists.watch() onSuccess { case ZNode.Watch(r, update) =>
          r onSuccess { exists =>
            exists mustEqual result
            update onSuccess {
              case e @ NodeEvent.Deleted(name) => e mustEqual event
              case e => fail("Incorrect event: %s".format(e))
            }
          }
        } apply()
      }

      "monitor" in {
        val deleted = NodeEvent.Deleted(znode.path)
        def expectZNodes(n: Int) {
          val results = 0 until n map { _ => ZNode.Exists(znode, new Stat) }
          expect {
            results foreach { r =>
              watch(znode.path)(Future(r.stat))(Future(deleted))
            }
          }
          val update = znode.exists.monitor()
          results foreach { result =>
            update syncWait() get() mustEqual result
          }
        }

        "that updates several times" in {
          expectZNodes(3)
        }

        "handles session events properly" in {
          "AuthFailed" in {
            expect {
              // this case is somewhat unrealistic
              watch(znode.path)(Future(new Stat))(Future(StateEvent.AuthFailed()))
              // does not reset
            }
            val offer = znode.exists.monitor()
            offer syncWait() get() mustEqual result
          }

          "Disconnected" in {
            val update = new Promise[WatchedEvent]
            expect {
              watch(znode.path)(Future(new Stat))(Future(StateEvent.Disconnected()))
              watch(znode.path)(Future(new Stat))(Future(NodeEvent.Deleted(znode.path)))
              watch(znode.path)(Future.exception(new KeeperException.NoNodeException(znode.path)))(update)
            }
            val offer = znode.exists.monitor()
            offer syncWait() get() mustEqual result
            offer syncWait() get() mustEqual result
            offer syncWait() get() must throwA[KeeperException.NoNodeException]
            offer.sync().isDefined must beFalse
          }

          "SessionExpired" in {
            expect {
              watch(znode.path)(Future(new Stat))(Future(StateEvent.Disconnected()))
              watch(znode.path) {
                Future.exception(new KeeperException.SessionExpiredException)
              } {
                Future(StateEvent.Expired())
              }
            }
            val offer = znode.exists.monitor()
            offer syncWait() get() mustEqual result
            offer.sync().isDefined must beFalse
          }
        }

        //"that updates many times" in {
        //  expectZNodes(20000)
        //}
      }
    }

    "getChildren" in {
      val znode = zkClient("/parent")
      val result = ZNode.Children(znode, new Stat, "doe" :: "ray" :: "me" :: Nil)

      "apply" in {
        "ok" in {
          expect {
            getChildren(znode.path)(Future(result))
          }
          znode.getChildren().apply() mustEqual result
        }

        "error" in {
          expect {
            getChildren(znode.path) {
              Future.exception(new KeeperException.NoChildrenForEphemeralsException(znode.path))
            }
          }
          znode.getChildren() map { _ =>
            fail("Unexpected success")
          } handle { case e: KeeperException.NoChildrenForEphemeralsException =>
            e.getPath mustEqual znode.path
          } apply()
        }
      }

      "watch" in {
        expect {
          watchChildren(znode.path)(Future(result))(Future(NodeEvent.ChildrenChanged(znode.path)))
        }
        znode.getChildren.watch().onSuccess { case ZNode.Watch(r, f) =>
          r onSuccess { case ZNode.Children(p, s, c) =>
            p mustBe result.path
            s mustBe result.stat
            f onSuccess {
              case NodeEvent.ChildrenChanged(name) => name mustEqual znode.path
              case e => fail("Incorrect event: %s".format(e))
            }
          }
        }.apply()
      }

      "monitor" in {
        val znode = zkClient("/characters")
        val results = List(
            Seq("Angel", "Buffy", "Giles", "Willow", "Xander"),
            Seq("Angel", "Buffy", "Giles", "Spike", "Willow", "Xander"),
            Seq("Buffy", "Giles", "Willow", "Xander"),
            Seq("Angel", "Spike")) map { ZNode.Children(znode, new Stat, _) }
        expect {
          results foreach { r =>
            watchChildren(znode.path)(Future(r))(Future(NodeEvent.ChildrenChanged(znode.path)))
          }

        }
        val update = znode.getChildren.monitor()
        results foreach { result =>
          val r = update syncWait() get()
          r mustEqual result
        }
      }
    }

    "getData" in {
      val znode = zkClient("/giles")
      val result = ZNode.Data(znode, new Stat, "good show, indeed".getBytes)

      "apply" in {
        "ok" in {
          expect {
            getData(znode.path)(Future(result))
          }
          znode.getData().apply() mustEqual result
        }

        "error" in {
          expect {
            getData(znode.path) {
              Future.exception(new KeeperException.SessionExpiredException)
            }
          }
          znode.getData() map { _ =>
            fail("Unexpected success")
          } handle { case e: KeeperException.SessionExpiredException =>
            e.getPath mustEqual znode.path
          } apply()
        }
      }

      "watch" in {
        expect {
          watchData(znode.path) {
            Future(result)
          } {
            Future(NodeEvent.DataChanged(znode.path))
          }
        }
        try {
          znode.getData.watch().onSuccess {
            case ZNode.Watch(Return(z), u) => {
              z mustEqual result
              u onSuccess {
                case NodeEvent.DataChanged(name) => name mustEqual znode.path
                case e => fail("Incorrect event: %s".format(e))
              }
            }
            case _ => fail("unexpected return value")
          }.apply()
        } catch { case e => fail("unexpected error: %s".format(e)) }
      }

      "monitor" in {
        val results = List(
            "In every generation there is a chosen one.",
            "She alone will stand against the vampires the demons and the forces of darkness.",
            "She is the slayer.") map { text =>
          ZNode.Data(znode, new Stat, text.getBytes)
        }
        expect {
          results foreach { result =>
            watchData(znode.path)(Future(result)) {
              Future(NodeEvent.ChildrenChanged(znode.path))
            }
          }
          watchData(znode.path) {
            Future.exception(new KeeperException.SessionExpiredException)
          } {
            new Promise[WatchedEvent]
          }
        }
        val update = znode.getData.monitor()
        try {
          results foreach { data =>
            update.syncWait().get() mustEqual data
          }
        } catch { case e =>
          fail("unexpected error: %s".format(e))
        }
      }
    }

    "monitorTree" in {
      // Lay out a tree of ZNode.Children
      val treeRoot = ZNode.Children(zkClient("/arboreal"), new Stat, 'a' to 'e' map { _.toString })

      val treeChildren = treeRoot +: ('a' to 'e').map { c =>
        ZNode.Children(treeRoot(c.toString), new Stat, 'a' to c map { _.toString })
      }.flatMap { z =>
        z +: z.children.map { c => ZNode.Children(c, new Stat, Nil) }
      }

      // Lay out node updates for the tree: Add a 'z' node to all nodes named 'a'
      val updateTree = treeChildren.collect {
        case z @ ZNode.Children(p, s, c) if (p.endsWith("/c")) => {
          val newChild = ZNode.Children(z("z"), new Stat, Nil)
          val kids = c.filterNot { _.path.endsWith("/") } :+ newChild
          ZNode.Children(ZNode.Exists(z, s), kids) :: newChild :: Nil
        }
      }.flatten

      // Initially, we should get a ZNode.TreeUpdate for each node in the tree with only added nodes
      val expectedByPath = treeChildren.map { z =>
        z.path -> ZNode.TreeUpdate(z, z.children.toSet)
      }.toMap
      val updatesByPath = updateTree.map { z =>
        val prior: Set[ZNode] = expectedByPath.get(z.path).map { _.added }.getOrElse(Set.empty)
        z.path -> ZNode.TreeUpdate(z, z.children.toSet -- prior, prior -- z.children.toSet)
      }.toMap

      def okUpdates(event: String => WatchedEvent) {
        // Create promises for each node in the tree -- satisfying a promise will fire a
        // ChildrenChanged event for its associated node.
        val updatePromises = treeChildren.map { _.path -> new Promise[WatchedEvent] }.toMap
        expect {
          treeChildren foreach { z =>
            watchChildren(z.path)(Future(z))(updatePromises(z.path))
          }
          updateTree foreach { z =>
            watchChildren(z.path)(Future(z))(new Promise[WatchedEvent])  // nohollaback
          }
        }

        val offer = treeRoot.monitorTree()
        treeChildren foreach { _ =>
          val ztu = offer sync() apply(1.second)
          val e = expectedByPath(ztu.parent.path)
          ztu.parent mustEqual e.parent
          ztu.added.map { _.path } mustEqual e.added.map { _.path }.toSet
          ztu.removed must beEmpty
        }
        updateTree foreach { z =>
          updatePromises get(z.path) foreach { _.setValue(event(z.path)) }
          val ztu = offer sync() apply(1.second)
          val e = updatesByPath(z.path)
          ztu.parent mustEqual e.parent
          ztu.added.map { _.path } mustEqual e.added.map { _.path }.toSet
          ztu.removed must beEmpty
        }
        offer.sync().apply(1.second) must throwA[TimeoutException]
      }

      "ok" in okUpdates { NodeEvent.ChildrenChanged(_) }
      "be resilient to disconnect" in okUpdates { _ => StateEvent.Disconnected() }

      "stop on session expiration" in {
        expect {
          treeChildren foreach { z =>
            watchChildren(z.path)(Future(z))(Future(StateEvent.Disconnected()))
            watchChildren(z.path)(Future.exception(new KeeperException.SessionExpiredException)) {
              Future(StateEvent.Expired())
            }
          }
        }

        val offer = treeRoot.monitorTree()
        treeChildren foreach { _ =>
          val ztu = offer sync() apply(1.second)
          val e = expectedByPath(ztu.parent.path)
          ztu.parent mustEqual e.parent
          ztu.added.map { _.path } mustEqual e.added.map { _.path }.toSet
          ztu.removed must beEmpty
        }
        offer.sync().apply(1.second) must throwA[TimeoutException]
      }
    }

    "set data" in {
      val znode = zkClient("/empty/node")
      val result = ZNode.Exists(znode, new Stat)
      val data = "word to your mother.".getBytes
      val version = 13
      "ok" in {
        expect {
          setData(znode.path, data, version)(Future(result.stat))
        }
        znode.setData(data, version).apply(1.second) mustEqual znode
      }

      "error" in {
        expect {
          setData(znode.path, data, version) {
            Future.exception(new KeeperException.SessionExpiredException)
          }
        }
        znode.setData(data, version).apply() must throwA[KeeperException.SessionExpiredException]
      }
    }

    "sync" in {
      val znode = zkClient("/sync")

      "ok" in {
        expect {
          sync(znode.path)(Future.Done)
        }
        znode.sync().apply(1.second) mustEqual znode
      }

      "error" in {
        expect {
          sync(znode.path) {
            Future.exception(new KeeperException.SystemErrorException)
          }
        }
        znode.sync() map { _ =>
          fail("Unexpected success")
        } handle { case e: KeeperException.SystemErrorException =>
          e.getPath mustEqual znode.path
        } apply(1.second)
      }
    }
  }

  Option { System.getProperty("com.twitter.zk.TEST_CONNECT") } foreach { connectString =>
    "A live server @ %s".format(connectString) should {
      val zkClient = ZkClient(connectString, 1.second, 1.minute)
      val znode = zkClient("/")
      doAfter { zkClient.release() }
      "have 'zookeeper' in '/'" in {
        znode.getChildren().apply(2.seconds).children.map { _.name } mustContain("zookeeper")
      }
    }
  }
}

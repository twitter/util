package com.twitter.zk

import scala.collection.Set
import scala.jdk.CollectionConverters._

import org.apache.zookeeper._
import org.apache.zookeeper.data.{ACL, Stat}
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.WordSpec
import org.scalatestplus.mockito.MockitoSugar

import com.twitter.conversions.DurationOps._
import com.twitter.logging.{Level, Logger}
import com.twitter.util._

class ZkClientTest extends WordSpec with MockitoSugar {
  Logger.get("").setLevel(Level.FATAL)

  implicit val javaTimer = new JavaTimer(true)

  class ZkClientHelper {
    val zk = mock[ZooKeeper]
    class TestZkClient extends ZkClient {
      val connector = new Connector {
        def apply() = Future(zk)
        def release() = Future.Done
      }
    }
    def zkClient = new TestZkClient

    def answer[A](idx: Int)(res: A => Unit) = new Answer[Unit] {
      override def answer(invocation: InvocationOnMock): Unit = {
        val value = invocation.getArguments()(idx).asInstanceOf[A]
        res(value)
      }
    }

    def answer[A, B](idx1: Int, idx2: Int)(res: (A, B) => Unit) = new Answer[Unit] {
      override def answer(invocation: InvocationOnMock): Unit = {
        val left = invocation.getArguments()(idx1).asInstanceOf[A]
        val right = invocation.getArguments()(idx2).asInstanceOf[B]
        res(left, right)
      }
    }

    /*
     * ZooKeeper expectation wrappers
     */
    def create(
      path: String,
      data: Array[Byte] = "".getBytes,
      acls: Seq[ACL] = zkClient.acl,
      mode: CreateMode = zkClient.mode
    )(
      wait: => Future[String]
    ): Unit = {
      when(
        zk.create(
          meq(path),
          meq(data),
          meq(acls.asJava),
          meq(mode),
          any[AsyncCallback.StringCallback],
          meq(null)
        )
      ) thenAnswer answer[AsyncCallback.StringCallback](4) { cbValue =>
        wait onSuccess { newPath =>
          cbValue.processResult(0, path, null, newPath)
        } onFailure {
          case ke: KeeperException =>
            cbValue.processResult(ke.code.intValue, path, null, null)
        }
      }
    }

    def delete(path: String, version: Int)(wait: => Future[Unit]): Unit = {
      when(
        zk.delete(
          meq(path),
          meq(version),
          any[AsyncCallback.VoidCallback],
          meq(null))) thenAnswer answer[
        AsyncCallback.VoidCallback
      ](2) { cbValue =>
        wait onSuccess { _ =>
          cbValue.processResult(0, path, null)
        } onFailure {
          case ke: KeeperException =>
            cbValue.processResult(ke.code.intValue, path, null)
        }
      }
    }

    def exists(path: String)(stat: => Future[Stat]): Unit = {
      when(
        zk.exists(
          meq(path),
          meq(false),
          any[AsyncCallback.StatCallback],
          meq(null)
        )
      ) thenAnswer answer[AsyncCallback.StatCallback](2) { cbValue =>
        stat onSuccess {
          cbValue.processResult(0, path, null, _)
        } onFailure {
          case ke: KeeperException =>
            cbValue.processResult(ke.code.intValue, path, null, null)
        }
      }
    }

    def watch(path: String)(stat: => Future[Stat])(update: => Future[WatchedEvent]): Unit = {
      val watcher = ArgumentCaptor.forClass(classOf[Watcher])
      val cb = ArgumentCaptor.forClass(classOf[AsyncCallback.StatCallback])
      when(zk.exists(meq(path), watcher.capture(), cb.capture(), meq(null))) thenAnswer answer[
        Watcher,
        AsyncCallback.StatCallback
      ](1, 2) {
        case (watcher: Watcher, cbValue: AsyncCallback.StatCallback) =>
          stat onSuccess {
            cbValue.processResult(0, path, null, _)
          } onFailure {
            case ke: KeeperException =>
              cbValue.processResult(ke.code.intValue, path, null, null)
          }
          update onSuccess { watcher.process(_) }
      }
    }

    def getChildren(path: String)(children: => Future[ZNode.Children]): Unit = {
      val cb = ArgumentCaptor.forClass(classOf[AsyncCallback.Children2Callback])
      when(
        zk.getChildren(
          meq(path),
          meq(false),
          any[AsyncCallback.Children2Callback],
          meq(null))) thenAnswer answer[
        AsyncCallback.Children2Callback
      ](2) { cbValue =>
        children onSuccess { znode =>
          cbValue.processResult(
            0,
            path,
            null,
            znode.children.map { _.name }.toList.asJava,
            znode.stat
          )
        } onFailure {
          case ke: KeeperException =>
            cbValue.processResult(ke.code.intValue, path, null, null, null)
        }
      }
    }

    def watchChildren(
      path: String
    )(
      children: => Future[ZNode.Children]
    )(
      update: => Future[WatchedEvent]
    ): Unit = {
      val w = ArgumentCaptor.forClass(classOf[Watcher])
      val cb = ArgumentCaptor.forClass(classOf[AsyncCallback.Children2Callback])
      when(zk.getChildren(meq(path), w.capture(), cb.capture(), meq(null))) thenAnswer answer[
        Watcher,
        AsyncCallback.Children2Callback
      ](1, 2) {
        case (watcher: Watcher, cbValue: AsyncCallback.Children2Callback) =>
          children onSuccess {
            case ZNode.Children(znode, stat, children) =>
              cbValue.processResult(0, path, null, children.map { _.name }.toList.asJava, stat)
          } onFailure {
            case ke: KeeperException =>
              cbValue.processResult(ke.code.intValue, path, null, null, null)
          }
          update onSuccess { watcher.process(_) }
      }
    }

    def getData(path: String)(result: => Future[ZNode.Data]): Unit = {
      when(
        zk.getData(
          meq(path),
          meq(false),
          any[AsyncCallback.DataCallback],
          meq(null))) thenAnswer answer[
        AsyncCallback.DataCallback
      ](2) { cbValue =>
        result onSuccess { z =>
          cbValue.processResult(0, path, null, z.bytes, z.stat)
        } onFailure {
          case ke: KeeperException =>
            cbValue.processResult(ke.code.intValue, path, null, null, null)
        }
      }
    }

    def watchData(
      path: String
    )(
      result: => Future[ZNode.Data]
    )(
      update: => Future[WatchedEvent]
    ): Unit = {
      val w = ArgumentCaptor.forClass(classOf[Watcher])
      val cb = ArgumentCaptor.forClass(classOf[AsyncCallback.DataCallback])
      when(zk.getData(meq(path), w.capture(), cb.capture(), meq(null))) thenAnswer answer[
        Watcher,
        AsyncCallback.DataCallback
      ](1, 2) {
        case (watcher: Watcher, cbValue: AsyncCallback.DataCallback) =>
          result onSuccess { z =>
            cbValue.processResult(0, path, null, z.bytes, z.stat)
          } onFailure {
            case ke: KeeperException =>
              cbValue.processResult(ke.code.intValue, path, null, null, null)
          }
          update onSuccess { watcher.process(_) }
      }
    }

    def setData(path: String, data: Array[Byte], version: Int)(waiting: => Future[Stat]): Unit = {
      when(
        zk.setData(meq(path), meq(data), meq(version), any[AsyncCallback.StatCallback], meq(null))
      ) thenAnswer answer[AsyncCallback.StatCallback](3) { cbValue =>
        waiting onSuccess { stat =>
          cbValue.processResult(0, path, null, stat)
        } onFailure {
          case ke: KeeperException =>
            cbValue.processResult(ke.code.intValue, path, null, null)
        }
      }
    }

    def sync(path: String)(wait: Future[Unit]): Unit = {
      val cb = ArgumentCaptor.forClass(classOf[AsyncCallback.VoidCallback])
      when(zk.sync(meq(path), any[AsyncCallback.VoidCallback], meq(null))) thenAnswer answer[
        AsyncCallback.VoidCallback
      ](1) { cbValue =>
        wait onSuccess { _ =>
          cbValue.processResult(0, path, null)
        } onFailure {
          case ke: KeeperException =>
            cbValue.processResult(ke.code.intValue, path, null)
        }
      }
    }
  }

  "ZkClient" should {
    val h = new ZkClientHelper
    import h._

    "apply" in {
      val path = "/root/path/to/a/node"
      val znode = zkClient(path)
      try {
        znode.asInstanceOf[ZNode]
      } catch {
        case _: ClassCastException => fail("not ZNode")
      }
      assert(znode.path == path)
    }

    "retry" should {
      val connectionLoss = new KeeperException.ConnectionLossException

      "retry KeeperException.ConnectionLossException until completion" in {
        var i = 0
        Await.ready(
          zkClient
            .withRetries(3)
            .retrying { _ =>
              i += 1
              Future.exception(connectionLoss)
            }
            .onSuccess { _ => fail("Unexpected success") }
            .handle {
              case e: KeeperException.ConnectionLossException =>
                assert(e == connectionLoss)
                assert(i == 4)
            }
        )
      }

      "not retry on success" in {
        var i = 0
        Await.ready(
          zkClient
            .withRetries(3)
            .retrying { _ =>
              i += 1
              Future.Done
            }
            .onSuccess { _ => assert(i == 1) }
        )
      }

      "convert exceptions to Futures" in {
        val rex = new RuntimeException
        var i = 0
        Await.ready(
          zkClient
            .withRetries(3)
            .retrying { _ =>
              i += 1
              throw rex
            }
            .onSuccess { _ => fail("Unexpected success") }
            .handle {
              case e: RuntimeException =>
                assert(e == rex)
                assert(i == 1)
            }
        )
      }

      "only retry when instructed to" in {
        var i = 0
        Await.ready(
          zkClient
            .retrying { _ =>
              i += 1
              Future.exception(connectionLoss)
            }
            .onSuccess { _ => fail("Shouldn't have succeeded") }
            .handle {
              case e: KeeperException.ConnectionLossException =>
                assert(i == 1)
            }
        )
      }
    }

    "transform" should {
      val acl = ZooDefs.Ids.OPEN_ACL_UNSAFE.asScala.toSeq
      val mode = CreateMode.EPHEMERAL_SEQUENTIAL
      val retries = 37
      val retryPolicy = new RetryPolicy.Basic(retries)

      "withAcl" in {
        val transformed = zkClient.withAcl(acl)
        try {
          transformed.asInstanceOf[ZkClient]
        } catch {
          case _: ClassCastException => fail("not ZNode")
        }

        assert(transformed.acl == acl)
      }

      "withMode" in {
        val transformed = zkClient.withMode(mode)
        try {
          transformed.asInstanceOf[ZkClient]
        } catch {
          case _: ClassCastException => fail("not ZNode")
        }
        assert(transformed.mode == mode)
      }

      "withRetries" in {
        val transformed = zkClient.withRetries(retries)
        try {
          transformed.asInstanceOf[ZkClient]
        } catch {
          case _: ClassCastException => fail("not ZNode")
        }
        assert(transformed.retryPolicy match {
          case RetryPolicy.Basic(r) => r == retries
          case _ => false
        })
      }

      "withRetryPolicy" in {
        val transformed = zkClient.withRetryPolicy(retryPolicy)
        try {
          transformed.asInstanceOf[ZkClient]
        } catch {
          case _: ClassCastException => fail("not ZNode")
        }
        assert(transformed.retryPolicy == retryPolicy)
      }

      "chained" in {
        val transformed = zkClient.withAcl(acl).withMode(mode).withRetryPolicy(retryPolicy)
        try {
          transformed.asInstanceOf[ZkClient]
        } catch {
          case _: ClassCastException => fail("not ZNode")
        }
        assert(transformed.acl == acl)
        assert(transformed.mode == mode)
        assert(transformed.retryPolicy == retryPolicy)
      }
    }
  }

  "ZNode" should {
    "apply a relative path to a ZNode" in {
      val h = new ZkClientHelper
      import h._

      val znode = zkClient("/some/path")
      assert(znode("to/child").path == "/some/path/to/child")
    }

    "create" should {
      val h = new ZkClientHelper
      import h._

      val path = "/root/path/to/a/node"

      "with data" in {
        val data = "blah".getBytes
        create(path, data)(Future(path))
        assert(Await.result(zkClient(path).create(data)) match {
          case ZNode(p) => p == path
        })
      }

      "error" in {
        val data = null
        create(path, data)(Future.exception(new KeeperException.NodeExistsException(path)))
        Await.ready(zkClient(path).create(data) map { _ =>
          fail("Unexpected success")
        } handle {
          case e: KeeperException.NodeExistsException =>
            assert(e.getPath == path)
        })
      }

      "sequential" in {
        val data = null

        create(path, data, mode = CreateMode.EPHEMERAL_SEQUENTIAL)(Future(path + "0000"))

        val newPath =
          Await.result(zkClient(path).create(data, mode = CreateMode.EPHEMERAL_SEQUENTIAL))
        assert(newPath.name == "node0000")
      }

      "a child" should {
        val childPath = path + "/child"

        "with a name and data" in {
          val data = "blah".getBytes
          create(childPath, data)(Future(childPath))
          val znode = Await.result(zkClient(path).create(data, child = Some("child")))
          assert(znode.path == childPath)
        }

        "error" in {
          val data = null
          create(childPath, data) {
            Future.exception(new KeeperException.NodeExistsException(childPath))
          }
          Await.ready(zkClient(path).create(data, child = Some("child")) map { _ =>
            fail("Unexpected success")
          } handle {
            case e: KeeperException.NodeExistsException =>
              assert(e.getPath == childPath)
          })
        }

        "sequential, with an empty name" in {
          val data = null

          create(path + "/", data, mode = CreateMode.EPHEMERAL_SEQUENTIAL)(Future(path + "/0000"))

          assert(
            Await
              .result(
                zkClient(path)
                  .create(data, child = Some(""), mode = CreateMode.EPHEMERAL_SEQUENTIAL)
              )
              .path == path + "/0000"
          )
        }
      }
    }

    "delete" should {
      val h = new ZkClientHelper
      import h._

      val version = 0
      val path = "/path"
      "ok" in {
        delete(path, version)(Future.Done)
        assert(Await.result(zkClient(path).delete(version)) match {
          case ZNode(p) => p == path
        })
      }

      "error" in {
        delete(path, version)(Future.exception(new KeeperException.NoNodeException(path)))
        Await.ready(zkClient(path).delete(version) map { _ =>
          fail("Unexpected success")
        } handle {
          case e: KeeperException.NoNodeException =>
            assert(e.getPath == path)
        })
      }
    }

    "exist" should {
      class ExistHelper extends ZkClientHelper {
        val znode = zkClient("/maybe/exists")
        val result = ZNode.Exists(znode, new Stat)
      }

      "apply" should {
        val h = new ExistHelper
        import h._

        "ok" in {
          exists(znode.path)(Future(result.stat))
          assert(Await.result(znode.exists()) == result)
        }

        "error" in {
          exists(znode.path)(Future.exception(new KeeperException.NoNodeException(znode.path)))
          Await.ready(znode.exists() map { _ =>
            fail("Unexpected success")
          } handle {
            case e: KeeperException.NoNodeException =>
              assert(e.getPath == znode.path)
          })
        }
      }

      "watch" in {
        val h = new ExistHelper
        import h._

        val event = NodeEvent.Deleted(znode.path)
        watch(znode.path)(Future(result.stat))(Future(event))
        Await.ready(znode.exists.watch() onSuccess {
          case ZNode.Watch(r, update) =>
            r onSuccess { exists =>
              assert(exists == result)
              update onSuccess {
                case e @ NodeEvent.Deleted(name) => assert(e == event)
                case e => fail("Incorrect event: %s".format(e))
              }
            }
        })
      }

      "monitor" should {
        class MonitorHelper extends ExistHelper {
          val deleted = NodeEvent.Deleted(znode.path)
          def expectZNodes(n: Int): Unit = {
            val results = 0 until n map { _ => ZNode.Exists(znode, new Stat) }
            results foreach { r => watch(znode.path)(Future(r.stat))(Future(deleted)) }
            val update = znode.exists.monitor()
            results foreach { result => assert(update.syncWait().get() == result) }
          }
        }

        "that updates several times" in {
          val h = new MonitorHelper
          import h._

          expectZNodes(3)
        }

        "handles session events properly" should {
          "AuthFailed" in {
            val h = new MonitorHelper
            import h._

            // does not reset
            watch(znode.path)(Future(new Stat))(Future(StateEvent.AuthFailed()))
            // this case is somewhat unrealistic
            val offer = znode.exists.monitor()
            assert(offer.syncWait().get() == result)
          }

          "Disconnected" in {
            val h = new MonitorHelper
            import h._

            val update = new Promise[WatchedEvent]

            watch(znode.path)(Future(new Stat))(Future(StateEvent.Disconnected()))
            val offer = znode.exists.monitor()
            assert(offer.syncWait().get() == result)

            watch(znode.path)(Future(new Stat))(Future(NodeEvent.Deleted(znode.path)))
            assert(offer.syncWait().get() == result)

            watch(znode.path)(Future.exception(new KeeperException.NoNodeException(znode.path)))(
              update
            )
            offer.sync()
            intercept[KeeperException.NoNodeException] {
              offer.syncWait().get
            }
            assert(offer.sync().isDefined == false)
          }

          "SessionExpired" in {
            val h = new MonitorHelper
            import h._

            watch(znode.path)(Future(new Stat))(Future(StateEvent.Disconnected()))
            val offer = znode.exists.monitor()
            assert(offer.syncWait().get == result)

            watch(znode.path) {
              Future.exception(new KeeperException.SessionExpiredException)
            } {
              Future(StateEvent.Expired())
            }
            offer.sync()
            assert(offer.sync().isDefined == false)
          }
        }

        //"that updates many times" in {
        //  expectZNodes(20000)
        //}
      }
    }

    "getChildren" should {
      val h = new ZkClientHelper
      import h._

      val znode = zkClient("/parent")
      val result = ZNode.Children(znode, new Stat, "doe" :: "ray" :: "me" :: Nil)

      "apply" should {
        "ok" in {
          getChildren(znode.path)(Future(result))
          assert(Await.result(znode.getChildren()) == result)
        }

        "error" in {
          getChildren(znode.path) {
            Future.exception(new KeeperException.NoChildrenForEphemeralsException(znode.path))
          }
          Await.ready(znode.getChildren() map { _ =>
            fail("Unexpected success")
          } handle {
            case e: KeeperException.NoChildrenForEphemeralsException =>
              assert(e.getPath == znode.path)
          })
        }
      }

      "watch" in {
        watchChildren(znode.path)(Future(result))(Future(NodeEvent.ChildrenChanged(znode.path)))
        Await.ready(znode.getChildren.watch().onSuccess {
          case ZNode.Watch(r, f) =>
            r onSuccess {
              case ZNode.Children(p, s, c) =>
                assert(p == result.path)
                assert(s == result.stat)
                f onSuccess {
                  case NodeEvent.ChildrenChanged(name) => assert(name == znode.path)
                  case e => fail("Incorrect event: %s".format(e))
                }
            }
        })
      }

      "monitor" in {
        val znode = zkClient("/characters")
        val results = List(
          Seq("Angel", "Buffy", "Giles", "Willow", "Xander"),
          Seq("Angel", "Buffy", "Giles", "Spike", "Willow", "Xander"),
          Seq("Buffy", "Giles", "Willow", "Xander"),
          Seq("Angel", "Spike")
        ) map { ZNode.Children(znode, new Stat, _) }
        results foreach { r =>
          watchChildren(znode.path)(Future(r))(Future(NodeEvent.ChildrenChanged(znode.path)))
        }

        val update = znode.getChildren.monitor()
        results foreach { result =>
          val r = update.syncWait().get
          assert(r.path == result.path)
        }
      }
    }

    "getData" should {

      class GetDataHelper extends ZkClientHelper {
        val znode = zkClient("/giles")
        val result = ZNode.Data(znode, new Stat, "good show, indeed".getBytes)
      }

      "apply" should {
        "ok" in {
          val h = new GetDataHelper
          import h._

          getData(znode.path)(Future(result))
          assert(Await.result(znode.getData()) == result)
        }

        "error" in {
          val h = new GetDataHelper
          import h._

          getData(znode.path) {
            Future.exception(new KeeperException.SessionExpiredException)
          }
          Await.ready(znode.getData() map { _ =>
            fail("Unexpected success")
          } handle {
            case e: KeeperException.SessionExpiredException =>
              assert(e.getPath == znode.path)
          })
        }
      }

      "watch" in {
        val h = new GetDataHelper
        import h._

        watchData(znode.path) {
          Future(result)
        } {
          Future(NodeEvent.DataChanged(znode.path))
        }
        try {
          Await.ready(znode.getData.watch().onSuccess {
            case ZNode.Watch(Return(z), u) => {
              assert(z == result)
              u onSuccess {
                case NodeEvent.DataChanged(name) => assert(name == znode.path)
                case e => fail("Incorrect event: %s".format(e))
              }
            }
            case _ => fail("unexpected return value")
          })
        } catch { case e: Throwable => fail("unexpected error: %s".format(e)) }
      }

      "monitor" in {
        val h = new GetDataHelper
        import h._

        val results = List(
          "In every generation there is a chosen one.",
          "She alone will stand against the vampires the demons and the forces of darkness.",
          "She is the slayer."
        ) map { text => ZNode.Data(znode, new Stat, text.getBytes) }
        results foreach { result =>
          watchData(znode.path)(Future(result)) {
            Future(NodeEvent.ChildrenChanged(znode.path))
          }
        }
        val update = znode.getData.monitor()
        try {
          results foreach { data =>
            assert(Await.result(update.sync(), 1.second).get().path == data.path)
          }
        } catch {
          case e: Throwable =>
            fail("unexpected error: %s".format(e))
        }
        watchData(znode.path) {
          Future.exception(new KeeperException.SessionExpiredException)
        } {
          new Promise[WatchedEvent]
        }
      }
    }

    "monitorTree" should {
      val h = new ZkClientHelper
      import h._

      // Lay out a tree of ZNode.Children
      val treeRoot = ZNode.Children(zkClient("/arboreal"), new Stat, 'a' to 'e' map { _.toString })

      val treeChildren = treeRoot +: ('a' to 'e')
        .map { c => ZNode.Children(treeRoot(c.toString), new Stat, 'a' to c map { _.toString }) }
        .flatMap { z => z +: z.children.map { c => ZNode.Children(c, new Stat, Nil) } }

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

      def okUpdates(event: String => WatchedEvent): Unit = {
        // Create promises for each node in the tree -- satisfying a promise will fire a
        // ChildrenChanged event for its associated node.
        val updatePromises = treeChildren.map { _.path -> new Promise[WatchedEvent] }.toMap
        treeChildren foreach { z => watchChildren(z.path)(Future(z))(updatePromises(z.path)) }

        val offer = treeRoot.monitorTree()
        treeChildren foreach { _ =>
          val ztu = Await.result(offer.sync(), 1.second)
          val e = expectedByPath(ztu.parent.path)
          assert(ztu.parent.path == e.parent.path)
          assert(ztu.added.map { _.path } == e.added.map { _.path }.toSet)
          assert(ztu.removed == Set())
        }

        updateTree foreach { z =>
          watchChildren(z.path)(Future(z))(new Promise[WatchedEvent]) // nohollaback
        }

        updateTree foreach { z =>
          updatePromises get (z.path) foreach { _.setValue(event(z.path)) }
          val ztu = Await.result(offer.sync(), 1.second)
          val e = updatesByPath(z.path)
          assert(ztu.parent.path == e.parent.path)
          assert(ztu.added.map { _.path } == e.added.map { _.path }.toSet)
          assert(ztu.removed == Set())
        }
        intercept[TimeoutException] {
          Await.result(offer.sync(), 1.second)
        }
      }

      "ok" in okUpdates { NodeEvent.ChildrenChanged(_) }
      "be resilient to disconnect" in okUpdates { _ => StateEvent.Disconnected() }

      "stop on session expiration" in {
        treeChildren foreach { z =>
          watchChildren(z.path)(Future(z))(Future(StateEvent.Disconnected()))
          watchChildren(z.path)(Future.exception(new KeeperException.SessionExpiredException)) {
            Future(StateEvent.Expired())
          }
        }

        val offer = treeRoot.monitorTree()
        intercept[TimeoutException] {
          treeChildren foreach { _ =>
            val ztu = Await.result(offer.sync(), 1.second)
            val e = expectedByPath(ztu.parent.path)
            assert(ztu.parent == e.parent)
            assert(ztu.added.map { _.path } == e.added.map { _.path }.toSet)
            assert(ztu.removed == List())
          }
          Await.result(offer.sync(), 1.second)
        }
      }
    }

    "set data" should {
      val h = new ZkClientHelper
      import h._

      val znode = zkClient("/empty/node")
      val result = ZNode.Exists(znode, new Stat)
      val data = "word to your mother.".getBytes
      val version = 13
      "ok" in {
        setData(znode.path, data, version)(Future(result.stat))
        assert(Await.result(znode.setData(data, version), 1.second) == znode)
      }

      "error" in {
        setData(znode.path, data, version) {
          Future.exception(new KeeperException.SessionExpiredException)
        }
        intercept[KeeperException.SessionExpiredException] {
          Await.result(znode.setData(data, version))
        }
      }
    }

    "sync" should {
      val h = new ZkClientHelper
      import h._

      val znode = zkClient("/sync")

      "ok" in {
        sync(znode.path)(Future.Done)
        assert(Await.result(znode.sync(), 1.second) == znode)
      }

      "error" in {
        sync(znode.path) {
          Future.exception(new KeeperException.SystemErrorException)
        }
        Await.ready(
          znode.sync() map { _ =>
            fail("Unexpected success")
          } handle {
            case e: KeeperException.SystemErrorException =>
              assert(e.getPath == znode.path)
          },
          1.second)
      }
    }
  }

  Option { System.getProperty("com.twitter.zk.TEST_CONNECT") } foreach { connectString =>
    "A live server @ %s".format(connectString) should {
      val zkClient = ZkClient(connectString, 1.second, 1.minute)
      val znode = zkClient("/")
      def after = { zkClient.release() }
      "have 'zookeeper' in '/'" in {
        Await.result(znode.getChildren(), 2.seconds).children.map { _.name }.contains("zookeeper")
      }
    }
  }
}

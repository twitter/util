package com.twitter.zk

import com.twitter.util.{Promise, Return, Throw}
import org.apache.zookeeper.{AsyncCallback, KeeperException}
import org.apache.zookeeper.data.Stat
import java.util.{List => JList}
import scala.collection.JavaConverters._

/** Mix-in to make an AsyncCallback a Promise */
trait AsyncCallbackPromise[T] extends Promise[T] {
  def process(rc: Int, path: String, result: T) {
    KeeperException.Code.get(rc) match {
      case KeeperException.Code.OK => updateIfEmpty(Return(result))
      case code => updateIfEmpty(Throw(KeeperException.create(code, path)))
    }
  }
}

class StringCallbackPromise extends AsyncCallbackPromise[String] with AsyncCallback.StringCallback {
  def processResult(rc: Int, path: String, ctx: Any, name: String) {
    process(rc, path, name)
  }
}

class UnitCallbackPromise extends AsyncCallbackPromise[Unit] with AsyncCallback.VoidCallback {
  def processResult(rc: Int, path: String, ctx: Any) {
    process(rc, path, Unit)
  }
}

class ExistsCallbackPromise(znode: ZNode) extends AsyncCallbackPromise[ZNode.Exists]
    with AsyncCallback.StatCallback {
  def processResult(rc: Int, path: String, ctx: Any, stat: Stat) {
    process(rc, path, znode(stat))
  }
}

class ChildrenCallbackPromise(znode: ZNode) extends AsyncCallbackPromise[ZNode.Children]
    with AsyncCallback.Children2Callback {
  def processResult(rc: Int, path: String, ctx: Any, children: JList[String], stat: Stat) {
    process(rc, path, znode(stat, children.asScala.toSeq))
  }
}

class DataCallbackPromise(znode: ZNode) extends AsyncCallbackPromise[ZNode.Data]
    with AsyncCallback.DataCallback {
  def processResult(rc: Int, path: String, ctx: Any, bytes: Array[Byte], stat: Stat) {
    process(rc, path, znode(stat, bytes))
  }
}

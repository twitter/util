package com.twitter.zk

import com.twitter.io.TempDirectory
import java.io.File
import java.net.InetAddress
import org.apache.zookeeper.server.ZooKeeperServer
import org.scalatest.{BeforeAndAfter, FunSuite}

class ServerCnxnFactoryTest extends FunSuite with BeforeAndAfter {
  val addr = InetAddress.getLocalHost

  var testServer: ZooKeeperServer = null
  var tmpDir: File = null

  before {
    tmpDir = TempDirectory.create()
    testServer = new ZooKeeperServer(tmpDir, tmpDir, ZooKeeperServer.DEFAULT_TICK_TIME)
  }

  after {
    tmpDir.delete()
  }

  test("ServerCnxnFactory returns valid Factory") {
    val factory = ServerCnxnFactory(addr)
    val boundPort = factory.getLocalPort

    factory.startup(testServer)
    assert(testServer.getClientPort == boundPort)

    factory.shutdown()
    assert(testServer.isRunning == false)
  }
}

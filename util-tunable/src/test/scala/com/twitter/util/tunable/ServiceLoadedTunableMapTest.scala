package com.twitter.util.tunable

import org.scalatest.funsuite.AnyFunSuite

class ServiceLoadedTunableTestClient1 extends ServiceLoadedTunableMap with TunableMap.Proxy {

  private val tunableMap = TunableMap.newMutable()

  tunableMap.put("tunableId1", "foo")
  tunableMap.put("tunableId2", 5)

  protected def underlying: TunableMap = tunableMap
  def id: String = "client1"
}

class ServiceLoadedTunableTestClient2 extends ServiceLoadedTunableMap with TunableMap.Proxy {
  protected def underlying: TunableMap = NullTunableMap
  def id: String = "client2"
}

class ServiceLoadedTunableTestClient2Dup extends ServiceLoadedTunableMap with TunableMap.Proxy {
  protected def underlying: TunableMap = NullTunableMap
  def id: String = "client2"
}

class ServiceLoadedTunableMapTest extends AnyFunSuite {

  test(
    "IllegalArgumentException thrown when there is more than one ServiceLoadedTunableMap " +
      "for a given serviceName/id"
  ) {

    val ex = intercept[IllegalStateException] {
      ServiceLoadedTunableMap("client2")
    }
    assert(ex.getMessage.contains("Found multiple `ServiceLoadedTunableMap`s for client2"))
  }

  test("NullTunableMap returned when no matches") {
    intercept[IllegalStateException] {
      val tunableMap = ServiceLoadedTunableMap("Non-existent-id")
      assert(tunableMap eq NullTunableMap)
    }
  }

  test("TunableMap returned when there is one match for id") {
    intercept[IllegalStateException] {

      val tunableMap = ServiceLoadedTunableMap("client1")

      assert(tunableMap.entries.size == 2)
      assert(tunableMap(TunableMap.Key[String]("tunableId1"))() == Some("foo"))
      assert(tunableMap(TunableMap.Key[Int]("tunableId2"))() == Some(5))
    }
  }
}

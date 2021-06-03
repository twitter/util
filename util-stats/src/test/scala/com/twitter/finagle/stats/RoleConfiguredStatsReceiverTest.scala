package com.twitter.finagle.stats

import org.scalatest.funsuite.AnyFunSuite

class RoleConfiguredStatsReceiverTest extends AnyFunSuite {
  test("RoleConfiguredSR configures metadata role correctly") {
    val mem = new InMemoryStatsReceiver
    val clientSR = new RoleConfiguredStatsReceiver(mem, Client)
    val serverSR = new RoleConfiguredStatsReceiver(mem, Server)

    clientSR.counter("foo")
    assert(mem.schemas.get(Seq("foo")).get.role == Client)
    clientSR.addGauge("bar")(3)
    assert(mem.schemas.get(Seq("bar")).get.role == Client)
    clientSR.stat("baz")
    assert(mem.schemas.get(Seq("baz")).get.role == Client)

    serverSR.counter("fee")
    assert(mem.schemas.get(Seq("fee")).get.role == Server)
    serverSR.addGauge("fi")(3)
    assert(mem.schemas.get(Seq("fi")).get.role == Server)
    serverSR.stat("foe")
    assert(mem.schemas.get(Seq("foe")).get.role == Server)
  }

  test("RoleConfiguredSR equality") {
    val mem = new InMemoryStatsReceiver
    val clientA = RoleConfiguredStatsReceiver(mem, Client)
    val clientB = RoleConfiguredStatsReceiver(mem, Client)
    val serverA = RoleConfiguredStatsReceiver(mem, Server)
    val serverB = RoleConfiguredStatsReceiver(mem, Server)

    assert(clientA == clientB)
    assert(clientA != serverA)
    assert(clientA != serverB)
    assert(serverA == serverB)
  }
}

package com.twitter.finagle.stats

import org.scalatest.FunSuite

class RoleConfiguredStatsReceiverTest extends FunSuite {
  test("RoleConfiguredSR configures metadata role correctly") {
    val mem = new InMemoryStatsReceiver
    val clientSR = new RoleConfiguredStatsReceiver(mem, Client)
    val serverSR = new RoleConfiguredStatsReceiver(mem, Server)

    clientSR.counter("foo")
    assert(mem.schemas.get(Seq("foo")).get.metricBuilder.role == Client)
    clientSR.addGauge("bar")(3)
    assert(mem.schemas.get(Seq("bar")).get.metricBuilder.role == Client)
    clientSR.stat("baz")
    assert(mem.schemas.get(Seq("baz")).get.metricBuilder.role == Client)

    serverSR.counter("fee")
    assert(mem.schemas.get(Seq("fee")).get.metricBuilder.role == Server)
    serverSR.addGauge("fi")(3)
    assert(mem.schemas.get(Seq("fi")).get.metricBuilder.role == Server)
    serverSR.stat("foe")
    assert(mem.schemas.get(Seq("foe")).get.metricBuilder.role == Server)
  }

  test("RoleConfiguredSR equality") {
    val mem = new InMemoryStatsReceiver
    val clientA = new RoleConfiguredStatsReceiver(mem, Client)
    val clientB = new RoleConfiguredStatsReceiver(mem, Client)
    val serverA = new RoleConfiguredStatsReceiver(mem, Server)
    val serverB = new RoleConfiguredStatsReceiver(mem, Server)

    assert(clientA == clientB)
    assert(clientA != serverA)
    assert(clientA != serverB)
    assert(serverA == serverB)
  }
}

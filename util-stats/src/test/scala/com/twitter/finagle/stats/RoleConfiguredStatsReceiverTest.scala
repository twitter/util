package com.twitter.finagle.stats

import org.scalatest.funsuite.AnyFunSuite

class RoleConfiguredStatsReceiverTest extends AnyFunSuite {
  test("RoleConfiguredSR configures metadata role correctly") {
    val mem = new InMemoryStatsReceiver
    val clientSR = new RoleConfiguredStatsReceiver(mem, SourceRole.Client)
    val serverSR = new RoleConfiguredStatsReceiver(mem, SourceRole.Server)

    clientSR.counter("foo")
    assert(mem.schemas.get(Seq("foo")).get.role == SourceRole.Client)
    clientSR.addGauge("bar")(3)
    assert(mem.schemas.get(Seq("bar")).get.role == SourceRole.Client)
    clientSR.stat("baz")
    assert(mem.schemas.get(Seq("baz")).get.role == SourceRole.Client)

    serverSR.counter("fee")
    assert(mem.schemas.get(Seq("fee")).get.role == SourceRole.Server)
    serverSR.addGauge("fi")(3)
    assert(mem.schemas.get(Seq("fi")).get.role == SourceRole.Server)
    serverSR.stat("foe")
    assert(mem.schemas.get(Seq("foe")).get.role == SourceRole.Server)
  }

  test("RoleConfiguredSR equality") {
    val mem = new InMemoryStatsReceiver
    val clientA = RoleConfiguredStatsReceiver(mem, SourceRole.Client)
    val clientB = RoleConfiguredStatsReceiver(mem, SourceRole.Client)
    val serverA = RoleConfiguredStatsReceiver(mem, SourceRole.Server)
    val serverB = RoleConfiguredStatsReceiver(mem, SourceRole.Server)

    assert(clientA == clientB)
    assert(clientA != serverA)
    assert(clientA != serverB)
    assert(serverA == serverB)
  }
}

package com.twitter.finagle.stats

import org.junit.runner.RunWith
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class DelegatingStatsReceiverTest extends FunSuite with GeneratorDrivenPropertyChecks {

  class Ctx {
    var leafCounter = 0

    private[this] val inMemoryStatsReceiver: Gen[StatsReceiver] = Gen.delay {
      leafCounter += 1
      Gen.const(new InMemoryStatsReceiver())
    }

    private[this] def blacklistStatsReceiver(depth: Int): Gen[StatsReceiver] =
      if (depth > 3) inMemoryStatsReceiver
      else statsReceiverTopology(depth).map(new BlacklistStatsReceiver(_, { case _ => false }))

    private[this] def rollupStatsReceiver(depth: Int): Gen[StatsReceiver] =
      if (depth > 3) inMemoryStatsReceiver
      else statsReceiverTopology(depth).map(new RollupStatsReceiver(_))

    private[this] def proxyStatsReceiver(depth: Int): Gen[StatsReceiver] =
      if (depth > 3) inMemoryStatsReceiver
      else {
        statsReceiverTopology(depth).map { sr =>
          new StatsReceiverProxy {
            val self = sr
          }
        }
      }

    private[this] def scopedStatsReceiver(depth: Int): Gen[StatsReceiver] =
      if (depth > 3) inMemoryStatsReceiver
      else {
        for {
          sr <- statsReceiverTopology(depth)
          string <- Gen.alphaStr
        } yield sr.scope(string)
      }

    private[this] def broadcastStatsReceiver(depth: Int): Gen[StatsReceiver] =
      if (depth > 3) inMemoryStatsReceiver
      else {
        Gen
          .nonEmptyListOf(statsReceiverTopology(depth))
          .flatMap {
            case Nil => statsReceiverTopology(depth)
            case srs => Gen.const(BroadcastStatsReceiver(srs))
          }
      }

    private[this] def statsReceiverTopology(depth: Int): Gen[StatsReceiver] = {
      val seq = Seq(
        inMemoryStatsReceiver,
        Gen.lzy(blacklistStatsReceiver(depth + 1)),
        Gen.lzy(scopedStatsReceiver(depth + 1)),
        Gen.lzy(broadcastStatsReceiver(depth + 1)),
        Gen.lzy(proxyStatsReceiver(depth + 1)),
        Gen.lzy(rollupStatsReceiver(depth + 1))
      )
      Gen.oneOf(seq).flatMap(identity)
    }

    implicit val impl = Arbitrary(Gen.delay(statsReceiverTopology(0)))
  }

  test("DelegatingStatsReceiver.all collects effectively across many StatsReceivers") {
    val ctx = new Ctx
    import ctx._

    forAll { statsReceiver: StatsReceiver =>
      assert(DelegatingStatsReceiver.all(statsReceiver).size == leafCounter)
      leafCounter = 0
    }
  }
}

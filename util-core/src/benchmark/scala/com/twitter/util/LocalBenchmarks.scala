package com.twitter.util

import com.twitter.util.Local.Context
import java.util.concurrent.ThreadLocalRandom
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Fork(2)
class LocalBenchmarks {

  private[this] val key = new Local.Key

  @Benchmark
  def let(blackhole: Blackhole): Unit = {
    val randLong = ThreadLocalRandom.current().nextLong()
    val newContext = Context.empty.set(key, Some(randLong))

    Local.let(newContext) {
      blackhole.consume(randLong)
    }
  }

}

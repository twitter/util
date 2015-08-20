package com.twitter.hashing

import com.twitter.util.StdBenchAnnotations
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class HashableBench extends StdBenchAnnotations {

  private[this] var i = 0

  private[this] val inputs = IndexedSeq(
    "lololol",
    "572490576966230016",
    "Bacon andouille tenderloin cow ribeye, capicola pork loin tri-tip shank turducken porchetta",
    "Mumblecore Thundercats Austin, jean shorts cliche ennui Kickstarter migas Blue Bottle artisan",
    "flannel Wes Anderson single-origin coffee slow-carb freegan"
  ).map(_.getBytes("UTF-8"))


  @Benchmark
  def md5_leInt: Int = {
    i += 1
    Hashable.MD5_LEInt(inputs(i % inputs.length))
  }

}

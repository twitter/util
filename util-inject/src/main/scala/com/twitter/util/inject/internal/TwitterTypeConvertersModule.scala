package com.twitter.inject.internal

import com.twitter.inject.TwitterModule
import com.twitter.util.{Duration, StorageUnit, Time}
import java.io.File
import java.net.InetSocketAddress
import java.time.LocalTime

private[inject] object TwitterTypeConvertersModule extends TwitterModule {

  override def configure(): Unit = {
    // Single value type converters
    addFlagConverter[LocalTime]
    addFlagConverter[Time]
    addFlagConverter[Duration]
    addFlagConverter[StorageUnit]
    addFlagConverter[InetSocketAddress]
    addFlagConverter[File]

    // Multi-value type converters (comma-separated) for scala.Seq
    addFlagConverter[Seq[String]]
    addFlagConverter[Seq[Int]]
    addFlagConverter[Seq[Long]]
    addFlagConverter[Seq[Double]]
    addFlagConverter[Seq[Float]]
    addFlagConverter[Seq[LocalTime]]
    addFlagConverter[Seq[Time]]
    addFlagConverter[Seq[Duration]]
    addFlagConverter[Seq[StorageUnit]]
    addFlagConverter[Seq[InetSocketAddress]]
    addFlagConverter[Seq[File]]

    // Multi-value type converters (comma-separated) for java.util.List
    addFlagConverter[java.util.List[String]]
    addFlagConverter[java.util.List[Int]]
    addFlagConverter[java.util.List[Long]]
    addFlagConverter[java.util.List[Double]]
    addFlagConverter[java.util.List[Float]]
    addFlagConverter[java.util.List[LocalTime]]
    addFlagConverter[java.util.List[Time]]
    addFlagConverter[java.util.List[Duration]]
    addFlagConverter[java.util.List[StorageUnit]]
    addFlagConverter[java.util.List[InetSocketAddress]]
    addFlagConverter[java.util.List[File]]
  }
}

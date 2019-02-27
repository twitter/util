package com.twitter.jvm

import com.twitter.util.Time

case class Snapshot(timestamp: Time, heap: Heap, lastGcs: Seq[Gc])

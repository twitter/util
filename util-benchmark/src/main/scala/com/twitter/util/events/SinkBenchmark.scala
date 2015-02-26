package com.twitter.util.events

import com.google.caliper.SimpleBenchmark

class SinkBenchmark extends SimpleBenchmark {

  private[this] val sizedSink = SizedSink(10000)
  private[this] val eventType = Event.nullType

  private[this] def event(sink: Sink, reps: Int): Unit = {
    var i = 0
    while (i < reps) {
      sink.event(eventType, doubleVal = 2.5d)
      i += 1
    }
  }

  def timeEventSizedSink(reps: Int): Unit = {
    event(sizedSink, reps)
  }

}

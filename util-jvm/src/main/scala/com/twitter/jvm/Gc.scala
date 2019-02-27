package com.twitter.jvm

import com.twitter.util.{Duration, Time}

case class Gc(count: Long, name: String, timestamp: Time, duration: Duration)

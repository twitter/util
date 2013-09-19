package com.twitter.jvm

import com.twitter.app.GlobalFlag

object numProcs extends GlobalFlag(
  Runtime.getRuntime.availableProcessors().toDouble,
  "number of logical cores"
)

package com.twitter.util

import java.util.concurrent.{TimeoutException => JUCTimeoutException}

// Now that this is inherits from the usual TimeoutException, we can move to
// j.u.c.TimeoutException during our next API break.
class TimeoutException(message: String) extends JUCTimeoutException(message)

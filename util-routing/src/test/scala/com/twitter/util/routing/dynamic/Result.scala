package com.twitter.util.routing.dynamic

private[routing] sealed trait Result

private[routing] object Result {
  case object Success extends Result
  case object Failure extends Result
}

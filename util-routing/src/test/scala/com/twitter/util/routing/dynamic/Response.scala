package com.twitter.util.routing.dynamic

private[routing] sealed trait Response

private[routing] object Response {
  case object Success extends Response
  case object Failure extends Response
}

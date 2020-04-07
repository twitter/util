package com.twitter.util.routing.dynamic

private[routing] sealed trait Method

private[routing] object Method {
  case object Read extends Method
  case object Write extends Method
}

package com.twitter.util.routing

private object ValidationException {
  def errorMsg(failures: Iterable[ValidationError]): String = failures match {
    case head :: Nil => head.msg
    case Nil => throw new IllegalArgumentException("RouteValidationError failures cannot be empty")
    case failures => failures.map(_.msg).mkString(", ")
  }
}

/**
 * Exception thrown when a [[RouterBuilder]] observes any [[Route routes]] that are not valid for
 * the [[Router]] type it is building.
 */
case class ValidationException private[routing] (failures: Iterable[ValidationError])
    extends Exception(
      s"Route Validation Failed! Errors encountered: [${ValidationException.errorMsg(failures)}]")

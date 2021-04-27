package com.twitter.util.validation.internal

package object validators {

  private[validation] def mkString(value: Iterable[_]): String = {
    val trimmed = value.map(_.toString.trim).filter(_.nonEmpty)
    if (trimmed.nonEmpty && trimmed.size > 1) s"${trimmed.mkString("[", ", ", "]")}"
    else if (trimmed.nonEmpty) trimmed.head
    else "<empty>"
  }
}

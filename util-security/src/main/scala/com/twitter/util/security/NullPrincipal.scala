package com.twitter.util.security

import java.security.Principal

/**
 * Represents a non-existent entity.
 */
object NullPrincipal extends Principal {
  def getName: String = ""
}

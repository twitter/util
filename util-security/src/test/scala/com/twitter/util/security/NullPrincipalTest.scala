package com.twitter.util.security

import java.security.Principal
import org.scalatest.funsuite.AnyFunSuite

class NullPrincipalTest extends AnyFunSuite {

  test("NullPrincipal is a Principal") {
    val principal: Principal = NullPrincipal
    assert(principal != null)
  }

  test("NullPrincipal has no name") {
    assert(NullPrincipal.getName == "")
  }

}

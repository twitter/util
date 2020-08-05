package com.twitter.util.security

import javax.net.ssl.SSLSessionContext
import org.scalatest.funsuite.AnyFunSuite

class NullSslSessionContextTest extends AnyFunSuite {

  test("NullSslSessionContext is an SSLSessionContext") {
    val sslSessionContext: SSLSessionContext = NullSslSessionContext
    assert(sslSessionContext != null)
  }

  test("NullSslSessionContext session is NullSslSession") {
    assert(NullSslSessionContext.getSession(null) == NullSslSession)
    assert(NullSslSessionContext.getSession(Array()) == NullSslSession)
    assert(NullSslSessionContext.getSession(Array[Byte](1, 2, 3)) == NullSslSession)
  }

  test("NullSslSessionContext accessors return values") {
    assert(!NullSslSessionContext.getIds.hasMoreElements)
    assert(NullSslSessionContext.getSessionCacheSize == 0)
    assert(NullSslSessionContext.getSessionTimeout == 0)
  }

  test("NullSslSessionContext mutators don't blow up") {
    NullSslSessionContext.setSessionCacheSize(0)
    NullSslSessionContext.setSessionTimeout(0)
  }

}

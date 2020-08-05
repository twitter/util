package com.twitter.util.security

import javax.net.ssl.SSLSession
import org.scalatest.funsuite.AnyFunSuite

class NullSslSessionTest extends AnyFunSuite {

  test("NullSslSession is an SSLSession") {
    val sslSession: SSLSession = NullSslSession
    assert(sslSession != null)
  }

  test("NullSslSession principals are NullPrincipals") {
    assert(NullSslSession.getLocalPrincipal == NullPrincipal)
    assert(NullSslSession.getPeerPrincipal == NullPrincipal)
  }

  test("NullSslSession session context is NullSslSessionContext") {
    assert(NullSslSession.getSessionContext == NullSslSessionContext)
  }

  test("NullSslSession accessors return values") {
    assert(NullSslSession.getApplicationBufferSize == 0)
    assert(NullSslSession.getCipherSuite == "")
    assert(NullSslSession.getCreationTime == 0)
    assert(NullSslSession.getId.length == 0)
    assert(NullSslSession.getLastAccessedTime == 0)
    assert(NullSslSession.getLocalCertificates.length == 0)
    assert(NullSslSession.getPacketBufferSize == 0)
    assert(NullSslSession.getPeerCertificateChain.length == 0)
    assert(NullSslSession.getPeerCertificates.length == 0)
    assert(NullSslSession.getPeerHost == "")
    assert(NullSslSession.getPeerPort == 0)
    assert(NullSslSession.getProtocol == "")
    assert(NullSslSession.getValue(null) == "")
    assert(NullSslSession.getValueNames.length == 0)
    assert(!NullSslSession.isValid)
  }

  test("NullSslSession mutators don't blow up") {
    NullSslSession.invalidate()
    NullSslSession.putValue(null, null)
    NullSslSession.removeValue(null)
  }

}

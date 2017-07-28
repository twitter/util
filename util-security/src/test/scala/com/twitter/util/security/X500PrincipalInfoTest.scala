package com.twitter.util.security

import javax.security.auth.x500.X500Principal
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class X500PrincipalInfoTest extends FunSuite {

  test("Empty principal") {
    val principal = new X500Principal("")
    val info = new X500PrincipalInfo(principal)
    assert(info.countryName == None)
    assert(info.stateOrProvinceName == None)
    assert(info.localityName == None)
    assert(info.organizationName == None)
    assert(info.organizationalUnitName == None)
    assert(info.commonName == None)
  }

  test("Missing expected values") {
    val principal = new X500Principal("CN=localhost.twitter.com, O=Twitter")
    val info = new X500PrincipalInfo(principal)
    assert(info.countryName == None)
    assert(info.stateOrProvinceName == None)
    assert(info.localityName == None)
    assert(info.organizationName == Some("Twitter"))
    assert(info.organizationalUnitName == None)
    assert(info.commonName == Some("localhost.twitter.com"))
  }

  test("Keys with empty values") {
    val name = "CN=localhost.twitter.com, OU=, O=Twitter, L=San Francisco, ST=, C=US"
    val principal = new X500Principal(name)
    val info = new X500PrincipalInfo(principal)
    assert(info.countryName == Some("US"))
    assert(info.stateOrProvinceName == Some(""))
    assert(info.localityName == Some("San Francisco"))
    assert(info.organizationName == Some("Twitter"))
    assert(info.organizationalUnitName == Some(""))
    assert(info.commonName == Some("localhost.twitter.com"))
  }

  test("RFC 1779 style") {
    val name =
      "EMAILADDRESS=ryano@twitter.com, CN=localhost.twitter.com, OU=Core Systems Libraries, O=Twitter, L=San Francisco, ST=California, C=US"
    val principal = new X500Principal(name)
    val info = new X500PrincipalInfo(principal)
    assert(info.countryName == Some("US"))
    assert(info.stateOrProvinceName == Some("California"))
    assert(info.localityName == Some("San Francisco"))
    assert(info.organizationName == Some("Twitter"))
    assert(info.organizationalUnitName == Some("Core Systems Libraries"))
    assert(info.commonName == Some("localhost.twitter.com"))
  }

}

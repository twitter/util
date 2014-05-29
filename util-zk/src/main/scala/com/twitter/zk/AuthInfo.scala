package com.twitter.zk

import org.apache.zookeeper.server.auth.DigestAuthenticationProvider

case class AuthInfo(mode: String, data: Array[Byte])

object AuthInfo {
  def digest(user: String, secret: String) = {
    val authString = "%s:%s".format(user, secret).getBytes("UTF-8")//DigestAuthenticationProvider.generateDigest("%s:%s".format(user, secret)).getBytes("UTF-8")
    AuthInfo("digest", authString)
  }
}

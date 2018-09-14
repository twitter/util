package com.twitter.util.security

import java.util.Collections
import javax.net.ssl.{SSLSession, SSLSessionContext}

/**
 * Represents a non-existent set of `SSLSession`s associated with a
 * non-existent entity.
 */
object NullSslSessionContext extends SSLSessionContext {
  def getIds: java.util.Enumeration[Array[Byte]] = Collections.emptyEnumeration[Array[Byte]]
  def getSession(name: Array[Byte]): SSLSession = NullSslSession
  def getSessionCacheSize: Int = 0
  def getSessionTimeout: Int = 0
  def setSessionCacheSize(size: Int): Unit = {}
  def setSessionTimeout(seconds: Int): Unit = {}
}

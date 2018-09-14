package com.twitter.util.security

import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.{SSLSession, SSLSessionContext}
import javax.security.cert.X509Certificate

/**
 * Represents a non-existent secure relationship between two entities, i.e.
 * a non-existent `SSLSession`.
 */
object NullSslSession extends SSLSession {
  def getApplicationBufferSize: Int = 0
  def getCipherSuite: String = ""
  def getCreationTime: Long = 0
  def getId: Array[Byte] = Array.empty
  def getLastAccessedTime: Long = 0
  def getLocalCertificates: Array[Certificate] = Array.empty
  def getLocalPrincipal: Principal = NullPrincipal
  def getPacketBufferSize: Int = 0
  def getPeerCertificateChain: Array[X509Certificate] = Array.empty
  def getPeerCertificates: Array[Certificate] = Array.empty
  def getPeerHost: String = ""
  def getPeerPort: Int = 0
  def getPeerPrincipal: Principal = NullPrincipal
  def getProtocol: String = ""
  def getSessionContext: SSLSessionContext = NullSslSessionContext
  def getValue(name: String): Object = ""
  def getValueNames: Array[String] = Array.empty
  def invalidate: Unit = {}
  def isValid: Boolean = false
  def putValue(name: String, value: Object): Unit = {}
  def removeValue(name: String): Unit = {}
}

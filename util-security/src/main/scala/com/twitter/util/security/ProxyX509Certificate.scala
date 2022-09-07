package com.twitter.util.security

import java.security.cert.X509Certificate

/**
 * X509Certificate that transparently proxies everything
 */
class ProxyX509Certificate(underlying: X509Certificate) extends X509Certificate {
  def getEncoded(): Array[Byte] = underlying.getEncoded()
  def getPublicKey(): java.security.PublicKey = underlying.getPublicKey()
  def verify(key: java.security.PublicKey, sigProvider: String): Unit =
    underlying.verify(key, sigProvider)
  def verify(key: java.security.PublicKey): Unit = underlying.verify(key)
  def checkValidity(date: java.util.Date): Unit = underlying.checkValidity(date)
  def checkValidity(): Unit = underlying.checkValidity()
  def getBasicConstraints(): Int = underlying.getBasicConstraints()
  def getIssuerDN(): java.security.Principal = underlying.getIssuerDN()
  def getIssuerUniqueID(): Array[Boolean] = underlying.getIssuerUniqueID()
  def getKeyUsage(): Array[Boolean] = underlying.getKeyUsage()
  def getNotAfter(): java.util.Date = underlying.getNotAfter()
  def getNotBefore(): java.util.Date = underlying.getNotBefore()
  def getSerialNumber(): java.math.BigInteger = underlying.getSerialNumber()
  def getSigAlgName(): String = underlying.getSigAlgName()
  def getSigAlgOID(): String = underlying.getSigAlgOID()
  def getSigAlgParams(): Array[Byte] = underlying.getSigAlgParams()
  def getSignature(): Array[Byte] = underlying.getSignature()
  def getSubjectDN(): java.security.Principal = underlying.getSubjectDN()
  def getSubjectUniqueID(): Array[Boolean] = underlying.getSubjectUniqueID()
  def getTBSCertificate(): Array[Byte] = underlying.getTBSCertificate()
  def getVersion(): Int = underlying.getVersion()

  def getCriticalExtensionOIDs(): java.util.Set[String] = underlying.getCriticalExtensionOIDs()
  def getExtensionValue(x: String): Array[Byte] = underlying.getExtensionValue(x)
  def getNonCriticalExtensionOIDs(): java.util.Set[String] =
    underlying.getNonCriticalExtensionOIDs()
  def hasUnsupportedCriticalExtension(): Boolean = underlying.hasUnsupportedCriticalExtension()

  override def toString: String = underlying.toString
}

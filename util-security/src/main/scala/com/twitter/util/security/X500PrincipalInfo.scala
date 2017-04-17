package com.twitter.util.security

import javax.naming.ldap.{LdapName, Rdn}
import javax.security.auth.x500.X500Principal
import com.twitter.util.security.X500PrincipalInfo._
import scala.collection.JavaConverters._

/**
 * Parses an [[javax.security.auth.x500.X500Principal X500Principal]] into
 * its individual pieces for easily extracting widely used items like an X.509
 * certificate's Common Name.
 */
class X500PrincipalInfo(principal: X500Principal) {

  private[this] val items = parseNameToMap(principal.getName())

  def countryName: Option[String] = items.get("C")
  def stateOrProvinceName: Option[String] = items.get("ST")
  def localityName: Option[String] = items.get("L")
  def organizationName: Option[String] = items.get("O")
  def organizationalUnitName: Option[String] = items.get("OU")
  def commonName: Option[String] = items.get("CN")

}

private object X500PrincipalInfo {

  /**
   * This works for simple cases only at the moment. It does not
   * translate OIDs or ASN.1 types (e.g. email address).
   */
  private def parseNameToMap(name: String): Map[String, String] = {
    val ldapName: LdapName = new LdapName(name)
    val rdns: List[Rdn] = ldapName.getRdns().asScala.toList
    rdns.map(item => (item.getType, item.getValue.toString)).toMap
  }

}

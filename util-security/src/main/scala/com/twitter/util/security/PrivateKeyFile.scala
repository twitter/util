package com.twitter.util.security

import com.twitter.logging.Logger
import com.twitter.util.{Throw, Try}
import com.twitter.util.security.PrivateKeyFile._
import java.io.File
import java.security.{KeyFactory, PrivateKey}
import java.security.spec.{InvalidKeySpecException, PKCS8EncodedKeySpec}

/**
 * A representation of a PrivateKey that is stored in a file
 * in Pkcs8 PEM format.
 *
 * @example
 * -----BEGIN PRIVATE KEY-----
 * base64encodedbytes
 * -----END PRIVATE KEY-----
 */
class PrivateKeyFile(file: File) {

  private[this] def logException(ex: Throwable): Unit =
    log.warning(s"PrivateKeyFile (${file.getName()}) failed to load: ${ex.getMessage()}.")

  /**
   * Attempts to convert a `PKCS8EncodedKeySpec` to a `PrivateKey`.
   *
   * @note
   * keeps identical behavior to netty
   * https://github.com/netty/netty/blob/netty-4.1.11.Final/handler/src/main/java/io/netty/handler/ssl/SslContext.java#L1006
   */
  private[this] def keySpecToPrivateKey(keySpec: PKCS8EncodedKeySpec): Try[PrivateKey] =
    Try {
      KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }.handle {
        case _: InvalidKeySpecException =>
          KeyFactory.getInstance("DSA").generatePrivate(keySpec)
      }
      .handle {
        case _: InvalidKeySpecException =>
          KeyFactory.getInstance("EC").generatePrivate(keySpec)
      }
      .rescue {
        case ex: InvalidKeySpecException =>
          Throw(new InvalidKeySpecException("None of RSA, DSA, EC worked", ex))
      }

  /**
   * Attemps to read the contents of an unencrypted PrivateKey file.
   *
   * @note Throws `InvalidPemFormatException` if the file is not a PEM format file.
   *
   * @note Throws `InvalidKeySpecException` if the file cannot be loaded as an
   * RSA, DSA, or EC PrivateKey.
   *
   * @note This method will attempt to load the key first as an RSA key, then
   * as a DSA key, and then as an EC (Elliptic Curve) key.
   */
  def readPrivateKey(): Try[PrivateKey] =
    new Pkcs8EncodedKeySpecFile(file)
      .readPkcs8EncodedKeySpec()
      .flatMap(keySpecToPrivateKey)
      .onFailure(logException)

}

private object PrivateKeyFile {
  private val log = Logger.get("com.twitter.util.security")
}

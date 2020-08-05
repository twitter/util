package com.twitter.util.security

import com.twitter.io.TempFile
import java.io.File
import javax.net.ssl.{KeyManager, X509KeyManager}
import org.scalatest.funsuite.AnyFunSuite

class PKCS8KeyManagerFactoryTest extends AnyFunSuite {

  private[this] def assertContainsCertsAndKey(keyManager: KeyManager, numCerts: Int = 1): Unit = {
    assert(keyManager.isInstanceOf[X509KeyManager])
    val x509km = keyManager.asInstanceOf[X509KeyManager]

    val clientAliases = x509km.getClientAliases("RSA", null)
    assert(clientAliases.length == 1)
    val serverAliases = x509km.getServerAliases("RSA", null)
    assert(serverAliases.length == 1)
    assert(clientAliases.head == serverAliases.head)
    val alias = clientAliases.head

    val certChain = x509km.getCertificateChain(alias)
    assert(certChain.length == numCerts)
    val cert = certChain.head
    assert(cert.getSubjectX500Principal.getName().contains("localhost.twitter.com"))

    val privateKey = x509km.getPrivateKey(alias)
    assert(privateKey.getAlgorithm == "RSA")
    assert(privateKey.getFormat == "PKCS#8")
  }

  test("Both files are bogus") {
    val tempCertFile = File.createTempFile("test", "crt")
    tempCertFile.deleteOnExit()

    val tempKeyFile = File.createTempFile("test", "key")
    tempKeyFile.deleteOnExit()

    val factory = new Pkcs8KeyManagerFactory(tempCertFile, tempKeyFile)
    val tryKeyManagers = factory.getKeyManagers()

    assert(tryKeyManagers.isThrow)
  }

  test("Cert file is good") {
    val tempCertFile = TempFile.fromResourcePath("/certs/test-rsa.crt")
    // deleteOnExit is handled by TempFile

    val tempKeyFile = File.createTempFile("test", "key")
    tempKeyFile.deleteOnExit()

    val factory = new Pkcs8KeyManagerFactory(tempCertFile, tempKeyFile)
    val tryKeyManagers = factory.getKeyManagers()

    assert(tryKeyManagers.isThrow)
  }

  test("Key file is good") {
    val tempCertFile = File.createTempFile("test", "crt")
    tempCertFile.deleteOnExit()

    val tempKeyFile = TempFile.fromResourcePath("/keys/test-pkcs8.key")
    // deleteOnExit is handled by TempFile

    val factory = new Pkcs8KeyManagerFactory(tempCertFile, tempKeyFile)
    val tryKeyManagers = factory.getKeyManagers()

    assert(tryKeyManagers.isThrow)
  }

  test("Both files are good") {
    val tempCertFile = TempFile.fromResourcePath("/certs/test-rsa.crt")
    // deleteOnExit is handled by TempFile

    val tempKeyFile = TempFile.fromResourcePath("/keys/test-pkcs8.key")
    // deleteOnExit is handled by TempFile

    val factory = new Pkcs8KeyManagerFactory(tempCertFile, tempKeyFile)
    val tryKeyManagers = factory.getKeyManagers()

    assert(tryKeyManagers.isReturn)
    val keyManagers = tryKeyManagers.get()
    assert(keyManagers.length == 1)
    assertContainsCertsAndKey(keyManagers.head)
  }

  test("Chain of certificates is good") {
    val tempCertsFile = TempFile.fromResourcePath("/certs/test-rsa-full-cert-chain.crt")
    // deleteOnExit is handled by TempFile

    val tempKeyFile = TempFile.fromResourcePath("/keys/test-pkcs8.key")
    // deleteOnExit is handled by TempFile

    val factory = new Pkcs8KeyManagerFactory(tempCertsFile, tempKeyFile)
    val tryKeyManagers = factory.getKeyManagers()

    assert(tryKeyManagers.isReturn)
    val keyManagers = tryKeyManagers.get()
    assert(keyManagers.length == 1)
    assertContainsCertsAndKey(keyManagers.head, numCerts = 3)
  }

}

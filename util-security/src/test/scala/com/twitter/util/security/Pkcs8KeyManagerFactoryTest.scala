package com.twitter.util.security

import com.twitter.io.TempFile
import java.io.File
import javax.net.ssl.{KeyManager, X509KeyManager}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PKCS8KeyManagerFactoryTest extends FunSuite {

  private[this] def assertContainsCertAndKey(keyManager: KeyManager): Unit = {
    assert(keyManager.isInstanceOf[X509KeyManager])
    val x509km = keyManager.asInstanceOf[X509KeyManager]

    val clientAliases = x509km.getClientAliases("RSA", null)
    assert(clientAliases.length == 1)
    val serverAliases = x509km.getServerAliases("RSA", null)
    assert(serverAliases.length == 1)
    assert(clientAliases.head == serverAliases.head)
    val alias = clientAliases.head

    val certChain = x509km.getCertificateChain(alias)
    assert(certChain.length == 1)
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
    assertContainsCertAndKey(keyManagers.head)
  }

}

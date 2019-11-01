package ch.aducommun.gelf

import java.security.KeyStore


object GELFCryptography {
  def loadKeyStore(is: java.io.InputStream, password: String): KeyStore = {
    val ks = KeyStore.getInstance("JKS")

    ks.load(is, password.toArray)
    ks
  }
}

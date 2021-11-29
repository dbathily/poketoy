package com.example.domain

import org.apache.commons.codec.binary.Base64.encodeBase64

import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Utils {

  def getHash(value: String, key: String, token: String): String = {
    // creates signature hash from token & key
    val keyString = URLEncoder.encode(key, "UTF-8") + "&" + URLEncoder.encode(token, "UTF-8")
    val keyBytes = keyString.getBytes
    val signingKey = new SecretKeySpec(keyBytes, "HmacSHA1")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(signingKey)
    val rawHmac = mac.doFinal(value.getBytes)
    new String(encodeBase64(rawHmac))
  }
}

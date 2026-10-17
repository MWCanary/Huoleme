package com.example

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    val pass = "50909800"
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(pass.toByteArray(Charsets.UTF_8))
    val hexString = hash.joinToString("") { String.format("%02x", it) }
    System.out.println("--- HASH IS: " + hexString + " ---")
    assertEquals(4, 2 + 2)
  }
}

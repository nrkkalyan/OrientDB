/*
 * Copyright 1999-2010 Luca Garulli (l.garulli--at--orientechnologies.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.core.security;

import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.exception.OConfigurationException;
import com.orientechnologies.orient.core.exception.OSecurityAccessException;
import com.orientechnologies.orient.core.exception.OSecurityException;

public class OSecurityManager {

  private static final OSecurityManager instance = new OSecurityManager();

  private MessageDigest                 md;

  public OSecurityManager() {
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      OLogManager.instance().error(this, "Can't use IntegrityFileManager", e);
    }
  }

  public boolean check(byte[] iInput1, byte[] iInput2) {
    return MessageDigest.isEqual(iInput1, iInput2);
  }

  public boolean check(String iInput1, byte[] iInput2) {
    return MessageDigest.isEqual(digest(iInput1), iInput2);
  }

  public boolean check(String iInput1, String iInput2) {
    return digest2String(iInput1).equals(iInput2);
  }

  public String digest2String(String iInput) {
    return byteArrayToHexStr(digest(iInput));
  }

  public synchronized byte[] digest(String iInput) {
    try {
      return md.digest(iInput.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      OLogManager.instance().error(this, "The request encoding is not supported: can't execute secutity checks", e,
          OConfigurationException.class);
    }
    return null;
  }

  public synchronized byte[] digest(byte[] iInput) {
    return md.digest(iInput);
  }

  public SecretKey generateKey(final String iAlgorithm, final int iKeySize) {
    KeyGenerator kg;
    try {
      kg = KeyGenerator.getInstance(iAlgorithm);
      kg.init(iKeySize);
      return kg.generateKey();
    } catch (Exception e) {
      throw new OSecurityException("Error on generating key for algorithm: " + iAlgorithm, e);
    }
  }

  public SecretKey createKey(final String iAlgorithm, final byte[] iKey) throws OSecurityAccessException {
    return new SecretKeySpec(iKey, iAlgorithm);
  }

  public byte[] encrypt(final String iAlgorithm, final Key iKey, final byte[] iData) throws OSecurityAccessException {
    Cipher c;
    try {
      c = Cipher.getInstance(iAlgorithm);
      c.init(Cipher.ENCRYPT_MODE, iKey);
      return c.doFinal(iData);
    } catch (Exception e) {
      throw new OSecurityException("Error on encrypting data", e);
    }
  }

  public byte[] decrypt(final String iAlgorithm, final Key iKey, final byte[] iData) throws OSecurityAccessException {
    Cipher c;
    try {
      c = Cipher.getInstance(iAlgorithm);
      c.init(Cipher.DECRYPT_MODE, iKey);
      return c.doFinal(iData);
    } catch (Exception e) {
      throw new OSecurityException("Error on decrypting data", e);
    }
  }

  public static OSecurityManager instance() {
    return instance;
  }

  private static String byteArrayToHexStr(byte[] data) {
    char[] chars = new char[data.length * 2];
    for (int i = 0; i < data.length; i++) {
      byte current = data[i];
      int hi = (current & 0xF0) >> 4;
      int lo = current & 0x0F;
      chars[2 * i] = (char) (hi < 10 ? ('0' + hi) : ('A' + hi - 10));
      chars[2 * i + 1] = (char) (lo < 10 ? ('0' + lo) : ('A' + lo - 10));
    }
    return new String(chars);
  }
}

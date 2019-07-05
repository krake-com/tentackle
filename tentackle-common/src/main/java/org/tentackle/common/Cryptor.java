/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.common;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Function;

/**
 * A simple en- and decryptor.<br>
 * Each application should provide a concrete instance with a confidential salt and passphase
 * and a no-arg constructor. If provided, it is used to encrypt passwords in memory, transmission during client/server login,
 * or decrypt passwords stored in backend.properties for database connections.
 * <br>
 * Example:
 * <pre>
 *    &#64;Service(Cryptor.class)
 *    public class MyCryptor extends Cryptor {
 *
 *      public MyCryptor() {
 *        ...
 *      }
 *    }
 * </pre>
 *
 * Cryptor also implements a {@link Function}{@code <String,String>} to encrypt strings like passwords to base64
 * and thus can directly be used by the tentackle-maven-plugin to generate properties for filtered resources.
 * <p>
 * IMPORTANT: at least the passphrase should *NOT* be part of the application! If an attacker gets access to the
 * jars, it's a no-brainer to write a small java program that happily decrypts all your data. Instead, provide the
 * passphrase via some external media, a mounted USB-stick, manual input, PGP keyring, whatever.<br>
 * For a secure client/server communication, it's better to use TLS/SSL anyway.
 */
public class Cryptor implements Function<String, String> {

  private static boolean cryptorLoaded;
  private static Cryptor cryptor;

  /**
   * Gets the optional application specific cryptor singleton.
   *
   * @return the cryptor, null if no {@code @Service(Cryptor.class)} configured
   */
  public static synchronized Cryptor getInstance() {
    if (!cryptorLoaded) {
      cryptorLoaded = true;
      try {
        cryptor = ServiceFactory.createService(Cryptor.class);
      }
      catch (TentackleRuntimeException rx) {
        // no application specific cryptor configured! That's ok, although not recommended.
      }
    }
    return cryptor;
  }


  private final SecretKey key;
  private final Cipher cipher;


  /**
   * Creates a cryptor.<br>
   * Notice that salt and passphrase will be scratched for security reasons.
   *
   * @param salt the salt
   * @param passphrase the passphrase
   * @param iterations number of iterations for key generation
   * @param keyStrength the key strength
   */
  public Cryptor(byte[] salt, char[] passphrase, int iterations, int keyStrength) {
    SecretKeyFactory factory = getSecretKeyFactory();
    KeySpec spec = new PBEKeySpec(passphrase, salt, iterations, keyStrength);

    SecretKey tmpKey;
    try {
      tmpKey = factory.generateSecret(spec);
    }
    catch (InvalidKeySpecException e1) {
      throw new TentackleRuntimeException("generating temporary key failed", e1);
    }

    StringHelper.blank(passphrase);
    Arrays.fill(salt, (byte) 0);

    key = createSecretKeySpec(tmpKey.getEncoded());
    cipher = getCipher();
  }

  /**
   * Creates a cryptor with 1024 iterations and a key strength of 256.<br>
   * Notice that salt and passphrase will be scratched for security reasons.
   *
   * @param salt the salt
   * @param passphrase the passphrase
   */
  public Cryptor(byte[] salt, char[] passphrase) {
    this(salt, passphrase, 1024, 256);
  }

  /**
   * Creates a cryptor with 1024 iterations and a key strength of 256.<br>
   * This is just a convenience method. Consider using {@link Cryptor#Cryptor(byte[], char[])} instead.
   *
   * @param salt the salt
   * @param passphrase the passphrase
   */
  public Cryptor(String salt, String passphrase) {
    this(salt.getBytes(Settings.encodingCharset), passphrase.toCharArray());
  }



  /**
   * Encrypts the data.
   *
   * @param data the byte array to encrypt
   * @return the encrypted byte array
   */
  public byte[] encrypt(byte[] data) {
    try {
      cipher.init(Cipher.ENCRYPT_MODE, key);
      return cipher.doFinal(data);
    }
    catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
      throw new TentackleRuntimeException("encryption failed", e);
    }
  }

  /**
   * Encrypts the data.
   *
   * @param data the byte array to encrypt
   * @param offset the offset in data
   * @param length the number of bytes
   * @return the encrypted byte array
   */
  public byte[] encrypt(byte[] data, int offset, int length) {
    try {
      cipher.init(Cipher.ENCRYPT_MODE, key);
      return cipher.doFinal(data, offset, length);
    }
    catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
      throw new TentackleRuntimeException("encryption failed", e);
    }
  }

  /**
   * Decrypts the data.
   *
   * @param encryptedData the encrypted byte array
   * @return the decrypted data
   */
  public byte[] decrypt(byte[] encryptedData) {
    try {
      cipher.init(Cipher.DECRYPT_MODE, key);
      return cipher.doFinal(encryptedData);
    }
    catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
      throw new TentackleRuntimeException("decryption failed", e);
    }
  }

  /**
   * Encrypts data to base64 encoding.
   *
   * @param data the byte array to encrypt
   * @return the encrypted string in base64 encoding
   */
  public String encrypt64(byte[] data) {
    return Base64.getEncoder().encodeToString(encrypt(data));
  }

  /**
   * Encrypts a char array to base64 encoding.<br>
   * The method clears all traces in memory, including the passed char array.
   *
   * @param chars the char array to encrypt
   * @return the encrypted string in base64 encoding
   */
  public String encrypt64(char[] chars) {
    CharBuffer charBuffer = CharBuffer.wrap(chars);
    ByteBuffer byteBuffer = Settings.encodingCharset.encode(charBuffer);
    byte[] data = byteBuffer.array();
    String base64 = Base64.getEncoder().encodeToString(encrypt(data, 0, byteBuffer.remaining()));
    Arrays.fill(charBuffer.array(), ' ');
    Arrays.fill(data, (byte) 0);
    return base64;
  }

  /**
   * Encrypts a string to base64 encoding.
   *
   * @param text the text to encrypt
   * @return the encrypted string in base64 encoding
   */
  public String encrypt64(String text) {
    return encrypt64(text.getBytes(Settings.encodingCharset));
  }


  /**
   * Decrypts a base64 encoded string.
   *
   * @param encryptedText the encrypted text in base64 encoding
   * @return the decrypted data
   */
  public byte[] decrypt64ToBytes(String encryptedText) {
    return decrypt(Base64.getDecoder().decode(encryptedText.getBytes(Settings.encodingCharset)));
  }

  /**
   * Decrypts a base64 encoded string.<br>
   * The method clears all traces in memory.
   *
   * @param encryptedText the encrypted text in base64 encoding
   * @return the decrypted data
   */
  public char[] decrypt64ToChars(String encryptedText) {
    byte[] data = decrypt64ToBytes(encryptedText);
    ByteBuffer byteBuffer = ByteBuffer.wrap(data);
    CharBuffer charBuffer = Settings.encodingCharset.decode(byteBuffer);
    char[] chars = new char[charBuffer.remaining()];
    charBuffer.get(chars);
    Arrays.fill(data, (byte) 0);
    Arrays.fill(byteBuffer.array(), (byte) 0);
    Arrays.fill(charBuffer.array(), ' ');
    return chars;
  }

  /**
   * Decrypts a base64 encoded string.
   *
   * @param encryptedText the encrypted text in base64 encoding
   * @return the decrypted text
   */
  public String decrypt64(String encryptedText) {
    return new String(decrypt64ToBytes(encryptedText), Settings.encodingCharset);
  }



  /**
   * Encrypts a string.<br>
   * Provided for the tentackle-maven-plugin.
   *
   * @param s the string
   * @return the encrypted string in base64 encoding
   */
  @Override
  public String apply(String s) {
    return encrypt64(s);
  }




  /**
   * Gets the key factory.<br>
   * The default implementation returns an instance of {@code PBKDF2WithHmacSHA1}.
   *
   * @return the factory
   */
  protected SecretKeyFactory getSecretKeyFactory() {
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
    }
    catch (NoSuchAlgorithmException e) {
      throw new TentackleRuntimeException("cannot load key factory", e);
    }
  }

  /**
   * Creates the key spec.<br>
   * The default implementation returns an {@code AES} spec.
   *
   * @param key the key
   * @return the spec
   */
  protected SecretKeySpec createSecretKeySpec(byte[] key) {
    return new SecretKeySpec(key, "AES");
  }

  /**
   * Gets the cipher instance.<br>
   * The default implementation returns an {@code AES} cipher.
   *
   * @return the cipher
   */
  protected Cipher getCipher() {
    try {
      return Cipher.getInstance("AES");
    }
    catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
      throw new TentackleRuntimeException("creating cipher failed", e);
    }
  }

}

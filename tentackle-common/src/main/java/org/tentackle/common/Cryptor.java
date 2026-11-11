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
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.function.Function;

interface CryptorHolder {

  private static Cryptor loadCryptor() {
    try {
      return ServiceFactory.createService(Cryptor.class);
    }
    catch (TentackleRuntimeException rx) {
      // No application-specific cryptor configured! That's ok, although not recommended.
      // Notice: this also applies if the cryptor is configured, but its constructor failed.
      return null;
    }
  }

  Cryptor INSTANCE = loadCryptor();
}


/**
 * A simple en- and decryptor.<br>
 * Each application should provide a concrete subclass with a confidential salt and passphrase
 * and a no-arg constructor. If provided, it is used to encrypt passwords in memory and during transmission
 * at client/server login, or to decrypt passwords stored in backend.properties for database connections.
 * <br>
 * Example:
 * <pre>
 *    &#64;Service(Cryptor.class)
 *    public class MyCryptor extends Cryptor {
 *
 *      public MyCryptor() {
 *        super(SALT, PASSPHRASE);   // whatever
 *      }
 *    }
 * </pre>
 * Notice that if the constructor of the application's cryptor throws a {@link TentackleRuntimeException},
 * {@link #getInstance()} silently returns null, as if no cryptor were configured.
 * <p>
 * Texts, salts, and passphrases are converted to bytes via {@link Settings#getEncodingCharset()}.
 * <p>
 * Cryptor also implements a {@link Function}{@code <String,String>} to encrypt strings like passwords to base64
 * and thus can directly be used by the tentackle-maven-plugin to generate properties for filtered resources.
 * <p>
 * Notice: the security of symmetric encryption algorithms in general depends on the confidentiality of the passphrase. Thus, the passphrase
 * should ideally not be part of the application, but provided via some external media, a mounted USB-stick, manual input, PGP keyring, whatever.
 * However, in practice this isn't always doable.
 * <p>
 * Cryptors are thread-safe. Since {@link Cipher} is stateful and <em>not</em> thread-safe, each thread gets
 * its own cipher, created lazily via {@link #getCipher()} (or, for {@link #Cryptor(SecretKey, Cipher)}, from the
 * given template cipher). This is essential because the cryptor is usually a singleton shared by all threads,
 * for example, by the encrypted transports of a server.
 */
public class Cryptor implements Function<String, String> {

  /**
   * Gets the optional application-specific cryptor singleton.
   *
   * @return the cryptor, null if no {@code @Service(Cryptor.class)} configured
   */
  public static Cryptor getInstance() {
    return CryptorHolder.INSTANCE;
  }

  /**
   * Gets the application-specific cryptor singleton.
   *
   * @return the cryptor, never null
   * @throws TentackleRuntimeException if no cryptor defined
   */
  public static Cryptor getInstanceSafely() {
    Cryptor cryptor = getInstance();
    if (cryptor == null) {
      throw new TentackleRuntimeException("missing application-specific Cryptor");
    }
    return cryptor;
  }


  private final SecretKey key;

  /**
   * The ciphers, one per thread.<br>
   * {@link Cipher} is stateful and not thread-safe: sharing one instance would make concurrent
   * en-/decryptions corrupt each other, silently returning wrong data.
   */
  private final ThreadLocal<Cipher> ciphers;


  /**
   * Creates a cryptor.<br>
   * Notice that the given cipher serves as the template for the per-thread ciphers,
   * i.e., its algorithm and provider are used to create them. {@link #getCipher()} is not used.
   *
   * @param key the secret key
   * @param cipher the cipher
   */
  public Cryptor(SecretKey key, Cipher cipher) {
    this.key = key;
    Objects.requireNonNull(cipher, "cipher");
    ciphers = ThreadLocal.withInitial(() -> createCipher(cipher));
  }

  /**
   * Creates a cryptor.<br>
   * Notice that salt and passphrase will be scratched for security reasons.
   *
   * @param salt the salt
   * @param passphrase the passphrase
   * @param iterations number of iterations for key generation
   * @param keyStrength the key strength
   * @throws TentackleRuntimeException if the key could not be generated
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
    ciphers = ThreadLocal.withInitial(this::getCipher);
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
   * This is just a convenience constructor. Since strings are immutable, salt and passphrase
   * cannot be scratched. Consider using {@link Cryptor#Cryptor(byte[], char[])} instead.
   *
   * @param salt the salt
   * @param passphrase the passphrase
   */
  public Cryptor(String salt, String passphrase) {
    this(salt.getBytes(Settings.getEncodingCharset()), passphrase.toCharArray());
  }



  /**
   * Encrypts the data.
   *
   * @param data the byte array to encrypt
   * @return the encrypted byte array
   * @throws TentackleRuntimeException if encryption failed
   */
  public byte[] encrypt(byte[] data) {
    try {
      Cipher cipher = ciphers.get();
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
   * @throws TentackleRuntimeException if encryption failed
   */
  public byte[] encrypt(byte[] data, int offset, int length) {
    try {
      Cipher cipher = ciphers.get();
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
   * @param offset        the offset in the encrypted byte array
   * @param length        the number of bytes to decrypt
   * @return the decrypted data
   * @throws TentackleRuntimeException if decryption failed
   */
  public byte[] decrypt(byte[] encryptedData, int offset, int length) {
    try {
      Cipher cipher = ciphers.get();
      cipher.init(Cipher.DECRYPT_MODE, key);
      return cipher.doFinal(encryptedData, offset, length);
    }
    catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
      throw new TentackleRuntimeException("decryption failed", e);
    }
  }

  /**
   * Decrypts the data.
   *
   * @param encryptedData the encrypted byte array
   * @return the decrypted data
   * @throws TentackleRuntimeException if decryption failed
   */
  public byte[] decrypt(byte[] encryptedData) {
    try {
      Cipher cipher = ciphers.get();
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
   * @throws TentackleRuntimeException if encryption failed
   */
  public String encrypt64(byte[] data) {
    return Base64.getEncoder().encodeToString(encrypt(data));
  }

  /**
   * Encrypts a char array.<br>
   * The method clears all traces in memory, including the passed char array.
   *
   * @param chars the char array to encrypt
   * @return the encrypted bytes
   * @throws TentackleRuntimeException if encryption failed
   */
  public byte[] encrypt(char[] chars) {
    CharBuffer charBuffer = CharBuffer.wrap(chars);
    ByteBuffer byteBuffer = Settings.getEncodingCharset().encode(charBuffer);
    byte[] data = byteBuffer.array();
    byte[] encryptedData = encrypt(data, 0, byteBuffer.remaining());
    Arrays.fill(charBuffer.array(), ' ');
    Arrays.fill(data, (byte) 0);
    return encryptedData;
  }

  /**
   * Encrypts a char array to base64 encoding.<br>
   * The method clears all traces in memory, including the passed char array.
   *
   * @param chars the char array to encrypt
   * @return the encrypted string in base64 encoding
   * @throws TentackleRuntimeException if encryption failed
   */
  public String encrypt64(char[] chars) {
    return Base64.getEncoder().encodeToString(encrypt(chars));
  }

  /**
   * Encrypts a string to base64 encoding.
   *
   * @param text the text to encrypt
   * @return the encrypted string in base64 encoding
   * @throws TentackleRuntimeException if encryption failed
   */
  public String encrypt64(String text) {
    return encrypt64(text.getBytes(Settings.getEncodingCharset()));
  }


  /**
   * Decrypts encrypted data to chars.
   *
   * @param encryptedData the encrypted data
   * @return the char array
   * @throws TentackleRuntimeException if decryption failed
   */
  public char[] decryptToChars(byte[] encryptedData) {
    return toChars( decrypt(encryptedData));
  }

  /**
   * Decrypts a base64 encoded string.
   *
   * @param encryptedText the encrypted text in base64 encoding
   * @return the decrypted data
   * @throws TentackleRuntimeException if decryption failed
   */
  public byte[] decrypt64ToBytes(String encryptedText) {
    return decrypt(Base64.getDecoder().decode(encryptedText.getBytes(Settings.getEncodingCharset())));
  }

  /**
   * Decrypts a base64 encoded string.<br>
   * The method clears all traces in memory.
   *
   * @param encryptedText the encrypted text in base64 encoding
   * @return the decrypted data
   * @throws TentackleRuntimeException if decryption failed
   */
  public char[] decrypt64ToChars(String encryptedText) {
    return toChars(decrypt64ToBytes(encryptedText));
  }

  /**
   * Decrypts a base64 encoded string.
   *
   * @param encryptedText the encrypted text in base64 encoding
   * @return the decrypted text
   * @throws TentackleRuntimeException if decryption failed
   */
  public String decrypt64(String encryptedText) {
    return new String(decrypt64ToBytes(encryptedText), Settings.getEncodingCharset());
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
   * Derives the unencrypted URL.<br>
   * The URL is considered to be encrypted if started with a fake protocol unsupported by the application in the given context.
   * The text after {@code =~} up to the next {@code &} (or the end) is taken as the encrypted URL. Example:
   * <pre>
   *   {@code https://somehost.somedomain.org/login?id=~GK+AG1QIjpBaD51HP/kw9HzpdKZLt2FrInFxd1jtPWvGzaw5lcLcHy5RB/q9yEKQ&user=100}
   * </pre>
   * Hidden gem... ;)
   *
   * @param url the possibly encrypted URL
   * @param protocols the fake protocols not used by the application, such as {@code "http:"} or {@code "https:"}
   * @return the decrypted URL or the unchanged URL, if no encryption pattern found
   * @throws TentackleRuntimeException if decryption failed
   */
  public String deriveURL(String url, String... protocols) {
    for (String protocol: protocols) {
      if (url.startsWith(protocol)) {
        int ndx = url.indexOf("=~");
        if (ndx >= 0) {
          StringTokenizer stok = new StringTokenizer(url.substring(ndx + 2), "&");
          if (stok.hasMoreTokens()) {
            url = decrypt64(stok.nextToken());
          }
        }
        break;
      }
    }
    return url;
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
   * Creates a cipher instance.<br>
   * The default implementation returns an {@code AES} cipher.
   * <p>
   * Invoked once per thread, unless the cryptor was created via {@link #Cryptor(SecretKey, Cipher)}.
   * Implementations must return a new instance each time, since {@link Cipher} is not thread-safe.
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


  /**
   * Creates a cipher from a template.<br>
   * The same algorithm and provider as the template, but a separate instance.
   *
   * @param template the cipher to derive the new instance from
   * @return the cipher
   */
  private static Cipher createCipher(Cipher template) {
    try {
      return Cipher.getInstance(template.getAlgorithm(), template.getProvider());
    }
    catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
      throw new TentackleRuntimeException("creating cipher failed", e);
    }
  }


  private char[] toChars(byte[] data) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(data);
    CharBuffer charBuffer = Settings.getEncodingCharset().decode(byteBuffer);
    char[] chars = new char[charBuffer.remaining()];
    charBuffer.get(chars);
    Arrays.fill(data, (byte) 0);
    Arrays.fill(byteBuffer.array(), (byte) 0);
    Arrays.fill(charBuffer.array(), ' ');
    return chars;
  }

}

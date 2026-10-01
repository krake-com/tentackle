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

import java.io.Serial;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * Encrypted {@link Properties}.<br>
 * If a value starts with a <code>~</code> (tilde), the remainder of the string is considered to be
 * encrypted by the application-specific {@link Cryptor}.
 * If an unencrypted value starts with a <code>~</code>, it must be escaped with a backslash.
 * Notice that {@link Properties#load(java.io.Reader)} drops a single backslash in front of a character
 * that is not an escape sequence. Hence, the escape must be written as <code>\~</code> in a properties file,
 * whereas {@code setProperty(key, "\\~...")} in Java code needs only a single backslash in the value.
 * <p>
 * Also supports case-insensitive keys and an optional key prefix.
 */
public class EncryptedProperties extends Properties {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Optional name of the properties.
   */
  private String name;

  /**
   * Optional prefix for the property keys.
   */
  private String keyPrefix;

  /**
   * Optional mapping of case-insensitive to case-sensitive keys.<br>
   * The map will be created and maintained after the first
   * invocation of getPropertyIgnoreCase or getKeyIgnoreCase.
   */
  private transient Map<String, String> keyCaseMap;


  /**
   * Creates an empty property list.
   */
  public EncryptedProperties() {
    super();
  }

  /**
   * Creates an empty property list with an initial size.
   *
   * @param  initialCapacity the {@code EncryptedProperties} will be sized to
   *         accommodate this many elements
   * @throws IllegalArgumentException if the initial capacity is less than
   *         zero.
   */
  public EncryptedProperties(int initialCapacity) {
    super(initialCapacity);
  }


  @Override
  public synchronized EncryptedProperties clone() {
    EncryptedProperties clone = (EncryptedProperties) super.clone();
    if (keyCaseMap != null) {
      clone.keyCaseMap = new HashMap<>(keyCaseMap);   // don't share with the original
    }
    return clone;
  }

  /**
   * Gets the optional name of the properties.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the optional name of the properties.<br>
   * The name can be used to express the source, for example the name of a properties file.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the key prefix.
   *
   * @return the prefix, null if none (default)
   */
  public String getKeyPrefix() {
    return keyPrefix;
  }

  /**
   * Sets a key prefix.<br>
   * The prefix is prepended to the key for {@link #getProperty(String)}, {@link #setProperty(String, String)} and alike.
   * This allows merging the properties with others (such as Spring's application.properties)
   * and avoids duplicates.<br>
   * Notice that all low-level methods of {@link java.util.Hashtable} dealing with objects rather than strings
   * ignore the prefix.
   *
   * @param keyPrefix the prefix, null if none
   */
  public void setKeyPrefix(String keyPrefix) {
    this.keyPrefix = keyPrefix;
  }

  /**
   * Searches for the property with the specified key in this property list.<br>
   * If the key is not found in this property list, the default property list,
   * and its defaults, recursively, are then checked. The method returns
   * {@code null} if the property is not found.
   * <p>
   * If the property is encrypted, it will be returned decrypted.
   *
   * @param   key the property key
   * @return  the value in this property list with the specified key value
   * @throws  TentackleRuntimeException if the value is encrypted, but there is no {@link Cryptor}
   * @see     #setProperty
   */
  @Override
  public String getProperty(String key) {
    return getValue(key).getText();
  }

  /**
   * Sets a property.<br>
   * The key prefix, if any, is prepended to the key.
   * The value is stored as is, i.e., it is <em>not</em> encrypted (see {@link #setEncryptedProperty(String, String)}).
   *
   * @param key the key to be placed into this property list
   * @param value the value corresponding to key
   * @return the previous value of the specified key in this property list, null if it did not have one
   */
  @Override
  public synchronized Object setProperty(String key, String value) {
    if (keyCaseMap != null) {
      keyCaseMap.put(key.toUpperCase(Locale.ROOT), key);
    }
    return super.setProperty(translateKey(key), value);
  }

  /**
   * Searches for the property with the specified key case insensitively.<br>
   * Throws {@link TentackleRuntimeException}, if duplicate case-mapped keys found.
   *
   * @param key the property key in any case combination
   * @return the value, null if none
   */
  public String getPropertyIgnoreCase(String key) {
    String csKey = getKeyIgnoreCase(key);
    return csKey == null ? null : getProperty(csKey);
  }

  /**
   * Gets the case-sensitive key.<br>
   * Throws {@link TentackleRuntimeException}, if duplicate case-mapped keys are found when the
   * case map is built on first invocation. Later keys colliding case-insensitively replace the mapping.
   *
   * @param key the case-insensitive key
   * @return the original case-sensitive key, the given key if no such key
   */
  public String getKeyIgnoreCase(String key) {
    String csKey = getKeyCaseMap().get(key.toUpperCase(Locale.ROOT));
    return csKey == null ? key : csKey;
  }

  /**
   * Same as {@link Properties#stringPropertyNames()} but returns only those
   * keys that belong to the <code>keyPrefix</code> space, if set.
   *
   * @return unmodifiable set of keys
   */
  @Override
  public Set<String> stringPropertyNames() {
    Set<String> keys = super.stringPropertyNames();
    if (keyPrefix != null) {
      Set<String> prefixKeys = new HashSet<>();
      for (String key : keys) {
        if (key.startsWith(keyPrefix)) {
          prefixKeys.add(key.substring(keyPrefix.length()));
        }
      }
      keys = Collections.unmodifiableSet(prefixKeys);
    }
    return keys;
  }

  /**
   * Gets the original value of the property.<br>
   * If the value is encrypted, the encrypted value will be returned.
   *
   * @param key the property key
   * @return the value, null if no such key
   */
  public String getPropertyBlunt(String key) {
    return super.getProperty(translateKey(key));
  }

  /**
   * Gets the property as a character array.<br>
   * Same as {@link #getProperty(String)}, but returns an array.
   * In case the value was encrypted, the decrypted array can be erased by the application after use.
   *
   * @param key the property key
   * @return the value as a character array or null if no such key
   * @throws TentackleRuntimeException if the value is encrypted, but there is no {@link Cryptor}
   */
  public char[] getPropertyAsChars(String key) {
    return getValue(key).getChars();
  }

  /**
   * Gets the property as a character array with a default value.
   *
   * @param key the property key
   * @param defaults the default value
   * @return the value as a character array or defaults if no such key
   * @throws TentackleRuntimeException if the value is encrypted, but there is no {@link Cryptor}
   */
  public char[] getPropertyAsChars(String key, char[] defaults) {
    char[] chars = getPropertyAsChars(key);
    return chars == null ? defaults : chars;
  }

  /**
   * Sets the property string and stores it encrypted, if a Cryptor is available.<br>
   * Without a Cryptor, the value is stored unencrypted. A leading {@code ~} is escaped
   * in that case, so that the value is not mistaken for an encrypted one.
   *
   * @param key the property key
   * @param value the unencrypted value
   */
  public void setEncryptedProperty(String key, String value) {
    Objects.requireNonNull(value, "missing value (String)");
    Cryptor cryptor = Cryptor.getInstance();
    setProperty(key, cryptor != null ? '~' + cryptor.encrypt64(value) : escapeTilde(value));
  }

  /**
   * Sets the property char array and stores it encrypted, if a Cryptor is available.<br>
   * Without a Cryptor, the value is stored unencrypted. A leading {@code ~} is escaped
   * in that case, so that the value is not mistaken for an encrypted one.
   * <p>
   * If a Cryptor is available, the given array is erased after encryption.
   *
   * @param key the property key
   * @param value the unencrypted value
   */
  public void setEncryptedProperty(String key, char[] value) {
    Objects.requireNonNull(value, "missing value (char[])");
    Cryptor cryptor = Cryptor.getInstance();
    setProperty(key, cryptor != null ? '~' + cryptor.encrypt64(value) : escapeTilde(String.valueOf(value)));
  }


  private Map<String, String> getKeyCaseMap() {
    if (keyCaseMap == null) {
      // initialize the case map with already existing keys
      Map<String, String> map = new HashMap<>();
      for (String name : stringPropertyNames()) {
        if (map.put(name.toUpperCase(Locale.ROOT), name) != null) {
          throw new TentackleRuntimeException("duplicate case-insensitive key '" + name + "' found");
        }
      }
      keyCaseMap = map;
    }
    return keyCaseMap;
  }


  /**
   * Holds the property value.
   *
   * @param text the encrypted or unencrypted text, may be null if encrypted is false
   * @param encrypted true if text is encrypted
   */
  private record Value(String text, boolean encrypted) {

    private String getText() {
      return encrypted ? Cryptor.getInstanceSafely().decrypt64(text) : text;
    }

    private char[] getChars() {
      return encrypted ? Cryptor.getInstanceSafely().decrypt64ToChars(text) : (text == null ? null : text.toCharArray());
    }
  }

  private Value getValue(String key) {
    String text = super.getProperty(translateKey(key));
    boolean encrypted = false;
    if (text != null) {
      if (text.startsWith("~")) {
        text = text.substring(1);
        encrypted = true;
      }
      else if (text.startsWith("\\~")) {
        text = text.substring(1);
      }
    }
    return new Value(text, encrypted);
  }

  private static String escapeTilde(String value) {
    return value.startsWith("~") ? "\\" + value : value;
  }

  private String translateKey(String key) {
    return keyPrefix == null ? key : (keyPrefix + key);
  }

}

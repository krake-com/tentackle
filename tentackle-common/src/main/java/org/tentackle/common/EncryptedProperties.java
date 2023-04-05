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
import java.util.Objects;
import java.util.Properties;

/**
 * Encrypted {@link Properties}.<br>
 * If a value starts with a <code>~</code> (tilde), the remainder of the string is considered to be
 * encrypted by the application-specific {@link Cryptor}.
 * If an unencrypted value starts with a <code>~</code>, it must be escaped with a backslash.
 */
public class EncryptedProperties extends Properties {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates an empty property list with no default values.
   */
  public EncryptedProperties() {
    super();
  }

  /**
   * Creates an empty property list with no default values, and with an
   * initial size accommodating the specified number of elements without the
   * need to dynamically resize.
   *
   * @param  initialCapacity the {@code EncryptedProperties} will be sized to
   *         accommodate this many elements
   * @throws IllegalArgumentException if the initial capacity is less than
   *         zero.
   */
  public EncryptedProperties(int initialCapacity) {
    super(initialCapacity);
  }

  /**
   * Creates an empty property list with the specified defaults.
   *
   * @param   defaults   the defaults.
   */
  public EncryptedProperties(Properties defaults) {
    super(defaults);
  }


  @Override
  public EncryptedProperties clone() {
    return (EncryptedProperties) super.clone();
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
   * @see     #setProperty
   * @see     #defaults
   */
  @Override
  public String getProperty(String key) {
    return getValue(key).getText();
  }

  /**
   * Gets the original value of the property.<br>
   * If the value is encrypted, the encrypted value will be returned.
   *
   * @param key the property key
   * @return the value, null if no such key
   */
  public String getPropertyBlunt(String key) {
    return super.getProperty(key);
  }

  /**
   * Gets the property as a character array.<br>
   * Same as {@link #getProperty(String)}, but returns an array.
   * In case the value was encrypted, the decrypted array can be erased by the application after use.
   *
   * @param key the property key
   * @return the value as a character array or null if no such key
   */
  public char[] getPropertyAsChars(String key) {
    return getValue(key).getChars();
  }

  /**
   * Gets the property as a character array with a default value.
   *
   * @param key the property key
   * @param defaults the default value
   * @return the value as a character array or null if no such key
   */
  public char[] getPropertyAsChars(String key, char[] defaults) {
    char[] chars = getPropertyAsChars(key);
    return chars == null ? defaults : chars;
  }

  /**
   * Sets the property string and stores it encrypted, if a Cryptor is available.
   *
   * @param key the property key
   * @param value the unencrypted value
   */
  public void setEncryptedProperty(String key, String value) {
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null) {
      setProperty(key, '~' + cryptor.encrypt64(Objects.requireNonNull(value, "missing value (String)")));
    }
    else {
      setProperty(key, value);
    }
  }

  /**
   * Sets the property char array and stores it encrypted, if a Cryptor is available.
   *
   * @param key the property key
   * @param value the unencrypted value
   */
  public void setEncryptedProperty(String key, char[] value) {
    value = Objects.requireNonNull(value, "missing value (char[])");
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null) {
      setProperty(key, '~' + Cryptor.getInstance().encrypt64(value));
    }
    else {
      super.setProperty(key, String.valueOf(value));
    }
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
    String text = super.getProperty(key);
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

}

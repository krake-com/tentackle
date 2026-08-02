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

package org.tentackle.session.auth;

import org.tentackle.common.Cryptor;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * A confidential character sequence, such as a password or an access token.
 * <p>
 * The value is encrypted in memory (and thus on the wire) if a {@link Cryptor} is configured,
 * exactly as passwords have always been treated by the session info. Without a cryptor the
 * value is kept as a plain character array, which can be wiped via {@link #clear()}.
 * <p>
 * A secret never reveals its value in {@link #toString()}.
 *
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)
public class Secret implements Serializable, Cloneable {

  @Serial
  private static final long serialVersionUID = 1L;

  private char[] value;               // the value if no cryptor is available
  private byte[] encryptedValue;      // the encrypted value if a cryptor is available
  private transient boolean cleared;  // true if the value was cleared (else set or never set)


  /**
   * Creates a secret.
   *
   * @param value the value, null if none
   */
  public Secret(char[] value) {
    set(value);
  }

  /**
   * Creates an unset secret.
   */
  public Secret() {
    this(null);
  }


  /**
   * Sets the value.
   *
   * @param value the value, null to unset
   */
  public final void set(char[] value) {
    cleared = false;
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null) {
      encryptedValue = value == null ? null : cryptor.encrypt(value);
      this.value = null;
    }
    else {
      this.value = value;
      this.encryptedValue = null;
    }
  }

  /**
   * Gets the value.
   *
   * @return the value, null if unset or cleared
   */
  public char[] get() {
    if (encryptedValue != null) {
      return Cryptor.getInstance().decryptToChars(encryptedValue);
    }
    return value;
  }

  /**
   * Returns whether a value is set.
   *
   * @return true if set
   */
  public boolean isSet() {
    return value != null || encryptedValue != null;
  }

  /**
   * Returns whether the value has been cleared.
   *
   * @return true if cleared
   */
  public boolean isCleared() {
    return cleared;
  }

  /**
   * Clears the value.<br>
   * Removes it from memory by overwriting each element of the array.
   */
  public void clear() {
    if (value != null) {
      Arrays.fill(value, ' ');
      value = null;
      cleared = true;
    }
    if (encryptedValue != null) {
      Arrays.fill(encryptedValue, (byte) 0);
      encryptedValue = null;
      cleared = true;
    }
  }

  /**
   * Clones the secret.<br>
   * The value is physically copied, since it may be cleared later.
   *
   * @return the cloned secret
   */
  @Override
  public Secret clone() {
    Secret secret;
    try {
      secret = (Secret) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
    }
    if (value != null) {
      secret.value = value.clone();
    }
    if (encryptedValue != null) {
      secret.encryptedValue = encryptedValue.clone();
    }
    return secret;
  }

  /**
   * Returns a description that never reveals the value.
   *
   * @return the description
   */
  @Override
  public String toString() {
    if (cleared) {
      return "<cleared>";
    }
    if (encryptedValue != null) {
      return "<encrypted>";
    }
    return value == null ? "<unset>" : "<set>";
  }

}

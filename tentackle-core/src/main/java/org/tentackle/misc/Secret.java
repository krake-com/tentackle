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

package org.tentackle.misc;

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
 * <p>
 * Secrets are thread-safe. They have to be, since a session info is shared between threads and
 * {@link #clear()} may run while another thread reads the value. Each method is atomic in itself:
 * a reader either gets the whole value or none of it, never a partially wiped one. Compound
 * operations are not, so a {@link #get()} following an {@link #isSet()} may still return null.
 * <p>
 * Notice that this protects the API only, not the reflective field access a serializer performs.
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
   * <p>
   * The value is copied, so the caller keeps ownership of the given array and is free to wipe it.
   *
   * @param value the value, null to unset
   */
  public final synchronized void set(char[] value) {
    cleared = false;
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null) {
      // encrypt() wipes the array it is given, so hand it a copy and keep the caller's intact
      encryptedValue = value == null ? null : cryptor.encrypt(value.clone());
      this.value = null;
    }
    else {
      // copy as well: the caller may wipe its array, and ours must not be wiped from the outside
      this.value = value == null ? null : value.clone();
      this.encryptedValue = null;
    }
  }

  /**
   * Gets the value.
   * <p>
   * The returned array is a copy the caller owns and should wipe once done with it.
   * {@link #clear()} therefore does not reach values handed out earlier.
   *
   * @return the value, null if unset or cleared
   */
  public synchronized char[] get() {
    if (encryptedValue != null) {
      return Cryptor.getInstance().decryptToChars(encryptedValue);
    }
    return value == null ? null : value.clone();
  }

  /**
   * Returns whether a value is set.
   *
   * @return true if set
   */
  public synchronized boolean isSet() {
    return value != null || encryptedValue != null;
  }

  /**
   * Returns whether the value has been cleared.<br>
   * Setting a new value resets the cleared state.
   *
   * @return true if cleared
   */
  public synchronized boolean isCleared() {
    return cleared;
  }

  /**
   * Clears the value.<br>
   * Removes it from memory by overwriting each element of the array.
   */
  public synchronized void clear() {
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
  public synchronized Secret clone() {
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
  public synchronized String toString() {
    if (cleared) {
      return "<cleared>";
    }
    if (encryptedValue != null) {
      return "<encrypted>";
    }
    return value == null ? "<unset>" : "<set>";
  }

}

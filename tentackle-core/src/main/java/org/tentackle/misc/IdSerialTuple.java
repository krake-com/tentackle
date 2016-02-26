/*
 * Tentackle - http://www.tentackle.org
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

import java.io.Serializable;
import org.tentackle.common.TentackleRuntimeException;


/**
 * A tuple holding the ID and the serial of an object.<br>
 * There is no further logic. Mainly used for serializing.
 *
 * @author harald
 */
public class IdSerialTuple implements Identifiable, SerialNumbered, Serializable, Cloneable, Comparable<IdSerialTuple> {

  private static final long serialVersionUID = 4038857102103180382L;

  /**
   * The object id.
   */
  private long id;

  /**
   * The serial number (version).
   */
  private long serial;


  /**
   * Creates an immutable id, serial tuple.
   *
   * @param id the object id
   * @param serial the serial number
   */
  public IdSerialTuple(long id, long serial) {
    this.id = id;
    this.serial = serial;
  }

  @Override
  public int compareTo(IdSerialTuple o) {
    return Long.compare(id, o.id);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final IdSerialTuple other = (IdSerialTuple) obj;
    return this.id == other.id;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 79 * hash + (int) (this.id ^ (this.id >>> 32));
    return hash;
  }

  @Override
  public IdSerialTuple clone() {
    try {
      return (IdSerialTuple) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException("clone failed", ex);
    }
  }

  @Override
  public long getId() {
    return id;
  }

  @Override
  public void setId(long id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return "[" + id + "/" + serial + "]";
  }

  @Override
  public String toGenericString() {
    return toString();
  }

  @Override
  public long getSerial() {
    return serial;
  }

  @Override
  public void setSerial(long serial) {
    this.serial = serial;
  }

  @Override
  public boolean isVirgin() {
    return serial == 0;
  }

}

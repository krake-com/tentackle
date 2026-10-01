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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalGetter;

import java.io.Serial;
import java.util.Objects;

/**
 * A tuple holding the ID, serial and name of an object.<br>
 * There is no further logic. Mainly used for serializing.
 *
 * @author harald
 */
public class IdSerialNameTuple extends IdSerialTuple {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String name;

  /**
   * Creates an immutable id, serial, name tuple.
   *
   * @param id     the object id
   * @param serial the serial number
   * @param name   the name associated with the ID
   * @throws NullPointerException if name is null
   */
  @CanonicalConstructor
  public IdSerialNameTuple(long id, long serial, String name) {
    super(id, serial);
    this.name = Objects.requireNonNull(name, "name cannot be null");
  }

  /**
   * Gets the name.
   *
   * @return the name associated with the ID
   */
  @CanonicalGetter(ordinal = 2)
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "[" + getId() + "/" + getSerial() + "/" + name + "]";
  }

}

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

package org.tentackle.pdo;

import java.io.Serializable;
import java.util.Objects;

/**
 * Holds the event detail.
 *
 * @author harald
 */
public class ModificationEventDetail implements Serializable {

  private static final long serialVersionUID = 1L;


  /** the modification name, usually a tablename. */
  private final String name;

  /** the serial number to track the number of changes. */
  private final long serial;

  /**
   * Creates an event detail.
   *
   * @param serial the serial
   * @param name the modification name
   */
  public ModificationEventDetail(String name, long serial) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("name must be non-empty");
    }
    this.name = name;
    this.serial = serial;
  }

  /**
   * Gets the modification name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the modification serial.
   *
   * @return the serial
   */
  public long getSerial() {
    return serial;
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 23 * hash + Objects.hashCode(this.name);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ModificationEventDetail other = (ModificationEventDetail) obj;
    return Objects.equals(this.name, other.name);
  }

  @Override
  public String toString() {
    return getName() + ':' + getSerial();
  }

}

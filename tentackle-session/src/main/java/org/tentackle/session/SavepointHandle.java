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

package org.tentackle.session;

import java.io.Serializable;
import java.util.Objects;

/**
 * A savepoint handle.
 *
 * @author harald
 */
public class SavepointHandle implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String name;      // the savepoint name, null if unnamed
  private final Integer id;       // the savepoint id, null if named

  /**
   * Creates a handle for a named savepoint.
   *
   * @param name the savepoint's name
   */
  public SavepointHandle(String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("name must be non-null and not empty");
    }
    this.name = name;
    this.id = null;
  }

  /**
   * Creates a handle for an unnamed savepoint.
   *
   * @param id the savepoint's id
   */
  public SavepointHandle(int id) {
    this.name = null;
    this.id = id;
  }

  /**
   * Gets the savepoint's name.
   *
   * @return the name, null if unnamed savepoint
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the savepoint's id.
   *
   * @return the id, null if named savepoint
   */
  public Integer getId() {
    return id;
  }

  @Override
  public String toString() {
    if (name != null) {
      return name;
    }
    return id.toString();
  }


  @Override
  public int hashCode() {
    int hash = 7;
    hash = 29 * hash + Objects.hashCode(this.name);
    hash = 29 * hash + Objects.hashCode(this.id);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final SavepointHandle other = (SavepointHandle) obj;
    if (!Objects.equals(this.name, other.name)) {
      return false;
    }
    return Objects.equals(this.id, other.id);
  }

}

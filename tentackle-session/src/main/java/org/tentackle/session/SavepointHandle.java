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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * A savepoint handle.
 *
 * @param name the savepoint's name, null if unnamed
 * @param id the savepoint's id, null if named
 *
 * @author harald
 */
public record SavepointHandle(String name, Integer id) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a savepoint handle.<br>
   * Exactly one of name or id must be given.
   *
   * @param name the savepoint's name, null if unnamed
   * @param id the savepoint's id, null if named
   * @throws NullPointerException if neither name nor id is given
   * @throws IllegalArgumentException if both are given, or the name is empty
   */
  public SavepointHandle {
    if (id == null) {
      if (Objects.requireNonNull(name, "name").isEmpty()) {
        throw new IllegalArgumentException("empty name");
      }
    }
    else if (name != null) {
      throw new IllegalArgumentException("name must be null, if id set");
    }
  }

  /**
   * Creates a handle for a named savepoint.
   *
   * @param name the savepoint's name
   */
  public SavepointHandle(String name) {
    this(name, null);
  }

  /**
   * Creates a handle for an unnamed savepoint.
   *
   * @param id the savepoint's id
   */
  public SavepointHandle(int id) {
    this(null, id);
  }

  @Override
  public String toString() {
    if (name != null) {
      return name;
    }
    return id.toString();
  }

}

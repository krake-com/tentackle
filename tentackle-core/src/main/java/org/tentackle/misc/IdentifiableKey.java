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

import java.io.Serial;
import java.io.Serializable;

/**
 * Key for an {@link Identifiable}.<br>
 * Composed of the class and the ID.
 * Can be used for hashing or as a comparable, sorted by class name and ID.
 *
 * @param <T> the identifiable type
 * @author harald
 */
public class IdentifiableKey<T extends Identifiable> implements Comparable<IdentifiableKey<T>>, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private final Class<T> identifiableClass;     // the identifiable class
  private final long identifiableId;            // the object id

  /**
   * Creates a key.
   *
   * @param identifiableClass the identifiable's class
   * @param identifiableId the identifiable's id
   */
  public IdentifiableKey(Class<T> identifiableClass, long identifiableId) {
    this.identifiableClass = identifiableClass;
    this.identifiableId    = identifiableId;
  }

  /**
   * Creates a key from an identifiable.
   *
   * @param identifiable the identifiable
   */
  @SuppressWarnings("unchecked")
  public IdentifiableKey(T identifiable) {
    this((Class<T>) identifiable.getClass(), identifiable.getId());
  }

  /**
   * Creates a key from a classname and id.
   *
   * @param identifiableClassName the classname
   * @param identifiableId the id
   * @throws ClassNotFoundException if no such class
   * @throws ClassCastException if the class is not an Identifiable
   */
  @SuppressWarnings("unchecked")
  public IdentifiableKey(String identifiableClassName, long identifiableId) throws ClassNotFoundException {
    this((Class<T>) Class.forName(identifiableClassName), identifiableId);
  }

  /**
   * Gets the object class.
   *
   * @return the class
   */
  public Class<T> getIdentifiableClass() {
    return identifiableClass;
  }

  /**
   * Gets the identifiable id.
   *
   * @return the id
   */
  public long getIdentifiableId() {
    return identifiableId;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    @SuppressWarnings("unchecked")
    final IdentifiableKey<T> other = (IdentifiableKey<T>) obj;
    if (this.identifiableClass != other.identifiableClass) {
      return false;
    }
    return this.identifiableId == other.identifiableId;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 43 * hash + (identifiableClass != null ? identifiableClass.hashCode() : 0);
    hash = 43 * hash + Long.hashCode(identifiableId);
    return hash;
  }

  @Override
  public int compareTo(IdentifiableKey<T> o) {
    int rv = identifiableClass.getName().compareTo(o.identifiableClass.getName());
    if (rv == 0) {
      rv = Long.compare(identifiableId, o.identifiableId);
    }
    return rv;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (identifiableClass != null) {
      buf.append(identifiableClass.getName());
    }
    buf.append('[');
    buf.append(identifiableId);
    buf.append(']');
    return buf.toString();
  }

}

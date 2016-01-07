/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.security;

import org.tentackle.pdo.PersistentDomainObject;

/**
 * Describes a grantee.
 *
 * @author harald
 */
public class GranteeDescriptor {

  private final int granteeClassId;
  private final long granteeId;

  /**
   * Creates a grantee descriptor.
   *
   * @param granteeClassId the class id
   * @param granteeId the object id
   */
  public GranteeDescriptor(int granteeClassId, long granteeId) {
    this.granteeClassId = granteeClassId;
    this.granteeId = granteeId;
  }

  /**
   * Creates a grantee descriptor from a PDO.<br>
   * The PDO is usually an entity representing a user or a group.
   *
   * @param pdo the PDO
   */
  public GranteeDescriptor(PersistentDomainObject<?> pdo) {
    this(pdo.getClassId(), pdo.getId());
  }

  /**
   * Gets the grantee's class id.
   *
   * @return the class id, 0 if all
   */
  public int getGranteeClassId() {
    return granteeClassId;
  }

  /**
   * Gets the grantee's object id.
   *
   * @return the object id, 0 if all of grantee class
   */
  public long getGranteeId() {
    return granteeId;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("classId=").append(granteeClassId).append(", id=").append(granteeId);
    return buf.toString();
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 89 * hash + this.granteeClassId;
    hash = 89 * hash + (int) (this.granteeId ^ (this.granteeId >>> 32));
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
    final GranteeDescriptor other = (GranteeDescriptor) obj;
    if (this.granteeClassId != other.granteeClassId) {
      return false;
    }
    return this.granteeId == other.granteeId;
  }

}

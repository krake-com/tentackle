/**
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


package org.tentackle.persist;

import java.io.Serializable;

/**
 * A unique handle to reference an object within a {@link DbTransaction}.
 * <p>
 * Handles are only valid within a transaction.
 *
 * @author harald
 */
public class DbTransactionHandle implements Serializable {

  private static final long serialVersionUID = -1632629938313090092L;


  private final long txId;    // the transaction id
  private final int handle;   // the object handle


  /**
   * Creates a handle.
   *
   * @param txId the transaction id
   * @param handle the unique ID referencing this object
   */
  public DbTransactionHandle(long txId, int handle) {
    this.txId = txId;
    this.handle = handle;
  }


  /**
   * Gets the transaction id.
   *
   * @return the transaction id
   */
  public long getTxId() {
    return txId;
  }


  /**
   * Gets the object handle.
   *
   * @return the handle
   */
  public int getHandle() {
    return handle;
  }



  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final DbTransactionHandle other = (DbTransactionHandle) obj;
    if (txId != other.txId) {
      return false;
    }
    return handle == other.handle;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 11 * hash + (int) (this.txId ^ (this.txId >>> 32));
    hash = 11 * hash + this.handle;
    return hash;
  }

}

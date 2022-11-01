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

package org.tentackle.dbms;

import java.io.Serial;
import java.io.Serializable;

/**
 * Holds the master serial.
 * <p>
 * This is just a DTO without further logic.<br>
 * It can be extended to provide additional application-specific data.
 *
 * @see org.tentackle.dbms.rmi.DbModificationTrackerRemoteDelegateImpl#createMasterSerial(long)
 */
public class MasterSerial implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private final long serial;

  /**
   * Creates a master serial object.
   *
   * @param serial the master serial
   */
  public MasterSerial(long serial) {
    this.serial = serial;
  }

  /**
   * Gets the serial value.
   *
   * @return the serial
   */
  public long getSerial() {
    return serial;
  }

}

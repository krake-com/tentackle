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

// Created on November 19, 2003, 5:46 PM

package org.tentackle.dbms.rmi;

import java.io.Serializable;
import org.tentackle.dbms.AbstractDbObject;

/**
 * Result used to return the possibly new objectid, changed serial and a
 * result code for success or failure.
 *
 * @author harald
 */
public class DbObjectResult implements Serializable {

  private static final long serialVersionUID = 3143529012167549556L;

  /** the object ID. */
  private final long id;

  /** the serial number. */
  private final long serial;

  /** the table serial. */
  private final long tableSerial;


  /**
   * Creates a result for the client.
   *
   * @param id the object ID
   * @param serial the object's serial
   * @param tableSerial the table serial
   */
  public DbObjectResult(long id, long serial, long tableSerial) {
    this.id = id;
    this.serial = serial;
    this.tableSerial = tableSerial;
  }

  /**
   * Creates a result for the client.
   *
   * @param object the object
   */
  public DbObjectResult(AbstractDbObject<?> object) {
    this(object.getId(), object.getSerial(), object.getTableSerial());
  }


  /**
   * Gets the obejct id.
   *
   * @return the id
   */
  public long getId() {
    return id;
  }

  /**
   * Gets the object's serial.
   *
   * @return the serial
   */
  public long getSerial() {
    return serial;
  }

  /**
   * Gets the object's table serial.
   *
   * @return the table serial, 0 if no table serial
   */
  public long getTableSerial() {
    return tableSerial;
  }

}

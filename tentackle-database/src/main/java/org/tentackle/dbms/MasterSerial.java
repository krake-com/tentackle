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

import java.io.Serializable;

/**
 * Holds the master serial and optional application-specific data.
 * <p>
 * This is just a DTO without further logic.
 *
 * @author harald
 */
public class MasterSerial implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * The master serial.
   */
  public long serial;

  /**
   * Optional application data returned to the client.
   */
  public Serializable applicationData;

}

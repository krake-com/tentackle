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

/**
 * A master serial event whose handler cannot be instantiated.
 * <p>
 * Top-level on purpose, see {@link RegisteredTestEntity}.
 *
 * @see DefaultMasterSerialEventHandlerFactoryTest
 */
public class BrokenHandlerTestEvent implements MasterSerialEvent {

  private long serial;

  /**
   * Creates the event.
   */
  public BrokenHandlerTestEvent() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setSerial(long serial) {
    this.serial = serial;
  }

  @Override
  public long serial() {
    return serial;
  }

}

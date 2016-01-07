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

// Created on December 8, 2004, 3:45 PM

package org.tentackle.io;

import org.tentackle.misc.ApplicationException;

/**
 * An adapter for a CSVObject.
 *
 * @author harald
 */
public class CSVObjectAdapter implements CSVObject {

  @Override
  public String formatCSVField(int index) throws ApplicationException {
    return null;
  }

  @Override
  public int getCSVFieldCount() throws ApplicationException {
    return 0;
  }

  @Override
  public void parseCSVField(int index, String value) throws ApplicationException {
  }

}

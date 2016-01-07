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

// Created on December 7, 2004, 4:50 PM

package org.tentackle.io;

import org.tentackle.misc.ApplicationException;

/**
 * CSV conversion interface.
 * <p>
 * Objects that can decoded and/or encoded to CSV format
 * (comma separated values) must implement this interface.
 *
 * @author harald
 * @see CSVReader
 * @see CSVWriter
 */
public interface CSVObject {

  // ---------- for reading an object from a csv-file -------------

  /**
   * Parses a field and stores it in this object.
   *
   * @param index the index of the field in the CSV-Object (starting at 0)
   * @param value the string-value. null if empty non-quoted field.
   *
   * @throws ApplicationException if setting the value failes for some reason
   */
  void parseCSVField(int index, String value) throws ApplicationException;



  // ---------- for writing an object to a csv-file -------------

  /**
   * Returns the string-representation for a csv-value.
   *
   * @param index the index of the field in the CSV-Stream (starting at 0)
   * @return the formatted string
   * @throws ApplicationException if formatting failed
   */
  String formatCSVField(int index) throws ApplicationException;

  /**
   * Returns the number of csv-fields to format for the current object.
   * @return the number of fields in this object
   * @throws ApplicationException if calculating the number of fields failed
   */
  int getCSVFieldCount() throws ApplicationException;

}

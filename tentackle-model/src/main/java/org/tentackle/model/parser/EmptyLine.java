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
 */
// $Id: EmptyLine.java 2450 2016-01-04 11:21:35Z svn $

package org.tentackle.model.parser;

/**
 * The empty line.
 *
 * @author harald
 */
public class EmptyLine extends SingleLine {

  /**
   * Creates an empty line.<br>
   * This line always spans only one line.
   *
   * @param document the whole document
   * @param offset offset to first character within document
   * @param lineType the line type
   */
  public EmptyLine(Document document, int offset, LineType lineType) {
    super(document, offset, lineType);
  }

}

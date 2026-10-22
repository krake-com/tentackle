/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.model.parse;

import org.tentackle.model.ModelException;

/**
 * A line consisting of multiple physical lines.<br>
 * Such lines are concatenated by trailing commas or colons.
 * A semicolon or an empty line ends the multiline.
 *
 * @author harald
 */
public abstract class MultiLine extends Line {

  /**
   * Creates a multi-line.
   *
   * @param document the whole document
   * @param offset offset to first character within document
   * @param lineType the line type
   */
  public MultiLine(Document document, int offset, LineType lineType) {
    super(document, offset, lineType);
  }

  @Override
  public void parse() throws ModelException {
    String text = getDocument().getText().substring(getOffset());
    boolean nextSectionFound = false;
    int length = 0;
    char lastC = 0;
    while (length < text.length()) {
      char c = text.charAt(length);
      if (c == ';' ||
          c == '\n' && (!nextSectionFound || lastC == '\n')) {
        // end of the multiline
        break;
      }
      if (c == ',' || c == ':') {
        nextSectionFound = true;
      }
      else if (!Character.isWhitespace(c)) {
        nextSectionFound = false;
      }
      lastC = c;
      length++;
    }
    setLength(length);
  }

}

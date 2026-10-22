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

import java.util.ArrayList;
import java.util.List;

/**
 * The option line.
 * <p>
 * Option lines start with an attribute or relation name followed by a colon and may span
 * more than one line. Options are separated by commas.
 *
 * @author harald
 */
public class OptionLine extends MultiLine {

  private String name;
  private List<String> options;


  /**
   * Creates an attribute or relation option line.<br>
   * May span multiple lines.
   *
   * @param document the whole document
   * @param offset offset to first character within document
   * @param lineType the line type
   */
  public OptionLine(Document document, int offset, LineType lineType) {
    super(document, offset, lineType);
  }

  @Override
  public void parse() throws ModelException {
    super.parse();

    String text = getText();

    // find first colon
    int ndx = text.indexOf(':');
    if (ndx < 0) {
      throw createModelException("missing colon (:)");
    }
    name = text.substring(0, ndx).trim();
    if (name.isEmpty()) {
      throw createModelException("missing attribute name");
    }

    String optionText = text.substring(ndx + 1);

    // scan options
    options = new ArrayList<>();
    OptionParser parser = new OptionParser(optionText, null);
    String option;
    while ((option = parser.nextOption()) != null) {
      options.add(option.trim());
    }
  }


  /**
   * Gets the attribute or relation name.
   *
   * @return the attribute or relation name, never null or empty
   * @throws ModelException if not parsed
   */
  public String getName() throws ModelException {
    assertParsed();
    return name;
  }


  /**
   * Gets the options.
   *
   * @return the options, never null
   * @throws ModelException if not parsed
   */
  public List<String> getOptions() throws ModelException {
    assertParsed();
    return options;
  }

}

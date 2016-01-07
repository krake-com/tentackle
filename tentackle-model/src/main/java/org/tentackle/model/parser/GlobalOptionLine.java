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
// $Id: GlobalOptionLine.java 2450 2016-01-04 11:21:35Z svn $

package org.tentackle.model.parser;

import java.util.ArrayList;
import java.util.List;
import org.tentackle.model.ModelException;

/**
 * Global options line.
 * <p>
 * Example:
 * <pre>
 * [bind, autoselect, tracked, editedby, tableserial, generated]
 * </pre>
 *
 * @author harald
 */
public class GlobalOptionLine extends SingleLine {

  private List<String> options;


  /**
   * Creates a global options line.<br>
   * This line always spans only one line.
   *
   * @param document the whole document
   * @param offset offset to first character within document
   * @param lineType the line type
   */
  public GlobalOptionLine(Document document, int offset, LineType lineType) {
    super(document, offset, lineType);
  }

  @Override
  public void parse() throws ModelException {
    super.parse();
    OptionParser parser = new OptionParser(getText(), true);
    options = new ArrayList<>();
    String option;
    while ((option = parser.nextOption()) != null) {
      options.add(option);
    }
  }


  /**
   * Gets the option strings.
   *
   * @return the options
   * @throws ModelException if not parsed
   */
  public List<String> getOptions() throws ModelException {
    assertParsed();
    return options;
  }

}

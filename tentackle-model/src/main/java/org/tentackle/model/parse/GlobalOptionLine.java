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

package org.tentackle.model.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
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
  private List<String> sortings;


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
    String optionsText = getText().trim();    // trim because of endsWith below
    String sortingText = null;
    int ndx = optionsText.lastIndexOf('|');   // sorting?
    if (ndx >= 0 && optionsText.length() > ndx) {
      boolean endsWithBracket = optionsText.endsWith("]");
      sortingText = optionsText.substring(ndx + 1);
      optionsText = optionsText.substring(0, ndx);
      if (endsWithBracket) {
        optionsText += "]";
        sortingText = sortingText.substring(0, sortingText.length()-1);
      }
    }
    OptionParser parser = new OptionParser(optionsText, true);
    options = new ArrayList<>();
    String option;
    while ((option = parser.nextOption()) != null) {
      options.add(option);
    }

    sortings = new ArrayList<>();
    if (sortingText != null) {
      StringTokenizer stok = new StringTokenizer(sortingText);
      while (stok.hasMoreTokens()) {
        sortings.add(stok.nextToken());
      }
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

  /**
   * Gets the sorting strings.
   *
   * @return the sortings
   * @throws ModelException if not parsed
   */
  public List<String> getSortings() throws ModelException {
    assertParsed();
    return sortings;
  }

}

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
 * The configuration line.<br>
 * {@code <key> := <config>[;]}
 *
 * @author harald
 */
public class ConfigurationLine extends SingleLine {

  private String key;     // the keyword
  private String value;   // the value


  /**
   * Creates a configuration line.<br>
   * This line always spans only one line.
   *
   * @param document the whole document
   * @param offset offset to first character within document
   * @param lineType the line type
   */
  public ConfigurationLine(Document document, int offset, LineType lineType) {
    super(document, offset, lineType);
  }

  @Override
  public void parse() throws ModelException {
    super.parse();
    String[] tokens = getText().split(":=", 2);
    if (tokens.length != 2) {
      throw createModelException("misconfigured configuration line");
    }
    key = tokens[0].trim();
    value = tokens[1].trim();
    if (value.endsWith(";")) {    // convenience: if terminated with a semicolon
      value = value.substring(0, value.length() - 1).trim();
    }
  }


  /**
   * Gets the key's name.
   *
   * @return the key
   * @throws ModelException if not parsed
   */
  public String getKey() throws ModelException {
    assertParsed();
    return key;
  }

  /**
   * Gets the key's value.
   *
   * @return the value
   * @throws ModelException if not parsed
   */
  public String getValue() throws ModelException {
    assertParsed();
    return value;
  }

}

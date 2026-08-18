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

package org.tentackle.misc;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

interface CsvConverterHolder {
  CsvConverter INSTANCE = ServiceFactory.createService(CsvConverter.class, CsvConverter.class);
}

/**
 * Converts values to or from a string separated by delimiters.<br>
 * Implements RFC 4180 as well.
 */
@Service(CsvConverter.class)    // defaults to self
public class CsvConverter {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static CsvConverter getInstance() {
    return CsvConverterHolder.INSTANCE;
  }


  private static final char RFC4180_QUOTE_CHARACTER = '"';
  private static final String RFC4180_LINE_ENDING = "\r\n";
  private static final String RFC4180_SEPARATOR = ",";
  private static final String QUOTABLE_CHARACTERS = "\n\r\f";
  private static final String[] SEPARATORS = new String[] { RFC4180_SEPARATOR, ";", " ", "\t" };


  /**
   * Creates the converter.
   */
  public CsvConverter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the separator strings automatically tried for export to avoid quoting.
   *
   * @return the separators in order to try
   */
  public String[] getSeparators() {
    return SEPARATORS;
  }

  /**
   * Gets the RFC4180 line ending.<br>
   * This is not platform dependent and always CR + LF.
   *
   * @return the line ending
   */
  public String getLineEnding() {
    return RFC4180_LINE_ENDING;
  }

  /**
   * Gets the RFC4180 quoting character.
   *
   * @return usually a double quote
   */
  public char getQuotingCharacter() {
    return RFC4180_QUOTE_CHARACTER;
  }

  /**
   * Gets quotable characters.<br>
   * If a value contains one of those characters, or the separator or the quoting character, it must be quoted.
   *
   * @return the quotable characters
   */
  public String getQuotableCharacters() {
    return QUOTABLE_CHARACTERS;
  }

  /**
   * Determines the separator char to avoid quoting.
   *
   * @param lines the string values, line by line
   * @return the separator, null if it is impossible to avoid quoting
   */
  public String determineBestSeparator(Collection<? extends Collection<String>> lines) {
    String quotables = getQuotableCharacters();
    char quotec = getQuotingCharacter();

    outer:
    for (String separator : getSeparators()) {
      for (Collection<String> line : lines) {
        for (String value : line) {
          if (value.contains(separator)) {
            continue outer;    // try next separator
          }
          if (value.indexOf(quotec) >= 0 || StringHelper.indexAnyOf(value, quotables) >= 0) {
            return null;    // quoting necessary
          }
        }
      }
      return separator;
    }

    return null;
  }

  /**
   * Converts lines of string values to CSV lines.
   *
   * @param separator the separator, null to pick automatically
   * @return the CSV lines
   */
  public List<String> toCsv(String separator, Collection<? extends Collection<String>> lines) {
    String quotables = getQuotableCharacters();
    char quotec = getQuotingCharacter();

    if (separator == null) {
      separator = determineBestSeparator(lines);
      if (separator == null) {   // fall back to RFC
        separator = RFC4180_SEPARATOR;
      }
    }

    List<String> csvLines = new ArrayList<>();
    StringBuilder buf = new StringBuilder();

    for (Collection<String> line : lines) {
      boolean firstValue = true;
      for (String value : line) {
        if (firstValue) {
          // don't use buf.isEmpty() to detect the first value: leading empty values
          // would not be separated at all and the line would lose them
          firstValue = false;
        }
        else {
          buf.append(separator);
        }
        if (value.contains(separator) ||
            value.indexOf(quotec) >= 0 ||
            StringHelper.indexAnyOf(value, quotables) >= 0) {
          // needs quoting
          buf.append(quotec);
          int valLen = value.length();
          for (int i=0; i < valLen; i++) {
            char c = value.charAt(i);
            if (c == quotec) {
              buf.append(quotec);
            }
            buf.append(c);
          }
          buf.append(quotec);
        }
        else {
          buf.append(value);
        }
      }
      csvLines.add(buf.toString());
      buf.setLength(0);
    }

    return csvLines;
  }

  /**
   * Converts lines of string values to CSV lines.<br>
   * Conforms to RFC 4180.
   *
   * @return the CSV lines
   */
  public List<String> toCsv(Collection<? extends Collection<String>> lines) {
    return toCsv(RFC4180_SEPARATOR, lines);
  }

  /**
   * Converts lines of string values to CSV lines.<br>
   * Picks the separator automatically to avoid quoting, if possible.
   *
   * @return the CSV lines
   */
  public List<String> toCsvLenient(Collection<? extends Collection<String>> lines) {
    return toCsv(null, lines);
  }

  /**
   * Reads CSV lines.
   *
   * @param separator the separator character
   * @param lines the CSV values in lines
   * @return the values
   */
  public List<List<String>> fromCsv(char separator, Collection<String> lines) {
    List<List<String>> values = new ArrayList<>();
    char quotec = getQuotingCharacter();
    StringBuilder buf = new StringBuilder();

    for (String line : lines) {
      List<String> lineValues = new ArrayList<>();
      int lineLen = line.length();
      boolean inQuotes = false;

      for (int i=0; i < lineLen; i++) {
        char c = line.charAt(i);
        if (inQuotes) {
          if (c == quotec) {
            if (i == lineLen - 1 || line.charAt(i + 1) == separator) {
              inQuotes = false;
            }
            else {
              buf.append(c);
              i++;
            }
          }
          else {
            buf.append(c);
          }
        }
        else {
          if (c == quotec && buf.isEmpty()) {
            inQuotes = true;
          }
          else {
            if (c == separator) {
              lineValues.add(buf.toString());
              buf.setLength(0);
            }
            else {
              buf.append(c);
            }
          }
        }
      }
      if (!buf.isEmpty()) {
        lineValues.add(buf.toString());
        buf.setLength(0);
      }
      values.add(lineValues);
    }
    return values;
  }

}

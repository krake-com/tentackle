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

package org.tentackle.model.parse;

/**
 * Parses [...] options.
 *
 * @author harald
 */
public class OptionParser {

  private final String text;          // the text to parse
  private final StringBuilder other;  // buffer gets remaining comment
  private final int max;              // length of text
  private int pos;                    // current offset in text
  private int optionLevel;            // nesting level of [...]
  private int braceLevel;             // number of () within [...], i.e. parentheses within option, for example @Annotation(blah=jksf, hsdf=...)


  /**
   * Creates a parser for the given text.
   *
   * @param text the text to parse, usually a comment
   * @param other buffer that gets ignored text outside [...], null if text is not enclosed in square brackets
   */
  public OptionParser(String text, StringBuilder other) {
    this.text = text;
    this.max  = text.length();
    this.other = other;
    this.optionLevel = other != null ? 0 : 1;
  }


  /**
   * Gets the next option as a string.
   *
   * @return the next option, null at end of text, empty string for an empty option
   */
  public String nextOption() {
    return nextOption(0);
  }


  /**
   * Gets the next option as a string.
   *
   * @param stopAtOptionLevel option level to stop on closing bracket
   * @return the next option, null at end of text, empty string for an empty option
   */
  public String nextOption(int stopAtOptionLevel) {
    StringBuilder option = new StringBuilder();
    while (pos < max) {
      char c = text.charAt(pos++);
      if (c == '\\' && pos < max) {    // pos already points to the escaped character
        // drop the backslash only if it escapes one of []()
        c = text.charAt(pos++);
        if (optionLevel > 0) {
          if (c != '[' && c != ']' && c != '(' && c != ')') {
            option.append('\\');
          }
          option.append(c);
        }
        else if (other != null) {
          other.append(c);
        }
        continue;
      }
      if (optionLevel > 0) {
        if (c == ']' && braceLevel == 0) {
          optionLevel--;
          if (optionLevel == stopAtOptionLevel) {
            if (stopAtOptionLevel > 0) {
              option.append(c);
            }
            break;    // ] ends all options
          }
          else  {
            option.append(c);
          }
        }
        else if (c == '[' && braceLevel == 0) {
          optionLevel++;
          option.append(c);
        }
        else if (c == ',' && braceLevel == 0) {
          // comma separates the options within [ ... ]
          break;
        }
        else  {
          option.append(c);
          if (c == ')') {
            if (braceLevel > 0) {
              braceLevel--;
            }
            // else: too many closing parentheses? treat as quoted
          }
          else if (c == '(') {
            braceLevel++;
          }
        }
      }
      else  {
        if (c == '[' && braceLevel == 0) {
          optionLevel++;
        }
        else if (other != null) {
          other.append(c);
        }
      }
    }

    String opt = option.toString().trim();
    if (!opt.isEmpty()) {
      return opt;
    }
    return pos >= max ? null : opt;
  }

}

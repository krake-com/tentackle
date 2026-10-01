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

package org.tentackle.misc.time;

/**
 * Filter to prepare dates with separators for the {@link NumberShortcut}.
 * <p>
 * Only for dates with separators, such as 5/30 or 05/30/21, whose length differs from the date pattern.
 * If the filter detects such a date, each part is padded to an even number of digits and the separators are removed,
 * for example {@code 5/30/21} becomes {@code 053021}. The {@link NumberShortcut} does the remaining part, such as
 * adding a missing year or converting a 2-digit year into a 4-digit one.
 */
public class DateFilter implements SmartDateTimeParser.TextFilter {

  /**
   * Creates a date filter.
   */
  public DateFilter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String apply(SmartDateTimeParser<?> parser, String text) {
    if (parser.isWithDate()) {
      // assumption: date comes first
      String pattern = parser.getPattern();
      int patternLen = pattern.indexOf(' ');
      if (patternLen < 0) {
        patternLen = pattern.length();
      }
      if (patternLen > 0) {
        String datePattern = pattern.substring(0, patternLen);
        int len1st = text.indexOf(' ');           // length of 1st word
        if (len1st < 0) {
          len1st = text.length();
        }
        if (len1st >= 3 && len1st != datePattern.length() && Character.isDigit(text.charAt(0))) {     // incorrect length
          String[] mdy = new String[3];
          int mdyNdx = 0;
          StringBuilder buf = new StringBuilder();
          for (int i=0; i < len1st; i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
              buf.append(c);
            }
            else if (Character.isLetter(c)) {   // something like 10d
              return text;
            }
            else {
              mdy[mdyNdx++] = buf.toString();
              buf.setLength(0);
              if (mdyNdx >= mdy.length) {
                break;
              }
            }
          }
          if (mdyNdx > 0 || !buf.isEmpty()) {     // at least two numbers
            mdy[mdyNdx++] = buf.toString();
            buf.setLength(0);
            for (int i=0; i < mdyNdx; i++) {
              // must be even number of digits
              if (mdy[i].length() % 2 == 1) {
                buf.append('0');
              }
              buf.append(mdy[i]);
            }
            text = buf + text.substring(len1st);
          }
          // else: single large number is treated in NumberShortcut
        }
      }
    }
    return text;
  }

}

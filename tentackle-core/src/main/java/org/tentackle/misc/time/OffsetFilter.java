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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter to process a zone offset.
 * <p>
 * Allows missing digits, for example {@code +2:30} is converted to {@code +02:30} and {@code +2} to {@code +02:00}.
 */
public class OffsetFilter implements SmartDateTimeParser.TextFilter {

  /**
   * A zone offset like string at the end of the text, starting with + or - and preceded by at least one non-whitespace string.
   */
  private static final Pattern pattern = Pattern.compile("\\S+\\s+([+-][0-9]{1,2}[:]?[\\d]{0,2}$)");

  /**
   * Creates an offset filter.
   */
  public OffsetFilter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String apply(SmartDateTimeParser<?> parser, String text) {
    if (parser.isWithOffset()) {
      Matcher matcher = pattern.matcher(text);
      if (matcher.find() && matcher.groupCount() == 1) {
        String offset = matcher.group(1);
        int offsetLen = offset.length();   // counted from the end of the string (max 6 chars)
        if (offsetLen < 6) {    // something is missing!
          int colonNdx = offset.indexOf(':');
          if (colonNdx == 2 || colonNdx < 0 && offsetLen == 2) {
            // leading zero missing (+2:30)
            offset = offset.charAt(0) + "0" + offset.substring(1);
          }
          if (colonNdx < 0) {
            offset += ":00";
          }
        }
        text = text.substring(0, text.length() - offsetLen) + offset;
      }
    }
    return text;
  }

}

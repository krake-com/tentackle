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

import org.tentackle.misc.DateTimeUtilities;

import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter for timezones.
 * <p>
 * Translates incomplete zone names, for example {@code "berlin"} to {@code Europe/Berlin}.
 *
 * @see DateTimeUtilities#parseZoneId(String)
 */
public class ZoneFilter implements SmartDateTimeParser.TextFilter {

  /**
   * A word at the end of the text, preceded by at least one other non-whitespace string.
   */
  private static final Pattern pattern = Pattern.compile("\\S+\\s+([\\w]+$)");


  /**
   * Creates a zone filter.
   */
  public ZoneFilter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String apply(SmartDateTimeParser<?> parser, String text) {
    if (parser.isWithTimeZone()) {
      Matcher matcher = pattern.matcher(text);
      if (matcher.find() && matcher.groupCount() == 1) {
        String zone = matcher.group(1);
        if (Character.isLetter(zone.charAt(0))) {
          int zoneLen = zone.length();
          ZoneId zoneId = DateTimeUtilities.getInstance().parseZoneId(zone);
          text = text.substring(0, text.length() - zoneLen) + zoneId.getId();
        }
      }
    }
    return text;
  }

}

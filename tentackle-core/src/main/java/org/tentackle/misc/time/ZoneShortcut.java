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
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shortcut for a {@link ZoneId}.
 * <p>
 * If the timezone is prepended with an equal sign ({@code "="}), the timestamp is adjusted to that timezone
 * (and zone offset accordingly), otherwise it is left unchanged.
 */
public class ZoneShortcut implements SmartDateTimeParser.Shortcut {

  private static final Pattern pattern = Pattern.compile("^[=]?\\w+$");


  /**
   * Creates the zone shortcut.
   */
  public ZoneShortcut() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public SmartDateTimeParser.TextRange test(SmartDateTimeParser<? extends Temporal> parser, String text) {
    if (parser.isWithTimeZone()) {
      Matcher matcher = pattern.matcher(text);
      if (matcher.find()) {
        return new SmartDateTimeParser.TextRange(matcher.start(), matcher.end());
      }
    }
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V extends Temporal> V parse(SmartDateTimeParser<V> parser, V currentValue, String part) {
    if (currentValue instanceof ZonedDateTime zonedDateTime) {
      boolean isSameInstant = part.charAt(0) == '=';
      if (isSameInstant) {
        part = part.substring(1);
      }
      ZoneId zoneId = DateTimeUtilities.getInstance().parseZoneId(part);
      currentValue = (V) (isSameInstant ? zonedDateTime.withZoneSameInstant(zoneId) : zonedDateTime.withZoneSameLocal(zoneId));
    }
    return currentValue;
  }

}

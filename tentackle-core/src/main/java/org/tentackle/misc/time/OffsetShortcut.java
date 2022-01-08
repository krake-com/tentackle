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

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shortcut for a {@link ZoneOffset}.
 * <p>
 * If the offset is prepended with an equal sign ({@code "="}), the time value is adjusted to the offset,
 * otherwise it is left unchanged.
 */
public class OffsetShortcut implements SmartDateTimeParser.Shortcut {

  private static final Pattern pattern = Pattern.compile("^[=]?[+-]{1}[\\d]{1,2}[:]?[\\d]{0,2}$");

  @Override
  public SmartDateTimeParser.TextRange test(SmartDateTimeParser<? extends Temporal> parser, String text) {
    if (parser.isWithOffset()) {
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
    boolean isSameInstant = part.charAt(0) == '=';
    if (isSameInstant) {
      part = part.substring(1);
    }
    ZoneOffset offset = DateTimeUtilities.getInstance().parseOffset(part);
    if (currentValue instanceof OffsetTime offsetTime) {
      currentValue = (V) (isSameInstant ? offsetTime.withOffsetSameInstant(offset) : offsetTime.withOffsetSameLocal(offset));
    }
    else if (currentValue instanceof OffsetDateTime offsetDateTime) {
      currentValue = (V) (isSameInstant ? offsetDateTime.withOffsetSameInstant(offset) : offsetDateTime.withOffsetSameLocal(offset));
    }
    return currentValue;
  }

}

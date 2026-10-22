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

import org.tentackle.common.TentackleRuntimeException;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shortcut to add some value.
 * <p>
 * Example: {@code "+10h"} adds 10 hours.<br>
 * The supported units are {@code y} (years), {@code M} (months, or minutes for types without a date),
 * {@code m} (minutes, or months for types without a time), {@code d} (days), {@code h} (hours) and {@code s} (seconds).<br>
 * If the unit is missing (e.g. {@code "-10"}), it defaults to {@code d} (days) for types containing a date
 * or to {@code h} (hours) otherwise.
 */
public class AddShortcut implements SmartDateTimeParser.Shortcut {

  private static final Pattern pattern = Pattern.compile("^[-+][0-9]+[ymMdhs]?");


  /**
   * Creates an add shortcut.
   */
  public AddShortcut() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public SmartDateTimeParser.TextRange test(SmartDateTimeParser<? extends Temporal> parser, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? new SmartDateTimeParser.TextRange(matcher.start(), matcher.end()) : null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V extends Temporal> V parse(SmartDateTimeParser<V> parser, V currentValue, String part) {
    boolean isAdding = part.charAt(0) == '+';
    char last = part.charAt(part.length() - 1);
    ChronoUnit unit;
    String numberString;
    if (Character.isDigit(last)) {
      // no type given: use default according to type
      numberString = part.substring(1);
      unit = parser.isWithDate() ? ChronoUnit.DAYS : ChronoUnit.HOURS;
    }
    else {
      unit = switch (last) {
        case 'y' -> ChronoUnit.YEARS;
        case 'M' -> parser.isWithDate() ? ChronoUnit.MONTHS : ChronoUnit.MINUTES;
        case 'm' -> parser.isWithTime() ? ChronoUnit.MINUTES : ChronoUnit.MONTHS;
        case 'd' -> ChronoUnit.DAYS;
        case 'h' -> ChronoUnit.HOURS;
        case 's' -> ChronoUnit.SECONDS;
        default -> throw new TentackleRuntimeException("unexpected unit '" + last + "'");   // regex problem?
      };
      numberString = part.substring(1, part.length() - 1);
    }
    long value = Long.parseLong(numberString);
    return (V) (isAdding ? currentValue.plus(value, unit) : currentValue.minus(value, unit));
  }

}

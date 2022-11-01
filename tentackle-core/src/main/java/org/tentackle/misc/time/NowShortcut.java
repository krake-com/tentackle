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

import java.time.temporal.Temporal;

/**
 * Shortcut to replace the value with the current time and/or date.
 * <p>
 * Applies to input strings beginning with {@code "."}, {@code ","} or {@code "/"}.
 */
public class NowShortcut implements SmartDateTimeParser.Shortcut {

  /**
   * Creates the "now" shortcut.
   */
  public NowShortcut() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public SmartDateTimeParser.TextRange test(SmartDateTimeParser<? extends Temporal> parser, String text) {
    if (!text.isEmpty()) {
      char c = text.charAt(0);
      if (c == '.' || c == ',' || c == '/') {
        return new SmartDateTimeParser.TextRange(0, 1);
      }
    }
    return null;
  }

  @Override
  public <V extends Temporal> V parse(SmartDateTimeParser<V> parser, V currentValue, String part) {
    return DateTimeUtilities.getInstance().nowOf(parser.getType());
  }

}

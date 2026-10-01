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
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;

/**
 * Error handler to add a missing zone offset.<br>
 * Appends the current default offset.
 */
public class MissingOffsetHandler implements SmartDateTimeParser.ErrorHandler {

  /**
   * Creates a missing offset handler.
   */
  public MissingOffsetHandler() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String apply(SmartDateTimeParser<? extends Temporal> parser, DateTimeParseException parseException, String text) {
    if (parser.isWithOffset() && parseException.getErrorIndex() == text.length()) {
      String[] split = text.split("\s+");
      if (parser.getType() == OffsetTime.class && split.length == 1 ||
          parser.getType() == OffsetDateTime.class && split.length == 2) {
        return text + " " + DateTimeUtilities.getInstance().parseOffset(null);
      }
    }
    return null;
  }

}

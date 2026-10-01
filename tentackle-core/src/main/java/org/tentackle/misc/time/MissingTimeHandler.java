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

import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;

/**
 * Error handler to add, expand or insert a missing time.
 * <p>
 * If only a date is given, midnight is appended. A partial time, such as {@code 9} or {@code 930},
 * is expanded to the full time according to the pattern, for example {@code 09:30:00}.
 */
public class MissingTimeHandler implements SmartDateTimeParser.ErrorHandler {

  /**
   * Creates a missing time handler.
   */
  public MissingTimeHandler() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String apply(SmartDateTimeParser<? extends Temporal> parser, DateTimeParseException parseException, String text) {
    if (parser.isWithTime()) {
      String[] split = text.split("\s+");
      if (parser.isWithDate()) {
        if (split.length == 1) {    // only date given
          text += " 00:00";
          if (parser.getPattern().contains("s")) {
            text += ":00";
          }
          return text;
        }
        if (split.length >= 2) {
          String timeStr = split[1];
          if (Character.isDigit(timeStr.charAt(0))) {    // partial time given?
            String[] timeSplit = timeStr.split(":");
            for (int i=0; i < timeSplit.length; i++) {
              String part = timeSplit[i];
              if (part.length() % 2 == 1) {
                // uneven length -> add leading zero
                timeSplit[i] = "0" + part;
              }
            }
            List<String> parts = new ArrayList<>();   // 2 digits parts
            // hh or hhmm?
            for (String part : timeSplit) {
              while (!part.isEmpty()) {
                parts.add(part.substring(0, 2));
                part = part.substring(2);
              }
            }

            StringBuilder buf = new StringBuilder();
            boolean hours = false;
            boolean minutes = false;
            boolean seconds = false;
            for (String part : parts) {
              if (!hours) {
                buf.append(part);
                hours = true;
              }
              else if (!minutes && parser.getPattern().contains("m")) {
                buf.append(':').append(part);
                minutes = true;
              }
              else if (!seconds && parser.getPattern().contains("s")) {
                buf.append(':').append(part);
                seconds = true;
              }
            }
            if (!minutes && parser.getPattern().contains("m")) {
              buf.append(":00");
            }
            if (!seconds && parser.getPattern().contains("s")) {
              buf.append(":00");
            }
            buf.insert(0, ' ');
            buf.insert(0, split[0]);    // -> split[0] + " " + buf
            for (int i=2; i < split.length; i++) {
              buf.append(' ').append(split[i]);   // add the remaining parts
            }
            String fixedStr = buf.toString();
            if (!fixedStr.equals(text)) {
              return fixedStr;
            }
          }
        }
      }
    }
    return null;
  }

}

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
import org.tentackle.misc.FormatHelper;
import org.tentackle.misc.Holder;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Parser for {@link Temporal} date and time types with smart syntax support.
 * <p>
 * Parsing works in 4 steps:
 * <ol>
 *   <li>The input is filtered by a list of {@link TextFilter}s, one after another. Filters usually
 *   convert parts of the input or add missing data.</li>
 *   <li>The list of {@link Shortcut}s is applied, one after another. Shortcuts modify the current date- or time value
 *   according to the patterns found in the input string. Applied patterns are usually removed from the input string.</li>
 *   <li>If at least one shortcut was applied, the current value is returned. Otherwise, the remaining input string is passed
 *   to the appropriate {@link DateTimeFormatter}.</li>
 *   <li>If parsing fails with an exception, optional {@link ErrorHandler}s will be invoked to fix the input.
 *   Each handler is applied at most once.</li>
 * </ol>
 *
 * Shortcuts are applied to the first whitespace-separated token only. If a shortcut was applied, an optional second token
 * denotes the zone offset or timezone. If it starts with {@code =}, the value is converted to the given offset or zone
 * (same instant), otherwise the offset or zone is just replaced (same local time).
 * <p>
 * A predefined list of {@link TextFilter}s, {@link Shortcut}s and {@link ErrorHandler}s is already provided by the framework
 * (see {@link DateTimeUtilities}). However, the application may provide its own, of course.
 * <p>
 * Example for a combination of filters and shortcuts:<br>
 * {@code ".+1d15h =paris"} -&gt; <em>tomorrow 3pm local time converted to the timezone of Europe/Paris</em>.
 *
 * @param <T> the temporal type
 * @see DateTimeUtilities
 */
public class SmartDateTimeParser<T extends Temporal> {

  /**
   * Filter applied to the input.<br>
   * Returns the possibly modified text.
   */
  public interface TextFilter {

    /**
     * Applies this filter to the input text.
     *
     * @param parser the parser
     * @param text the input
     * @return the possibly modified text
     */
    String apply(SmartDateTimeParser<? extends Temporal> parser, String text);

  }

  /**
   * A part within a string.
   *
   * @param beginIndex the starting index, starting at 0
   * @param endIndex the exclusive ending index
   */
  public record TextRange(int beginIndex, int endIndex) {}

  /**
   * Shortcut for date- or time input.
   */
  public interface Shortcut {

    /**
     * Returns whether the shortcut applies to the given string.
     *
     * @param parser the parser
     * @param text the string
     * @return a range within the string to which the shortcut applies, else null
     */
    TextRange test(SmartDateTimeParser<? extends Temporal> parser, String text);

    /**
     * Parses the shortcut string.
     *
     * @param parser the parser
     * @param currentValue the current value
     * @param part the part of the original string
     * @param <V> the data type
     * @return the modified value
     */
    <V extends Temporal> V parse(SmartDateTimeParser<V> parser, V currentValue, String part);

  }

  /**
   * Parse exception handler.
   */
  public interface ErrorHandler {

    /**
     * Applies the handler to given text and modifies it for retry.
     *
     * @param parser the parser
     * @param parseException the parse exception
     * @param text the input text
     * @return the updated text, null if handler does not apply
     */
    String apply(SmartDateTimeParser<? extends Temporal> parser, DateTimeParseException parseException, String text);

  }


  private final Class<T> type;
  private final BiFunction<T, TemporalUnit, T> truncate;
  private final DateTimeFormatter formatter;
  private final List<TextFilter> filters;
  private final List<Shortcut> shortcuts;
  private final List<ErrorHandler> errorHandlers;
  private final TemporalQuery<T> temporalQuery;

  private final String pattern;
  private final boolean withDate;
  private final boolean withTime;
  private final boolean withOffset;
  private final boolean withTimeZone;

  /**
   * Creates a parser.
   *
   * @param type the temporal class
   * @param temporalQuery the formatter query, usually the method reference {@code ::from}
   * @param truncate optional truncate method, usually the method reference {@code ::truncatedTo}, null if unsupported by the temporal type
   * @param formatter the formatter
   * @param pattern the formatting pattern
   * @param filters list of filters, null or empty if none
   * @param shortcuts list of shortcut input methods, null or empty if none
   * @param errorHandlers list of error handlers, null or empty if none
   * @throws NullPointerException if type, temporalQuery, formatter or pattern is null
   */
  public SmartDateTimeParser(Class<T> type,
                             TemporalQuery<T> temporalQuery, BiFunction<T, TemporalUnit, T> truncate,
                             DateTimeFormatter formatter, String pattern,
                             List<TextFilter> filters, List<Shortcut> shortcuts, List<ErrorHandler> errorHandlers) {

    this.type = Objects.requireNonNull(type, "type");
    this.temporalQuery = Objects.requireNonNull(temporalQuery, "temporalQuery");
    this.truncate = truncate;
    this.formatter = Objects.requireNonNull(formatter, "formatter");
    this.pattern = Objects.requireNonNull(pattern, "pattern");
    this.filters = filters == null ? Collections.emptyList() : filters;
    this.shortcuts = shortcuts == null ? Collections.emptyList() : shortcuts;
    this.errorHandlers = errorHandlers == null ? Collections.emptyList() : errorHandlers;

    withDate = FormatHelper.isFormattingDate(pattern);
    withTime = FormatHelper.isFormattingTime(pattern);
    withOffset = FormatHelper.isFormattingOffset(pattern);
    withTimeZone = FormatHelper.isFormattingTimeZone(pattern);
  }

  /**
   * Gets the temporal class.
   *
   * @return the type
   */
  public Class<T> getType() {
    return type;
  }

  /**
   * Gets the formatter query.
   *
   * @return the query
   */
  public TemporalQuery<T> getTemporalQuery() {
    return temporalQuery;
  }

  /**
   * Gets the truncate function.
   *
   * @return the function
   */
  public BiFunction<T, TemporalUnit, T> getTruncate() {
    return truncate;
  }

  /**
   * Gets the formatter instance.
   *
   * @return the formatter
   */
  public DateTimeFormatter getFormatter() {
    return formatter;
  }

  /**
   * Gets the pattern used to initialize the formatter.
   *
   * @return the pattern
   */
  public String getPattern() {
    return pattern;
  }

  /**
   * Gets the list of shortcut implementations.
   *
   * @return the list, empty if none, never null
   */
  public List<Shortcut> getShortcuts() {
    return shortcuts;
  }

  /**
   * Gets the list of filter implementations.
   *
   * @return the list, empty if none, never null
   */
  public List<TextFilter> getFilters() {
    return filters;
  }

  /**
   * Gets the list of error handler implementations.
   *
   * @return the list, empty if none, never null
   */
  public List<ErrorHandler> getErrorHandlers() {
    return errorHandlers;
  }

  /**
   * Returns whether the formatter is parsing a date.
   *
   * @return true if with date
   */
  public boolean isWithDate() {
    return withDate;
  }

  /**
   * Returns whether the formatter is parsing a time.
   *
   * @return true if with time
   */
  public boolean isWithTime() {
    return withTime;
  }

  /**
   * Returns whether the formatter is parsing a zone offset.
   *
   * @return true if with offset
   */
  public boolean isWithOffset() {
    return withOffset;
  }

  /**
   * Returns whether the formatter is parsing a timezone.
   *
   * @return true if with timezone
   */
  public boolean isWithTimeZone() {
    return withTimeZone;
  }


  /**
   * Converts a string to a temporal value of the given type.
   *
   * @param currentValueSupplier the supplier of the current value, usually the method reference {@code type::now}.
   *                             If the supplier returns null, the current time of the parser's type is used.
   * @param text the string to parse
   * @return the temporal value object, null if text is null
   * @throws DateTimeParseException if parsing failed
   */
  @SuppressWarnings("unchecked")
  public T parse(Supplier<T> currentValueSupplier, String text) {
    Objects.requireNonNull(currentValueSupplier, "currentValueSupplier");
    Holder<T> temporalHolder = new Holder<>();

    if (text != null) {
      text = text.trim();
      for (TextFilter filter : getFilters()) {
        text = filter.apply(this, text);
      }

      StringTokenizer stok = new StringTokenizer(text);
      if (stok.hasMoreTokens()) {
        String part1;
        String token = part1 = stok.nextToken();
        // apply shortcuts, if any
        for (Shortcut shortcut : getShortcuts()) {
          TextRange textRange = shortcut.test(this, token);
          if (textRange != null) {
            String part = token.substring(textRange.beginIndex, textRange.endIndex);
            token = (token.substring(0, textRange.beginIndex) + token.substring(textRange.endIndex)).trim();
            T currentValue = currentValueSupplier.get();
            if (currentValue == null) {
              currentValue = DateTimeUtilities.getInstance().nowOf(getType());
            }
            temporalHolder.accept(shortcut.parse(this, currentValue, part));
            currentValueSupplier = temporalHolder;
          }
        }
        if (currentValueSupplier == temporalHolder) {
          String part2;
          boolean sameLocal = true;
          if (stok.hasMoreTokens()) {
            part2 = stok.nextToken();
            if (part2.startsWith("=")) {    // recalculate to offset/timezone
              part2 = part2.substring(1);
              sameLocal = false;
              if (part2.isEmpty()) {
                part2 = null;
              }
            }
          }
          else {
            part2 = null;
          }

          if (!text.equals(part1)) {
            if (isWithOffset()) {
              T value = temporalHolder.get();
              ZoneOffset offset = DateTimeUtilities.getInstance().parseOffset(part2);
              if (value instanceof OffsetTime offsetTime) {
                if (sameLocal) {
                  temporalHolder.accept((T) offsetTime.withOffsetSameLocal(offset));
                }
                else {
                  temporalHolder.accept((T) offsetTime.withOffsetSameInstant(offset));
                }
              }
              else if (value instanceof OffsetDateTime offsetDateTime) {
                if (sameLocal) {
                  temporalHolder.accept((T) offsetDateTime.withOffsetSameLocal(offset));
                }
                else {
                  temporalHolder.accept((T) offsetDateTime.withOffsetSameInstant(offset));
                }
              }
            }
            else if (isWithTimeZone()) {
              T value = temporalHolder.get();
              if (value instanceof ZonedDateTime zonedDateTime) {
                ZoneId zoneId = DateTimeUtilities.getInstance().parseZoneId(part2);
                if (sameLocal) {
                  temporalHolder.accept((T) zonedDateTime.withZoneSameLocal(zoneId));
                }
                else {
                  temporalHolder.accept((T) zonedDateTime.withZoneSameInstant(zoneId));
                }
              }
            }
          }
        }
      }

      if (temporalHolder != currentValueSupplier) {
        // none of the shortcuts matched: parse with full pattern
        Set<ErrorHandler> appliedHandlers = new HashSet<>();
        for (;;) {   // retry once if handlers apply
          try {
            temporalHolder.accept(getFormatter().parse(text, getTemporalQuery()));
            break;
          }
          catch (DateTimeParseException px) {
            String fixedText = null;
            for (ErrorHandler handler : getErrorHandlers()) {
              fixedText = handler.apply(this, px, text);
              if (fixedText != null) {
                if (appliedHandlers.add(handler)) {
                  break;
                }
                fixedText = null;   // handler already applied
              }
            }
            if (fixedText != null) {
              text = fixedText;
              continue;
            }
            throw px;   // no handler applied or exception remains after one retry
          }
        }
      }
    }

    return temporalHolder.get();
  }


  /**
   * Formats the given temporal.
   *
   * @param value the temporal
   * @return the formatted string, null if value is null
   */
  public String format(T value) {
    return value == null ? null : getFormatter().format(value);
  }

}

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

package org.tentackle.fx.component.auto;

import org.tentackle.misc.SubString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Suggestion generator function for strings composed of separated strings.<br>
 * Useful for number matching in choices like <code>XXX-YYY-Z-AAA</code>.
 *
 * @param <T> the type of the available choices
 */
public class SeparatorSuggestionGenerator<T> extends AbstractSegmentedSuggestionGenerator<T> {

  private final String separator;
  private boolean caseSensitive;

  /**
   * Creates a suggestion generator for a given separator.
   *
   * @param separator the separator string
   */
  public SeparatorSuggestionGenerator(String separator) {
    this.separator = Objects.requireNonNull(separator);
  }

  /**
   * Returns whether comparison is case-sensitive.
   *
   * @return true if case-sensitive, false if not (default)
   */
  public boolean isCaseSensitive() {
    return caseSensitive;
  }

  /**
   * Sets case sensitivity.
   *
   * @param caseSensitive true if case-sensitive, false if not (default)
   */
  public void setCaseSensitive(boolean caseSensitive) {
    this.caseSensitive = caseSensitive;
  }

  @Override
  protected SubString match(SubString inputSegment, SubString itemSegment) {
    int ndx = caseSensitive ? itemSegment.indexOf(inputSegment) : itemSegment.indexOfIgnoreCase(inputSegment);
    if (ndx >= 0) {
      return itemSegment.with(ndx, inputSegment.getLength());
    }
    return null;
  }

  /**
   * Extracts the separated segments from a string.
   *
   * @param text the string
   * @return the segments
   */
  protected List<SubString> extractSegments(String text) {
    List<SubString> segments = new ArrayList<>();
    int beginIndex = 0;
    int endIndex;
    while ((endIndex = text.indexOf(separator, beginIndex)) >= 0) {
      if (endIndex > beginIndex) {
        segments.add(createSubString(text, beginIndex, endIndex));
      }
      beginIndex = endIndex + separator.length();
    }
    if (beginIndex < text.length()) {
      segments.add(createSubString(text, beginIndex, text.length()));
    }
    return segments;
  }

}

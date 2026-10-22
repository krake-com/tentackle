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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggestion generator function based on a regular expression.<br>
 * The segments are the matches of the pattern in the input and the items.
 *
 * @param <T> the type of the available choices
 */
public class RegexSuggestionGenerator<T> extends AbstractSegmentedSuggestionGenerator<T> {

  private final Pattern pattern;
  private Matcher matcher;

  /**
   * Creates a suggestion generator for a given regular expression.
   *
   * @param pattern the regex pattern
   */
  public RegexSuggestionGenerator(Pattern pattern) {
    this.pattern = Objects.requireNonNull(pattern);
  }

  @Override
  protected SubString match(SubString inputSegment, SubString itemSegment) {
    int ndx = itemSegment.indexOf(inputSegment);
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
    Matcher matcher = getMatcher(text);
    while (matcher.find()) {
      segments.add(createSubString(text, matcher.start(), matcher.end()));
    }
    return segments;
  }

  private Matcher getMatcher(String text) {
    if (matcher == null) {
      matcher = pattern.matcher(text);
    }
    else {
      matcher.reset(text);
    }
    return matcher;
  }

}

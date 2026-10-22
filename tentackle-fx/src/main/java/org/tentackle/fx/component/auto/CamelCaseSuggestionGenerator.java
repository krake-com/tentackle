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

/**
 * Camel-case suggestion generator function.<br>
 * The segments start at the uppercase letters. Lowercase input is treated as uppercase until the first
 * uppercase character is typed, i.e. {@code nfe} and {@code NFE} both match {@code NumberFormatException}.
 *
 * @param <T> the type of the available choices
 */
public class CamelCaseSuggestionGenerator<T> extends AbstractSegmentedSuggestionGenerator<T> {

  /**
   * Creates the suggestion generator.
   */
  public CamelCaseSuggestionGenerator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public List<List<SubString>> apply(String s) {
    List<List<SubString>> suggestions = super.apply(s);
    // The usual sorting of the string values doesn't make sense for auto-completion by camel case.
    // Instead, we must sort the suggestions by the best "visual match":
    // segments with the lowest beginIndex first, then highest number of matches first, then remainder sorted alphabetically
    suggestions.sort((o1, o2) -> {
      int rv = 0;
      int i = 0;
      while (rv == 0 && i < o1.size() && i < o2.size()) {
        rv = o1.get(i).getBeginIndex() - o2.get(i).getBeginIndex();
        ++i;
      }
      if (rv == 0) {
        rv = o2.size() - o1.size();
      }
      if (rv == 0) {
        String remainder1 = o1.getFirst().getString().substring(o1.getLast().getEndIndex());
        String remainder2 = o2.getFirst().getString().substring(o2.getLast().getEndIndex());
        rv = remainder1.compareTo(remainder2);
      }
      return rv;
    });
    return suggestions;
  }

  @Override
  protected SubString match(SubString inputSegment, SubString itemSegment) {
    if (itemSegment.startsWith(inputSegment)) {
      return itemSegment.with(0, inputSegment.getLength());
    }
    return null;
  }

  @Override
  protected List<SubString> extractInputSegments(String input) {
    // Lowercase characters are treated as uppercase (non-case-sensitive mode)
    // until an uppercase character is found, which switches to case-sensitive mode.
    boolean upperCaseFound = false;
    StringBuilder buf = new StringBuilder();
    int len = input.length();
    for (int i=0; i < len; i++) {
      char c = input.charAt(i);
      if (!upperCaseFound) {
        if (Character.isUpperCase(c)) {
          upperCaseFound = true;
        }
        else {
          c = Character.toUpperCase(c);
        }
      }
      buf.append(c);
    }
    return super.extractInputSegments(buf.toString());
  }

  /**
   * Extracts the separated segments from a string.
   *
   * @param text the string
   * @return the segments
   */
  protected List<SubString> extractSegments(String text) {
    List<SubString> segments = new ArrayList<>();
    int length = text.length();
    if (length > 0) {
      int beginIndex = -1;    // no segment so far
      int endIndex = 0;
      while (endIndex < length) {
        char c = text.charAt(endIndex);
        if (Character.isUpperCase(c)) {
          if (beginIndex >= 0) {
            segments.add(createSubString(text, beginIndex, endIndex));
          }
          beginIndex = endIndex;
        }
        endIndex++;
      }
      if (beginIndex >= 0) {
        segments.add(createSubString(text, beginIndex, endIndex));
      }
    }
    return segments;
  }

}

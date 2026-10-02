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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Base class for a segmented suggestion generator.<br>
 * Both the user's input and the items are split into segments.
 * An item matches if each input segment matches one of the item's segments, in the same order.
 *
 * @param <T> the type of the available choices
 */
public abstract class AbstractSegmentedSuggestionGenerator<T> extends AbstractSuggestionGenerator<T> {

  private final Map<String, List<SubString>> itemSegmentsCache = new HashMap<>();
  private int maxSuggestions;

  /**
   * Parent constructor.
   */
  public AbstractSegmentedSuggestionGenerator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the max. number of suggestions.
   *
   * @return the max. number, default 0 = unlimited
   */
  public int getMaxSuggestions() {
    return maxSuggestions;
  }

  /**
   * Sets the max. number of suggestions.
   *
   * @param maxSuggestions the max. number, 0 if unlimited
   */
  public void setMaxSuggestions(int maxSuggestions) {
    this.maxSuggestions = maxSuggestions;
  }

  @Override
  public List<List<SubString>> apply(String s) {
    List<List<SubString>> completions = new ArrayList<>();
    List<SubString> inputSegments = extractInputSegments(s);
    if (!inputSegments.isEmpty()) {
      for (String item : getSelectList()) {
        List<SubString> matchingSegments = new ArrayList<>();
        Iterator<SubString> inputIter = inputSegments.iterator();
        Iterator<SubString> itemIter = getItemSegments(item).iterator();
        while (inputIter.hasNext()) {
          SubString inputSegment = inputIter.next();
          while (itemIter.hasNext()) {
            SubString itemSegment = itemIter.next();
            SubString matchingSegment = match(inputSegment, itemSegment);
            if (matchingSegment != null) {
              matchingSegments.add(matchingSegment);
              break;
            }
          }
        }
        if (inputSegments.size() == matchingSegments.size()) {
          completions.add(matchingSegments);
          int max = getMaxSuggestions();
          if (max != 0 && completions.size() >= max) {
            return completions;
          }
        }
      }
    }
    return completions;
  }

  @Override
  protected void setSelectList(List<String> selectList) {
    itemSegmentsCache.clear();
    super.setSelectList(selectList);
  }

  /**
   * Gets the item's text segments via cache.
   *
   * @param item the item
   * @return the segments
   */
  protected List<SubString> getItemSegments(String item) {
    return itemSegmentsCache.computeIfAbsent(item, this::extractItemSegments);
  }

  /**
   * Extracts the segments from an input string.
   *
   * @param input the user's input
   * @return the segments
   */
  protected List<SubString> extractInputSegments(String input) {
    return extractSegments(input);
  }

  /**
   * Extracts the segments from an item string.
   *
   * @param item the item
   * @return the segments
   */
  protected List<SubString> extractItemSegments(String item) {
    return extractSegments(item);
  }

  /**
   * Extracts the separated segments from a string.
   *
   * @param text the string
   * @return the segments
   */
  protected abstract List<SubString> extractSegments(String text);

  /**
   * Determines the matching segment between an input- and item-segment.
   *
   * @param inputSegment the input segment
   * @param itemSegment the item segment
   * @return the matching segment, null if none
   */
  protected abstract SubString match(SubString inputSegment, SubString itemSegment);

}

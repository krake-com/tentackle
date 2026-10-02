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
 * Simple suggestion generator function.<br>
 * Matches all items containing the user's input and marks its first occurrence.
 *
 * @param <T> the type of the available choices
 */
public class SimpleSuggestionGenerator<T> extends AbstractSuggestionGenerator<T> {

  private boolean caseSensitive;

  /**
   * Creates the suggestion generator.
   */
  public SimpleSuggestionGenerator() {
    // see -Xlint:missing-explicit-ctor since Java 16
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
  public List<List<SubString>> apply(String s) {
    List<List<SubString>> completions = new ArrayList<>();
    if (!caseSensitive) {
      s = s.toUpperCase();
    }
    for (String select : getSelectList()) {
      String item = caseSensitive ? select : select.toUpperCase();
      int start = item.indexOf(s);
      if (start >= 0) {
        completions.add(List.of(createSubString(select, start, start + s.length())));
      }
    }
    return completions;
  }

}

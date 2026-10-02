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

package org.tentackle.persist;

import org.tentackle.common.StringNormalizer;

/**
 * A helper class to create a normtext-like string from a user's input.<br>
 * It is intended to search in normtext columns of PDOs with LIKE or NOT LIKE.
 *
 * @author harald
 */
public class NormText {

  private final String pattern;
  private final boolean matchingAll;
  private final boolean inverted;

  /**
   * Creates a normtext from a search string.<br>
   * This is usually a user input.<br>
   * The input will be normalized via {@link StringNormalizer#normalize(java.lang.String)}.
   * If the string starts with a "!" or "-" it will be treated as "NOT LIKE".
   * The normalized string is wrapped in "%" to match anywhere in the normtext.
   * An empty string matches all.
   *
   * @param str the search string
   */
  public NormText(String str) {
    String sqlPattern = str == null ? "" : str;
    inverted = sqlPattern.startsWith("!") || sqlPattern.startsWith("-");
    sqlPattern = StringNormalizer.getInstance().normalize(sqlPattern);    // this also removes leading and trailing !,%,_, whitespace and the like
    matchingAll = sqlPattern.isEmpty();
    this.pattern = matchingAll ? null : ("%" + sqlPattern + "%");
  }

  /**
   * Gets the search pattern ready to be used in a LIKE expression.
   *
   * @return the pattern, null if isMatchingAll(), never empty
   */
  public String getPattern() {
    return pattern;
  }

  /**
   * Returns whether the pattern matches all.
   *
   * @return true if all
   */
  public boolean isMatchingAll() {
    return matchingAll;
  }

  /**
   * Returns whether the pattern should be interpreted as "NOT LIKE".
   *
   * @return true if NOT LIKE, else LIKE
   */
  public boolean isInverted() {
    return inverted;
  }

}

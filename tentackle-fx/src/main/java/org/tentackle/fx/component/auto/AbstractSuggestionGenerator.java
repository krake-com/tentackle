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
import java.util.function.Function;

/**
 * Base class for a generator of suggestions based on some user input.
 *
 * @param <T> the type of the available choices
 */
public abstract class AbstractSuggestionGenerator<T> implements Function<String, List<List<SubString>>> {

  private List<T> choices;
  private List<String> selectList;

  /**
   * Parent constructor.
   */
  public AbstractSuggestionGenerator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the original list of available choices.
   *
   * @return the choices
   */
  public List<T> getChoices() {
    return choices;
  }

  /**
   * Sets the original list of available choices.<br>
   * {@link #convert(Object)} is invoked on these objects to compare against the user's input.
   * Null choices are skipped.
   *
   * @param choices the choices
   */
  public void setChoices(List<T> choices) {
    this.choices = choices;
    if (choices == null) {
      setSelectList(null);
    }
    else {
      List<String> list = new ArrayList<>(choices.size());
      choices.stream().filter(Objects::nonNull).forEach(c -> list.add(convert(c)));
      setSelectList(list);
    }
  }

  /**
   * Gets the list of strings to compare against the user's input.
   *
   * @return the select list, never null
   */
  public List<String> getSelectList() {
    return selectList == null ? List.of() : selectList;
  }

  /**
   * Sets the select list.<br>
   * May be used by implementations to bypass or filter choices.
   *
   * @param selectList the select list
   */
  protected void setSelectList(List<String> selectList) {
    this.selectList = selectList;
  }

  /**
   * Converts an object to a string.<br>
   * Defaults to <code>obj.toString()</code>, but may be overridden.
   *
   * @param obj the object
   * @return the string
   */
  protected String convert(T obj) {
    return obj.toString();
  }

  /**
   * Creates a new substring.<br>
   * This method is provided to allow inherited instances of {@link SubString}.
   *
   * @param string the original string
   * @param beginIndex the beginning index
   * @param endIndex the ending index (exclusive)
   * @return the substring
   */
  protected SubString createSubString(String string, int beginIndex, int endIndex) {
    return new SubString(string, beginIndex, endIndex);
  }

}

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

package org.tentackle.fx;

import org.tentackle.common.Compare;

import javafx.util.StringConverter;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * A {@link StringConverter} to navigate within a collection of objects.<br>
 * Provides navigation with keystrokes that do not correspond to the text displayed in the UI.
 * <p>
 * Example:
 * <pre>
 *   converter = new NavigationLetterStringConverter&lt;SomeType&gt;(someCollection) {
 *     &#64;Override
 *     public String getNavigationLetters(SomeType object) {
 *       return StringHelper.filterUpperCase(object.getName());
 *     }
 *     &#64;Override
 *     public String getName(SomeType object) {
 *       return object.getName();
 *     }
 *   };
 *   sortedObjects = converter.getSortedObjects();    // for ComboBox.getItems().setAll(sortedObjects)
 *   comboBox.setConverter(converter);
 * </pre>
 * Notice that the ComboBox needs a cell-factory to show the object name, otherwise
 * the navigation-letters will be shown instead of the object's name.
 * <p>
 * Notice further, that enhanced camelcase navigation is also available via the
 * {@link org.tentackle.fx.component.config.PrefixSelectionFeature#CAMEL_CASE} property,
 * which is usually much simpler and does not need a {@link NavigationLetterStringConverter} at all.
 *
 * @param <T> the type to convert
 */
public abstract class NavigationLetterStringConverter<T> extends StringConverter<T> {

  private record Key(String navigationLetters, String name) implements Comparable<Key> {

    @Override
    public int compareTo(Key other) {
      int rv = Compare.compare(navigationLetters, other.navigationLetters);
      if (rv == 0) {
        rv = Compare.compare(name, other.name);
      }
      return rv;
    }
  }

  private final TreeMap<Key, T> navigationMap;

  /**
   * Creates a converter.
   *
   * @param objects the objects to navigate within
   */
  public NavigationLetterStringConverter(Collection<T> objects) {
    navigationMap = new TreeMap<>();
    objects.forEach(o -> navigationMap.put(new Key(getNavigationLetters(o), getName(o)), o));
  }

  /**
   * Extracts the navigation letters from an object.
   *
   * @param object the object
   * @return the navigation letters
   */
  public abstract String getNavigationLetters(T object);

  /**
   * Extracts the name from an object.
   *
   * @param object the object
   * @return the object's name
   */
  public abstract String getName(T object);

  /**
   * Gets the objects sorted by navigation letters + name.
   *
   * @return the sorted objects
   */
  public Collection<T> getSortedObjects() {
    return navigationMap.values();
  }

  @Override
  public String toString(T object) {
    return object == null ? null : getNavigationLetters(object);
  }

  @Override
  public T fromString(String string) {
    T object = null;
    if (string != null) {
      Iterator<Map.Entry<Key, T>> iterator = navigationMap.tailMap(new Key(string, null)).entrySet().iterator();
      if (iterator.hasNext()) {
        object = iterator.next().getValue();
      }
    }
    return object;
  }

}

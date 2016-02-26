/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.misc;

/**
 * A convertible object.
 * <p>
 * Convertibles provide a mapping to some other type. Enums, for example,
 * map to an integer or a string and optionally to some application specific types.
 * Any class implementing this interface must provide three methods:
 *
 * <ul>
 *   <li>toExternal: an instance method to convert a given object to another type</li>
 *   <li>toInternal: a class method to create an object from another type</li>
 *   <li>getDefault: a class method to create the default value</li>
 * </ul>
 *
 * Because class methods cannot be inherited, the {@link ConvertibleHelper} resolves
 * the method by reflection.
 * <p>
 * If there is no default value the method must throw a RuntimeException, for example a PersistenceException.
 * <p>
 * Note: the static method by reflection approach has been chosen,
 * because enums are a good candidate for {@link Convertible}s but cannot
 * be instantiated by constructors.
 *
 *
 * <p>
 * Example:
 * <pre>
 *   public enum TestConvertible implements Convertible&lt;Integer&gt; {
 *
 *     ONE(100),
 *     TWO(200),
 *     THREE(300);
 *
 *     private Integer whatsoever;
 *
 *     TestConvertible(Integer whatsoever) {
 *       this.whatsoever = whatsoever;
 *     }
 *
 *     public static TestConvertible toInternal(Integer external) {
 *       if (external != null) {
 *         for (TestConvertible value: values()) {
 *           if (value.whatsoever.equals(external)) {
 *             return value;
 *           }
 *         }
 *       }
 *       throw new IllegalArgumentException("invalid external value " + external);
 *     }
 *
 *     public static TestConvertible getDefault() {
 *       return ONE;
 *     }
 *
 *     public Integer toExternal() {
 *       return whatsoever;
 *     }
 *   }
 * </pre>
 *
 *
 *
 * @param <T> the external type
 * @see ConvertibleHelper
 * @author harald
 */
public interface Convertible<T> {

  /**
   * Convert this object to an external value.
   *
   * @return the external value
   */
  T toExternal();

}

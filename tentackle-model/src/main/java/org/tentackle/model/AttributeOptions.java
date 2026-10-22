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

package org.tentackle.model;

import java.util.List;

/**
 * Options for attributes.
 *
 * @author harald
 */
public interface AttributeOptions extends CommonOptions {

  /**
   * Gets the attribute this option-set belongs to.
   *
   * @return the attribute
   */
  Attribute getAttribute();

  /**
   * Gets the list of annotations.<br>
   * Annotations start with an &#64;. By default, they are applied to the getters
   * of the interfaces only. However, the at-sign may be followed by one or two modifiers
   * that change the code generation:
   *
   * <ul>
   *   <li><code>=</code>: annotate setter only.</li>
   *   <li><code>+</code>: annotate setter and getter.</li>
   *   <li><code>~</code>: annotation is hidden, i.e. applied to the implementations instead of the interfaces.</li>
   * </ul>
   * Example:
   * <pre>
   *   <code>&#64;=~MyAnno</code>
   *   will be applied to the implementation of the setter only.
   * </pre>
   *
   * @return the annotations
   */
  List<String> getAnnotations();

  /**
   * Gets the default value if [DEFAULT value] given.<br>
   * The returned type corresponds to the DataType of the Attribute.
   *
   * @return the defaultValue
   */
  Object getDefaultValue();

  /**
   * Gets the initial value if [INIT value] given.
   *
   * @return the Java code to initialize the declaration with
   */
  String getInitialValue();

  /**
   * Gets the new value if [NEW value] given.
   *
   * @return the Java code to initialize before validation if PDO is persisted the first time
   */
  String getNewValue();

  /**
   * Returns whether this is the object id holding the context.
   *
   * @return true if context id
   */
  boolean isContextId();

  /**
   * Returns whether this is a unique domain key (or part of it).
   *
   * @return true if domain key
   */
  boolean isDomainKey();

  /**
   * Returns whether this is a UTC timestamp.
   *
   * @return true if UTC
   */
  boolean isUTC();

  /**
   * Returns whether to use a dedicated timezone.<br>
   * If set, the PDO must provide a method to configure the timezone
   * for the given attribute of the form:
   * <pre>
   * Calendar get&lt;attribute-name&gt;TimezoneConfig();
   *
   * Example: Calendar getPrintedTimezoneConfig();
   * </pre>
   * <p>
   * This option applies only to predefined data types that provide special methods
   * in {@code ResultSet} and {@code PreparedStatement} with a {@code Calendar} argument for
   * timezone correction (such as {@code Timestamp}, {@code Date} or {@code Time}).
   *
   * @return true if timezone is applied
   */
  boolean isWithTimezone();

  /**
   * Returns whether this is an unsigned numeric field.
   *
   * @return true if unsigned
   */
  boolean isUnsigned();

  /**
   * Returns whether only digits are allowed as input.<br>
   * Applies only to strings. Number types provide their own rules.
   *
   * @return true if allow only digits
   */
  boolean isDigitsOnly();

  /**
   * Returns whether the attribute is not declared in the interface.
   *
   * @return true if implemented only, no public interface definition
   */
  boolean isHidden();

  /**
   * Returns whether the attribute should be skipped in snapshot and copy operations.
   *
   * @return true if skip this attribute, false to include (default)
   */
  boolean isShallow();

  /**
   * Returns whether the attribute becomes part of the normtext.
   *
   * @return true if add to normtext
   */
  boolean isPartOfNormText();

}

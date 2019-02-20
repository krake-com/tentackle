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
   * @return the entity
   */
  Attribute getAttribute();

  /**
   * list of annotations.
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
   * Gets the initial value if [INIT value] given.<br>
   *
   * @return the string to initialize the declaration with
   */
  String getInitialValue();

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
   *
   * @return true if with timezone
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

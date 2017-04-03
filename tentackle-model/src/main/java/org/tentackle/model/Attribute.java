/*
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

package org.tentackle.model;

/**
 * An attribute.
 * <p>
 * Each attribute corresponds to a database column.
 *
 * @author harald
 */
public interface Attribute {

  /**
   * Gets the entity this attribute belongs to.
   *
   * @return the entity
   */
  Entity getEntity();

  /**
   * Gets the Java-Name of the attribute.
   *
   * @return the java name
   */
  String getJavaName();


  /**
   * Gets the database column name.
   *
   * @return the column name (always in lowercase)
   */
  String getColumnName();


  /**
   * Gets the data type.
   *
   * @return the java data type
   */
  DataType getDataType();


  /**
   * Gets the inner name.<br>
   * This is either the generic inner type or the wrapped type
   * used by application specific types,
   * for example enums.
   *
   * @return the wrapping type, null if not wrapped
   * @throws ModelException if not an application type or innername not set
   */
  String getInnerName() throws ModelException;


  /**
   * Gets the column width.
   * <p>
   * For strings this is the maximum number of characters.
   * For numerics this is only a hint for the GUI.
   *
   * @return the size, null if not set
   */
  Integer getSize();

  /**
   * Gets the column width with default 0.
   *
   * @return the column width or 0 if not set
   */
  int getSizeWithDefault();


  /**
   * Gets the scale for numbers with a fraction part.
   *
   * @return the scale, null if not set
   */
  Integer getScale();

  /**
   * Gets the scale width with default 0.
   *
   * @return the scale or 0 if not set
   */
  int getScaleWithDefault();


  /**
   * Gets the options.
   *
   * @return the options
   */
  AttributeOptions getOptions();

  /**
   * Returns whether attribute is implicit and not result of an attribute line configuration.
   *
   * @return true if implicit
   */
  boolean isImplicit();

  /**
   * Gets the application specific type.
   *
   * @return the application specific type, never null or empty
   * @throws ModelException if application specific type not set or this is not {@link DataType#APPLICATION}
   */
  String getApplicationType() throws ModelException;

  /**
   * Gets the innertype for application specific types.
   *
   * @return the inner type to convert to agains the backend
   * @throws ModelException if not application specific type or innertype not a legal DataType.
   */
  DataType getInnerType() throws ModelException;

  /**
   * Gets the effective type exposed to the backend.<br>
   * If this is an application specific type, the inner type will be returned.
   *
   * @return the effective data type
   * @throws ModelException if model garbeled
   */
  DataType getEffectiveType() throws ModelException;

  /**
   * Gets the java type.<br>
   * Returns the java type with optional generic info.
   * <p>
   * Examples:
   * <pre>
   * Date
   * Binary&lt;Invoice&gt;
   * MyType;
   * </pre>
   *
   * @return the type string
   * @throws ModelException if type is misconfigured
   */
  String getJavaType() throws ModelException;

  /**
   * Returns whether database column is nullable.
   *
   * @return true if WITH NULL, false if NOT NULL
   * @throws ModelException if type is misconfigured
   */
  boolean isNullable() throws ModelException;

  /**
   * Gets the associated relation.
   *
   * @return the relation, null if none
   */
  Relation getRelation();

  /**
   * Validates the attribute.
   *
   * @throws ModelException if validation failed
   */
  void validate() throws ModelException;

  /**
   * Gets the suffix to be used in methodnames.
   * <p>
   * Example:
   * <pre>
   * "set" + getMethodNameSuffix() would return "setBlah" if the javaName is "blah".
   * </pre>
   * @return the suffix
   */
  String getMethodNameSuffix();

  /**
   * Gets the getter method name.
   *
   * @return the getter
   */
  String getGetterName();

  /**
   * Gets the setter method name.
   *
   * @return the setter
   */
  String getSetterName();

  /**
   * Returns the @Bindable-annotation text.
   *
   * @return the annotation text, null if none
   */
  String getBindableAnnotation();

}


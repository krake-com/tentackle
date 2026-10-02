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

import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;

import java.util.List;

/**
 * An attribute.
 * <p>
 * Each attribute corresponds to one or more database columns, depending on its {@link DataType}.
 *
 * @author harald
 */
public interface Attribute extends ModelElement {

  /**
   * Gets the entity this attribute belongs to.
   *
   * @return the entity
   */
  Entity getEntity();


  /**
   * Gets the column name.<br>
   * This is the model's column name.<br>
   * According to the {@link DataType}, an attribute may be mapped to
   * more than one database column.
   *
   * @return the column name (always in lowercase)
   * @see #getColumnName(Backend, int)
   * @see #getDataType()
   */
  String getColumnName();


  /**
   * Gets the database column name.<br>
   * For data types with only one column, this is usually the same as the model's column name.
   *
   * @param backend the backend
   * @param columnIndex the column index according to the {@link DataType}
   * @return the column name used by the database
   * @throws ModelException if the data type is misconfigured
   * @see #getColumnName()
   * @see #getDataType()
   */
  String getColumnName(Backend backend, int columnIndex) throws ModelException;


  /**
   * Gets the model's data type.
   *
   * @return the data type
   */
  DataType<?> getDataType();


  /**
   * Gets the inner type name.<br>
   * This is either the generic inner type or the wrapped type of Convertible types.
   *
   * @return the inner type name
   * @throws ModelException if not of an application type or inner name is not set
   */
  String getInnerTypeName() throws ModelException;


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
   * Gets the scale for numbers with a fraction part.
   *
   * @return the scale, null if not set
   */
  Integer getScale();

  /**
   * Gets the options.
   *
   * @return the options
   */
  AttributeOptions getOptions();

  /**
   * Returns whether the attribute is implicit and not result of an attribute line configuration.
   *
   * @return true if implicit
   */
  boolean isImplicit();

  /**
   * Returns whether the attribute is hidden and not part of the interface.
   *
   * @return true if hidden
   */
  boolean isHidden();

  /**
   * Returns whether the attribute's type implements a Convertible.
   *
   * @return true if Convertible
   */
  boolean isConvertible();

  /**
   * Creates an embedded copy of this attribute.
   *
   * @param embeddingEntity the embedding entity
   * @param pathName        the logical pathname
   * @param columnName      the new column name
   * @return the created attribute
   */
  Attribute createEmbedded(Entity embeddingEntity, String pathName, String columnName);

  /**
   * Gets the embedding entity.<br>
   * Please notice that the embedding entity is determined by the specific relation,
   * and {@link #isEmbedded()} just means that the attribute is part of an embeddable entity.
   *
   * @return the embedding entity, null if not an embedded attribute
   */
  Entity getEmbeddingEntity();

  /**
   * Gets the path of embedding entities.<br>
   * The first entity is non-embedded.
   *
   * @return the embedding path (never empty), null if the attribute is not embedded
   */
  List<Entity> getEmbeddingPath();

  /**
   * Gets the pathname.<br>
   * The pathname is identical to the Java name for non-embedded entities.<br>
   * For embedded entities, the pathname consists of the dotted path from the parent entity to the embedded attribute.
   *
   * @return the pathname
   */
  String getPathName();

  /**
   * Returns whether the attribute belongs to an embedded entity.
   *
   * @return true if embedded
   */
  boolean isEmbedded();

  /**
   * Gets the name of the application-specific type, if a Convertible type.
   *
   * @return the application-specific type, never null or empty
   * @throws ModelException if this is not a Convertible type
   */
  String getApplicationTypeName() throws ModelException;

  /**
   * Gets the inner datatype exposed to the backend if Convertible.
   *
   * @return the inner type for the backend
   * @throws ModelException if not a Convertible
   */
  DataType<?> getInnerDataType() throws ModelException;

  /**
   * Gets the effective type exposed to the backend.<br>
   * If this is an application-specific type, the inner type will be returned.
   *
   * @return the effective data type
   * @throws ModelException if model garbled
   */
  DataType<?> getEffectiveDataType() throws ModelException;

  /**
   * Gets the Java type.<br>
   * Returns the Java type with optional generic info.
   * <p>
   * Examples:
   * <pre>
   * Date
   * Binary&lt;Invoice&gt;
   * MyType
   * </pre>
   *
   * @return the type string
   * @throws ModelException if the type is misconfigured
   */
  String getJavaType() throws ModelException;

  /**
   * Returns whether the database column is nullable.
   *
   * @return true if WITH NULL, false if NOT NULL
   * @throws ModelException if the type is misconfigured
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
   * Gets the suffix to be used in method names.
   * <p>
   * Example:
   * <pre>
   * "set" + getMethodNameSuffix() would return "setBlah" if the javaName is "blah".
   * </pre>
   *
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

  /**
   * Converts a given value string to a valid Java method argument.<br>
   * Adds a downcast, if necessary.
   *
   * @param value the value string
   * @return the method argument string
   * @throws ModelException if the data type is misconfigured
   */
  String toMethodArgument(String value) throws ModelException;

}

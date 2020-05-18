/*
 * Tentackle - https://tentackle.org.
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
 * A relation to an entity.
 *
 * @author harald
 */
public interface Relation extends ModelElement {

  /**
   * Gets the entity this relation belongs to.
   *
   * @return the entity
   */
  Entity getEntity();

  /**
   * Gets the related entity.
   *
   * @return the foreign entity
   */
  Entity getForeignEntity();

  /**
   * Gets the associated attribute.
   *
   * @return the attribute, null if ID of foreign entity
   */
  Attribute getAttribute();

  /**
   * Gets the foreign attribute.
   *
   * @return the attribute, null if ID of this entity
   */
  Attribute getForeignAttribute();

  /**
   * Gets the foreign (opposite) relation.
   *
   * @return the foreign relation, null if no such relation
   */
  Relation getForeignRelation();

  /**
   * Gets the N:M relation of the foreign entity.
   *
   * @return the nm-relation, null if this is no nm-relation
   */
  Relation getNmRelation();

  /**
   * Gets the relation of the entity defining the nm-relation.
   *
   * @return the defining relation
   */
  Relation getDefiningNmRelation();

  /**
   * Gets the comment.
   *
   * @return the comment
   */
  String getComment();

  /**
   * Gets the name of the related class.
   *
   * @return the classname
   */
  String getClassName();

  /**
   * Gets the access scope for generated methods.
   *
   * @return the scope
   */
  AccessScope getAccessScope();

  /**
   * Gets the list of annotations.<br>
   * Annotations start with an &#64; and are applied to the interfaces.
   * However, if the at-sign is followed by a <code>~</code>, the implementation
   * will be annotated instead.
   *
   * @return the annotations
   */
  List<String> getAnnotations();

  /**
   * Gets the list of custom stereotypes.
   *
   * @return the stereotypes
   */
  List<String> getStereotypes();


  // relation = ....

  /**
   * Gets the relation type.
   *
   * @return list or object
   */
  RelationType getRelationType();

  /**
   * Returns whether the relation points to components of a composite.
   *
   * @return true if composite relation
   */
  boolean isComposite();

  /**
   * Returns whether the modification state of the relation is available.
   *
   * @return true if tracked object or tracked list
   */
  boolean isTracked();

  /**
   * Returns whether the parent object is referenced by a variable.
   *
   * @return true if referenced
   */
  boolean isReferenced();

  /**
   * Returns whether a process-method is invoked after initialization, select and before save.<br>
   * The method has the same name as the getter with "get" replaced by "process" and must be
   * provided by the application.<br>
   * This flag applies to composite relations only.
   *
   * @return true if processed
   */
  boolean isProcessed();

  /**
   * Returns whether the relation is read only.
   *
   * @return true if no setter method
   */
  boolean isReadOnly();

  /**
   * Returns whether the relation is write-only.
   *
   * @return true if no getter method
   */
  boolean isWriteOnly();

  /**
   * Returns whether the relation is serialized.<br>
   * Used for non-composite relations to make them non-transient.
   *
   * @return true if serialized, false if transient
   */
  boolean isSerialized();

  /**
   * Returns whether the relation should be cleared before save.<br>
   * Used for non-composite serialized relations that should not be transmitted to the server.
   *
   * @return true if transient on save
   */
  boolean isClearOnRemoteSave();

  /**
   * Returns whether this is list relation with only one element.<br>
   * A.k.a. reversed 1:1 object relation.
   *
   * @return true if reversed
   */
  boolean isReversed();

  /**
   * Returns whether the relation should be skipped in snapshot and copy operations.
   *
   * @return true if skip this relation, false to include (default)
   */
  boolean isShallow();

  /**
   * Returns whether the relation should be set to immutable after loading.
   *
   * @return true if immutable
   */
  boolean isImmutable();

  /**
   * Returns whether the relation becomes part of the normtext.
   *
   * @return true if add to normtext
   */
  boolean isPartOfNormText();

  /**
   * Returns whether the blunt-method becomes part if the interface.
   *
   * @return true if declared in interface, false if only implementation generated
   */
  boolean isBluntDeclared();

  /**
   * Returns the attribute that holds the number of elements of a 1:N relation.<br>
   * Applies only to composite non-reversed list relations.<br>
   * If set, this column gets the number of elements in the list when the PDO is persisted.
   * This avoids an initial select when the list is accessed the first time and the list is empty.
   *
   * @return the counter attribute, null if none
   */
  Attribute getCountAttribute();


  // select = ...

  /**
   * Gets the selection type.
   *
   * @return the selection tupe
   */
  SelectionType getSelectionType();

  /**
   * Returns whether the select method is a caching one.
   *
   * @return true if cached
   */
  boolean isSelectionCached();

  /**
   * Gets the optional wurblet arguments.<br>
   * Only available for composite list relations.
   *
   * @return the extra wurblet args
   */
  String getSelectionWurbletArguments();


  // delete = ...

  /**
   * Returns whether delete method is invoked on main class instead of relation class.
   *
   * @return true if delete from main class
   */
  boolean isDeletionFromMainClass();

  /**
   * Returns whether deletion is cascaded to elements in lists.
   *
   * @return true if cascaded
   */
  boolean isDeletionCascaded();


  // link = ...

  /**
   * Gets the name for the link method.
   *
   * @return the link method, null if default
   */
  String getLinkMethodName();

  /**
   * Returns the optional link method index parameter.
   *
   * @return the index parameter, null if no index
   */
  String getLinkMethodIndex();


  // method = ...

  /**
   * Gets the name-part for select- and delete-methods.
   *
   * @return the method name, null if default from foreign entity name
   */
  String getMethodName();


  // args = ...

  /**
   * Gets the effective method arguments for select- and delete-methods.<br>
   *
   * @return the args, never null, at least 1 element
   */
  List<MethodArgument> getMethodArgs();


  // nm = ...

  /**
   * Gets the name of the nm-relation.
   *
   * @return the n:m relation name, null if this is not an n:m-relation
   */
  String getNmName();

  /**
   * Same as {@link #getMethodName()} but for nm relation.
   *
   * @return the list method name, null if default
   */
  String getNmMethodName();


  // ---------------------------------------------------------------------------

  /**
   * Validates the relation.
   *
   * @throws ModelException if validation failed
   */
  void validate() throws ModelException;

  /**
   * Gets the variable name.<br>
   *
   * @return the variable name
   */
  String getVariableName();

  /**
   * Gets the suffix to be used in methodnames.
   * <p>
   * Example:
   * <pre>
   * "set" + getMethodNameSuffix() would return "setBlah" if the
   * classname is "Blah" or the name is "blah".
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
   * Gets the comment line for getter and setter.
   *
   * @return the comment
   */
  String getGetterSetterComment();

  /**
   * Gets the declared java type of the relation.
   *
   * @param withinForeignEntity true if we need the type from within the foreign entity,
   *                            else from within the relation's entity
   * @return the declared java type
   */
  String getDeclaredJavaType(boolean withinForeignEntity);

  /**
   * Gets the effective java type of the relation.
   * <p>
   * Example: the effective type is ArrayList and the declared type is List.
   *
   * @return the effective java type
   */
  String getJavaType();

  /**
   * Returns whether this relation is a deep reference.<br>
   * Deep references point to components of another composite entity.
   *
   * @return true if deep reference
   */
  boolean isDeepReference();

}

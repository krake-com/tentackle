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

package org.tentackle.validate;

import java.io.Serializable;

/**
 * The result of a validation.
 * <p>
 * Validation results are serializable, however,
 * the validation context and the validator are transient and
 * therefore null if deserialized!
 *
 * @author harald
 */
public interface ValidationResult extends Serializable {

  /**
   * Indicates that this result must be treated as failed.
   *
   * @return true if failed
   */
  boolean hasFailed();

  /**
   * Gets the error code.<br>
   * The error code is optional and provided to identify
   * an error by a unique code rather than some text message.
   *
   * @return the error code, null if none
   */
  String getErrorCode();

  /**
   * Indicates that this result provides a message.
   *
   * @return true if there is a message
   */
  boolean hasMessage();

  /**
   * Gets the message of this result.
   *
   * @return the message, null if none
   */
  String getMessage();

  /**
   * Gets the validation path.<br>
   * Applications may use it to associate the validation result with a component.
   *
   * @return the validation path
   */
  String getValidationPath();

  /**
   * Gets the configured scopes for this validation.
   *
   * @return the configured scopes
   */
  Class<? extends ValidationScope>[] getConfiguredScopes();


  
  /**
   * Gets the validator that created this result.
   * <p>
   * Notice that the validator is null for results sent over the wire,
   * since it is transient!
   *
   * @return the validator, null if received via a remote session
   */
  Validator getValidator();

  /**
   * Gets the validation context.
   * <p>
   * Notice that the context is null for results sent over the wire,
   * since it is transient!
   *
   * @return the validation context, null if received via a remote session
   */
  ValidationContext getValidationContext();




  /**
   * A collection index within a validation path.
   *
   * @param index the index within the collection
   * @param pathPrefix the leading part of the path
   * @param pathPostfix the trailing part, empty if the path ends with the index
   */
  record CollectionIndex(int index, String pathPrefix, String pathPostfix) implements Serializable {}


  /**
   * Gets all {@link CollectionIndex}es for this validation result.
   * <p>
   * Example:
   * <pre>
   * "invoice.invoiceLineList[3].referenceList[0].text"
   * </pre>
   * would return 2 indexes with the following contents:
   * <pre>
   *   { 3, "invoice.invoiceLineList", "referenceList[0].text" },
   *   { 0, "invoice.invoiceLineList[3].referenceList", "text" }
   * </pre>
   * If the path ends with an index, the postfix of the last index is the empty string.
   *
   * @return the indexes, empty array if validation path without indexes
   * @throws ValidationRuntimeException if decoding the path failed
   */
  CollectionIndex[] getValidationCollectionIndexes();

}

/*
 * Tentackle - http://www.tentackle.org.
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

import java.util.HashSet;
import java.util.Set;

/**
 * Model parsing exception.
 *
 * @author harald
 */
public class ModelException extends Exception {

  private static final long serialVersionUID = 3405985077474898397L;

  private SourceInfo sourceInfo;  // the optional source information, null if unknown
  private Set<Entity> entities;   // related entities


  /**
   * Creates a model exception.
   *
   * @param message the error message
   * @param sourceInfo the source info, null if none
   * @param cause the cause
   */
  public ModelException(String message, SourceInfo sourceInfo, Throwable cause) {
    super(message, cause);
    this.sourceInfo = sourceInfo;
  }

  /**
   * Creates a model exception.
   *
   * @param message the error message
   * @param entity the related entity
   * @param cause the cause
   */
  public ModelException(String message, Entity entity, Throwable cause) {
    super(message, cause);
    if (entity != null) {
      getEntities().add(entity);
    }
  }

  /**
   * Creates a model exception.
   *
   * @param message the error message
   * @param cause the cause
   */
  public ModelException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a model exception.
   *
   * @param message the error message
   * @param sourceInfo the source info, null if none
   */
  public ModelException(String message, SourceInfo sourceInfo) {
    super(message);
    this.sourceInfo = sourceInfo;
  }

  /**
   * Creates a model exception.
   *
   * @param message the error message
   * @param entity the related entity
   */
  public ModelException(String message, Entity entity) {
    super(message);
    if (entity != null) {
      getEntities().add(entity);
    }
  }

  /**
   * Creates a model exception.
   *
   * @param message the error message
   */
  public ModelException(String message) {
    super(message);
  }

  /**
   * Gets the source info.
   *
   * @return the source info, null if unknown
   */
  public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  /**
   * Gets the (first) related entity.
   *
   * @return the entity related to this exception, null if none
   */
  public Entity getEntity() {
    return getEntities().isEmpty() ? null : getEntities().iterator().next();
  }

  /**
   * Gets the related entities.<br>
   * If related to more than one entity.
   *
   * @return
   */
  public Set<Entity> getEntities() {
    if (entities == null) {
      entities = new HashSet<>();
    }
    return entities;
  }

  @Override
  public String getMessage() {
    StringBuilder msg = new StringBuilder(super.getMessage());
    if (sourceInfo != null) {
      msg.append(" (").append(sourceInfo).append(")");
    }
    if (!getEntities().isEmpty()) {
      msg.append("\naffected Entities: ");
      boolean first = true;
      for (Entity entity: getEntities()) {
        if (first) {
          first = false;
        }
        else  {
          msg.append(", ");
        }
        msg.append(entity);
      }
    }
    return msg.toString();
  }

}

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
 *
 */

package org.tentackle.persist.wurblet;

import java.util.Objects;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.wurbelizer.wurbel.WurbelException;

/**
 * Describes the join to a component's attribute.
 *
 * @author harald
 */
public class ComponentJoin {

  private final Entity entity;                      // the left side of the join
  private final Entity joinedEntity;                // the right side of the join
  private final String rootIdColumnName;            // the CN_... name of the rootid
  private final String rootIdClassName;             // the peristence implementation class name holding the rootid (and optional rootclassid)
  private final String extraClassName;              // != null if attribute belongs to a table that needs an extra join to the rootid's table



  /**
   * Creates a component join from a wurblet parameter.
   *
   * @param wurblet the wurblet
   * @param entity the entity to join to (left side of the join)
   * @param joinedEntity the entity to join (right side of the join)
   * @throws WurbelException if given parameter is not a component key
   */
  public ComponentJoin(DbModelWurblet wurblet, Entity entity, Entity joinedEntity) throws WurbelException {

    this.entity = entity;
    this.joinedEntity = joinedEntity;

    Entity topEntity = joinedEntity.getHierarchyInheritanceType() == InheritanceType.PLAIN ? joinedEntity : joinedEntity.getTopSuperEntity();
    Attribute rootAttribute = null;
    if (topEntity.equals(joinedEntity)) {
      extraClassName = null;
      topEntity = joinedEntity;
      while (topEntity != null) {
        rootAttribute = topEntity.getRootAttribute();
        if (rootAttribute != null) {
          break;
        }
        topEntity = topEntity.getSuperEntity();
      }
    }
    else  {
      extraClassName = wurblet.deriveClassNameForEntity(joinedEntity);
      rootAttribute = topEntity.getRootAttribute();
    }

    if (rootAttribute != null) {    // no extra column: rootId is some parentid or whatever
      Entity rootAttributeEntity = rootAttribute.getEntity().getInheritanceType() == InheritanceType.PLAIN ?
              joinedEntity : rootAttribute.getEntity();
      rootIdClassName = wurblet.deriveClassNameForEntity(rootAttributeEntity);
      rootIdColumnName = rootIdClassName + ".CN_" + rootAttribute.getJavaName().toUpperCase();
    }
    else  {
      rootIdClassName = wurblet.deriveClassNameForEntity(joinedEntity);
      rootIdColumnName = "CN_ROOTID";
    }
  }


  /**
   * Gets the entity to join to (left side of the join).
   *
   * @return the entity
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the entity to join (right side of the join).
   *
   * @return the joined entity
   */
  public Entity getJoinedEntity() {
    return joinedEntity;
  }


  /**
   * Returns whether the joined attribute belongs to another table than that holding the rootid.
   *
   * @return true if extra join needed
   */
  public boolean isExtraJoinNecessary() {
    return extraClassName != null;
  }

  /**
   * Returns the extra classname to be joined by id.<br>
   * Necessary for multi-table inherited joins if attribute belongs to child class.
   *
   * @return the extra classname
   */
  public String getExtraClassName() {
    return extraClassName;
  }

  /**
   * Gets the implementing classname holding the root id.
   *
   * @return the class name
   */
  public String getRootIdClassName() {
    return rootIdClassName;
  }

  /**
   * Gets the column name holding the rootid.
   *
   * @return the column name
   */
  public String getRootIdColumnName() {
    return rootIdColumnName;
  }


  @Override
  public int hashCode() {
    int hash = 7;
    hash = 79 * hash + Objects.hashCode(this.rootIdClassName);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ComponentJoin other = (ComponentJoin) obj;
    return Objects.equals(this.rootIdClassName, other.rootIdClassName);
  }

}

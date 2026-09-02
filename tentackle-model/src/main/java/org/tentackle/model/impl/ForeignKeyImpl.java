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

package org.tentackle.model.impl;

import org.tentackle.common.Compare;
import org.tentackle.model.Attribute;
import org.tentackle.model.CodeFactory;
import org.tentackle.model.Deferrability;
import org.tentackle.model.Entity;
import org.tentackle.model.ForeignKey;
import org.tentackle.sql.Backend;

import java.util.Objects;

/**
 * Implementation of a foreign key.
 *
 * @author harald
 */
public class ForeignKeyImpl implements ForeignKey, Comparable<ForeignKey> {

  private final Entity referencingEntity;
  private final Attribute referencingAttribute;
  private final Entity referencedEntity;
  private final Attribute referencedAttribute;
  private boolean composite;
  private Deferrability deferrability = Deferrability.NOT_DEFERRABLE;


  /**
   * Creates a foreign key.
   *
   * @param referencingEntity the referencing entity
   * @param referencingAttribute the referenced entity
   * @param referencedEntity the referenced entity
   * @param referencedAttribute the referenced attribute
   * @param composite true if cascade on delete and update
   */
  public ForeignKeyImpl(Entity referencingEntity, Attribute referencingAttribute,
                        Entity referencedEntity, Attribute referencedAttribute,
                        boolean composite) {

    this.referencingEntity = referencingEntity;
    this.referencingAttribute = referencingAttribute;
    this.referencedEntity = referencedEntity;
    this.referencedAttribute = referencedAttribute;
    this.composite = composite;
  }


  @Override
  public int hashCode() {
    int hash = 7;
    hash = 89 * hash + Objects.hashCode(this.referencingEntity);
    hash = 89 * hash + Objects.hashCode(this.referencingAttribute);
    hash = 89 * hash + Objects.hashCode(this.referencedAttribute);
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
    final ForeignKeyImpl other = (ForeignKeyImpl) obj;
    if (!Objects.equals(this.referencingEntity, other.referencingEntity)) {
      return false;
    }
    if (!Objects.equals(this.referencingAttribute, other.referencingAttribute)) {
      return false;
    }
    return Objects.equals(this.referencedAttribute, other.referencedAttribute);
  }

  @Override
  public int compareTo(ForeignKey o) {
    int rv = Compare.compare(getReferencingEntity().getName(), o.getReferencingEntity().getName());
    if (rv == 0) {
      rv = Compare.compare(getReferencingAttribute().getPathName(), o.getReferencingAttribute().getPathName());
    }
    return rv;
  }

  @Override
  public Entity getReferencingEntity() {
    return referencingEntity;
  }

  @Override
  public Attribute getReferencingAttribute() {
    return referencingAttribute;
  }

  @Override
  public Entity getReferencedEntity() {
    return referencedEntity;
  }

  @Override
  public Attribute getReferencedAttribute() {
    return referencedAttribute;
  }

  @Override
  public boolean isComposite() {
    return composite;
  }


  public void setComposite(boolean composite) {
    this.composite = composite;
  }

  @Override
  public Deferrability getDeferrability() {
    return deferrability;
  }

  public void setDeferrability(Deferrability deferrability) {
    this.deferrability = deferrability;
  }


  @Override
  public String sqlCreateForeignKey(Backend backend) {
    return CodeFactory.getInstance().createSqlForeignKey(this, backend);
  }


  @Override
  public Entity getReferencingTableProvidingEntity() {
    Entity entity = getReferencingEntity().getTableProvidingEntity();
    if (entity == null) {
      entity = getReferencingEntity();
    }
    return entity;
  }

  @Override
  public Entity getReferencedTableProvidingEntity() {
    Entity entity = getReferencedEntity().getTableProvidingEntity();
    if (entity == null) {
      entity = getReferencedEntity();
    }
    return entity;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (referencingEntity != null) {
      buf.append(referencingEntity).append('.').append(referencingAttribute);
    }
    buf.append(": ");
    if (referencedEntity != null) {
      buf.append(referencedEntity).append('.').append(referencedAttribute);
    }
    return buf.toString();
  }

}

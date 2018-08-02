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

package org.tentackle.maven.wizard;

import org.tentackle.bind.Bindable;
import org.tentackle.fx.Fx;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.validate.ScopeConfigurator;
import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.scope.ChangeableScope;
import org.tentackle.validate.scope.InteractiveScope;
import org.tentackle.validate.scope.MandatoryScope;
import org.tentackle.validate.validator.Changeable;
import org.tentackle.validate.validator.GreaterOrEqual;
import org.tentackle.validate.validator.NotNull;
import org.tentackle.validate.validator.True;

import java.util.ArrayList;
import java.util.List;

/**
 * Generator for the PDO files.
 */
public class PdoGenerator implements ScopeConfigurator, Validateable {

  private PdoProfile profile;
  private String entityName;
  private Integer classId;
  private String tableName;
  private Entity superEntity;
  private InheritanceType inheritanceType;
  private String shortDescription;
  private String longDescription;
  private String domainInterface;
  private String persistenceInterface;
  private String domainImplementation;
  private String persistenceImplementation;
  private boolean remoteEnabled;


  /**
   * Creates the PDO files.
   */
  public List<ValidationResult> generate() {
    List<ValidationResult> results = validate(null, ValidationScopeFactory.getInstance().getMandatoryScope());
    results.addAll(validate(null, ValidationScopeFactory.getInstance().getInteractiveScope()));
    return results;
  }


  public boolean isNewEntity() {
    try {
      return entityName == null || Model.getInstance().getByEntityName(entityName) == null;
    }
    catch (ModelException e) {
      Fx.error("cannot check for duplicate entity", e);
      return true;
    }
  }

  public boolean isNewClassId() {
    if (classId != null && classId > 0) {
      try {
        for (Entity entity : Model.getInstance().getAllEntitites()) {
          if (entity.getClassId() == classId) {
            return false;
          }
        }
      }
      catch (ModelException e) {
        Fx.error("cannot check for duplicate entity", e);
      }
    }
    return true;
  }

  public boolean isClassIdNecessary() {
    boolean necessary = InheritanceType.NONE == inheritanceType;
    if (!necessary) {
      classId = null;
    }
    return necessary;
  }

  public boolean isNewTableName() {
    if (tableName != null) {
      try {
        for (Entity entity : Model.getInstance().getAllEntitites()) {
          if (tableName.equals(entity.getTableName())) {
            return false;
          }
        }
      }
      catch (ModelException e) {
        Fx.error("cannot check for duplicate entity", e);
      }
    }
    return true;
  }

  public boolean isTableNameNecessary() {
    boolean necessary = false;
    if (inheritanceType != null) {
      switch (inheritanceType) {
        case NONE:
          necessary = superEntity == null ||
                      superEntity.getInheritanceType().isMappingToNoTable() ||
                      superEntity.getInheritanceType().isMappingToOwnTable();
          break;

        case SINGLE:
          necessary = superEntity == null;
          break;

        case MULTI:
          necessary = true;
          break;
      }
    }
    if (!necessary) {
      tableName = null;
    }
    return necessary;
  }

  public boolean isInheritanceTypeValid() {
    return InheritanceType.NONE == inheritanceType ||
           superEntity == null ||
           superEntity.getInheritanceType() == inheritanceType;
  }


  @Bindable
  @NotNull
  public PdoProfile getProfile() {
    return profile;
  }

  @Bindable
  public void setProfile(PdoProfile profile) {
    this.profile = profile;
  }

  @Bindable
  @NotNull(message = "missing entity name")
  @True(value = "$isNewEntity", scope = InteractiveScope.class, message = "entity already exists")
  public String getEntityName() {
    return entityName;
  }

  @Bindable
  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  @Bindable
  @True(value = "$isNewClassId", scope = InteractiveScope.class, message = "class ID already in use")
  @Changeable(condition = "$isClassIdNecessary")
  @GreaterOrEqual(value = "100", condition = "$isClassIdNecessary",
                  message = "class IDs below 100 are reserved for tentackle", scope = InteractiveScope.class)
  @NotNull(condition = "$isClassIdNecessary", message = "missing class ID")
  public Integer getClassId() {
    return classId;
  }

  @Bindable
  public void setClassId(Integer classId) {
    this.classId = classId;
  }

  @Bindable
  @True(value = "$isNewTableName", scope = InteractiveScope.class, message = "table name already in use")
  @Changeable(condition = "$isTableNameNecessary")
  @NotNull(condition = "$isTableNameNecessary", message = "missing table name")
  public String getTableName() {
    return tableName;
  }

  @Bindable
  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  @Bindable
  public Entity getSuperEntity() {
    return superEntity;
  }

  @Bindable
  public void setSuperEntity(Entity superEntity) {
    this.superEntity = superEntity;
  }

  @Bindable
  @NotNull
  @True(value = "$isInheritanceTypeValid", scope = InteractiveScope.class, message = "inheritance type does not match super entity")
  public InheritanceType getInheritanceType() {
    return inheritanceType;
  }

  @Bindable
  public void setInheritanceType(InheritanceType inheritanceType) {
    this.inheritanceType = inheritanceType;
  }

  @Bindable
  @NotNull(message = "missing short description")
  public String getShortDescription() {
    return shortDescription;
  }

  @Bindable
  public void setShortDescription(String shortDescription) {
    this.shortDescription = shortDescription;
  }

  @Bindable
  public String getLongDescription() {
    return longDescription;
  }

  @Bindable
  public void setLongDescription(String longDescription) {
    this.longDescription = longDescription;
  }

  @Bindable
  @NotNull(message = "missing short domain interface")
  public String getDomainInterface() {
    return domainInterface;
  }

  @Bindable
  public void setDomainInterface(String domainInterface) {
    this.domainInterface = domainInterface;
  }

  @Bindable
  @NotNull(message = "missing persistence interface")
  public String getPersistenceInterface() {
    return persistenceInterface;
  }

  @Bindable
  public void setPersistenceInterface(String persistenceInterface) {
    this.persistenceInterface = persistenceInterface;
  }

  @Bindable
  public String getDomainImplementation() {
    return domainImplementation;
  }

  @Bindable
  public void setDomainImplementation(String domainImplementation) {
    this.domainImplementation = domainImplementation;
  }

  @Bindable
  public String getPersistenceImplementation() {
    return persistenceImplementation;
  }

  @Bindable
  public void setPersistenceImplementation(String persistenceImplementation) {
    this.persistenceImplementation = persistenceImplementation;
  }

  @Bindable
  public boolean isRemoteEnabled() {
    return remoteEnabled;
  }

  @Bindable
  public void setRemoteEnabled(boolean remoteEnabled) {
    this.remoteEnabled = remoteEnabled;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Class<? extends ValidationScope>[] getDefaultScopes() {
    return new Class[] { MandatoryScope.class, ChangeableScope.class};
  }

  @Override
  public List<ValidationResult> validate(String validationPath, ValidationScope scope) {
    return ValidationUtilities.getInstance().validate(this, validationPath, scope);
  }

}

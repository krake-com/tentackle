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

package org.tentackle.maven.plugin.wizard;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import org.tentackle.bind.Bindable;
import org.tentackle.buildsupport.codegen.GeneratedFile;
import org.tentackle.common.NamingRules;
import org.tentackle.common.Settings;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.scope.InteractiveScope;
import org.tentackle.validate.validator.Changeable;
import org.tentackle.validate.validator.GreaterOrEqual;
import org.tentackle.validate.validator.NotNull;
import org.tentackle.validate.validator.True;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generator for the PDO files.<br>
 * Used by the interactive PDO wizard and the batch mode of the {@code pdo} goal.
 */
public class PdoGenerator extends AbstractValidateableGenerator {

  private File statusDir;
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
  private boolean cacheEnabled;
  private String modelSource;


  /**
   * Creates a new generator.
   *
   * @param model the model
   */
  public PdoGenerator(Model model) {
    super(model);
  }


  /**
   * Sets the status directory.
   *
   * @param statusDir the status dir
   */
  public void setStatusDir(File statusDir) {
    this.statusDir = statusDir;
  }

  /**
   * Creates the PDO files.<br>
   * The generator is validated first, and the files are only created if there are no validation errors.
   * If a class ID was used, the next free class ID is stored in the status file of the profile.
   *
   * @return the validation results, empty if okay, never null
   * @throws IOException if file creation failed
   * @throws TemplateException if code generation failed
   */
  public List<ValidationResult> generate() throws IOException, TemplateException {
    List<ValidationResult> results = validate(null, ValidationScopeFactory.getInstance().getMandatoryScope());
    results.addAll(validate(null, ValidationScopeFactory.getInstance().getInteractiveScope()));
    if (results.isEmpty()) {
      // configure freemarker
      Configuration cfg = createFreemarkerConfiguration();

      // create freemarker model
      PdoTemplateModel templateModel = new PdoTemplateModel(this);

      // create targets
      List<GeneratedFile> generatedFiles = new ArrayList<>();
      generatedFiles.add(new GeneratedFile(cfg, templateModel,"PdoInterface.ftl",
                         new File(getProfile().getPdoPackageInfo().getPath(), getEntityName() + Constants.JAVA_EXT)));
      generatedFiles.add(new GeneratedFile(cfg, templateModel,"DomainInterface.ftl",
                         new File(getProfile().getDomainPackageInfo().getPath(), getDomainInterface() + Constants.JAVA_EXT)));
      generatedFiles.add(new GeneratedFile(cfg, templateModel,"PersistenceInterface.ftl",
                         new File(getProfile().getPersistencePackageInfo().getPath(), getPersistenceInterface() + Constants.JAVA_EXT)));
      if (getDomainImplementation() != null) {
        generatedFiles.add(new GeneratedFile(cfg, templateModel,"DomainImplementation.ftl",
                           new File(getProfile().getDomainImplPackageInfo().getPath(), getDomainImplementation() + Constants.JAVA_EXT)));
      }
      if (getPersistenceImplementation() != null) {
        generatedFiles.add(new GeneratedFile(cfg, templateModel,"PersistenceImplementation.ftl",
                           new File(getProfile().getPersistenceImplPackageInfo().getPath(), getPersistenceImplementation() + Constants.JAVA_EXT)));
      }

      // generate targets
      for (GeneratedFile generatedFile: generatedFiles) {
        generatedFile.generate();
      }

      // save class ID status if any classId created
      if (classId != null) {
        statusDir.mkdirs();
        try (FileWriter statusWriter = new FileWriter(new File(statusDir, profile.getName() + Constants.CLASSID_EXT), Settings.getEncodingCharset())) {
          statusWriter.append(Integer.toString(classId + 1));
        }
        if (classId >= profile.getMinClassId()) {
          // next entity generated in the same run (batch mode) must not get the same class ID
          profile.setMinClassId(classId + 1);
        }
      }
    }
    return results;
  }


  /**
   * Checks whether the entity name is not already used in the model.
   *
   * @return true if ok
   * @throws ModelException if failed
   */
  public boolean isNewEntity() throws ModelException {
    return entityName == null || getModel().getByEntityName(entityName) == null;
  }

  /**
   * Checks whether classID is not already used in the model.
   *
   * @return true if ok
   * @throws ModelException if failed
   */
  public boolean isNewClassId() throws ModelException {
    if (classId != null && classId > 0) {
      for (Entity entity : getModel().getAllEntities()) {
        if (entity.getClassId() == classId) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Checks whether the entity needs a class ID.<br>
   * Clears the class ID if not.
   *
   * @return true if class ID necessary
   */
  public boolean isClassIdNecessary() {
    boolean necessary = InheritanceType.NONE == inheritanceType;
    if (!necessary) {
      classId = null;
    }
    return necessary;
  }

  /**
   * Returns whether extending a super entity is allowed.<br>
   * Clears the super entity if not.
   *
   * @return true if allowed, else no inheritance
   */
  public boolean isSuperEntityAllowed() {
    boolean allowed = InheritanceType.EMBEDDED != inheritanceType;
    if (!allowed) {
      superEntity = null;
    }
    return allowed;
  }

  /**
   * Checks whether the table name is not already used in the model.
   *
   * @return true if ok
   * @throws ModelException if failed
   */
  public boolean isNewTableName() throws ModelException {
    if (tableName != null) {
      for (Entity entity : getModel().getAllEntities()) {
        if (tableName.equalsIgnoreCase(entity.getTableName())) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Checks whether the entity needs a table name.<br>
   * Clears the table name if not.
   *
   * @return true if table name necessary
   */
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

  /**
   * Checks whether the table name is set properly by the user.<br>
   * A table name consisting of the profile's table prefix only is not valid.
   *
   * @return {@link Boolean#TRUE} if valid, null if missing (so that {@code @NotNull} triggers)
   */
  public Boolean isTableNameValid() {
    return tableName != null && (profile == null || profile.getTablePrefix() == null || !tableName.equals(profile.getTablePrefix())) ?
           Boolean.TRUE : null;   // TRUE != null, null -> @NotNull triggers
  }

  /**
   * Checks whether the inheritance type is valid.
   *
   * @return true if valid, false if not
   */
  public boolean isInheritanceTypeValid() {
    return InheritanceType.NONE == inheritanceType ||
           superEntity == null ||
           superEntity.getInheritanceType() == inheritanceType;
  }

  /**
   * Checks whether caching is allowed.
   *
   * @return true if allowed, false if not
   */
  public boolean isCachingAllowed() {
    return inheritanceType != InheritanceType.EMBEDDED;
  }


  // ------------------------------------- model -------------------------------------------------


  /**
   * Gets the profile.
   *
   * @return the profile
   */
  @Bindable
  @NotNull
  public PdoProfile getProfile() {
    return profile;
  }

  /**
   * Sets the profile.
   *
   * @param profile the profile
   */
  @Bindable
  public void setProfile(PdoProfile profile) {
    if (isTableNameNecessary()) {
      if (this.profile != null) {
        String oldPrefix = this.profile.getTablePrefix();
        if (oldPrefix != null && tableName != null && tableName.startsWith(oldPrefix)) {
          tableName = tableName.substring(oldPrefix.length());
          if (tableName.isEmpty()) {
            tableName = null;
          }
        }
      }
      if (profile != null && profile.getTablePrefix() != null) {
        if (tableName == null) {
          tableName = profile.getTablePrefix();
        }
        else {
          tableName = profile.getTablePrefix() + tableName;
        }
      }
    }

    this.profile = profile;
  }

  /**
   * Gets the entity name, i.e. the name of the PDO interface.
   *
   * @return the entity name
   */
  @Bindable
  @NotNull(message = "missing entity name")
  @True(value = "$isNewEntity", scope = InteractiveScope.class, message = "entity already exists")
  public String getEntityName() {
    return entityName;
  }

  /**
   * Sets the entity name, i.e. the name of the PDO interface.<br>
   * Also sets the derived names of the interfaces and implementations.
   *
   * @param entityName the entity name
   */
  @Bindable
  public void setEntityName(String entityName) {
    this.entityName = entityName;
    if (entityName == null) {
      domainInterface = null;
      domainImplementation = null;
      persistenceInterface = null;
      persistenceImplementation = null;
    }
    else {
      NamingRules namingRules = NamingRules.getInstance();
      domainInterface = namingRules.getPdoDomainInterface(entityName);
      domainImplementation = namingRules.getPdoDomainImplementation(entityName);
      persistenceInterface = namingRules.getPdoPersistenceInterface(entityName);
      persistenceImplementation = namingRules.getPdoPersistenceImplementation(entityName);
    }
  }

  /**
   * Gets the class ID.<br>
   * If a class ID is necessary but missing or outside the profile's range, the next free class ID of the profile is set.
   *
   * @return the class ID, null if none
   */
  @Bindable
  @True(value = "$isNewClassId", scope = InteractiveScope.class, message = "class ID already in use")
  @Changeable(condition = "$isClassIdNecessary")
  @GreaterOrEqual(value = "100", condition = "$isClassIdNecessary",
                  message = "class IDs below 100 are reserved for tentackle", scope = InteractiveScope.class)
  @NotNull(condition = "$isClassIdNecessary", message = "missing class ID")
  public Integer getClassId() {
    if (isClassIdNecessary() &&
        (classId == null || classId < profile.getMinClassId() || classId > profile.getMaxClassId())) {
      classId = profile.getMinClassId();
    }
    return classId;
  }

  /**
   * Sets the class ID.
   *
   * @param classId the class ID, null if none
   */
  @Bindable
  public void setClassId(Integer classId) {
    this.classId = classId;
  }

  /**
   * Gets the table name.<br>
   * If a table name is necessary but missing, it is initialized with the profile's table prefix.
   *
   * @return the table name, null if none
   */
  @Bindable(options = "lc")
  @True(value = "$isNewTableName", scope = InteractiveScope.class, message = "table name already in use")
  @Changeable(condition = "$isTableNameNecessary")
  @NotNull(value = "$isTableNameValid", condition = "$isTableNameNecessary", message = "missing table name")
  public String getTableName() {
    if (isTableNameNecessary() && tableName == null && profile != null && profile.getTablePrefix() != null) {
      tableName = profile.getTablePrefix();
    }
    return tableName;
  }

  /**
   * Sets the table name.
   *
   * @param tableName the table name, null if none
   */
  @Bindable
  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  /**
   * Gets the super entity.
   *
   * @return the super entity, null if none
   */
  @Bindable
  @Changeable(condition = "$isSuperEntityAllowed")
  public Entity getSuperEntity() {
    return superEntity;
  }

  /**
   * Sets the super entity.
   *
   * @param superEntity the super entity, null if none
   */
  @Bindable
  public void setSuperEntity(Entity superEntity) {
    this.superEntity = superEntity;
  }

  /**
   * Gets the inheritance type.
   *
   * @return the inheritance type
   */
  @Bindable
  @NotNull
  @True(value = "$isInheritanceTypeValid", scope = InteractiveScope.class, message = "inheritance type does not match super entity")
  public InheritanceType getInheritanceType() {
    return inheritanceType;
  }

  /**
   * Sets the inheritance type.<br>
   * An embedded entity is never cached.
   *
   * @param inheritanceType the inheritance type
   */
  @Bindable
  public void setInheritanceType(InheritanceType inheritanceType) {
    this.inheritanceType = inheritanceType;
    if (inheritanceType == InheritanceType.EMBEDDED) {
      cacheEnabled = false;
    }
  }

  /**
   * Gets the short description.
   *
   * @return the short description
   */
  @Bindable
  @NotNull(message = "missing short description")
  public String getShortDescription() {
    return shortDescription;
  }

  /**
   * Sets the short description.
   *
   * @param shortDescription the short description
   */
  @Bindable
  public void setShortDescription(String shortDescription) {
    this.shortDescription = shortDescription;
  }

  /**
   * Gets the long description.
   *
   * @return the long description, null if none
   */
  @Bindable
  public String getLongDescription() {
    return longDescription;
  }

  /**
   * Sets the long description.
   *
   * @param longDescription the long description, null if none
   */
  @Bindable
  public void setLongDescription(String longDescription) {
    this.longDescription = longDescription;
  }

  /**
   * Gets the name of the domain interface.
   *
   * @return the name, null if none
   */
  @Bindable
  @NotNull(message = "missing domain interface")
  public String getDomainInterface() {
    return domainInterface;
  }

  /**
   * Sets the name of the domain interface.
   *
   * @param domainInterface the name, null if none
   */
  @Bindable
  public void setDomainInterface(String domainInterface) {
    this.domainInterface = domainInterface;
  }

  /**
   * Gets the name of the persistence interface.
   *
   * @return the name, null if none
   */
  @Bindable
  @NotNull(message = "missing persistence interface")
  public String getPersistenceInterface() {
    return persistenceInterface;
  }

  /**
   * Sets the name of the persistence interface.
   *
   * @param persistenceInterface the name, null if none
   */
  @Bindable
  public void setPersistenceInterface(String persistenceInterface) {
    this.persistenceInterface = persistenceInterface;
  }

  /**
   * Gets the name of the domain implementation.
   *
   * @return the name, null if none
   */
  @Bindable
  public String getDomainImplementation() {
    return domainImplementation;
  }

  /**
   * Sets the name of the domain implementation.
   *
   * @param domainImplementation the name, null if none
   */
  @Bindable
  public void setDomainImplementation(String domainImplementation) {
    this.domainImplementation = domainImplementation;
  }

  /**
   * Gets the name of the persistence implementation.
   *
   * @return the name, null if none
   */
  @Bindable
  public String getPersistenceImplementation() {
    return persistenceImplementation;
  }

  /**
   * Sets the name of the persistence implementation.
   *
   * @param persistenceImplementation the name, null if none
   */
  @Bindable
  public void setPersistenceImplementation(String persistenceImplementation) {
    this.persistenceImplementation = persistenceImplementation;
  }

  /**
   * Returns whether remoting is enabled.
   *
   * @return true if remoting is enabled
   */
  @Bindable
  public boolean isRemoteEnabled() {
    return remoteEnabled;
  }

  /**
   * Sets whether remoting is enabled.
   *
   * @param remoteEnabled true if remoting is enabled
   */
  @Bindable
  public void setRemoteEnabled(boolean remoteEnabled) {
    this.remoteEnabled = remoteEnabled;
  }

  /**
   * Returns whether caching is enabled.
   *
   * @return true if caching is enabled
   */
  @Bindable
  @Changeable(condition = "$isCachingAllowed")
  public boolean isCacheEnabled() {
    return cacheEnabled;
  }

  /**
   * Sets whether caching is enabled.
   *
   * @param cacheEnabled true if caching is enabled
   */
  @Bindable
  public void setCacheEnabled(boolean cacheEnabled) {
    this.cacheEnabled = cacheEnabled;
  }

  /**
   * Sets the model source in batch mode.
   *
   * @param modelSource the model source, null if interactive mode
   */
  public void setModelSource(String modelSource) {
    this.modelSource = modelSource;
  }

  /**
   * Gets the model source in batch mode.
   *
   * @return the model source, null if interactive mode
   */
  public String getModelSource() {
    return modelSource;
  }


  /**
   * Gets the name of the parent PDO interface.
   *
   * @return the super interface
   */
  public String getSuperPdoInterface() {
    String superPdoInterface;
    if (superEntity != null) {
      superPdoInterface = superEntity.getName();
    }
    else if (profile.getPdoInterface() != null) {
      superPdoInterface = profile.getPdoInterface();
    }
    else {
      superPdoInterface = "PersistentDomainObject";
    }
    return superPdoInterface;
  }

  /**
   * Gets the name of the parent domain interface.
   *
   * @return the super domain interface
   */
  public String getSuperDomainInterface() {
    String superDomainInterface;
    if (superEntity != null) {
      superDomainInterface = NamingRules.getInstance().getPdoDomainInterface(superEntity.getName());
    }
    else if (profile.getDomainInterface() != null) {
      superDomainInterface = profile.getDomainInterface();
    }
    else {
      superDomainInterface = "DomainObject";
    }
    return superDomainInterface;
  }

  /**
   * Gets the name of the parent persistence interface.
   *
   * @return the super persistence interface
   */
  public String getSuperPersistenceInterface() {
    String superPersistenceInterface;
    if (superEntity != null) {
      superPersistenceInterface = NamingRules.getInstance().getPdoPersistenceInterface(superEntity.getName());
    }
    else if (profile.getPersistenceInterface() != null) {
      superPersistenceInterface = profile.getPersistenceInterface();
    }
    else {
      superPersistenceInterface = "PersistentObject";
    }
    return superPersistenceInterface;
  }

  /**
   * Gets the name of the parent domain class.
   *
   * @return the super domain class
   */
  public String getSuperDomainImplementation() {
    String superDomainImplementation;
    if (superEntity != null) {
      superDomainImplementation = NamingRules.getInstance().getPdoDomainImplementation(superEntity.getName());
    }
    else if (profile.getDomainImplementation() != null) {
      superDomainImplementation = profile.getDomainImplementation();
    }
    else {
      superDomainImplementation = "AbstractDomainObject";
    }
    return superDomainImplementation;
  }

  /**
   * Gets the name of the parent persistence class.
   *
   * @return the super persistence class
   */
  public String getSuperPersistenceImplementation() {
    String superPersistenceImplementation;
    if (superEntity != null) {
      superPersistenceImplementation = NamingRules.getInstance().getPdoPersistenceImplementation(superEntity.getName());
    }
    else if (profile.getPersistenceImplementation() != null) {
      superPersistenceImplementation = profile.getPersistenceImplementation();
    }
    else {
      superPersistenceImplementation = "AbstractPersistentObject";
    }
    return superPersistenceImplementation;
  }

}

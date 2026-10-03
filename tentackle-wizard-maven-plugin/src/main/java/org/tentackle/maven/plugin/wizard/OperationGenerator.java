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
import org.tentackle.model.Model;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.validator.Changeable;
import org.tentackle.validate.validator.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generator for the operation files.<br>
 * Used by the interactive operation wizard and the batch mode of the {@code operation} goal.
 */
public class OperationGenerator extends AbstractValidateableGenerator {

  private OperationProfile profile;
  private String operationName;
  private String superOperationName;
  private String shortDescription;
  private String longDescription;
  private String domainInterface;
  private String persistenceInterface;
  private String domainImplementation;
  private String persistenceImplementation;
  private boolean abstractOperation;
  private boolean remoteEnabled;


  /**
   * Creates a new generator.
   *
   * @param model the model
   */
  public OperationGenerator(Model model) {
    super(model);
  }


  /**
   * Creates the operation files.
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
      OperationTemplateModel templateModel = new OperationTemplateModel(this);

      // create targets
      List<GeneratedFile> generatedFiles = new ArrayList<>();
      for (Map.Entry<String, File> entry: createTargets().entrySet()) {
        generatedFiles.add(new GeneratedFile(cfg, templateModel, entry.getKey(), entry.getValue()));
      }

      // generate targets
      for (GeneratedFile generatedFile: generatedFiles) {
        generatedFile.generate();
      }
    }
    return results;
  }

  /**
   * Gets the files that {@link #generate()} would create.<br>
   * Requires the profile and the operation name to be set.
   *
   * @return the target files, never null
   */
  public List<File> getTargetFiles() {
    return new ArrayList<>(createTargets().values());
  }

  /**
   * Creates the mapping of the templates to their target files.
   *
   * @return the map, ordered by template
   */
  private Map<String, File> createTargets() {
    Map<String, File> targets = new LinkedHashMap<>();
    targets.put("OperationInterface.ftl",
                new File(getProfile().getOperationPackageInfo().getPath(), getOperationName() + Constants.JAVA_EXT));
    if (getDomainInterface() != null) {
      targets.put("DomainInterface.ftl",
                  new File(getProfile().getDomainPackageInfo().getPath(), getDomainInterface() + Constants.JAVA_EXT));
    }
    if (getPersistenceInterface() != null) {
      targets.put("PersistenceInterface.ftl",
                  new File(getProfile().getPersistencePackageInfo().getPath(), getPersistenceInterface() + Constants.JAVA_EXT));
    }
    if (getDomainInterface() != null && getDomainImplementation() != null) {
      targets.put("DomainImplementation.ftl",
                  new File(getProfile().getDomainImplPackageInfo().getPath(), getDomainImplementation() + Constants.JAVA_EXT));
    }
    if (getPersistenceInterface() != null && getPersistenceImplementation() != null) {
      targets.put("PersistenceImplementation.ftl",
                  new File(getProfile().getPersistenceImplPackageInfo().getPath(), getPersistenceImplementation() + Constants.JAVA_EXT));
    }
    return targets;
  }

  /**
   * Initializes the remoting option from the project's model defaults.
   *
   * @see #isRemoteDefault()
   */
  public void initializeRemoteDefault() {
    setRemoteEnabled(isRemoteDefault());
  }

  /**
   * Returns whether the operation has a domain part.
   *
   * @return true if there is a domain interface
   */
  public boolean isDomainValid() {
    return domainInterface != null;
  }

  /**
   * Returns whether the operation has a persistence part.
   *
   * @return true if there is a persistence interface
   */
  public boolean isPersistenceValid() {
    return persistenceInterface != null;
  }


  // ------------------------------------- model -------------------------------------------------


  /**
   * Gets the profile.
   *
   * @return the profile
   */
  @Bindable
  @NotNull
  public OperationProfile getProfile() {
    return profile;
  }

  /**
   * Sets the profile.
   *
   * @param profile the profile
   */
  @Bindable
  public void setProfile(OperationProfile profile) {
    this.profile = profile;
  }

  /**
   * Gets the operation name, i.e. the name of the operation interface.
   *
   * @return the operation name
   */
  @Bindable
  @NotNull(message = "missing operation name")
  public String getOperationName() {
    return operationName;
  }

  /**
   * Sets the operation name, i.e. the name of the operation interface.<br>
   * Also sets the derived names of the interfaces and implementations and the remoting default.
   *
   * @param operationName the operation name
   */
  @Bindable
  public void setOperationName(String operationName) {
    this.operationName = operationName;
    if (operationName == null) {
      domainInterface = null;
      domainImplementation = null;
      persistenceInterface = null;
      persistenceImplementation = null;
    }
    else {
      NamingRules namingRules = NamingRules.getInstance();
      domainInterface = namingRules.getOperationDomainInterface(operationName);
      domainImplementation = namingRules.getOperationDomainImplementation(operationName);
      persistenceInterface = namingRules.getOperationPersistenceInterface(operationName);
      persistenceImplementation = namingRules.getOperationPersistenceImplementation(operationName);
      initializeRemoteDefault();
    }
  }

  /**
   * Gets the name of the super operation.
   *
   * @return the super operation, null if none
   */
  @Bindable
  public String getSuperOperationName() {
    return superOperationName;
  }

  /**
   * Sets the name of the super operation.
   *
   * @param superOperationName the super operation, null if none
   */
  @Bindable
  public void setSuperOperationName(String superOperationName) {
    this.superOperationName = superOperationName;
  }

  /**
   * Returns whether the operation is abstract.
   *
   * @return true if abstract
   */
  @Bindable
  public boolean isAbstractOperation() {
    return abstractOperation;
  }

  /**
   * Sets whether the operation is abstract.
   *
   * @param abstractOperation true if abstract
   */
  @Bindable
  public void setAbstractOperation(boolean abstractOperation) {
    this.abstractOperation = abstractOperation;
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
  @NotNull(message = "missing domain interface", condition = "!$isPersistenceValid")
  public String getDomainInterface() {
    return domainInterface;
  }

  /**
   * Sets the name of the domain interface.<br>
   * Null drops the domain implementation as well.
   *
   * @param domainInterface the name, null if none
   */
  @Bindable
  public void setDomainInterface(String domainInterface) {
    this.domainInterface = domainInterface;
    if (domainInterface == null) {
      domainImplementation = null;
    }
    else if (domainImplementation == null) {
      domainImplementation = NamingRules.getInstance().getOperationDomainImplementation(operationName);
    }
  }

  /**
   * Gets the name of the persistence interface.
   *
   * @return the name, null if none
   */
  @Bindable
  @NotNull(message = "missing persistence interface", condition = "!$isDomainValid")
  public String getPersistenceInterface() {
    return persistenceInterface;
  }

  /**
   * Sets the name of the persistence interface.<br>
   * Null drops the persistence implementation as well and disables remoting.
   *
   * @param persistenceInterface the name, null if none
   */
  @Bindable
  public void setPersistenceInterface(String persistenceInterface) {
    this.persistenceInterface = persistenceInterface;
    if (persistenceInterface == null) {
      persistenceImplementation = null;
      remoteEnabled = false;
    }
    else if (persistenceImplementation == null) {
      persistenceImplementation = NamingRules.getInstance().getOperationPersistenceImplementation(operationName);
      initializeRemoteDefault();
    }
  }

  /**
   * Gets the name of the domain implementation.
   *
   * @return the name, null if none
   */
  @Bindable
  @Changeable(condition = "$isDomainValid")
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
  @Changeable(condition = "$isPersistenceValid")
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
  @Changeable(condition = "$isPersistenceValid")
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
   * Gets the name of the parent operation interface.
   *
   * @return the super interface
   */
  public String getSuperOperationInterface() {
    String superOperationInterface;
    if (superOperationName != null) {
      superOperationInterface = superOperationName;
    }
    else if (profile.getOperationInterface() != null) {
      superOperationInterface = profile.getOperationInterface();
    }
    else {
      superOperationInterface = "Operation";
    }
    return superOperationInterface;
  }

  /**
   * Gets the name of the parent domain interface.
   *
   * @return the super domain interface
   */
  public String getSuperDomainInterface() {
    String superDomainInterface;
    if (superOperationName != null) {
      superDomainInterface = NamingRules.getInstance().getOperationDomainInterface(superOperationName);
    }
    else if (profile.getDomainInterface() != null) {
      superDomainInterface = profile.getDomainInterface();
    }
    else {
      superDomainInterface = "DomainOperation";
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
    if (superOperationName != null) {
      superPersistenceInterface = NamingRules.getInstance().getOperationPersistenceInterface(superOperationName);
    }
    else if (profile.getPersistenceInterface() != null) {
      superPersistenceInterface = profile.getPersistenceInterface();
    }
    else {
      superPersistenceInterface = "PersistentOperation";
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
    if (superOperationName != null) {
      superDomainImplementation = NamingRules.getInstance().getOperationDomainImplementation(superOperationName);
    }
    else if (profile.getDomainImplementation() != null) {
      superDomainImplementation = profile.getDomainImplementation();
    }
    else {
      superDomainImplementation = "AbstractDomainOperation";
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
    if (superOperationName != null) {
      superPersistenceImplementation = NamingRules.getInstance().getOperationPersistenceImplementation(superOperationName);
    }
    else if (profile.getPersistenceImplementation() != null) {
      superPersistenceImplementation = profile.getPersistenceImplementation();
    }
    else {
      superPersistenceImplementation = "AbstractPersistentOperation";
    }
    return superPersistenceImplementation;
  }

}

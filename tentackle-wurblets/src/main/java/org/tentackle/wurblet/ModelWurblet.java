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

package org.tentackle.wurblet;

import org.wurbelizer.wurbel.JavaSourceType;
import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurbel.WurbelTerminationException;
import org.wurbelizer.wurbel.Wurbler;
import org.wurbelizer.wurblet.AbstractJavaWurblet;
import org.wurbelizer.wurblet.AbstractWurblet;

import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeOptions;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SourceInfo;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;
import org.tentackle.sql.DataType;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extended {@link AbstractJavaWurblet} providing basic functionality for the persistent object model.
 * <p>
 * The following wurblet options are parsed:
 * <ul>
 * <li><em>--method=&lt;method-name&gt;:</em> optional name of the generated method. Defaults to the wurblet tag.</li>
 * <li><em>--model=&lt;mapping&gt;:</em> optional model mapping. Derived from the source file, if missing.</li>
 * <li><em>--remote:</em> force generating remote code.</li>
 * <li><em>--noremote:</em> don't generate remote code.</li>
 * </ul>
 * If neither {@code --remote} nor {@code --noremote} is given, the remote flag is taken from the entity's options,
 * or from the model defaults if there is no entity.
 * <p>
 * The wurblet configuration option {@code missingModelOk} allows the entity to be missing, i.e. {@link #getEntity()}
 * returns null.
 * <p>
 * For the general form of a wurblet-anchor, see {@link AbstractWurblet}.
 *
 * @author  harald
 */
public class ModelWurblet extends AbstractJavaWurblet {

  private static final Pattern ANNOTATION_PATTERN = Pattern.compile("\\s*([\\w\\.]+)\\.class");  // only 1-line annos for now
  private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([\\w\\.]+)\\s*;");

  private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w\\.]+)\\s*;");

  private static final Map<String, String> PACKAGE_CACHE = new ConcurrentHashMap<>();         // <source>:<class> -> package
  private static final Map<String, String> ENTITY_PACKAGE_CACHE = new ConcurrentHashMap<>();  // source file -> package

  /**
   * The name of the model directory.<br>
   * This is the model of the project's current (maven) module.
   */
  private String modelDirName;

  /**
   * Names of other model dirs.<br>
   * Those models will be loaded *before* the model in modelDirName and
   * represent models of other modules that are dependencies of the current module
   * (i.e., the current model refers to).
   * <p>
   * Important: the modelDefaults and entityAliases must be the same for all models,
   * including the current!
   */
  private List<String> otherModelDirNames;

  /** name of the entity model (not the whole model!). */
  private String modelFileName;

  /** the model defaults. */
  private ModelDefaults modelDefaults;

  /** the original parsed mapping. */
  private Entity entity;

  /** wurblet specific arguments. */
  private List<String> args;

  /** list of wurblet args starting with -- (-- cut off). */
  private List<String> optionArgs;

  /** list of regular wurblet args (all args except the options). */
  private List<String> wurbletArgs;

  /** != null if detected whether this is a pdo or not. */
  private Boolean isPdo;

  /** true if isPdo != null and wurblet anchor is declared within an interface. */
  private boolean isInterface;

  /** the effective remote flag. */
  private boolean remote;

  /** the pdo classname if isPdo == true, the operation classname if isPdo == false, null if the class cannot be determined. */
  private String pdoClassName;

  /** true if PDO-level operation. */
  private boolean isOperation;

  /** true if a missing model file is ok. */
  private boolean missingModelOk;

  /** the backends that are going to be used. */
  private Collection<Backend> backends;


  /**
   * Creates a wurblet.
   */
  public ModelWurblet() {
    super();
  }

  /**
   * Gets the name of the model directory.
   *
   * @return the model dir name
   */
  public String getModelDirName() {
    return modelDirName;
  }

  /**
   * Gets the optional model defaults.
   *
   * @return the defaults, null for none
   */
  public ModelDefaults getModelDefaults() {
    return modelDefaults;
  }

  /**
   * Gets the pdo/operation class from the source.<br>
   * Looks for annotations {@code @DomainObjectService, @PersistentObjectService, @DomainOperationService, @PersistentOperationService}
   * and interface extensions.
   *
   * @return the pdo name
   * @throws WurbelException if pdo class cannot be determined from the source file
   */
  public String getPdoClassName() throws WurbelException {
    if (isPdo == null)  {   // not determined yet
      isPdo = false;

      for (int ndx=0; ; ndx++) {
        String annotation = getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, JavaSourceType.ANNOTATION + ndx);
        if (annotation == null) {
          break;
        }
        if (annotation.startsWith("@DomainObjectService") ||
            annotation.startsWith("@PersistentObjectService")) {
          Matcher matcher = ANNOTATION_PATTERN.matcher(annotation);
          if (matcher.find()) {
            pdoClassName = matcher.group(1);
            if (getContainer().getVerbosity().isDebug()) {
              getContainer().getLogger().info("pdo: " + pdoClassName);
            }
            isPdo = true;
            return pdoClassName;
          }
          throw new WurbelException("malformed annotation: " + annotation);
        }
        else if (annotation.startsWith("@DomainOperationService") ||
                 annotation.startsWith("@PersistentOperationService")) {
          Matcher matcher = ANNOTATION_PATTERN.matcher(annotation);
          if (matcher.find()) {
            pdoClassName = matcher.group(1);
            isOperation = true;
            if (getContainer().getVerbosity().isDebug()) {
              getContainer().getLogger().info("operation: " + pdoClassName);
            }
            return pdoClassName;
          }
          throw new WurbelException("malformed annotation: " + annotation);
        }
      }

      // maybe an interface that extends PersistentObject<Blah>
      String persistentIface = getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, JavaSourceType.EXTENDS + 0);
      if (persistentIface != null) {
        int ndx = persistentIface.indexOf('<');
        if (ndx > 0) {
          persistentIface = persistentIface.substring(ndx + 1);
          ndx = persistentIface.lastIndexOf('>');
          if (ndx > 0) {
            pdoClassName = persistentIface.substring(0, ndx);
            if (pdoClassName.length() > 1 && pdoClassName.indexOf('<') < 0) {   // not T or some other generic type
              isPdo = true;
              isInterface = true;
              return pdoClassName;
            }
          }
        }
      }

      // may be an abstract class (if inheritance is used)
      pdoClassName = getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, JavaSourceType.DEFINITION);
      if (pdoClassName != null) {
        /*
         * If something of:
         * extends AbstractPersistentObject<Adresse, AdressePersistenceImpl> implements AdressePersistence
         * or
         * extends UmzugsListePersistenceImpl<UmzugsErfassungsListe, UmzugsErfassungsListePersistenceImpl>
         *   implements UmzugsErfassungsListePersistence
         * or
         * <T extends UmzugsListe<T>, P extends UmzugsListePersistenceImpl<T, P>>
         *   extends AbstractPersistentObject<T, P> implements UmzugsListePersistence<T>
         */
        if (getContainer().getVerbosity().isDebug()) {
          getContainer().getLogger().info(getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, JavaSourceType.CLASS_NAME) +
                                          ": definition = '" + pdoClassName + "'");
        }
        int ndx = pdoClassName.indexOf("extends");
        if (ndx >= 0) {
          int ndx1 = pdoClassName.indexOf('<');
          int ndx2 = pdoClassName.indexOf(',');
          if (ndx1 > ndx && ndx2 > ndx1) {
            pdoClassName = pdoClassName.substring(ndx1 + 1, ndx2).trim();
            isPdo = true;
            return pdoClassName;
          }
        }
        ndx = pdoClassName.indexOf("T extends");
        if (ndx >= 0) {
          pdoClassName = pdoClassName.substring(ndx + 9);
          ndx = pdoClassName.indexOf('<');
          if (ndx > 0) {
            pdoClassName = pdoClassName.substring(0, ndx).trim();
            isPdo = true;
            return pdoClassName;
          }
        }
      }

      // last try: use interface name (manually implemented PDOs, or migration from TT1)
      pdoClassName = getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, JavaSourceType.INTERFACE_NAME);
      if (pdoClassName != null) {
        return pdoClassName;
      }

      throw new WurbelException("cannot determine the pdo-class from the java-source");
    }

    return pdoClassName;
  }


  /**
   * Returns whether wurblet located within a pdo.
   *
   * @return true for PDOs, false for low-level objects
   */
  public boolean isPdo() {
    if (isPdo == null) {
      try {
        getPdoClassName();    // sets isPdo!
      }
      catch (WurbelException wex) {
        // ignore
      }
    }
    return isPdo;
  }

  /**
   * Returns whether wurblet located within an operation.
   *
   * @return true if operation
   */
  public boolean isOperation() {
    return isOperation;
  }

  /**
   * Returns whether wurblet is defined within an interface.<br>
   * Only valid if isPdo() returns non-null.
   *
   * @return true if interface
   */
  public boolean isInterface() {
    return isInterface;
  }

  /**
   * Returns whether remote code should be generated.<br>
   * Determined by the {@code --remote}/{@code --noremote} options, the entity's options or the model defaults.
   *
   * @return true if remote
   */
  public boolean isRemote() {
    return remote;
  }

  /**
   * Sets the remote option explicitly.
   *
   * @param remote true if remoting enabled
   */
  public void setRemote(boolean remote) {
    this.remote = remote;
  }

  /**
   * Returns whether the entity is part of an inheritance tree.
   *
   * @return true if part of an inheritance tree
   */
  public boolean isPartOfInheritanceHierarchy() {
    return isPdo() && getEntity().getTopSuperEntity().isAbstract();
  }

  /**
   * Returns whether the class is defined using java generics.
   * <p>
   * Generics are used in abstract inheritable classes.
   * Final concrete PDO classes must not use generics.
   * Otherwise, the generated wurblet code will not compile.
   *
   * @return true if the class is generified
   */
  public boolean isGenerified() {
    String definition = getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, JavaSourceType.DEFINITION);
    return definition != null && definition.startsWith("<");
  }

  /**
   * Returns whether at least one of the mapped attributes has the [MUTE]-option set.
   *
   * @param entity the entity
   * @return true if not all attributes mapped to the Java entity
   */
  public boolean isMuteOptionSet(Entity entity) {
    boolean mute = false;
    for (Attribute attr: entity.getAttributes()) {
      AttributeOptions options = attr.getOptions();
      if (options.isMute() && !options.isNoConstant() && !options.isNoDeclare() && !options.isFromSuper()) {
        mute = true;
        break;
      }
    }
    return mute;
  }

  /**
   * Gets the method name.<br>
   * From the guardname or from arg {@code --method=<...>}, if present.
   *
   * @return the method name
   * @throws WurbelException if no guard name
   */
  public String getMethodName() throws WurbelException {
    String methodName = getOption("method");
    if (methodName == null) {
      methodName = getGuardName();
    }
    return methodName;
  }

  /**
   * Gets the name of the entity model.<br>
   * Not to be mixed up with name of the whole {@link Model}!
   *
   * @return the entity model name
   * @throws WurbelException if the model could not be determined
   */
  public String getModelFileName() throws WurbelException {
    if (modelFileName == null) {
      modelFileName = getOption("model");
      if (modelFileName == null) {
        // determine from the source file
        modelFileName = getPdoClassName();
      }
      if (modelFileName == null) {
        throw new WurbelException("model name not specified");
      }
    }
    return modelFileName;
  }

  /**
   * Applies the semantics of {@link #getClassName()} to another entity.<br>
   * Example:
   * <pre>
   * getEntity() -&gt; Firma
   * getClassName() -&gt; "MyFirmaPersistenceImpl"
   * Assumed that otherEntity = Kontakt (which is a superclass of Firma, for example), then:
   * deriveClassNameForEntity(otherEntity) -&gt; "MyKontaktPersistenceImpl"
   * </pre>
   * @param otherEntity the other entity
   * @return the derived classname
   * @throws WurbelException if this classname does not contain the entity name as a substring
   */
  public String deriveClassNameForEntity(Entity otherEntity) throws WurbelException {
    String className = getClassName();
    String entityName = getEntity().getName();
    int ndx = className.indexOf(entityName);
    if (ndx < 0) {
      throw new WurbelException(className + " does not contain the entity name '" + entityName + "' substring");
    }
    String lead = className.substring(0, ndx);
    String tail = className.substring(ndx + entityName.length());
    return lead + otherEntity.getName() + tail;
  }

  /**
   * Sorts the given list of entities by inheritance level plus classid.
   *
   * @param entities the entities
   * @return the sorted entities (same reference as argument)
   */
  public List<Entity> orderByInheritanceLevelAndClassId(List<Entity> entities) {
    entities.sort(Comparator.comparingInt(Entity::getOrdinal).thenComparingInt(Entity::getClassId));
    return entities;
  }

  /**
   * Gets the model entity.
   *
   * @return the entity, null only if the wurblet is configured with {@code missingModelOk}
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the wurblet arguments.
   *
   * @return the wurblet args
   */
  public List<String> getArgs() {
    return args;
  }

  /**
   * Gets the wurblet options.<br>
   * The options got the leading '--' removed.
   *
   * @return the option args
   */
  public List<String> getOptionArgs() {
    return optionArgs;
  }

  /**
   * Gets the option if set.<br>
   * Options come in two flavors:
   * <ol>
   * <li>without a value. Example: --remote</li>
   * <li>with a value. Example: --model=modlog.map</li>
   * </ol>
   *
   * @param option the option
   * @return the empty string (case 1), the value (case 2) or null if option not set
   */
  public String getOption(String option) {
    int equalsOffset = option.length();
    for (String arg: getOptionArgs()) {
      if (arg.equals(option)) {
        return "";
      }
      if (arg.startsWith(option) && arg.charAt(equalsOffset) == '=') {
        return arg.substring(equalsOffset + 1);
      }
    }
    return null;
  }

  /**
   * Gets the wurblet arguments.<br>
   * All arguments except the options.
   *
   * @return the wurblet args
   */
  public List<String> getWurbletArgs() {
    return wurbletArgs;
  }

  /**
   * Creates the method name to select a relation.
   *
   * @param relation the relation
   * @return the method name
   */
  public String createRelationSelectMethodName(Relation relation) {
    StringBuilder text = new StringBuilder();
    text.append("select");

    if (relation.getMethodName() != null) {
      if (relation.getRelationType() == RelationType.LIST) {
        text.append("By");
      }
      text.append(relation.getMethodName());
    }
    else {
      if (relation.getRelationType() == RelationType.LIST) {
        text.append("By");
        for (MethodArgument arg: relation.getMethodArgs()) {
          text.append(StringHelper.firstToUpper(arg.getForeignAttribute().getName()));
        }
      }
      else {
        if (relation.isSelectionCached()) {
          text.append("Cached");
        }
      }
    }

    return text.toString();
  }

  /**
   * Creates the method name to select a relation.
   *
   * @param relation the relation
   * @return the method name
   */
  public String createListRelationDeleteMethodName(Relation relation) {
    StringBuilder text = new StringBuilder();
    text.append("deleteBy");
    if (relation.getMethodName() != null) {
      text.append(relation.getMethodName());
    }
    else  {
      for (MethodArgument arg : relation.getMethodArgs()) {
        text.append(StringHelper.firstToUpper(arg.getForeignAttribute().getName()));
      }
    }
    return text.toString();
  }

  /**
   * Creates the method argument declaration for the select or delete method.
   *
   * @param relation the relation
   * @return the argument declaration
   * @throws ModelException if failed
   */
  public String createDeclaredArgsForSelectOrDeleteMethod(Relation relation) throws ModelException {
    StringBuilder text = new StringBuilder();
    for (MethodArgument arg : relation.getMethodArgs()) {
      if (!text.isEmpty()) {
        text.append(", ");
      }
      Attribute attr = arg.getForeignAttribute();
      text.append(attr.getJavaType()).append(' ').append(attr.getName());
    }
    return text.toString();
  }

  /**
   * Gets the effective datatype.<br>
   * The effective type may be different if the type is a {@link org.tentackle.sql.datatypes.ConvertibleType}.
   *
   * @param attribute the attribute
   * @return the data type
   */
  public DataType<?> getEffectiveDataType(Attribute attribute) throws WurbelException {
    try {
      return attribute.getEffectiveDataType();
    }
    catch (ModelException e) {
      throw new WurbelException("cannot determine effective datatype for " + attribute, e);
    }
  }

  /**
   * Gets the column name.
   *
   * @param attribute the attribute
   * @param columnIndex the column index, -1 if without suffix
   * @return the column name
   * @throws WurbelException if failed
   */
  public String getColumnName(Attribute attribute, int columnIndex) throws WurbelException {
    try {
      return columnIndex < 0 ? attribute.getColumnName() : attribute.getColumnName(null, columnIndex);
    }
    catch (ModelException e) {
      throw new WurbelException("cannot determine column name for " + attribute + ", index " + columnIndex, e);
    }
  }

  /**
   * Gets the name of the string constant for a database column name.
   *
   * @param attribute the attribute
   * @param columnIndex the column index within the attribute, -1 if without suffix
   * @return the constant CN_...
   */
  public String getColumnNameConstant(Attribute attribute, int columnIndex) throws WurbelException {
    DataType<?> effectiveDataType = getEffectiveDataType(attribute);
    if (columnIndex >= 0 && effectiveDataType.isColumnCountBackendSpecific()) {
      throw new WurbelException("backend-specific type " + effectiveDataType + " has varying number of columns");
    }
    return "CN_" + (attribute.getName() + (columnIndex < 0 ? "" :
                                           getEffectiveDataType(attribute).getColumnSuffix(null, columnIndex).orElse(""))).toUpperCase(Locale.ROOT);
  }

  /**
   * Returns the annotation options for all annotation strings provided by the model and a given annotation type.
   *
   * @param annotations the annotations of the model
   * @return the annotation options matching the annotation type (without package name)
   */
  public List<AnnotationOption> getAnnotationOptions(List<String> annotations, String annotationType) {
    List<AnnotationOption> annotationOptions = new ArrayList<>();
    for (String annotation : annotations) {
      AnnotationOption annotationOption = new AnnotationOption(annotation);
      String type = annotationOption.getAnnotationType();
      int ndx = type.lastIndexOf('.');    // in case FQCN
      if (ndx >= 0) {
        type = type.substring(ndx + 1);
      }
      if (type.equals(annotationType)) {
        annotationOptions.add(annotationOption);
      }
    }
    return annotationOptions;
  }

  /**
   * Determines all embedded attributes for the table of the entity.
   *
   * @return the list of embedded attributes, empty if none
   * @throws WurbelException if failed
   */
  public List<Attribute> getEmbeddedTableAttributes() throws WurbelException {
    List<Attribute> embeddedAttributes = new ArrayList<>();
    try {
      for (Attribute attribute : getEntity().getTableAttributes()) {
        if (attribute.isEmbedded()) {
          embeddedAttributes.add(attribute);
        }
      }
    }
    catch (ModelException mx) {
      throw new WurbelException("cannot determine embedded attributes", mx);
    }
    return embeddedAttributes;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Thread-safe if loaded by the wurblet classloader of the current wurbel run.
   *
   * @see WurbletThreadSafety#isIsolated(Class)
   */
  @Override
  public boolean isThreadSafe() {
    return WurbletThreadSafety.isIsolated(getClass());
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to load the model and determine the entity.
   *
   * @throws WurbelException if running the wurblet failed
   */
  @Override
  public void run() throws WurbelException {

    String wurbletOptions = getConfiguration();
    if (wurbletOptions != null && wurbletOptions.contains("missingModelOk")) {
      missingModelOk = true;
    }

    super.run();

    args = Arrays.asList(container.getArgs());
    wurbletArgs = new ArrayList<>();
    optionArgs = new ArrayList<>();
    for (String arg : args) {
      if (arg != null) {
        if (arg.startsWith("--")) {
          optionArgs.add(arg.substring(2));
        }
        else {
          wurbletArgs.add(arg);
        }
      }
    }

    modelDirName = getContainer().getProperty(Wurbler.PROPSPACE_EXTRA, "model");
    if (modelDirName == null) {
      throw new WurbelException("missing wurblet property 'model'");
    }
    File modelDir = new File(modelDirName);
    if (!modelDir.exists()) {
      getContainer().getLogger().info("creating " + modelDir);
      modelDir.mkdirs();
    }
    if (!modelDir.isDirectory()) {
      throw new WurbelException(modelDir + " is not a directory");
    }

    String otherModels = getContainer().getProperty(Wurbler.PROPSPACE_EXTRA, "otherModels");
    if (otherModels != null) {
      otherModelDirNames = new ArrayList<>();
      StringTokenizer stok = new StringTokenizer(otherModels, " \t\n\r\f,");
      while (stok.hasMoreTokens()) {
        otherModelDirNames.add(stok.nextToken());
      }
    }

    String modelName = getContainer().getProperty(Wurbler.PROPSPACE_EXTRA, "modelName");
    Model model = modelName == null ? Model.getInstance() : ModelManager.getInstance().getModel(modelName);

    // set the backends to validate the model, if any.
    backends = BackendFactory.getInstance().getBackends(getContainer().getProperty(Wurbler.PROPSPACE_EXTRA, "backends"));
    model.getEntityFactory().setBackends(backends);

    // scan optional model defaults
    modelDefaults = null;
    String modelDefaultsStr = getContainer().getProperty(Wurbler.PROPSPACE_EXTRA, "modelDefaults");
    if (modelDefaultsStr != null) {
      try {
        modelDefaults = new ModelDefaults(modelDefaultsStr);
      }
      catch (ModelException mex) {
        throw new WurbelException(mex.getMessage(), mex);
      }
    }

    // scan optional entity aliases
    EntityAliases entityAliases = null;
    String entityAliasesStr = getContainer().getProperty(Wurbler.PROPSPACE_EXTRA, "entityAliases");
    if (entityAliasesStr != null) {
      try {
        entityAliases = new EntityAliases(entityAliasesStr);
      }
      catch (ModelException mex) {
        throw new WurbelException(mex.getMessage(), mex);
      }
    }

    // load the model (if not yet done)
    model.setModelDefaults(modelDefaults);
    model.setEntityAliases(entityAliases);

    try {
      if (otherModelDirNames != null) {
        for (String otherModelDirName: otherModelDirNames) {
          model.loadFromDirectory(otherModelDirName, false);
        }
      }
      model.loadFromDirectory(modelDirName, true);
    }
    catch (ModelException mex) {
      // model exceptions cause the whole wurbel run to abort because it doesn't make any sense to clutter the sources
      WurbelException wex = new WurbelTerminationException(
              "errors in model loaded from directory '" + modelDirName + "'", true, mex);
      if (model instanceof TentackleWurbletsModel tentackleWurbletsModel && mex.getElement() != null &&
          tentackleWurbletsModel.getLoadingException() == null) {
        // delay first exception to concrete classes if exception could be associated with entities
        tentackleWurbletsModel.setLoadingException(wex);
      }
      else  {
        if (model instanceof TentackleWurbletsModel tentackleWurbletsModel) {
          WurbelException loadingException = tentackleWurbletsModel.getLoadingException();
          if (loadingException != null) {
            // prepend initial loading exception
            wex = new WurbelTerminationException(mex.getMessage(), true, loadingException.getCause());
          }
        }
        throw wex;
      }
    }

    // get the entity

    try {
      if (getModelFileName().indexOf(File.separatorChar) >= 0) {
        // is a filepath (load it if it's not already loaded)
        try {
          entity = model.loadFromURL(new File(getModelFileName()).toURI().toURL(), true).getEntity();
        }
        catch (IOException ex) {
          throw new ModelException("cannot determine canonical path of model " + getModelFileName(), ex);
        }
      }
      else  {
        // is an entity name
        entity = model.getByEntityName(getModelFileName());
      }
    }
    catch (ModelException mex) {
      throw new WurbelTerminationException("errors in model loaded from file '" + getModelFileName() + "'", mex);
    }

    if (entity == null && !missingModelOk) {
      Throwable delayedException = null;
      if (model instanceof TentackleWurbletsModel) {
        WurbelException wex = ((TentackleWurbletsModel) model).getLoadingException();
        if (wex != null) {
          delayedException = wex.getCause();
        }
      }
      if (delayedException instanceof ModelException) {
        throw new WurbelTerminationException(delayedException.getMessage(), delayedException);
      }
      throw new WurbelTerminationException("no such entity '" + getModelFileName() + "' in model " + modelDir, delayedException);
    }

    if (entity != null) {
      remote = entity.getOptions().isRemote();
    }
    else if (modelDefaults != null && modelDefaults.getRemote() != null) {
      remote = modelDefaults.getRemote();
    }

    // override global option
    if (getOption("remote") != null) {
      remote = true;
    }
    if (getOption("noremote") != null) {
      remote = false;
    }

    // Throw delayed wurbel exception if the real cause is related to this entity
    if (model instanceof TentackleWurbletsModel && entity != null) {
      WurbelException wex = ((TentackleWurbletsModel) model).getLoadingException();
      if (wex != null && wex.getCause() instanceof ModelException mex) {
        if (mex.isRelatedTo(entity)) {
          throw wex;
        }
      }
    }
  }



  // ----------------- utility methods to simplify writing wurblets ----------------------------------


  /**
   * Gets the backends that will be used.<br>
   * Useful if a feature is used that may not be supported by all backends.
   *
   * @return the backends, never null but may be empty
   */
  public Collection<Backend> getBackends() {
    return backends;
  }

  /**
   * Checks that all backends support a given feature.<br>
   * The feature validator is a {@link Consumer} that must return false or throw a {@link RuntimeException}
   * if unsupported.
   *
   * @param feature a string describing the feature
   * @param backendValidator the feature validator
   * @throws WurbelException if some validator failed
   */
  public void assertSupportedByBackends(String feature, Function<Backend, Boolean> backendValidator) throws WurbelException {
    Map<Backend, String> result = new HashMap<>();
    for (Backend backend : getBackends()) {
      try {
        if (Boolean.FALSE.equals(backendValidator.apply(backend))) {
          result.put(backend, "");
        }
      }
      catch (RuntimeException rx) {
        String msg = rx.getMessage();
        result.put(backend, msg == null ? "" : msg);
      }
    }
    if (result.isEmpty()) {
      return;
    }
    StringBuilder msg = new StringBuilder();
    StringBuilder detailMsg = new StringBuilder();
    msg.append(feature).append(" not supported by ");
    boolean needComma = false;
    for (Map.Entry<Backend, String> entry : result.entrySet()) {
      if (needComma) {
        msg.append(", ");
      }
      else {
        needComma = true;
      }
      msg.append(entry.getKey());
      if (!entry.getValue().isBlank()) {
        detailMsg.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
      }
    }
    msg.append(detailMsg);
    throw new WurbelException(msg.toString());
  }

  /**
   * Checks whether the attribute is the pdo ID.
   *
   * @param attribute the attribute
   * @return true if pdo id
   */
  public boolean isIdAttribute(Attribute attribute) {
    return attribute.getName().equals(Constants.CN_ID);
  }

  /**
   * Checks whether the attribute is the pdo serial.
   *
   * @param attribute the attribute
   * @return true if pdo serial
   */
  public boolean isSerialAttribute(Attribute attribute) {
    return attribute.getName().equals(Constants.CN_SERIAL);
  }

  /**
   * Checks whether the attribute is the pdo ID or serial.
   *
   * @param attribute the attribute
   * @return true if pdo id or serial
   */
  public boolean isIdOrSerialAttribute(Attribute attribute) {
    return isIdAttribute(attribute) || isSerialAttribute(attribute);
  }

  /**
   * Checks whether the attribute is derived from a superclass.
   *
   * @param attribute the attribute
   * @return true if derived from superclass
   */
  public boolean isAttributeDerived(Attribute attribute) {
    return attribute.getOptions().isFromSuper() || attribute.getEntity() != entity;
  }

  /**
   * Adds a string to a comma-separated list.
   *
   * @param builder the string builder
   * @param appendStr the string to append
   */
  public void appendCommaSeparated(StringBuilder builder, String appendStr) {
    if (!builder.isEmpty()) {
      builder.append(", ");
    }
    builder.append(appendStr);
  }

  /**
   * Prepends a string to a comma-separated list.
   *
   * @param builder the string builder
   * @param prependStr the string to prepend
   */
  public void prependCommaSeparated(StringBuilder builder, String prependStr) {
    if (!builder.isEmpty()) {
      builder.insert(0, ", ");
    }
    builder.insert(0, prependStr);
  }

  /**
   * Creates the component info for a given component.
   *
   * @param component the component of the wurblet's entity
   * @return the info
   * @throws WurbelException if failed
   */
  public ComponentInfo createComponentInfo(Entity component) throws WurbelException {
    return new ComponentInfo(this, component);
  }

  /**
   * Creates the java code to access an attribute or column.<br>
   * Chained relations will be shown separated by dots. For multi-column types, the
   * column is separated from the attribute by a hash.
   *
   * @param entity the entity where the path begins
   * @param path the accessor path
   * @param createSetter true if create setter, else getter
   * @return the Java code without the trailing braces
   * @throws WurbelException if failed
   */
  public String createAccessorCode(Entity entity, String path, boolean createSetter) throws WurbelException {
    StringBuilder buf = new StringBuilder();
    StringTokenizer stok = new StringTokenizer(path, ".");   // . for relation, # for column in datatype
    int tokenCount = stok.countTokens();
    int tokenIndex = 0;
    while (stok.hasMoreTokens()) {
      String name = stok.nextToken();
      if (tokenIndex < tokenCount - 1) {
        Relation relation = entity.getRelation(name, true);
        if (relation == null) {
          throw new WurbelException("no such relation '" + name + "' in path '" + path + "'");
        }
        buf.append(relation.getGetterName()).append("().");
        entity = relation.getForeignEntity();
      }
      else {
        String column = null;
        int colNdx = name.indexOf('#');
        if (colNdx >= 0 && colNdx < name.length() - 1) {
          column = name.substring(colNdx + 1);
          name = name.substring(0, colNdx);
        }
        Attribute attribute = entity.getAttributeByJavaName(name, true);
        if (attribute == null) {
          throw new WurbelException("no such attribute '" + name + "' in path '" + path + "'");
        }
        if (column != null) {
          buf.append(attribute.getGetterName()).append("().");
          DataType<?> dataType = attribute.getDataType();
          int columnCount = dataType.getColumnCount(null);
          if (columnCount == 1) {
            throw new WurbelException("datatype '" + dataType + "' in path '" + path + "' is not a multi-column type");
          }
          if (createSetter) {
            throw new WurbelException("multi-column datatypes such as '" + dataType + "' in path '" + path + "' are immutable");
          }
          boolean found = false;
          for (int i = 0; i < columnCount; i++) {
            String columnAlias = dataType.getColumnAlias(i);
            if (columnAlias.equalsIgnoreCase(column)) {
              found = true;
              buf.append(dataType.getColumnGetter(i, column));
              break;
            }
          }
          if (!found) {
            throw new WurbelException("no such datatype column '" + column + "' in path '" + path + "'");
          }
        }
        else {
          if (createSetter) {
            buf.append(attribute.getSetterName());
          }
          else {
            buf.append(attribute.getGetterName());
          }
        }
      }
      tokenIndex++;
    }
    return buf.toString();
  }

  /**
   * Gets the non-primitive java type string of an attribute.
   *
   * @param attribute the attribute
   * @return the java type as a string
   * @throws ModelException if type conversion failed
   */
  public String getNonPrimitiveJavaType(Attribute attribute) throws ModelException {
    DataType<?> dataType = attribute.getDataType();
    return dataType.isPrimitive() ?
           dataType.toNonPrimitive().getJavaType() :
           attribute.getJavaType();
  }

  /**
   * Determines the package name of a class from the source contents.<br>
   * This is done by scanning the import statements.<br>
   * Rather costly. Use only if there is no other way to determine the package (by rule, for example).
   *
   * @param className the simple classname
   * @return the package name (never null)
   * @throws WurbelException if the package could not be determined
   */
  public String determinePackageName(String className) throws WurbelException {
    final String packageName = getPackageName();    // default is the same package
    // the result depends on the imports of the current source, hence the key includes the source's class
    String key = packageName + '.' + getClassName() + ':' + className;
    return PACKAGE_CACHE.computeIfAbsent(key, _ -> {
      String importedPackageName = findImportedPackageName(className);
      return importedPackageName != null ? importedPackageName : packageName;
    });
  }

  /**
   * Finds the package of a class imported by the current source.<br>
   * Scans the import statements.
   *
   * @param className the simple classname
   * @return the package name, null if the class is not imported
   */
  public String findImportedPackageName(String className) {
    String sourceText = getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, Wurbler.WURBLET_FILECONTENTS);
    if (sourceText != null) {
      String endStr = '.' + className;
      for (String line : sourceText.lines().toList()) {
        Matcher matcher = IMPORT_PATTERN.matcher(line);
        if (matcher.find()) {
          String fqcn = matcher.group(1);
          if (fqcn.endsWith(endStr)) {
            return fqcn.substring(0, fqcn.length() - endStr.length());
          }
        }
      }
    }
    return null;
  }

  /**
   * Determines the package of an entity's PDO interface.<br>
   * The package is read from the package declaration of the source file the entity's model was loaded from.
   *
   * @param entity the entity
   * @return the package name, null if unknown
   */
  public String determinePdoPackageName(Entity entity) {
    SourceInfo sourceInfo = entity.getSourceInfo();
    String fileName = sourceInfo == null ? null : sourceInfo.getFileName();
    if (fileName == null) {
      return null;
    }
    return ENTITY_PACKAGE_CACHE.computeIfAbsent(fileName, _ -> {
      try (Stream<String> lines = Files.lines(Path.of(fileName))) {
        return lines.map(PACKAGE_PATTERN::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> matcher.group(1))
                    .findFirst()
                    .orElse(null);
      }
      catch (IOException | UncheckedIOException | InvalidPathException e) {
        return null;    // no source file available
      }
    });
  }

  /**
   * Gets the name of an entity's PDO interface as used in the current source.<br>
   * The simple name is returned if the interface is imported or belongs to the same package,
   * otherwise the fully qualified name, if its package is known.
   *
   * @param entity the entity
   * @return the class name
   * @throws WurbelException if the package of the current source could not be determined
   */
  public String getPdoClassNameInSource(Entity entity) throws WurbelException {
    String name = entity.getName();
    if (findImportedPackageName(name) == null) {
      String pdoPackageName = determinePdoPackageName(entity);
      if (pdoPackageName != null && !pdoPackageName.equals(getPackageName())) {
        name = pdoPackageName + '.' + name;
      }
    }
    return name;
  }

  /**
   * Creates a multi-line long comment.
   *
   * @param lead the comment lead (spaces plus asterisk or double slash) plus a space
   * @param comment the long comment
   * @return the output to be inserted into a comment block within a wurblet
   */
  public String createMultiLineComment(String lead, String comment) {
    StringBuilder buf = new StringBuilder();
    if (!StringHelper.isAllWhitespace(comment)) {
      comment.lines().forEach(line -> buf.append("<br>\n").append(lead).append(line));
    }
    return buf.toString();
  }

  /**
   * Creates a comment for the param and return doc tags.
   *
   * @param comment the comment
   * @return the tag comment
   */
  public String createDocTagComment(String comment) {
    if (comment != null) {
      comment = comment.trim();
      String prefix = StringHelper.startsWithAnyOf(comment, "the ", "The ", "THE ");
      if (prefix == null) {
        comment = "the " + firstToLowerCase(comment);
      }
      else if (prefix.equals("The ")) {
        // the and first word in lower-case
        comment = "the " + firstToLowerCase(comment.substring(prefix.length()));
      }
      // else: THE -> leave all in uppercase?
    }
    return comment;
  }

  /**
   * Creates a comment for the param and return doc tags.
   *
   * @param attribute the attribute
   * @return the tag comment
   */
  public String createDocTagComment(Attribute attribute) {
    String comment = attribute.getOptions().getComment();
    return attribute.getDataType().isBool() ? comment : createDocTagComment(comment);
  }

  /**
   * Creates a comment for the param and return doc tags.
   *
   * @param relation the relation
   * @return the tag comment
   */
  public String createDocTagComment(Relation relation) {
    return createDocTagComment(relation.getComment());
  }

  /**
   * Converts the first character to lowercase if only the first is uppercase.
   *
   * @param str the string
   * @return the converted string
   */
  public String firstToLowerCase(String str) {
    if (str != null && str.length() >= 2) {
      if (Character.isUpperCase(str.charAt(0)) && !Character.isUpperCase(str.charAt(1))) {
        return StringHelper.firstToLower(str);
      }
      // else leave "ID", for example, unchanged!
    }
    return str;
  }

}

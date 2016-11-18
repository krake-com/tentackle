/**
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


package org.tentackle.wurblet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tentackle.common.Constants;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;
import org.wurbelizer.wurbel.JavaSourceType;
import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurbel.Wurbler;
import org.wurbelizer.wurblet.AbstractWurblet;



/**
 * Extended {@link AbstractWurblet} providing basic functionality for the persistent object model.
 * <p>
 * The following wurblet options are parsed:
 * <ul>
 * <li><em>--method=&lt;method-name&gt;:</em> optional name of the generated method. Defaults to the wurblet tag.</li>
 * <li><em>--model=&lt;mapping&gt;:</em> optional model mapping. Derived from source file, if missing.</li>
 * <li><em>--remote:</em> force generating remote code. Taken from model-defaults, if missing.</li>
 * <li><em>--noremote:</em> don't generate remote code. Taken from model-defaults, if missing.</li>
 * </ul>
 * For the general form of a wurblet-anchor, see {@link AbstractWurblet}.
 * @author  harald
 */
public class ModelWurblet extends AbstractWurblet {

  private static final Pattern ANNOTATION_PATTERN = Pattern.compile("\\s*([\\w\\.]+)\\.class");  // only 1-line annos for now

  /** the name of the model directory. */
  private String modelDirName;

  /** name of the model. */
  private String modelName;

  /** the original parsed mapping. */
  private Entity entity;

  /** wurblet specific arguments. */
  private List<String> args;

  /** whether context attribute is valid (even if set in model). */
  private boolean contextIdAttributeValid;

  /** != null if detected whether this is a pdo or not. */
  private Boolean isPdo;

  /** true if --remote option set. */
  private boolean remote;

  /** the pdo classname if it's a pdo. */
  private String pdoClassName;

  /** the wurblet key parser. */
  private WurbletParameterParser parser;

  /** the joins. */
  private List<Relation> joins;


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
   * Gets the pdo class from the source.<br>
   * Looks for annotations {@code @DomainObjectService}, {@code @PersistentObjectService} and interface extensions.
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
              getContainer().getLogger().info("pdoClassName: " + pdoClassName);
            }
            isPdo = true;
            return pdoClassName;
          }
          throw new WurbelException("malformed annotation: " + annotation);
        }
      }

      // may be an interface that extends PersistentObject<Blah>
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
              return pdoClassName;
            }
          }
        }
      }

      // may be an abstract class (if inheritance is used)
      pdoClassName = getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, JavaSourceType.DEFINITION);
      if (pdoClassName != null) {
        /**
         * If something of:
         * extends AbstractPersistentObject<Adresse, AdressePersistenceImpl> implements AdressePersistence
         * or
         * extends UmzugsListePersistenceImpl<UmzugsErfassungsListe, UmzugsErfassungsListePersistenceImpl>
         *   implements UmzugsErfassungsListePersistence
         * or
         * <T extends UmzugsListe<T>, P extends UmzugsListePersistenceImpl<T,P>>
         *   extends AbstractPersistentObject<T,P> implements UmzugsListePersistence<T>
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
    }

    if (isPdo) {
      return pdoClassName;
    }

    throw new WurbelException("cannot determine the pdo-class from the java-source");
  }


  /**
   * Returns whether this is a pdo.
   *
   * @return true if pdo
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
   * Returns true if --remote option set.
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
   * @return truf if part of an inheritance tree
   */
  public boolean isPartOfInheritanceHierarchy() {
    return isPdo() && getEntity().getTopSuperEntity().isAbstract();
  }

  /**
   * Returns whether the class is defined using java generics.
   * <p>
   * Generics are used in abstract inheritable classes.
   * Final concrete PDO classes must not use generics.
   * Otherwise the generated wurblet code will not compile.
   *
   * @return true if class is generified
   */
  public boolean isGenerified() {
    String definition = getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, JavaSourceType.DEFINITION);
    return definition != null && definition.startsWith("<");
  }


  /**
   * Gets the methodname.<br>
   * From the guardname or from arg "--method=&lt;.....&gt;" if present.
   *
   * @return the method name
   * @throws WurbelException if no guardname
   */
  public String getMethodName() throws WurbelException {
    String methodName = getOption("method");
    if (methodName == null) {
      methodName = getGuardName();
    }
    return methodName;
  }


  /**
   * Gets the name of the modelfile.
   *
   * @return the name
   * @throws WurbelException if model could not be determined
   */
  public String getModelName() throws WurbelException {
    if (modelName == null) {
      modelName = getOption("model");
      if (modelName == null) {
        // determine from source file
        modelName = getPdoClassName();
      }
      if (modelName == null) {
        throw new WurbelException("model not specified");
      }
    }
    return modelName;
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
    Collections.sort(entities, (Entity o1, Entity o2) -> {
      int rv = o1.getInheritanceLevel() - o2.getInheritanceLevel();
      if (rv == 0) {
        rv = Long.compare(o1.getClassId(), o2.getClassId());
      }
      return rv;
    });
    return entities;
  }

  /**
   * Returns whether context attribute is valid (even if set in model).
   *
   * @return true if context id attribute is valid
   */
  public boolean isContextIdAttributeValid() {
    return contextIdAttributeValid;
  }

  /**
   * Gets the model entity.
   *
   * @return the entity
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
    return parser.getOptionArgs();
  }


  /**
   * Gets the option if set.<br>
   * Options come in two flavours:
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
   * Gets all parameters.
   *
   * @return the parameters
   */
  public List<WurbletParameter> getAllParameters() {
    return parser.getAllParameters();
  }

  /**
   * Gets the method parameters.
   *
   * @return the method parameters
   */
  public List<WurbletParameter> getMethodParameters() {
    return parser.getMethodParameters();
  }

  /**
   * Gets the expression parameters.
   *
   * @return the parameters used within the expression
   */
  public List<WurbletParameter> getExpressionParameters() {
    return parser.getExpressionParameters();
  }

  /**
   * Gets the select/where expression.
   *
   * @return the expression
   */
  public WurbletParameterExpression getExpression() {
    return parser.getExpression();
  }

  /**
   * Gets the extra parameters.
   *
   * @return the parameters used within the expression
   */
  public List<WurbletParameter> getExtraParameters() {
    return parser.getExtraParameters();
  }

  /**
   * Gets the sorting parameters.
   *
   * @return the sorting parameters, empty if no "order by"
   */
  public List<WurbletParameter> getSortingParameters() {
    return parser.getSortingParameters();
  }

  /**
   * Returns whether sorting is configured for this wurblet.
   *
   * @return true if sorting defined in args
   */
  public boolean isWithSorting() {
    return !getSortingParameters().isEmpty();
  }

  /**
   * Gets the joins.
   *
   * @return the joins
   */
  public List<Relation> getJoins() {
    return joins;
  }

  /**
   * Creates the method name to select a relation.
   *
   * @param relation the relation
   * @return the method name
   */
  public String createRelationSelectMethodName(Relation relation) {
    String text = "select";

    if (relation.getMethodName() != null) {
      if (relation.getRelationType() == RelationType.LIST) {
        text += "By";
      }
      text += relation.getMethodName();
    }
    else {
      if (relation.getRelationType() == RelationType.LIST) {
        text += "By" + relation.getEntity().getName() + "Id";
      }
      else {
        if (relation.isSelectionCached()) {
          text += "Cached";
        }
      }
    }

    if (text.equals("select")) {
      text = "select";
    }

    return text;
  }


  /**
   * Creates the method name to select a relation.
   *
   * @param relation the relation
   * @return the method name
   */
  public String createListRelationDeleteMethodName(Relation relation) {
    String text = "deleteBy";
    if (relation.getMethodName() != null) {
      text += relation.getMethodName();
    }
    else  {
      text += relation.getEntity().getName() + "Id";
    }
    return text;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to load the map file.
   *
   * @throws WurbelException if running the wurblet failed
   */
  @Override
  public void run() throws WurbelException {

    super.run();

    args = Arrays.asList(container.getArgs());
    joins = new ArrayList<>();

    parser = new WurbletParameterParser(args);

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

    // set the backends to validate the model, if any.
    // this is a comma separated list of backends, null if none, all for all backends in classpath
    String backends = getContainer().getProperty(Wurbler.PROPSPACE_EXTRA, "backends");
    if (backends != null) {
      Collection<Backend> backendList = new ArrayList<>();
      if ("all".equalsIgnoreCase(backends)) {
        backendList.addAll(BackendFactory.getInstance().getAllBackends());
      }
      else if (!"none".equalsIgnoreCase(backends)) {
        for (String backend: backends.split(",")) {
          backendList.add(BackendFactory.getInstance().getBackendByName(backend.trim()));
        }
      }
      Model.getInstance().getEntityFactory().setBackends(backendList);
    }

    // scan optional model defaults
    ModelDefaults modelDefaults = null;
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
    Model model = Model.getInstance();
    try {
      model.loadModel(modelDirName, modelDefaults, entityAliases);
    }
    catch (ModelException mex) {
      WurbelException wex = new WurbelException("errors in model loaded from directory '" + modelDirName + "'", mex);
      if (model instanceof TentackleWurbletsModel && !mex.getEntities().isEmpty() &&
          ((TentackleWurbletsModel) model).getLoadingException() == null) {
        // delay first exception to concrete classes if exception could be associated to entities
        ((TentackleWurbletsModel) model).setLoadingException(wex);
      }
      else  {
        throw wex;
      }
    }

    // get the entity

    try {
      if (getModelName().indexOf(File.separatorChar) >= 0) {
        // is a filename (load it if it's not already loaded)
        entity = model.loadByFileName(getModelName(), modelDefaults, entityAliases);
      }
      else  {
        // is an entity name
        entity = model.getByEntityName(getModelName());
      }
    }
    catch (ModelException mex) {
        throw new WurbelException("errors in model loaded from file '" + getModelName() + "'", mex);
    }

    if (entity == null) {
      Throwable delayedModelException = null;
      if (model instanceof TentackleWurbletsModel) {
        WurbelException wex = ((TentackleWurbletsModel) model).getLoadingException();
        if (wex != null) {
          delayedModelException = wex.getCause();
        }
      }
      throw new WurbelException("no such entity '" + getModelName() + "' in model " + modelDir, delayedModelException);
    }


    remote = entity.getOptions().isRemote();
    // override global option
    if (getOption("remote") != null) {
      remote = true;
    }
    if (getOption("noremote") != null) {
      remote = false;
    }

    contextIdAttributeValid = entity.getContextIdAttribute() != null;

    Set<Entity> componentKeyEntities = new HashSet<>();

    // setup wurblet parameters
    for (WurbletParameter par: getAllParameters()) {

      Entity parEntity = entity;    // the parameter's entity

      if (par.isComponentKey() ||
          par.isRelationKey() && !par.getComponentEntityName().isEmpty()) {
        if (par.isSortKey()) {
          throw new WurbelException("sorting not allowed for component keys: " + par);
        }
        if (!entity.isRootEntityAccordingToModel()) {
          throw new WurbelException("component keys are only allowed for root-entities: " + par);
        }
        try {
          parEntity = model.getByEntityName(par.getComponentEntityName());
        }
        catch (ModelException mex) {
          throw new WurbelException("model errors while loading '" + par.getComponentEntityName() + "'", mex);
        }
        if (parEntity == null && !par.isRelationKey()) {
          // try relation-name instead of entity-name
          Relation relation = entity.getRelation(par.getComponentEntityName(), true);
          if (relation != null) {
            parEntity = relation.getForeignEntity();
          }
        }
        if (parEntity == null) {
          throw new WurbelException("no such entity '" + par.getComponentEntityName() + "' in " + par);
        }
        boolean rootOk = false;
        Entity joinedEntity = parEntity;
        outer:
        while (joinedEntity != null) {
          for (Entity rootEntity: joinedEntity.getRootEntities()) {
            if (rootEntity.equals(entity)) {
              rootOk = true;
              break outer;
            }
          }
          joinedEntity = joinedEntity.getSuperEntity();
        }
        if (!rootOk) {
          throw new WurbelException(parEntity + " is not a component of " + entity + ": " + par);
        }
        // remember related entities for check against joins later
        Entity topEntity = parEntity.getTopSuperEntity();
        componentKeyEntities.add(topEntity);
        componentKeyEntities.addAll(parEntity.getAllSubEntities());
      }

      if (par.isRelationKey()) {
        Relation relation = parEntity.getRelation(par.getRelationName(), true);
        if (relation == null) {
          throw new WurbelException("no such relation '" + par.getRelationName() +"' in " + parEntity + ": " + par);
        }
        par.setRelation(relation);
        parEntity = relation.getForeignEntity();  // must work!
        if (par.getRelationComponentEntityName() != null) {
          try {
            parEntity = model.getByEntityName(par.getRelationComponentEntityName());
          }
          catch (ModelException mex) {
            throw new WurbelException("model errors while loading '" + par.getRelationComponentEntityName() + "'", mex);
          }
          if (parEntity == null) {
            throw new WurbelException("no such related entity '" + par.getRelationComponentEntityName() + "' in " + par);
          }
        }
        if (relation.isComposite()) {
          // misuse of .relation as a component key?
          for (Entity root: parEntity.getRootEntities()) {
            if (root.equals(entity)) {
              Entity topEntity = parEntity.getTopSuperEntity();
              componentKeyEntities.add(topEntity);
              componentKeyEntities.addAll(parEntity.getAllSubEntities());
              break;
            }
          }
        }
      }

      Attribute attribute = parEntity.getAttributeByJavaName(par.getName(), true);
      if (attribute == null) {
        throw new WurbelException("no such attribute '" + par.getName() + "' in " + parEntity + ": " + par);
      }
      par.setAttribute(attribute);

      if (attribute.getEntity().equals(entity) && attribute.getOptions().isContextId()) {
        // the contextId or an attribute with context scope is already part of the where-clause
        // --> context-clause not necessary
        contextIdAttributeValid = false;
      }
    }

    // setup joins
    for (String joinName: parser.getJoinNames()) {
      Relation join = entity.getRelation(joinName, true);
      if (join == null) {
        throw new WurbelException("no such relation to join: " + joinName);
      }
      if (join.getSelectionType() != SelectionType.LAZY && join.getSelectionType() != SelectionType.EAGER) {
        throw new WurbelException("joined relation '" + join.getName() + "' must be lazy or eager");
      }
      if (remote && !join.isComposite() && !join.isSerialized() && join.getSelectionType() != SelectionType.EAGER) {
        throw new WurbelException("joined non-composite relation '" + join.getName() + "' must be serialized for remote access");
      }
      // verify that join is not used by a component key
      if (componentKeyEntities.contains(join.getForeignEntity())) {
        throw new WurbelException("join '" + join.getName() + "' is already used by a component key");
      }
      joins.add(join);
    }


    if (contextIdAttributeValid) {  // if context id option set for an attribute

      /**
       * Furthermore, if _all_ unique parameters are part of the where-clause, we
       * don't need a contextAttribute as well. However, in that case, we need
       * to makeValidContext in selects.
       */
      int uniqueAttributeCount = 0;
      for (Attribute attribute: entity.getAttributes()) {
        if (attribute.getOptions().isDomainKey()) {
          uniqueAttributeCount++;
        }
      }

      int uniqueKeyCount = 0;
      for (WurbletParameter key: getExpressionParameters()) {
        Attribute attribute = key.getAttribute();
        if (attribute != null && attribute.getOptions().isDomainKey()) {
          uniqueKeyCount++;
        }
      }

      if (uniqueAttributeCount > 0 && uniqueAttributeCount == uniqueKeyCount) {
        contextIdAttributeValid = false;
      }
    }

    /**
     * Throw delayed wurbel execption if the real cause if related to this entity
     */
    if (model instanceof TentackleWurbletsModel) {
      WurbelException wex = ((TentackleWurbletsModel) model).getLoadingException();
      if (wex != null && wex.getCause() instanceof ModelException) {
        ModelException mex = (ModelException) wex.getCause();
        if (mex.getEntities().contains(entity)) {
          throw wex;
        }
      }
    }
  }



  // ----------------- utility methods to simplify writing wurblets ----------------------------------


  /**
   * Checks whether attribute is the pdo ID.
   *
   * @param attribute the attribute
   * @return true if pdo id
   */
  public boolean isIdAttribute(Attribute attribute) {
    return attribute.getJavaName().equals(Constants.CN_ID);
  }

  /**
   * Checks whether attribute is the pdo serial.
   *
   * @param attribute the attribute
   * @return true if pdo serial
   */
  public boolean isSerialAttribute(Attribute attribute) {
    return attribute.getJavaName().equals(Constants.CN_SERIAL);
  }

  /**
   * Checks whether attribute is the pdo ID or serial.
   *
   * @param attribute the attribute
   * @return true if pdo id or serial
   */
  public boolean isIdOrSerialAttribute(Attribute attribute) {
    return isIdAttribute(attribute) || isSerialAttribute(attribute);
  }

  /**
   * Checks whether attribute is derived from a superclass.
   *
   * @param attribute the attribute
   * @return true if derived from superclass
   */
  public boolean isAttributeDerived(Attribute attribute) {
    return attribute.getOptions().isDerived() || attribute.getEntity() != entity;
  }

  /**
   * Determines whether setter/getter need to be used to access an attribute.
   *
   * @param attribute the attribute
   * @return true if use set/get
   */
  public boolean isSetGetRequired(Attribute attribute) {
    return attribute.getOptions().isSetGet() || isAttributeDerived(attribute);
  }


    /**
   * Adds a string to a comma separated list.
   *
   * @param builder the string builder
   * @param appendStr the string to append
   */
  public void appendCommaSeparated(StringBuilder builder, String appendStr) {
    if (builder.length() > 0) {
      builder.append(", ");
    }
    builder.append(appendStr);
  }

  /**
   * Prepends a string to a comma separated list.
   *
   * @param builder the string builder
   * @param prependStr the string to prepend
   */
  public void prependCommaSeparated(StringBuilder builder, String prependStr) {
    if (builder.length() > 0) {
      builder.insert(0, ", ");
    }
    builder.insert(0, prependStr);
  }

}

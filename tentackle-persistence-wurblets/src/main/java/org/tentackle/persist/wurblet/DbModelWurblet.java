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

package org.tentackle.persist.wurblet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeSorting;
import org.tentackle.model.DataType;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.TrackType;
import org.tentackle.sql.Backend;
import org.tentackle.wurblet.ModelWurblet;
import org.tentackle.wurblet.WurbletParameter;
import org.wurbelizer.wurbel.WurbelException;

/**
 * Base class for persistence wurblets.
 * <p>
 * The following wurblet options are supported:
 * <ul>
 * <li><em>--context=&lt;key&gt;:</em> defines the context id (--context= turns off default context).</li>
 * <li><em>--tracked:</em> return a TrackedList instead of List (useful if [TRACKED] not given in model).</li>
 * <li><em>--attracked:</em> same as --tracked but generate is...Modified per attribute.</li>
 * <li><em>--fulltracked:</em> same as --attracked but keep last persisted values as well.</li>
 * <li><em>--untracked:</em> disable [TRACKED, ATTRACKED or FULLTRACKED] from model.</li>
 * </ul>
 * For more options, see {@link ModelWurblet}.
 *
 * @author harald
 */
public class DbModelWurblet extends ModelWurblet {

  private boolean tracked;
  private boolean attracked;
  private boolean fullTracked;
  private Attribute contextAttribute;
  private Set<InnerComponentJoin> componentJoins;
  private List<Relation> nonComponentRelations;


  @Override
  public void run() throws WurbelException {

    super.run();

    tracked = getEntity().getOptions().getTrackType().isTracked();
    attracked = getEntity().getOptions().getTrackType().isAttracked();
    fullTracked = getEntity().getOptions().getTrackType() == TrackType.FULLTRACKED;
    contextAttribute = getEntity().getContextIdAttribute();

    for (String arg: getOptionArgs())  {
      if ("tracked".equals(arg)) {
        tracked = true;
        attracked = false;
        fullTracked = false;
      }
      else if ("attracked".equals(arg)) {
        tracked = true;
        attracked = true;
        fullTracked = false;
      }
      else if ("fulltracked".equals(arg)) {
        tracked = true;
        attracked = true;
        fullTracked = true;
      }
      else if ("untracked".equals(arg)) {
        tracked = false;
        attracked = false;
        fullTracked = false;
      }
      else if (arg.startsWith("context="))  {
        String ctx = arg.substring(8);
        if (ctx.length() == 0) {
          contextAttribute = null;
        }
        else {
          contextAttribute = getEntity().getAttributeByJavaName(arg.substring(8), false);
        }
      }
    }

  }


  /**
   * Returns whether the entity is tracked.
   *
   * @return true if tracked (TRACKED, ATTRACKED or FULLTRACKED)
   */
  public boolean isTracked() {
    return tracked;
  }

  /**
   * Returns whether the entity is attracked or fulltracked.
   *
   * @return true if attracked (ATTRACKED or FULLTRACKED)
   */
  public boolean isAttracked() {
    return attracked;
  }

  /**
   * Returns whether the entity is fulltracked.
   *
   * @return true if FULLTRACKED
   */
  public boolean isFullTracked() {
    return fullTracked;
  }

  /**
   * Gets the context attribute.
   *
   * @return the context attribute, null if none
   */
  public Attribute getContextAttribute() {
    return contextAttribute;
  }


  /**
   * Returns whether the classid needs to be inluded in the WHERE-clause.
   *
   * @param entity the entity
   * @return true if include classid
   */
  public boolean isClassIdRequiredInWhereClause(Entity entity) {
    return !entity.isAbstract() &&   // only leafs
           entity.getSuperEntity() != null &&    // with inheritance
           entity.getHierarchyInheritanceType().isMappingToSuperTable(); // SINGE table inheritance
  }

  /**
   * Returns whether the classid needs to be inluded in the WHERE-clause.
   *
   * @return true if include classid
   */
  public boolean isClassIdRequiredInWhereClause() {
    return isClassIdRequiredInWhereClause(getEntity());
  }


  /**
   * Returns whether SQL statements can be executed for that entity.
   *
   * @return true if persistable
   */
  public boolean isEntityPersistable() {
    return !getEntity().isAbstract() ||
           !getEntity().getHierarchyInheritanceType().isMappingToNoTable();
  }

  /**
   * Asserts that SQL statements can be executed for that entity.
   *
   * @throws WurbelException if not
   */
  public void assertEntityIsPersistable() throws WurbelException {
    if (!isEntityPersistable()) {
      throw new WurbelException(getEntity() + " does not map to any table and is not persistable");
    }
  }


  /**
   * Goes up the inheritance tree until a default sorting is found.
   *
   * @return the sorting, null if none
   */
  public List<AttributeSorting> getDefaultSorting() {
    Entity entity = getEntity();
    while (entity != null) {
      List<AttributeSorting> sorting = entity.getSorting();
      if (sorting != null && !sorting.isEmpty()) {
        return sorting;
      }
      entity = entity.getSuperEntity();
    }
    return null;
  }


  /**
   * Gets the default sorting keys.
   *
   * @return the sorting keys, empty if no "order by"
   */
  public List<WurbletParameter> getDefaultSortKeys() {
    List<WurbletParameter> sortKeys = new ArrayList<>();
    List<AttributeSorting> sorting = getDefaultSorting();
    if (sorting != null) {
      for (AttributeSorting as: sorting) {
        WurbletParameter key = new WurbletParameter(as.toString());
        key.setAttribute(as.getAttribute());
        sortKeys.add(key);
      }
    }
    return sortKeys;
  }


  /**
   * Returns whether default sorting is configured for the entity.
   *
   * @return true if sorting enabled
   */
  public boolean isWithDefaultSorting() {
    return getDefaultSorting() != null;
  }


  /**
   * Gets the inner component joins.
   *
   * @return the joins, empty if none
   * @throws WurbelException if a join could not be created
   */
  public Set<InnerComponentJoin> getInnerComponentJoins() throws WurbelException {
    if (componentJoins == null) {
      componentJoins = new HashSet<>();
      for (WurbletParameter par: getExpressionParameters()) {
        if (par.isComponentKey() ||
            par.isRelationKey() && par.getRelationComponentEntityName() != null) {
          InnerComponentJoin join = new InnerComponentJoin(this,
                                                 par.isComponentKey() ? getEntity() : par.getRelation().getForeignEntity(),
                                                 par.getAttribute().getEntity());
          componentJoins.add(join);
        }
        else if (par.isRelationKey() && !par.getComponentEntityName().isEmpty()) {
          InnerComponentJoin join = new InnerComponentJoin(this,
                                                 par.getRelation().getForeignEntity(),
                                                 par.getRelation().getEntity());
          componentJoins.add(join);
        }
      }
    }
    return componentJoins;
  }


  /**
   * Gets the non-component relations.
   *
   * @return the non-component relations
   */
  public List<Relation> getNonComponentRelations() {
    if (nonComponentRelations == null) {
      nonComponentRelations = new ArrayList<>();
      for (WurbletParameter par: getExpressionParameters()) {
        if (par.isRelationKey()) {
          nonComponentRelations.add(par.getRelation());
        }
      }
    }
    return nonComponentRelations;
  }


  /**
   * Parses the joins defined in the wurblet anchor.
   *
   * @return the parsed joins
   */
  public List<LeftJoin> getLeftJoins() {

    // convert relations to linked lists of leftjoins
    List<List<LeftJoin>> joinPaths = new ArrayList<>();
    int maxLength = 0;
    for (List<Relation> relations: super.getJoinedRelations()) {
      List<LeftJoin> joinPath = new ArrayList<>();
      LeftJoin parentJoin = null;
      for (Relation relation: relations) {
        LeftJoin join = new LeftJoin(relation);
        if (parentJoin != null) {
          parentJoin.getJoins().add(join);
        }
        joinPath.add(join);
        parentJoin = join;
      }
      joinPaths.add(joinPath);
      if (joinPath.size() > maxLength) {
        maxLength = joinPath.size();
      }
    }

    // sort by path length (longest path comes first)
    Collections.sort(joinPaths, (o1, o2) -> o2.size() - o1.size());

    // merge matching join paths
    outer:
    while (maxLength > 0) {
      for (List<LeftJoin> joinPath: joinPaths) {
        if (joinPath.size() >= maxLength) {
          List<LeftJoin> subPath = joinPath.subList(0, maxLength);
          for (Iterator<List<LeftJoin>> iter = joinPaths.iterator(); iter.hasNext(); ) {
            List<LeftJoin> otherJoinPath = iter.next();
            if (otherJoinPath != joinPath && otherJoinPath.size() >= maxLength) {
              List<LeftJoin> otherSubPath = otherJoinPath.subList(0, maxLength);
              if (subPath.equals(otherSubPath)) {
                if (otherJoinPath.size() > maxLength) {
                  joinPath.get(maxLength - 1).getJoins().add(otherJoinPath.get(maxLength));
                }
                iter.remove();
                continue outer;   // start over
              }
            }
          }
        }
      }
      maxLength--;
    }

    List<LeftJoin> leftJoins = new ArrayList<>();
    for (List<LeftJoin> joinPath: joinPaths) {
      leftJoins.add(joinPath.get(0));
    }
    return leftJoins;
  }


  /**
   * Creates the order by clause.
   *
   * @param sortKeys the sorting keys
   * @return the order by clause, null if unsorted
   * @throws WurbelException if failed
   */
  public String createOrderBy(List<WurbletParameter> sortKeys) throws WurbelException {
    if (!sortKeys.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      boolean needComma = false;
      for (WurbletParameter key: sortKeys) {
        Attribute attr = key.getAttribute();
        for (DataType.SqlTypeWithPostfix sp: attr.getDataType().getSqlTypesWithPostfix()) {
          if (needComma) {
            buf.append("\n           .append(Backend.SQL_COMMA)");
          }
          String name = "CN_" + attr.getJavaName().toUpperCase() + sp.getPostfix();
          buf.append(".append(");
          if (getEntity().getHierarchyInheritanceType() == InheritanceType.MULTI) {
            buf.append(deriveClassNameForEntity(getEntity().getTopSuperEntity()));
            buf.append(".CLASSVARIABLES.getColumnName(").append(name).append(')');
          }
          else  {
            if (isPdo()) {
              buf.append("getColumnName(").append(name).append(')');
            }
            else  {
              buf.append(name);
            }
          }
          buf.append(").append(");
          buf.append(key.getSortMode().equals(Backend.SQL_SORTASC) ? "Backend.SQL_SORTASC" : "Backend.SQL_SORTDESC");
          buf.append(')');
          needComma = true;
        }
      }
      return buf.toString();
    }
    return null;
  }

  /**
   * Creates the order by clause.
   *
   * @return the order by clause, null if unsorted
   * @throws WurbelException if failed
   */
  public String createOrderBy() throws WurbelException {
    return createOrderBy(getSortingParameters());
  }


  /**
   * Creates the name of a statement id.<br>
   * Because statement ids are final, the name is in uppercase.
   *
   * @return the id string
   * @throws WurbelException if guardname could not be determined
   */
  public String createStatementId() throws WurbelException {
    StringBuilder buf = new StringBuilder();
    String id = getGuardName();
    for (int i=0; i < id.length(); i++) {
      char c = id.charAt(i);
      if (Character.isUpperCase(c) && buf.length() > 0) {
        buf.append('_');
      }
      buf.append(Character.toUpperCase(c));
    }
    buf.append("_STMT");
    return buf.toString();
  }


  /**
   * Builds the string of method parameters.
   *
   * @param limit optional limit rows
   * @param offset optional skip rows
   * @return the method parameters
   * @throws WurbelException if failed
   */
  public String buildMethodParameters(boolean limit, boolean offset) throws WurbelException {

    StringBuilder params = new StringBuilder();

    if (limit) {
      appendCommaSeparated(params, "int limit");
    }
    if (offset) {
      appendCommaSeparated(params, "int offset");
    }

    Set<String> argSet = new HashSet<>();     // each arg only once
    for (WurbletParameter key: getMethodParameters())  {
      if (key.isArg() && argSet.add(key.getArg())) {
        Attribute attr = key.getAttribute();
        try {
          appendCommaSeparated(params, attr.getJavaType());
        }
        catch (ModelException me) {
          throw new WurbelException("cannot determine java type for key " + key, me);
        }
        params.append(' ');
        params.append(key.getArg());
      }
    }

    return params.toString();
  }


  /**
   * Builds the string of method parameters.
   *
   * @return the method parameters
   * @throws WurbelException if failef
   */
  public String buildMethodParameters() throws WurbelException {
    return buildMethodParameters(false, false);
  }


  /**
   * Builds the string of invocation parameters.
   *
   * @param limit optional limit rows
   * @param offset optional skip rows
   * @return the invocation parameters
   */
  public String buildInvocationParameters(boolean limit, boolean offset) {

    StringBuilder params = new StringBuilder();

    if (limit) {
      appendCommaSeparated(params, "limit");
    }
    if (offset) {
      appendCommaSeparated(params, "offset");
    }

    Set<String> argSet = new HashSet<>();     // each arg only once
    for (WurbletParameter key: getMethodParameters())  {
      if (key.isArg() && argSet.add(key.getArg())) {
        appendCommaSeparated(params, key.getArg());
      }
    }

    return params.toString();
  }


  /**
   * Builds the string of invocation parameters.
   *
   * @return the invocation parameters
   */
  public String buildInvocationParameters() {
    return buildInvocationParameters(false, false);
  }


  /**
   * Adds a string to a comma separated list.
   *
   * @param str the string
   * @param appendStr the string to append
   * @return the new string
   */
  public String acs(String str, String appendStr) {
    StringBuilder builder = new StringBuilder(str);
    appendCommaSeparated(builder, appendStr);
    return builder.toString();
  }

  /**
   * Prepends a string to a comma separated list.
   *
   * @param str the string builder
   * @param prependStr the string to prepend
   * @return the new string
   */
  public String pcs(String str, String prependStr) {
    StringBuilder builder = new StringBuilder(str);
    prependCommaSeparated(builder, prependStr);
    return builder.toString();
  }



  /**
   * Utility method to generate argument list.
   *
   * @param str the argument string
   * @return comma + str if str not empty or null, the empty string otherwise
   */
  public String aas(String str) {
    if (str != null && str.length() > 0) {
      return ", " + str;
    }
    else  {
      return "";
    }
  }


  /**
   * Utility method to always get a non-null string.
   *
   * @param str the source string
   * @return the string or the empty string if str is null
   */
  public String as(String str) {
    return str == null ? "" : str;
  }



  /**
   * Gets the effective DataType used agains Tentackle's JDBC wrapper layer.
   *
   * @param attribute the model attribute
   * @return the data type
   * @throws WurbelException if type is misconfigured
   */
  public DataType getJdbcDataType(Attribute attribute) throws WurbelException {
    try {
      return attribute.getEffectiveType();
    }
    catch (ModelException me) {
      throw new WurbelException("cannot determine effective JDBC datatype for " + attribute, me);
    }
  }

  /**
   * Creates the "setXXXX" method name for PreparedStatementWrapper.
   *
   * @param attribute the model attribute
   * @return the method name
   * @throws WurbelException if type is misconfigured
   */
  public String createJdbcSetterName(Attribute attribute) throws WurbelException {
    StringBuilder buf = new StringBuilder("set");
    DataType type = getJdbcDataType(attribute);
    if (type.isPrimitive()) {
      buf.append(StringHelper.firstToUpper(type.toString()));
    }
    else  {
      buf.append(type.toString());
    }
    return buf.toString();
  }

  /**
   * Creates the "getXXXX" method name for ResultSetWrapper.
   *
   * @param attribute the model attribute
   * @return the method name
   * @throws WurbelException if type is misconfigured
   */
  public String createJdbcGetterName(Attribute attribute) throws WurbelException {
    StringBuilder buf = new StringBuilder("get");
    DataType type = getJdbcDataType(attribute);
    if (type.isPrimitive()) {
      buf.append(StringHelper.firstToUpper(type.toString()));
    }
    else if (type == DataType.BOOLEAN) {
      buf.append("ABoolean");
    }
    else if (type == DataType.BYTE) {
      buf.append("AByte");
    }
    else if (type == DataType.SHORT) {
      buf.append("AShort");
    }
    else if (type == DataType.LONG) {
      buf.append("ALong");
    }
    else if (type == DataType.FLOAT) {
      buf.append("AFloat");
    }
    else if (type == DataType.DOUBLE) {
      buf.append("ADouble");
    }
    else  {
      buf.append(type.toString());
    }
    return buf.toString();
  }


  /**
   * Creates java code applicable to the application model from JDBC variable access code.<br>
   * Applies .toInternal() if attribute is an application specific type.
   * Otherwise jdbcCode is returned unchanged.
   *
   * @param attribute the attribute
   * @param jdbcCode the jdbc code
   * @return the code the model code
   * @throws WurbelException if some model error
   */
  public String getModelCode(Attribute attribute, String jdbcCode) throws WurbelException {
    if (attribute.getDataType() == DataType.APPLICATION) {
      try {
        return attribute.getJavaType() + ".toInternal(" + jdbcCode + ")";
      }
      catch (ModelException me) {
        throw new WurbelException("cannot determine java model-side code for " + attribute + ": " + jdbcCode, me);
      }
    }
    else {
      return jdbcCode;
    }
  }

  /**
   * Creates java code applicable to the JDBC layer from model variable access code.<br>
   * Applies .toExternal() if attribute is an application specific type.
   * Otherwise modelCode is returned unchanged.
   *
   * @param attribute the attribute
   * @param modelCode the jdbc code
   * @return the code the model code
   * @throws WurbelException if some model error
   */
  public String getJdbcCode(Attribute attribute, String modelCode) throws WurbelException {
    String jdbcCode = modelCode;
    if (attribute.getDataType() == DataType.APPLICATION) {
      try {
        String applicationType = attribute.getApplicationType();
        if (modelCode.startsWith(applicationType + ".")) {
          // (Enum) constant
          jdbcCode = modelCode + ".toExternal()";
        }
        else {
          if (attribute.getInnerType().isPrimitive()) {
            jdbcCode = modelCode + " == null ? " + applicationType + ".getDefault().toExternal() : " +
                       modelCode + ".toExternal()";
          }
          else {
            // is nullable
            jdbcCode = modelCode + " == null ? null : " + modelCode + ".toExternal()";
          }
        }
      }
      catch (ModelException me) {
        throw new WurbelException("cannot determine java jdbc-side code for " + attribute + ": " + modelCode, me);
      }
    }
    return jdbcCode;
  }


  /**
   * Returns whether the relation is transient.
   *
   * @param relation the relation
   * @return true if transient modifier required
   */
  public boolean isRelationTransient(Relation relation) {
    return relation.getSelectionType() == SelectionType.LAZY && !relation.isComposite() && !relation.isSerialized();
  }



  /**
   * Gets the relation arguments for delete or select methods.
   *
   * @param relation the relation
   * @param select is true if args for select-method, else delete-method
   * @return the arguments for select/delete methods
   */
  public List<String> getRelationArgs(Relation relation, boolean select) {
    if (select) {
      if (relation.isSelectionFromMainClass()) {
        return new ArrayList<>(); // no args if invoke non-static in main class
      }
    }
    else {
      if (relation.isDeletionFromMainClass()) {
        return new ArrayList<>(); // no args if invoke non-static in main class
      }
    }
    return relation.getMethodArgs();
  }


  /**
   * Creates the argument string for select- or delete statement of a relation.
   *
   * @param relation the relation.
   * @param select is true if for selectStatement, else deleteStatement
   * @return the arg string
   */
  public String createRelationArgString(Relation relation, boolean select) {
    StringBuilder buf = new StringBuilder();
    buf.append('(');
    List<String> args = getRelationArgs(relation, select);
    boolean first = true;
    for (String arg: args) {
      if (first) {
        first = false;
      }
      else  {
        buf.append(", ");
      }
      buf.append(arg);
    }
    buf.append(')');
    return buf.toString();
  }


  /**
   * Creates the java code to select a relation.
   *
   * @param relation the relation
   * @return the java code
   */
  public String createRelationSelectCode(Relation relation) {

    String text = "on(" + relation.getClassName() + ".class)." + createRelationSelectMethodName(relation);

    text += createRelationArgString(relation, true);

    if (relation.getForeignEntity().isAbstract()) {
      // we need a cast
      text = "(" + relation.getClassName() + "<?>) " + text;
    }
    return text;
  }


  /**
   * Creates the java code to update the reference of a relation.
   *
   * @param relation the relation
   * @param pdo the PDO name
   * @return the java code
   */
  public String createRelationUpdateReferenceCode(Relation relation, String pdo) {
    if (relation.getLinkMethodName() != null) {
      return relation.getLinkMethodName() + (relation.getLinkMethodIndex() != null ?
              ("(" + pdo + ", ndx++)") : ("(" + pdo + ")"));
    }
    else {
      return "set" + relation.getEntity().getName() + "(pdo())";
    }
  }

  /**
   * Creates the java code to update the reference of a relation.<br>
   * The pdo name is fixed {@code "pdo()"}.
   *
   * @param relation the relation
   * @return the java code
   */
  public String createRelationUpdateReferenceCode(Relation relation) {
    return createRelationUpdateReferenceCode(relation, "pdo()");
  }

  /**
   * Creates the set-first-arg method name (useful only in object relations).
   *
   * @param relation the relation
   * @return the set-method name
   */
  public String createRelationSetFirstArgMethodName(Relation relation) {
    return "set" + StringHelper.firstToUpper(relation.getMethodArgs().get(0));
  }


  /**
   * Creates the java code to delete a relation.
   *
   * @param relation the relation
   * @return the java code
   */
  public String createRelationDeleteCode(Relation relation) {
    String text = "delete";
    if (relation.getRelationType() == RelationType.LIST) {
      text += "By";
    }
    if (relation.getMethodName() != null) {
      text += relation.getMethodName();
    }
    else {
      text += relation.getEntity().getName() + "Id";
    }
    text = "on(" + relation.getClassName() + ".class)." + text;
    text += createRelationArgString(relation, false);
    return text;
  }


  /**
   * Creates the java code to set the link of a relation.
   *
   * @param relation the relation
   * @return the java code
   */
  public String createRelationLinkCode(Relation relation) {
    if (relation.getLinkMethodName() != null) {
      return relation.getLinkMethodName() + (relation.getLinkMethodIndex() != null ? "(pdo(), ndx++)" : "(pdo())");
    }
    String text = "set";
    if (relation.getMethodName() != null) {
      text += relation.getMethodName();
    }
    else {
      text += relation.getEntity().getName() + "Id";
    }
    return text + createRelationArgString(relation, true);
  }


  /**
   * The TT persistence layer provides an optimized select for a single eager relations via a LeftJoin.
   *
   * @return the eager relations, empty list if no optimization possible or no eagers at all
   */
  public List<Relation> getEagerRelations() {
    List<Relation> eagerRelations = new ArrayList<>();
    outer:
    for (Relation relation: getEntity().getRelations()) {
      if (relation.getSelectionType() == SelectionType.EAGER) {
        // verify that no sub class has an eager relation
        for (Relation rel: getEntity().getSubEntityRelations()) {
          if (rel.getSelectionType() == SelectionType.EAGER) {
            break outer;
          }
        }
        // verify that none of the direct components of this relation has an eager relation
        for (Entity component: relation.getForeignEntity().getComponentsIncludingInherited()) {
          for (Relation rel: component.getAllRelations()) {
            if (rel.getSelectionType() == SelectionType.EAGER) {
              break outer;
            }
          }
        }
        // fine:
        eagerRelations.add(relation);
      }
    }
    return eagerRelations.isEmpty() ? null : eagerRelations;
  }




}

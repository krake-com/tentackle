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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeSorting;
import org.tentackle.model.DataType;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.SortType;
import org.tentackle.model.TrackType;
import org.tentackle.wurblet.ModelWurblet;
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

  private boolean argumentGroupingEnabled;
  private boolean pathAllowed;
  private boolean tracked;
  private boolean attracked;
  private boolean fullTracked;
  private Attribute contextAttribute;
  private List<JoinPath> joinPaths;
  private WurbletArgumentParser parser;


  /**
   * Returns whether argument grouping is enabled.<br>
   * By default, the grouping separator '|' just improves readability.<br>
   * Some wurblet, however, need extra arguments separated from the main expression.
   * This is true for the PdoUpdateBy or DbUpdateBy wurblets, for example.
   * Grouping is turned on via the wurblet option "groupArgs".
   *
   * @return true if grouping enabled
   */
  public boolean isArgumentGroupingEnabled() {
    return argumentGroupingEnabled;
  }

  /**
   * Returns whether expression arguments may contain paths to other entities.
   *
   * @return true if allowed, false if not
   */
  public boolean isPathAllowed() {
    return pathAllowed;
  }


  @Override
  public void run() throws WurbelException {

    String wurbletOptions = getConfiguration();
    if (wurbletOptions != null) {
      if (wurbletOptions.contains("groupArgs")) {
        argumentGroupingEnabled = true;
      }
      else if (wurbletOptions.contains("pathAllowed")) {
        pathAllowed = true;
      }
    }

    super.run();

    Entity entity = getEntity();
    if (entity != null) {   // null if missingModelOk set and no such entity in model (see ModelWurblet)

      tracked = entity.getOptions().getTrackType().isTracked();
      attracked = entity.getOptions().getTrackType().isAttracked();
      fullTracked = entity.getOptions().getTrackType() == TrackType.FULLTRACKED;
      contextAttribute = entity.getContextIdAttribute();

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
            contextAttribute = entity.getAttributeByJavaName(arg.substring(8), false);
          }
        }
      }

      // analyze the wurblet arguments
      parser = new WurbletArgumentParser(entity, argumentGroupingEnabled, getWurbletArgs());

      if (!pathAllowed) {
        for (WurbletArgument arg: parser.getAllArguments()) {
          if (arg.isPath()) {
            throw new WurbelException("relation paths not allowed in wurblet " + this);
          }
        }
      }

      if (isRemote()) {
        // check whether relations are not transient
        for (WurbletArgument joinArgument: parser.getJoinArguments()) {
          for (Relation relation: joinArgument.getRelations()) {
            if (!relation.isComposite() && !relation.isSerialized() && relation.getSelectionType() != SelectionType.EAGER) {
              throw new WurbelException("joined non-composite relation '" + relation.getName() +
                      "' must be serialized for remote access");
            }
            Relation opRel = relation.getForeignRelation();
            if (opRel != null && !opRel.isSerialized() && !opRel.isComposite()) {
              throw new WurbelException("joined opposite relation '" + opRel.getEntity() + "." + opRel.getName() +
                      "' must be serialized for remote access");
            }
          }
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
   * Gets the method arguments.
   *
   * @return the method arguments
   */
  public List<WurbletArgument> getMethodArguments() {
    return parser.getMethodArguments();
  }


  /**
   * Gets the expression arguments.
   *
   * @return the arguments used within the expression
   */
  public List<WurbletArgument> getExpressionArguments() {
    return parser.getExpressionArguments();
  }


  /**
   * Gets the extra arguments.<br>
   * Those are not part of the expression and not sorting arguments.
   *
   * @return the extra arguments
   */
  public List<WurbletArgument> getExtraArguments() {
    return parser.getExtraArguments();
  }

  /**
   * Gets the select/where expression.
   *
   * @return the expression
   */
  public WurbletArgumentExpression getExpression() {
    return parser.getExpression();
  }

  /**
   * Gets the sorting arguments.
   *
   * @return the sorting arguments, empty if no "order by"
   */
  public List<WurbletArgument> getSortingArguments() {
    return parser.getSortingArguments();
  }

  /**
   * Returns whether sorting is configured for this wurblet.
   *
   * @return true if sorting defined in args
   */
  public boolean isWithSorting() {
    return !getSortingArguments().isEmpty();
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
   * @throws WurbelException if failed
   */
  public List<WurbletArgument> getDefaultSortKeys() throws WurbelException {
    List<WurbletArgument> sortKeys = new ArrayList<>();
    List<AttributeSorting> sorting = getDefaultSorting();
    if (sorting != null) {
      for (AttributeSorting as: sorting) {
        WurbletArgument key = parser.createArgument(as.toString(), true, false);
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
   * Gets the consolidated relation paths for the join arguments.
   *
   * @return the join paths
   */
  public List<JoinPath> getJoinPaths() {
    if (joinPaths == null) {
      joinPaths = parser.getJoinPaths();
      // normalize paths (simplifies code generation with JoinedSelects below)
      for (JoinPath path: joinPaths) {
        path.normalize();
      }
    }
    return joinPaths;
  }

  /**
   * Returns whether there are load joins.
   *
   * @return true if with load joins
   */
  public boolean isWithJoins() {
    return !getJoinPaths().isEmpty();
  }


  /**
   * Creates the order by clause.
   *
   * @param sortKeys the sorting keys
   * @return the order by clause, null if unsorted
   * @throws WurbelException if failed
   */
  public String createOrderBy(List<WurbletArgument> sortKeys) throws WurbelException {
    if (!sortKeys.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      boolean needComma = false;
      for (WurbletArgument key: sortKeys) {
        Attribute attr = key.getAttribute();
        for (DataType.SqlTypeWithPostfix sp: attr.getDataType().getSqlTypesWithPostfix()) {
          if (needComma) {
            buf.append("\n           .append(Backend.SQL_COMMA)");
          }
          String name = "CN_" + attr.getName().toUpperCase() + sp.getPostfix();
          buf.append(".append(");
          if (getEntity().getHierarchyInheritanceType() == InheritanceType.MULTI) {
            buf.append(deriveClassNameForEntity(getEntity().getTopSuperEntity()))
               .append(".CLASSVARIABLES.getColumnName(").append(name).append(')');
          }
          else  {
            if (!getEntity().equals(attr.getEntity())) {
              // must be a joined component (otherwise the column is not in the result set)
              boolean joinFound = false;
              for (JoinPath joinPath: getJoinPaths()) {
                Join join = joinPath.findJoin(key.getRelations());
                if (join != null) {
                  buf.append('"').append(join.getName())
                     .append('.').append(attr.getColumnName()).append('"');
                  joinFound = true;
                  break;
                }
              }
              if (!joinFound) {
                throw new WurbelException("missing join for sort key: " + key);
              }
            }
            else {
              if (isPdo()) {
                buf.append("getColumnName(").append(name).append(')');
              }
              else  {
                buf.append(name);
              }
            }
          }
          buf.append(").append(")
             .append(SortType.ASC == key.getSortType() ? "Backend.SQL_SORTASC" : "Backend.SQL_SORTDESC")
             .append(')');
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
    return createOrderBy(getSortingArguments());
  }


  /**
   * Creates the java code for {@code JoinedSelect}s.
   *
   * @return the generated java code
   * @throws WurbelException if failed
   */
  public String createJoins() throws WurbelException {
    StringBuilder buf = new StringBuilder();
    String pdoType = getPdoClassName();
    if (isGenerified()) {
      pdoType = "T";
    }
    buf.append("    JoinedSelect<").append(pdoType).append("> js = new JoinedSelect<")
       .append(pdoType).append(">()\n");
    createJoinsImpl(buf, "      ", getJoinPaths(), pdoType, null);
    buf.replace(buf.length() - 1, buf.length(), ";\n");
    return buf.toString();
  }

  private void createJoinsImpl(StringBuilder buf, String inset, List<JoinPath> paths, String pdoType, String parentName)
          throws WurbelException {
    for (JoinPath path: paths) {
      String lastJoinName = null;
      if (!path.getElements().isEmpty()) {
        Join join = path.getElements().get(0);    // normalized, i.e. only one (see above)
        Relation relation = join.getRelation();
        lastJoinName = join.getName();
        String poImpl = deriveClassNameForEntity(relation.getEntity());
        String alias = relation.getEntity().getTopSuperEntity().getTableAlias();
        String leftClass = relation.getEntity().equals(getEntity()) ? pdoType : relation.getEntity().getName();
        String joinClass = relation.getForeignEntity().getName();
        String joinAlias = relation.getForeignEntity().getTableProvidingEntity().getTableAlias();
        buf.append(inset).append(".addJoin(\n");
        if (relation.getRelationType() == RelationType.LIST) {
          buf.append(inset).append("  new Join<>(JoinType.LEFT, ");
          if (parentName == null) {
            buf.append("getColumnName(CN_ID), \"");
          }
          else {
            buf.append('"').append(parentName).append(".id\", \"");
          }
          buf.append(join.getName()).append('.').append(relation.getForeignAttribute().getColumnName())
             .append("\", ").append(joinClass).append(".class, \"").append(join.getName()).append("\",\n");
          String extraSql = createOptionalWhereForJoin(relation, parentName, join.getName());
          if (!extraSql.isEmpty()) {
            buf.append(inset).append("             ").append(extraSql).append(",\n");
          }
          buf.append(inset).append("    (").append(leftClass).append(" ").append(alias).append(", ")
             .append(joinClass).append(" ").append(joinAlias).append(") -> {\n");
          if (relation.isReversed()) {
            buf.append(inset).append("      ((").append(poImpl).append(") ").append(alias)
               .append(".getPersistenceDelegate()).").append(relation.getSetterName());
            if (relation.isSerialized()) {
              buf.append("Blunt");
            }
            buf.append("(").append(joinAlias).append(");\n");
          }
          else {
            buf.append(inset).append("      ((").append(poImpl).append(") ").append(alias)
               .append(".getPersistenceDelegate()).").append(relation.getGetterName()).append("Blunt().addBlunt(")
               .append(joinAlias).append(");\n");
            if (relation.getLinkMethodName() != null) {
              String joinImpl = deriveClassNameForEntity(relation.getForeignEntity());
              buf.append(inset).append("      ((").append(joinImpl).append(") ")
                 .append(joinAlias).append(".getPersistenceDelegate()).")
                 .append(createRelationUpdateReferenceCode(relation, alias, true)).append(";\n");
            }
          }
        }
        else {
          buf.append(inset).append("  new Join<>(JoinType.LEFT, ");
          if (parentName == null) {
            buf.append("getColumnName(CN_").append(relation.getAttribute().getName().toUpperCase()).append("), \"");
          }
          else {
            buf.append('"').append(parentName).append('.').append(relation.getAttribute().getColumnName()).append("\", \"");
          }
          buf.append(join.getName()).append(".id\", ")
             .append(joinClass).append(".class, \"").append(join.getName()).append("\",\n")
             .append(inset).append("    (").append(leftClass).append(" ").append(alias).append(", ")
             .append(joinClass).append(" ").append(joinAlias).append(") -> {\n")
             .append(inset).append("      ((").append(poImpl).append(") ")
             .append(alias).append(".getPersistenceDelegate()).")
             .append(relation.getSetterName());
          if (relation.isSerialized()) {
            buf.append("Blunt");
          }
          buf.append("(").append(joinAlias).append(");\n");
        }
        buf.append(inset).append("    }\n")
           .append(inset).append("  )\n");
      }
      List<JoinPath> subPaths = path.getPaths();
      if (!subPaths.isEmpty()) {
        // all subpaths start with the same entity
        Entity subEntity = subPaths.get(0).getElements().get(0).getRelation().getEntity();
        createJoinsImpl(buf, inset + "  ", subPaths, subEntity.getName(), lastJoinName);
      }
      buf.append(inset).append(")\n");
    }
  }


  private String createOptionalWhereForJoin(Relation relation, String parentName, String joinName) {
    StringBuilder buf = new StringBuilder();
    List<MethodArgument> methodArgs = relation.getMethodArgs();
    if (methodArgs.size() > 1) {
      for (MethodArgument methodArg: methodArgs.subList(1, methodArgs.size())) {
        Attribute attr = methodArg.getForeignAttribute();
        buf.append("\" AND ").append(joinName).append('.')
           .append(attr.getColumnName())
           .append('=');
        if (methodArg.isValue()) {
          buf.append('?');
          for (DataType.SqlTypeWithPostfix sp: attr.getDataType().getSqlTypesWithPostfix()) {
            String name = attr.getName().toUpperCase() + sp.getPostfix();
            boolean mainColumn = sp.getPostfix().isEmpty();
            if (!mainColumn) {
              buf.append(" AND ").append(joinName).append('.').append(name).append("=?");
            }
          }
          buf.append('"');
        }
        else {
          attr = methodArg.getAttribute();
          if (parentName == null) {
            buf.append("\" + getColumnName(CN_").append(attr.getName().toUpperCase()).append(")");
          }
          else {
            buf.append('"').append(parentName).append('.').append(attr.getColumnName()).append("\"");
          }
        }
      }
    }
    return buf.toString();
  }



  /**
   * Creates optional JDBC-set paramaters code for joins with extra arguments.
   *
   * @return the optional code, empty if none
   * @throws WurbelException if failed
   */
  public String createJoinSetPars() throws WurbelException {
    StringBuilder buf = new StringBuilder();
    for (JoinPath path: getJoinPaths()) {
      createJoinSetPars(buf, path);
    }
    return buf.toString();
  }

  private void createJoinSetPars(StringBuilder buf, JoinPath path) throws WurbelException {
    if (!path.getElements().isEmpty()) {
      Join join = path.getElements().get(0);    // normalized, i.e. only one (see above)
      Relation relation = join.getRelation();
      if (isClassIdRequiredInWhereClause(relation.getForeignEntity())) {
        buf.append("    st.setInt(ndx++, ").append(relation.getForeignEntity().getClassId()).append(");\n");
      }
      List<MethodArgument> methodArgs = relation.getMethodArgs();
      if (methodArgs.size() > 1) {
        for (MethodArgument methodArg: methodArgs.subList(1, methodArgs.size())) {
          if (methodArg.isValue()) {
            Attribute attr = methodArg.getForeignAttribute();
            buf.append("    st.")
               .append(createJdbcSetterName(attr))
               .append("(ndx++, ")
               .append(getJdbcCode(attr, attr.toMethodArgument(methodArg.getValue())));
            if (attr.getOptions().isMapNull())  {
              buf.append(", true);\n");
            }
            else {
              buf.append(");\n");
            }
            // for multi column types, such as BMoney/DMoney
            for (int p = 1; p < attr.getDataType().getSqlTypes().length; p++) {
              buf.append("    ndx++;\n");
            }
          }
        }
      }
      for (JoinPath subPath: path.getPaths()) {
        createJoinSetPars(buf, subPath);
      }
    }
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
    for (WurbletArgument key: getMethodArguments())  {
      if (key.isMethodArgument()) {
        String arg = key.getMethodArgumentName();
        if (argSet.add(arg)) {
          Attribute attr = key.getAttribute();
          try {
            appendCommaSeparated(params, attr.getJavaType());
          }
          catch (ModelException me) {
            throw new WurbelException("cannot determine java type for key " + key, me);
          }
          params.append(' ');
          params.append(arg);
        }
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
   * @throws WurbelException if failed
   */
  public String buildInvocationParameters(boolean limit, boolean offset) throws WurbelException {

    StringBuilder params = new StringBuilder();

    if (limit) {
      appendCommaSeparated(params, "limit");
    }
    if (offset) {
      appendCommaSeparated(params, "offset");
    }

    Set<String> argSet = new HashSet<>();     // each arg only once
    for (WurbletArgument key: getMethodArguments())  {
      if (key.isMethodArgument()) {
        String arg = key.getMethodArgumentName();
        if (argSet.add(arg)) {
          appendCommaSeparated(params, arg);
        }
      }
    }

    return params.toString();
  }


  /**
   * Builds the string of invocation parameters.
   *
   * @return the invocation parameters
   * @throws WurbelException if failed
   */
  public String buildInvocationParameters() throws WurbelException {
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
   * Creates the argument string for select- or delete statement of a relation.
   *
   * @param relation the relation.
   * @return the arg string
   */
  public String createRelationArgString(Relation relation) {
    StringBuilder buf = new StringBuilder();
    buf.append('(');
    List<MethodArgument> args = relation.getMethodArgs();
    boolean first = true;
    for (MethodArgument arg: args) {
      if (first) {
        first = false;
      }
      else  {
        buf.append(", ");
      }
      buf.append(arg.getMethodArgument());
    }
    buf.append(')');
    return buf.toString();
  }


  /**
   * Creates the wurblet argument string for select- or delete statement of a relation.
   *
   * @param relation the relation.
   * @return the arg string
   */
  public String createRelationWurbletArgString(Relation relation) {
    StringBuilder buf = new StringBuilder();
    List<MethodArgument> args = relation.getMethodArgs();
    for (MethodArgument arg: args) {
      if (buf.length() > 0) {
        buf.append(' ');
      }
      buf.append(arg.getForeignAttribute().getName());
    }
    return buf.toString();
  }


  /**
   * Creates the java code to select a relation.
   *
   * @param relation the relation
   * @return the java code
   */
  public String createRelationSelectCode(Relation relation) {
    StringBuilder text = new StringBuilder();
    if (relation.getForeignEntity().isAbstract() &&
        relation.getRelationType() == RelationType.OBJECT &&
        relation.getMethodName() == null) {
      /**
       * we need a cast for object relations of type T and methods "select" or "selectCached" (i.e. from framework).
       * Ex.:
       *
       * OrgUnit<?> ou = on(OrgUnit.class).select(id);    ERROR because on(OrgUnit.class) is of type OrgUnit and not OrgUnit<?>
       *
       * but:
       *
       * OrgUnit<?> ou = on(OrgUnit.class);  OK!
       * ou = ou.select(id);                 OK!
       */
      text.append("(").append(relation.getClassName()).append("<?>) ");
    }
    text.append("on(").append(relation.getClassName()).append(".class).")
        .append(createRelationSelectMethodName(relation))
        .append(createRelationArgString(relation));
    return text.toString();
  }


  /**
   * Creates the java code to update the reference of a relation.
   *
   * @param relation the relation
   * @param pdo the PDO name
   * @param blunt use the blunt setter method, if provided
   * @return the java code
   */
  public String createRelationUpdateReferenceCode(Relation relation, String pdo, boolean blunt) {
    Relation foreignRel = relation.getForeignRelation();
    if (blunt &&
        (foreignRel == null ||
         foreignRel.getRelationType() != RelationType.OBJECT || !foreignRel.isSerialized() ||
         (foreignRel.getSelectionType() != SelectionType.LAZY && foreignRel.getSelectionType() != SelectionType.EAGER))) {
      blunt = false;
    }

    if (!blunt && relation.getLinkMethodName() != null) {
      return relation.getLinkMethodName() + (relation.getLinkMethodIndex() != null ?
              ("(" + pdo + ", ndx++)") : ("(" + pdo + ")"));
    }
    else {
      if (foreignRel != null) {
        return foreignRel.getSetterName() + (blunt ? "Blunt" : "") + "(" + pdo + ")";
      }
      return "set" + relation.getForeignEntity() + (blunt ? "Blunt" : "") + "(" + pdo + ")";
    }
  }

  /**
   * Creates the java code to update the reference of a relation.<br>
   * The pdo name is fixed {@code "me()"}.
   *
   * @param relation the relation
   * @return the java code
   */
  public String createRelationUpdateReferenceCode(Relation relation) {
    return createRelationUpdateReferenceCode(relation, "me()", false);
  }

  /**
   * Creates the set-first-arg method name (useful only in object relations).
   *
   * @param relation the relation
   * @return the set-method name
   */
  public String createRelationSetFirstArgMethodName(Relation relation) {
    return "set" + StringHelper.firstToUpper(relation.getMethodArgs().get(0).getAttribute().getName());
  }


  /**
   * Creates the java code to delete a relation.
   *
   * @param relation the relation
   * @return the java code
   */
  public String createRelationDeleteCode(Relation relation) {
    StringBuilder text = new StringBuilder();
    text.append("on(").append(relation.getClassName()).append(".class).")
        .append(createListRelationDeleteMethodName(relation))
        .append(createRelationArgString(relation));
    return text.toString();
  }


  /**
   * Creates the java code to set the link of a relation.
   *
   * @param relation the relation
   * @return the java code
   */
  public String createRelationLinkCode(Relation relation) {
    if (relation.getLinkMethodName() != null) {
      return relation.getLinkMethodName() + (relation.getLinkMethodIndex() != null ? "(me(), ndx++)" : "(me())");
    }
    String text = "set";
    if (relation.getMethodName() != null) {
      text += relation.getMethodName();
    }
    else {
      text += relation.getEntity().getName() + "Id";
    }
    return text + createRelationArgString(relation);
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

}

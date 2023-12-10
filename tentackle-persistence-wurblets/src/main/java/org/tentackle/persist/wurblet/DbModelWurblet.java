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

package org.tentackle.persist.wurblet;

import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeSorting;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.SortType;
import org.tentackle.model.TrackType;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.wurblet.Join;
import org.tentackle.wurblet.JoinPath;
import org.tentackle.wurblet.ModelWurblet;
import org.tentackle.wurblet.WurbletArgument;
import org.tentackle.wurblet.WurbletArgumentExpression;
import org.tentackle.wurblet.WurbletArgumentParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;


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
          if (ctx.isEmpty()) {
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
   * Asserts that entity can be selected from the database.
   *
   * @throws WurbelException if not
   */
  public void assertEntityNotEmbedded() throws WurbelException {
    if (getEntity().isEmbedded()) {
      StringBuilder buf = new StringBuilder();
      for (Entity embeddingEntity : getEntity().getEmbeddingEntities()) {
        if (!buf.isEmpty()) {
          buf.append(", ");
        }
        buf.append(embeddingEntity);
      }
      throw new WurbelException(getEntity() + " is embedded in " + buf);
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
   * Creates the relop code for prepared statements.<br>
   * The code assumes a StringBuilder and begins with <code>".append("</code>.
   *
   * @param arg the wurblet argument
   * @return the java code
   */
  public String createRelopCode(WurbletArgument arg) throws WurbelException {

    if (arg.isArray()) {
      // this feature may not be supported by all backends
      String op = arg.getArrayOperator();
      assertSupportedByBackends("array operator '" + op + "'", b -> b.isArrayOperatorSupported(op));
    }

    String value = arg.getValue();
    if (arg.isValueLiterally() && !value.startsWith("\"")) {
      value = StringHelper.toDoubleQuotes(value);
    }

    String str;

    // translate to predefined strings, if possible
    switch(arg.getRelop()) {
      case Backend.SQL_EQUAL:
        if (arg.isValueLiterally()) {
          str = "Backend.SQL_EQUAL).append(" + value;
        }
        else  {
          str = arg.isArray() ? "Backend.SQL_EQUAL" : "Backend.SQL_EQUAL_PAR";
        }
        break;

      case Backend.SQL_NOTEQUAL:
      case "!=":
        if (arg.isValueLiterally()) {
          str = "Backend.SQL_NOTEQUAL).append(" + value;
        }
        else  {
          str = arg.isArray() ? "Backend.SQL_NOTEQUAL" : "Backend.SQL_NOTEQUAL_PAR";
        }
        break;

      case Backend.SQL_LESS:
        if (arg.isValueLiterally()) {
          str = "Backend.SQL_LESS).append(" + value;
        }
        else  {
          str = arg.isArray() ? "Backend.SQL_LESS" : "Backend.SQL_LESS_PAR";
        }
        break;

      case Backend.SQL_GREATER:
        if (arg.isValueLiterally()) {
          str = "Backend.SQL_GREATER).append(" + value;
        }
        else  {
          str = arg.isArray() ? "Backend.SQL_GREATER" : "Backend.SQL_GREATER_PAR";
        }
        break;

      case Backend.SQL_LESSOREQUAL:
        if (arg.isValueLiterally()) {
          str = "Backend.SQL_LESSOREQUAL).append(" + value;
        }
        else  {
          str = arg.isArray() ? "Backend.SQL_LESSOREQUAL" : "Backend.SQL_LESSOREQUAL_PAR";
        }
        break;

      case Backend.SQL_GREATEROREQUAL:
        if (arg.isValueLiterally()) {
          str = "Backend.SQL_GREATEROREQUAL).append(" + value;
        }
        else  {
          str = arg.isArray() ? "Backend.SQL_GREATEROREQUAL" : "Backend.SQL_GREATEROREQUAL_PAR";
        }
        break;

      case Backend.SQL_LIKE:
        if (arg.isValueLiterally()) {
          str = "Backend.SQL_LIKE).append(" + value;
        }
        else  {
          str = "Backend.SQL_LIKE_PAR";
        }
        break;

      case Backend.SQL_NOTLIKE:
        if (arg.isValueLiterally()) {
          str = "Backend.SQL_NOTLIKE).append(" + value;
        }
        else  {
          str = "Backend.SQL_NOTLIKE_PAR";
        }
        break;

      case Backend.SQL_ISNULL:
        str = "Backend.SQL_ISNULL";
        break;

      case Backend.SQL_ISNOTNULL:
        str = "Backend.SQL_ISNOTNULL";
        break;

      default:
        if (arg.isArray()) {
          str = "";
        }
        else {
          str = "\"" + arg.getRelop() + (arg.isValueLiterally() ? value : "?") + "\"";
        }
    }

    if (!str.isEmpty()) {
      str = ".append(" + str + ")";
    }

    if (arg.isArray()) {
      switch (arg.getArrayOperator()) {
        case Backend.SQL_ARRAY_ANY:
          str += ".append(Backend.SQL_ARRAY_ANY_PAR)";
          break;
        case Backend.SQL_ARRAY_ALL:
          str += ".append(Backend.SQL_ARRAY_ALL_PAR)";
          break;
        case Backend.SQL_ARRAY_NOT_IN:
          str = ".append(Backend.SQL_ARRAY_NOT_IN_PAR)";  // includes leading space
          break;
        default:
          str = ".append(Backend.SQL_ARRAY_IN_PAR)";    // includes leading space
      }
    }

    return str;
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
        int[] columnIndexes;
        Attribute attr = key.getAttribute();
        Integer index = key.getColumnIndex();
        DataType<?> effectiveDataType = getEffectiveDataType(attr);
        if (index != null) {
          columnIndexes = new int[] { index };
        }
        else {
          columnIndexes = effectiveDataType.getSortableColumns();
        }
        if (columnIndexes == null || columnIndexes.length == 0) {
          throw new WurbelException(effectiveDataType + " is not sortable by the database");
        }
        for (int columnIndex : columnIndexes) {
          if (needComma) {
            buf.append("\n           .append(Backend.SQL_COMMA)");
          }
          String name = getColumnNameConstant(attr, columnIndex);
          buf.append(".append(");
          if (getEntity().getHierarchyInheritanceType() == InheritanceType.MULTI) {
            buf.append(deriveClassNameForEntity(getEntity().getTopSuperEntity()))
               .append(".CLASSVARIABLES.getColumnName(").append(name).append(')');
          }
          else  {
            if (!getEntity().equals(attr.getEntity())) {
              // must be a joined component (otherwise the column is not in the result set)
              List<Relation> relations = key.getRelations();
              List<Relation> embeddingPath = key.getEmbeddingPath();
              if (embeddingPath != null && !embeddingPath.isEmpty() && relations != null && relations.size() > embeddingPath.size()) {
                relations = new ArrayList<>(relations.subList(0, relations.size() - embeddingPath.size()));
              }
              boolean joinFound = false;
              for (JoinPath joinPath: getJoinPaths()) {
                Join join = joinPath.findJoin(relations);
                if (join == null) {
                  join = joinPath.findJoin(key.getComponent());
                }
                if (join != null) {
                  buf.append('"').append(join.getName()).append('.');
                  if (attr.isEmbedded()) {
                    buf.append(key.getEmbeddingColumnPrefix()).append(attr.getColumnName());
                  }
                  else {
                    buf.append(attr.getColumnName());
                  }
                  buf.append('"');
                  joinFound = true;
                  break;
                }
              }
              if (!joinFound) {
                if ((relations == null || relations.isEmpty()) && embeddingPath != null && !embeddingPath.isEmpty()) {
                  buf.append('"').append(key.getEmbeddingColumnPrefix()).append(attr.getColumnName()).append('"');
                }
                else {
                  throw new WurbelException("missing join for sort key: " + key);
                }
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
   * Returns whether joins with delegates of abstract classes are used.<br>
   * The casts of such joins are "unchecked" and need a SuppressWarnings to avoid Xlint warnings.
   *
   * @return true if join paths involve delegates of abstract classes
   * @throws WurbelException if failed
   */
  public boolean isAbstractJoinPath(List<JoinPath> paths) throws WurbelException {
    boolean isAbstract = false;
    for (JoinPath path: paths) {
      if (!path.getElements().isEmpty()) {
        Join join = path.getElements().get(0);  // normalized, i.e. only one (see above)
        Relation relation = join.getRelation();
        if (relation.getEntity().isAbstract() || relation.getForeignEntity().isAbstract()) {
          isAbstract = true;
          break;
        }
      }
      if (isAbstractJoinPath(path.getPaths())) {
        isAbstract = true;
        break;
      }
    }
    return isAbstract;
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
    createJoinsImpl(buf, getEntity(), "      ", getJoinPaths(), pdoType, null);
    buf.replace(buf.length() - 1, buf.length(), ";\n");
    return buf.toString();
  }

  private void createJoinsImpl(StringBuilder buf, Entity lastNonEmbeddedEntity, String inset, List<JoinPath> paths, String pdoType, String parentName)
          throws WurbelException {
    for (JoinPath path: paths) {
      String lastJoinName = null;
      if (!path.getElements().isEmpty()) {
        Join join = path.getElements().get(0);    // normalized, i.e. only one (see above)
        Relation relation = join.getRelation();
        lastJoinName = join.getName();
        String poImpl = deriveClassNameForEntity(relation.getEntity());
        String alias = lastNonEmbeddedEntity.getTopSuperEntity().getTableAlias();
        String leftClass = relation.getEntity().equals(getEntity()) ? pdoType : lastNonEmbeddedEntity.getName();
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
          Attribute attribute = relation.getMethodArgs().getFirst().getAttribute();   // not relation.getAttribute!
          if (parentName == null) {
            buf.append("getColumnName(");
            if (attribute.isEmbedded()) {
              buf.append('"').append(join.getArgument().getEmbeddingColumnPrefix()).append(attribute.getColumnName()).append('"');
            }
            else {
              buf.append(getColumnNameConstant(attribute, 0));
            }
            buf.append("), \"");
          }
          else {
            buf.append('"').append(parentName).append('.').append(attribute.getColumnName()).append("\", \"");
          }
          buf.append(join.getName()).append(".id\", ")
             .append(joinClass).append(".class, \"").append(join.getName()).append("\",\n")
             .append(inset).append("    (").append(leftClass).append(" ").append(alias).append(", ")
             .append(joinClass).append(" ").append(joinAlias).append(") -> {\n")
             .append(inset).append("      ((").append(poImpl).append(") ")
             .append(alias).append('.');
          if (relation.getEntity().isEmbedded()) {
            buf.append(join.getArgument().getEmbeddingGetterPrefix());
            StringTokenizer stok = new StringTokenizer(relation.getPathName(), ".");
            while(stok.hasMoreTokens()) {
              buf.append("get").append(stok.nextToken()).append("().");
            }
          }
          buf.append("getPersistenceDelegate()).")
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
        // all sub paths start with the same entity
        Relation subRelation = subPaths.get(0).getElements().getFirst().getRelation();
        Entity subEntity = subRelation.getEntity();
        createJoinsImpl(buf, subEntity.isEmbedded() ? lastNonEmbeddedEntity : subEntity, inset + "  ", subPaths, subEntity.getName(), lastJoinName);
      }
      buf.append(inset).append(")\n");
    }
  }


  private String createOptionalWhereForJoin(Relation relation, String parentName, String joinName) throws WurbelException {
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
          DataType<?> effectiveDataType = getEffectiveDataType(attr);
          if (effectiveDataType.isColumnCountBackendSpecific()) {
            throw new WurbelException("backend-specific datatype " + effectiveDataType + " cannot be used in joins");
          }
          for (int columnIndex = 1; columnIndex < effectiveDataType.getColumnCount(null); columnIndex++) {   // for more than one column
            buf.append(" AND ").append(joinName).append('.').append(getColumnName(attr, columnIndex)).append("=?");
          }
          buf.append('"');
        }
        else {    // join by ID
          attr = methodArg.getAttribute();
          if (parentName == null) {
            buf.append("\" + getColumnName(").append(getColumnNameConstant(attr, 0)).append(")");
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
   * Creates the java code to set a parameter in a PreparedStatementWrapper.<br>
   * Works for single- and multi-column data types.
   *
   * @param arg the wurblet argument
   * @return the java code
   * @throws WurbelException if malformed or illegal wurblet argument
   * @throws ModelException if getting the jdbc java code failed
   */
  public String createWhereSetPars(WurbletArgument arg) throws WurbelException, ModelException {
    Attribute attr = arg.getAttribute();
    int columnIndex = 0;
    if (arg.isArray()) {
      String javaType;
      if (attr.isConvertible()) {
        javaType = attr.getInnerTypeName();
        columnIndex = -1;
      }
      else {
        javaType = attr.getDataType().toNonPrimitive().getJavaType();
        if (arg.getColumnIndex() != null) {
          columnIndex = arg.getColumnIndex();
        }
      }
      return "st.setArray(ndx++, " + javaType + ".class, " + columnIndex + ", " +
             attr.toMethodArgument(arg.getJdbcValue()) + ", Backend.SQL_ARRAY_" +
             arg.getArrayOperator().replace(' ', '_') + ")";
    }
    if (arg.getColumnIndex() == null) {
      return createWhereSetPars(attr, attr.toMethodArgument(arg.getJdbcValue()));
    }
    columnIndex = arg.getColumnIndex();
    StringBuilder buf = new StringBuilder();
    if (arg.getDataType().isPredefined()) {   // predefined multi-column types like BMoney, OffsetDateTime, etc...
      String jdbcValue = arg.getJdbcValue();
      buf.append("st.set(SqlType.").append(arg.getDataType().getSqlType(null, columnIndex)).append(", ndx++, ");
      if (arg.isMethodArgument()) {
        buf.append(arg.getDataType().getColumnGetter(columnIndex, jdbcValue));
      }
      else {
        buf.append(jdbcValue);
      }
      buf.append(")");
    }
    else {
      buf.append("ndx += st.set(");
      if (!attr.getEntity().equals(getEntity())) {
        buf.append(deriveClassNameForEntity(attr.getEntity())).append('.');
      }
      buf.append(arg.getDataType().getDataTypeConstant())
         .append(", ndx, ")
         .append(arg.getJdbcValue())
         .append(", ").append(columnIndex)
         .append(", ").append(attr.getOptions().isMapNull())
         .append(", ").append(attr.getSize()).append(")");
    }
    return buf.toString();
  }


  /**
   * Creates the java code to set a single-column parameter in a PreparedStatementWrapper.
   *
   * @param attr the model attribute
   * @param argument the argument string
   * @return the created java code
   * @throws WurbelException if failed
   */
  public String createWhereSetPars(Attribute attr, String argument) throws WurbelException {
    StringBuilder buf = new StringBuilder();
    DataType<?> dataType = getEffectiveDataType(attr);
    if (dataType.isPredefined()) {
      buf.append("st.")
         .append(createJdbcSetterName(dataType))
         .append("(ndx++, ")
         .append(getJdbcCode(attr, argument));
      if (attr.getOptions().isMapNull()) {
        buf.append(", true)");
      }
      else {
        buf.append(")");
      }
      // for multi-column types, such as BMoney (not application specific types)
      if (dataType.isColumnCountBackendSpecific()) {
        throw new WurbelException("backend-specific datatype " + dataType + " cannot be used in where-clauses");
      }
      int diff = dataType.getColumnCount(null) - 1;
      if (diff == 1) {
        buf.append(";\n    ndx++");
      }
      else if (diff > 1) {
        buf.append(";\n    ndx += ").append(diff);
      }
    }
    else {
      buf.append("ndx += st.set(");
      if (!attr.getEntity().equals(getEntity())) {
        buf.append(deriveClassNameForEntity(attr.getEntity())).append('.');
      }
      buf.append(dataType.getDataTypeConstant())
         .append(", ndx, ")
         .append(getJdbcCode(attr, argument))
         .append(", ").append(attr.getOptions().isMapNull())
         .append(", ").append(attr.getSize()).append(")");
    }
    return buf.toString();
  }


  /**
   * Creates optional JDBC-set parameters code for joins with extra arguments.
   *
   * @return the optional code, empty if none
   * @throws WurbelException if failed
   */
  public String createJoinSetPars() throws WurbelException, ModelException {
    StringBuilder buf = new StringBuilder();
    for (JoinPath path: getJoinPaths()) {
      createJoinSetPars(buf, path);
    }
    return buf.toString();
  }

  private void createJoinSetPars(StringBuilder buf, JoinPath path) throws WurbelException, ModelException {
    if (!path.getElements().isEmpty()) {
      Join join = path.getElements().get(0);    // normalized, i.e. only one (see above)
      Relation relation = join.getRelation();
      List<MethodArgument> methodArgs = relation.getMethodArgs();
      if (methodArgs.size() > 1) {
        for (MethodArgument methodArg: methodArgs.subList(1, methodArgs.size())) {
          if (methodArg.isValue()) {
            Attribute attr = methodArg.getForeignAttribute();
            buf.append("    ").append(createWhereSetPars(attr, attr.toMethodArgument(methodArg.getValue()))).append(";\n");
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
      if (Character.isUpperCase(c) && !buf.isEmpty()) {
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
            String javaType;
            if (key.isArray()) {
              String elementType;
              if (attr.isConvertible()) {
                elementType = attr.getApplicationTypeName();
              }
              else {
                elementType = attr.getDataType().toNonPrimitive().getJavaType();
              }
              javaType = "Collection<" + elementType + ">";
            }
            else {
              javaType = attr.getJavaType();
            }
            appendCommaSeparated(params, javaType);
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
   * @throws WurbelException if failed
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
   * Determines whether one the wurblet arguments refer to a value returned by a PDO's method.
   *
   * @return true if at least one argument is something like "getBlah()"
   */
  public boolean isPdoProvidingArguments() {
    for (WurbletArgument key: getMethodArguments())  {
      if (!key.isMethodArgument() && !key.isValueLiterally()) {
        String arg = key.getValue();
        if (arg != null && arg.endsWith("()")) {
          return true;
        }
      }
    }
    return false;
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
    if (str != null && !str.isEmpty()) {
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
   * Creates the "setXXXX" method name for PreparedStatementWrapper.
   *
   * @param dataType the datatype
   * @return the method name
   */
  public String createJdbcSetterName(DataType<?> dataType) {
    StringBuilder buf = new StringBuilder("set");
    if (dataType.isPrimitive()) {
      buf.append(StringHelper.firstToUpper(dataType.getJavaType()));
    }
    else  {
      if ("String".equals(dataType.getJavaType()) && DataTypeFactory.LARGE_VARIANT.equals(dataType.getVariant())) {
        buf.append("LargeString");
      }
      else {
        buf.append(dataType.getJavaType());
      }
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
    DataType<?> dataType = getEffectiveDataType(attribute);
    if (dataType.isPrimitive()) {
      buf.append(StringHelper.firstToUpper(dataType.getJavaType()));
    }
    else {
      switch (dataType.getJavaType()) {
        case "Boolean":
        case "Byte":
        case "Short":
        case "Long":
        case "Float":
        case "Double":
          buf.append('A');   // ABoolean, AByte, etc...
          break;

        case "String":
          if (DataTypeFactory.LARGE_VARIANT.equals(dataType.getVariant())) {
            buf.append("Large");    // LargeString
          }
          break;
      }
      buf.append(dataType.getJavaType());
    }
    return buf.toString();
  }


  /**
   * Creates java code applicable to the application model from JDBC variable access code.<br>
   * Applies .toInternal() if attribute is an application specific type.
   * Otherwise, jdbcCode is returned unchanged.
   *
   * @param attribute the attribute
   * @param jdbcCode the jdbc code
   * @return the code the model code
   * @throws WurbelException if some model error
   */
  public String getModelCode(Attribute attribute, String jdbcCode) throws WurbelException {
    if (attribute.isConvertible()) {
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
   * Otherwise, modelCode is returned unchanged.
   *
   * @param attribute the attribute
   * @param modelCode the jdbc code
   * @return the code the model code
   * @throws WurbelException if some model error
   */
  public String getJdbcCode(Attribute attribute, String modelCode) throws WurbelException {
    String jdbcCode = modelCode;
    if (attribute.isConvertible()) {
      try {
        String applicationType = attribute.getApplicationTypeName();
        if (modelCode.startsWith(applicationType + ".")) {
          // (Enum) constant
          jdbcCode = modelCode + ".toExternal()";
        }
        else {
          if (attribute.getInnerDataType().isPrimitive()) {
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
   * Creates the argument string for select or delete statement of a relation.
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
   * Creates the wurblet argument string for select or delete statement of a relation.
   *
   * @param relation the relation.
   * @return the arg string
   */
  public String createRelationWurbletArgString(Relation relation) {
    StringBuilder buf = new StringBuilder();
    List<MethodArgument> args = relation.getMethodArgs();
    for (MethodArgument arg: args) {
      if (!buf.isEmpty()) {
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
      /*
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
      // foreignRel == null -> blunt = false (see above)
      return "set" + relation.getForeignEntity() + "(" + pdo + ")";
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
   * @throws ModelException if unexpected method args
   */
  public String createRelationSetFirstArgMethodName(Relation relation) throws ModelException {
    if (relation.getMethodArgs().size() > 1) {
      throw new ModelException("more than one method argument for relation " + relation, relation);
    }
    return "set" + StringHelper.firstToUpper(getFirstMethodAttribute(relation).getName());
  }


  /**
   * Gets the method attribute for a relation.
   *
   * @param relation the relation
   * @return the attribute
   * @throws ModelException if no method args found or missing attribute
   */
  public Attribute getFirstMethodAttribute(Relation relation) throws ModelException {
    List<MethodArgument> methodArgs = relation.getMethodArgs();
    if (methodArgs.isEmpty()) {
      throw new ModelException("no method arguments for relation " + relation, relation);
    }
    Attribute attribute = methodArgs.get(0).getAttribute();
    if (attribute == null) {
      throw new ModelException("missing attribute for relation " + relation, relation);
    }
    return attribute;
  }


  /**
   * Creates the java code to delete a relation.
   *
   * @param relation the relation
   * @return the java code
   */
  public String createRelationDeleteCode(Relation relation) {
    return "on(" + relation.getClassName() + ".class)." +
           createListRelationDeleteMethodName(relation) +
           createRelationArgString(relation);
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
   * @return the eager relations, empty list if no optimization possible or no eager relations at all
   */
  public List<Relation> getEagerRelations() throws ModelException {
    List<Relation> eagerRelations = new ArrayList<>();
    outer:
    for (Relation relation: getEntity().getRelations()) {
      if (relation.getSelectionType() == SelectionType.EAGER) {
        // verify that no subclass has an eager relation
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

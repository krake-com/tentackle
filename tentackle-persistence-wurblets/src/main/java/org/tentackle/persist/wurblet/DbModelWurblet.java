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
import org.tentackle.wurblet.Join;
import org.tentackle.wurblet.JoinPath;
import org.tentackle.wurblet.ModelWurblet;
import org.tentackle.wurblet.WurbletArgument;
import org.tentackle.wurblet.WurbletArgumentExpression;
import org.tentackle.wurblet.WurbletArgumentParser;
import org.tentackle.wurblet.WurbletRelation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;


/**
 * Base class for persistence wurblets.
 * <p>
 * The following wurblet options are supported:
 * <ul>
 * <li><em>--context=&lt;key&gt;:</em> defines the context id (--context= turns off default context).</li>
 * <li><em>--tracked:</em> return a TrackedList instead of List (useful if [TRACKED] not given in the model).</li>
 * <li><em>--attracked:</em> same as --tracked but generate is...Modified per attribute.</li>
 * <li><em>--fulltracked:</em> same as --attracked but keep last persisted values as well.</li>
 * <li><em>--untracked:</em> disable [TRACKED, ATTRACKED, or FULLTRACKED] from the model.</li>
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
  private List<WurbletArgument> methodArguments;

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
      if (wurbletOptions.contains("pathAllowed")) {
        pathAllowed = true;
      }
    }

    super.run();

    Entity entity = getEntity();
    if (entity != null) {   // null if missingModelOk set and no such entity in model (see ModelWurblet)

      applyTrackingAndContextOptions(entity);

      // analyze the wurblet arguments
      parser = new WurbletArgumentParser(entity, argumentGroupingEnabled, getWurbletArgs());

      if (!pathAllowed) {
        for (WurbletArgument arg: parser.getAllArguments()) {
          if (arg.isPath()) {
            throw new WurbelException("relation paths not allowed in wurblet " + this);
          }
        }
      }

      // optional conditions are generated for the where-expression only, not for the filters of load joins
      for (WurbletArgument arg: getMethodArguments()) {
        if (arg.isOptional() && !parser.getExpressionArguments().contains(arg)) {
          throw new WurbelException("optional condition '" + arg + "' not allowed in a join filter");
        }
      }

      if (isRemote()) {
        // check whether relations are not transient
        for (WurbletArgument joinArgument: parser.getJoinArguments()) {
          List<WurbletRelation> wurbletRelations = joinArgument.getWurbletRelations();
          if (wurbletRelations != null) {   // is always != null, but one never knows ...
            for (WurbletRelation wurbletRelation : wurbletRelations) {
              Relation relation = wurbletRelation.getRelation();
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
          else {
            throw new WurbelException("join argument '" + joinArgument + "' does not provide relations?");
          }
        }
      }
    }

  }


  /**
   * Applies the tracking and context options.<br>
   * The defaults are taken from the entity's model and overridden by the options
   * {@code --tracked}, {@code --attracked}, {@code --fulltracked}, {@code --untracked} and {@code --context=<key>}.
   *
   * @param entity the entity
   * @throws WurbelException if the context attribute does not exist
   */
  protected void applyTrackingAndContextOptions(Entity entity) throws WurbelException {
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
          // a typo must not silently drop the context condition
          contextAttribute = entity.getAttributeByJavaName(ctx, true);
          if (contextAttribute == null) {
            throw new WurbelException("no such context attribute '" + ctx + "' in " + entity);
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
    if (methodArguments == null) {
      List<WurbletArgument> wurbletArguments = new ArrayList<>();
      for (JoinPath joinPath : getJoinPaths()) {
        for (Join element : joinPath.getElements()) {
          WurbletArgumentParser elementParser = element.getWurbletRelation().getParser();
          if (elementParser != null) {
            wurbletArguments.addAll(elementParser.getMethodArguments());
          }
        }
      }
      wurbletArguments.addAll(parser.getMethodArguments());
      methodArguments = wurbletArguments;
    }
    return methodArguments;
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
   * Returns whether at least one condition is optional.<br>
   * Because the SQL text of such finders depends on the boolean method arguments,
   * the prepared statement cannot be cached, i.e. the option {@code --oneshot} is implied.
   *
   * @return true if there are optional conditions
   * @see WurbletArgument#isOptional()
   */
  public boolean isWithOptionalArguments() {
    return parser.isWithOptionalArguments();
  }


  /**
   * Asserts that none of the conditions is optional.<br>
   * To be invoked by wurblets caching their prepared statement, since the SQL text
   * of an optional condition is not fixed.
   *
   * @throws WurbelException if there is an optional condition
   */
  public void assertNoOptionalArguments() throws WurbelException {
    for (WurbletArgument arg: getMethodArguments()) {
      if (arg.isOptional()) {
        throw new WurbelException("optional condition '" + arg + "' not supported by wurblet " + this);
      }
    }
  }


  /**
   * Creates the java boolean expression that determines whether the given arguments
   * are part of the query.<br>
   * The expression is true if at least one of the arguments is applicable.
   * Arguments referring to the same boolean method argument are considered only once.
   *
   * @param args the wurblet arguments
   * @return the Java code, null if the arguments are always part of the query
   */
  public String createOptionalCondition(List<WurbletArgument> args) {
    StringBuilder buf = new StringBuilder();
    Set<String> names = new LinkedHashSet<>();
    for (WurbletArgument arg: args) {
      if (!arg.isOptional()) {
        return null;    // mandatory condition: always applicable
      }
      names.add(arg.getOptionalName());
    }
    for (String name: names) {
      if (!buf.isEmpty()) {
        buf.append(" || ");
      }
      buf.append(name);
    }
    return buf.isEmpty() ? null : buf.toString();
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
   * Returns whether there are load joins with filters.
   *
   * @param componentsOnly true if check component relations only
   * @return true if the result must be made immutable, false if no filters
   */
  public boolean isWithFilteredJoins(boolean componentsOnly) {
    for (JoinPath joinPath : getJoinPaths()) {
      if (joinPath.isFiltered(componentsOnly)) {
        return true;
      }
    }
    return false;
  }


  /**
   * Creates the relational operation code for prepared statements.<br>
   * The code assumes a StringBuilder and begins with <code>".append("</code>.
   *
   * @param arg the wurblet argument
   * @return the Java code
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
          str += ".append(Backend.SQL_ARRAY_NOT_IN_PAR)";  // includes leading space
          break;
        default:
          str += ".append(Backend.SQL_ARRAY_IN_PAR)";    // includes leading space
      }
    }

    return str;
  }


  /**
   * Creates the relation operation SQL for prepared statements.<br>
   * Same as {@link #createRelopCode(WurbletArgument)}, but creates SQL code.
   *
   * @param arg the wurblet argument
   * @return the SQL code
   */
  public String createRelopSql(WurbletArgument arg) throws WurbelException {

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
          str = Backend.SQL_EQUAL + value;
        }
        else  {
          str = arg.isArray() ? Backend.SQL_EQUAL : Backend.SQL_EQUAL_PAR;
        }
        break;

      case Backend.SQL_NOTEQUAL:
      case "!=":
        if (arg.isValueLiterally()) {
          str = Backend.SQL_NOTEQUAL + value;
        }
        else  {
          str = arg.isArray() ? Backend.SQL_NOTEQUAL : Backend.SQL_NOTEQUAL_PAR;
        }
        break;

      case Backend.SQL_LESS:
        if (arg.isValueLiterally()) {
          str = Backend.SQL_LESS + value;
        }
        else  {
          str = arg.isArray() ? Backend.SQL_LESS : Backend.SQL_LESS_PAR;
        }
        break;

      case Backend.SQL_GREATER:
        if (arg.isValueLiterally()) {
          str = Backend.SQL_GREATER + value;
        }
        else  {
          str = arg.isArray() ? Backend.SQL_GREATER : Backend.SQL_GREATER_PAR;
        }
        break;

      case Backend.SQL_LESSOREQUAL:
        if (arg.isValueLiterally()) {
          str = Backend.SQL_LESSOREQUAL + value;
        }
        else  {
          str = arg.isArray() ? Backend.SQL_LESSOREQUAL : Backend.SQL_LESSOREQUAL_PAR;
        }
        break;

      case Backend.SQL_GREATEROREQUAL:
        if (arg.isValueLiterally()) {
          str = Backend.SQL_GREATEROREQUAL + value;
        }
        else  {
          str = arg.isArray() ? Backend.SQL_GREATEROREQUAL : Backend.SQL_GREATEROREQUAL_PAR;
        }
        break;

      case Backend.SQL_LIKE:
        if (arg.isValueLiterally()) {
          str = Backend.SQL_LIKE + value;
        }
        else  {
          str = Backend.SQL_LIKE_PAR;
        }
        break;

      case Backend.SQL_NOTLIKE:
        if (arg.isValueLiterally()) {
          str = Backend.SQL_NOTLIKE + value;
        }
        else  {
          str = Backend.SQL_NOTLIKE_PAR;
        }
        break;

      case Backend.SQL_ISNULL:
        str = Backend.SQL_ISNULL;
        break;

      case Backend.SQL_ISNOTNULL:
        str = Backend.SQL_ISNOTNULL;
        break;

      default:
        if (arg.isArray()) {
          str = "";
        }
        else {
          str = arg.getRelop() + (arg.isValueLiterally() ? value : "?");
        }
    }

    if (arg.isArray()) {
      switch (arg.getArrayOperator()) {
        case Backend.SQL_ARRAY_ANY:
          str += Backend.SQL_ARRAY_ANY_PAR;
          break;
        case Backend.SQL_ARRAY_ALL:
          str += Backend.SQL_ARRAY_ALL_PAR;
          break;
        case Backend.SQL_ARRAY_NOT_IN:
          str += Backend.SQL_ARRAY_NOT_IN_PAR;  // includes leading space
          break;
        default:
          str += Backend.SQL_ARRAY_IN_PAR;    // includes leading space
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
              List<WurbletRelation> wurbletRelations = key.getWurbletRelations();
              List<Relation> embeddingPath = key.getEmbeddingPath();
              if (embeddingPath != null && !embeddingPath.isEmpty() && wurbletRelations != null && wurbletRelations.size() > embeddingPath.size()) {
                wurbletRelations = new ArrayList<>(wurbletRelations.subList(0, wurbletRelations.size() - embeddingPath.size()));
              }
              boolean joinFound = false;
              for (JoinPath joinPath: getJoinPaths()) {
                Join join = joinPath.findJoin(wurbletRelations);
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
                if ((wurbletRelations == null || wurbletRelations.isEmpty()) && embeddingPath != null && !embeddingPath.isEmpty()) {
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
   */
  public boolean isAbstractJoinPath(List<JoinPath> paths) {
    boolean isAbstract = false;
    for (JoinPath path: paths) {
      if (!path.getElements().isEmpty()) {
        Join join = path.getElements().get(0);  // normalized, i.e., only one (see above)
        Relation relation = join.getWurbletRelation().getRelation();
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
   * @return the generated Java code
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
        Join join = path.getElements().get(0);    // normalized, i.e., only one (see above)
        Relation relation = join.getWurbletRelation().getRelation();
        lastJoinName = join.getName();
        String poImpl = deriveClassNameForEntity(relation.getEntity());
        String alias = lastNonEmbeddedEntity.getTableProvidingEntity().getTableAlias();
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
          String extraSql = createOptionalWhereForJoin(relation, parentName, join);
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
        Relation subRelation = subPaths.get(0).getElements().getFirst().getWurbletRelation().getRelation();
        Entity subEntity = subRelation.getEntity();
        createJoinsImpl(buf, subEntity.isEmbedded() ? lastNonEmbeddedEntity : subEntity, inset + "  ", subPaths, subEntity.getName(), lastJoinName);
      }
      buf.append(inset).append(")\n");
    }
  }


  private String createOptionalWhereForJoin(Relation relation, String parentName, Join join) throws WurbelException {
    StringBuilder buf = new StringBuilder();
    List<MethodArgument> methodArgs = relation.getMethodArgs();
    if (methodArgs.size() > 1) {
      for (MethodArgument methodArg: methodArgs.subList(1, methodArgs.size())) {
        Attribute attr = methodArg.getForeignAttribute();
        buf.append("\" AND ").append(join.getName()).append('.')
           .append(attr.getColumnName())
           .append('=');
        if (methodArg.isValue()) {
          buf.append('?');
          DataType<?> effectiveDataType = getEffectiveDataType(attr);
          if (effectiveDataType.isColumnCountBackendSpecific()) {
            throw new WurbelException("backend-specific datatype " + effectiveDataType + " cannot be used in joins");
          }
          for (int columnIndex = 1; columnIndex < effectiveDataType.getColumnCount(null); columnIndex++) {   // for more than one column
            buf.append(" AND ").append(join.getName()).append('.').append(getColumnName(attr, columnIndex)).append("=?");
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

    WurbletArgumentParser joinFilterParser = join.getWurbletRelation().getParser();
    if (joinFilterParser != null && join.getName() != null) {
      if (buf.isEmpty()) {
        buf.append('"');
      }
      else if (buf.charAt(buf.length() - 1) == '"') {
        buf.deleteCharAt(buf.length() - 1);
      }
      else {
        buf.append(" + \"");
      }
      buf.append(new JoinClauseGenerator(this, join).generate()).append('"');
    }

    return buf.toString();
  }


  /**
   * Creates the java code to set a parameter in a PreparedStatementWrapper.<br>
   * Works for single- and multi-column data types.
   *
   * @param arg the wurblet argument
   * @return the Java code
   * @throws WurbelException if malformed or illegal wurblet argument
   * @throws ModelException if getting the JDBC Java code failed
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
   * @return the created Java code
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
      // for multi-column types, such as BMoney (not application-specific types)
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
      Join join = path.getElements().get(0);    // normalized, i.e., only one (see above)
      Relation relation = join.getWurbletRelation().getRelation();
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
   * @throws WurbelException if guard name could not be determined
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
      // the boolean determining whether an optional condition is part of the query
      if (key.isOptional() && argSet.add(key.getOptionalName())) {
        appendCommaSeparated(params, "boolean");
        params.append(' ');
        params.append(key.getOptionalName());
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
      if (key.isOptional() && argSet.add(key.getOptionalName())) {
        appendCommaSeparated(params, key.getOptionalName());
      }
    }

    return params.toString();
  }


  /**
   * Determines whether one of the wurblet arguments refers to a value returned by a PDO's method.
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
   * Adds a string to a comma-separated list.
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
   * Prepends a string to a comma-separated list.
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
   * Utility method to generate the argument list.
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
      String variant = dataType.getVariant();
      if (variant.isEmpty()) {
        buf.append(dataType.getJavaType());
      }
      else {
        buf.append(variant);
      }
    }
    return buf.toString();
  }

  /**
   * Creates the "getXXXX" method name for ResultSetWrapper.
   *
   * @param attribute the model attribute
   * @return the method name
   * @throws WurbelException if the type is misconfigured
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
      }
      String variant = dataType.getVariant();
      if (variant.isEmpty()) {
        buf.append(dataType.getJavaType());
      }
      else {
        buf.append(variant);
      }
    }
    return buf.toString();
  }


  /**
   * Creates java code applicable to the application model from JDBC variable access code.<br>
   * Applies .toInternal() if the attribute is an application-specific type.
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
   * Applies .toExternal() if the attribute is an application-specific type.
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
   * Creates the argument string for the {@code select} or {@code delete} statement of a relation.
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
   * Creates the wurblet argument string for {@code select} or {@code delete} statement of a relation.
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
   * @return the Java code
   */
  public String createRelationSelectCode(Relation relation) {
    StringBuilder text = new StringBuilder();
    if (relation.getForeignEntity().isAbstract() &&
        relation.getRelationType() == RelationType.OBJECT &&
        relation.getMethodName() == null) {
      /*
       * we need a cast for object relations of type T and methods "select" or "selectCached" (i.e., from the framework).
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
   * @return the Java code
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
   * @return the Java code
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
   * @return the Java code
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
   * @return the Java code
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
   * The TT persistence layer provides an optimized {@code select} for eager relations via LeftJoins.
   * <p>
   * The joins are applied to the generic selects, i.e. to every read of the entity. Each relation
   * cascades as deep as its {@link Relation#getEagerDepth()} allows, the default 1 joining the
   * related entity only. Deeper levels follow the eager relations of the joined entity, which is
   * what turns {@code A -eager-&gt; B -eager-&gt; C} into a single statement.
   * <p>
   * The whole optimization is abandoned (an empty list is returned, so the relations are loaded by
   * follow-up selects) if a subclass of the entity declares an eager relation, because the joins of
   * a polymorphic select cannot depend on the row's class.
   *
   * @return the eager join tree, empty list if no optimization possible or no eager relations at all
   */
  public List<EagerJoin> getEagerJoinTree() {
    // the joins of a select on the base class cannot depend on the class of the row
    for (Relation rel: getEntity().getSubEntityRelations()) {
      if (rel.getSelectionType() == SelectionType.EAGER) {
        return new ArrayList<>();
      }
    }
    return createEagerJoinTree(getEntity().getRelations(), "e", 0);
  }

  /**
   * Creates the eager joins for one level of the tree.
   *
   * @param relations the relations to inspect
   * @param aliasPrefix the alias prefix of this level
   * @param remainingDepth the number of hops still allowed, 0 to take it from the relation itself
   * @return the joins, empty if none
   */
  private List<EagerJoin> createEagerJoinTree(List<Relation> relations, String aliasPrefix, int remainingDepth) {
    List<EagerJoin> eagerJoins = new ArrayList<>();
    int joinCount = 0;
    for (Relation relation: relations) {
      if (relation.getSelectionType() == SelectionType.EAGER) {
        // a root relation brings its own budget, a cascaded one spends the budget of its root
        int depth = remainingDepth == 0 ? relation.getEagerDepth() : remainingDepth;
        String alias = aliasPrefix + "_" + ++joinCount;
        EagerJoin eagerJoin = new EagerJoin(relation, alias);
        if (depth > 1) {
          eagerJoin.getJoins().addAll(
                  createEagerJoinTree(relation.getForeignEntity().getRelationsIncludingInherited(), alias, depth - 1));
        }
        eagerJoins.add(eagerJoin);
      }
    }
    return eagerJoins;
  }

  /**
   * Creates the java code for the eager joins of the class variables.
   * <p>
   * The result is the whole trailing argument of {@code PersistentObjectClassVariables.create(...)},
   * i.e. either {@code null);} or the complete {@code List.of(...));} block.
   * <p>
   * This is deliberately kept apart from {@link #createJoins()}, which emits the same kind of join
   * tree for the load joins of the {@code PdoSelect} wurblets: the class variables are a <em>static</em>
   * initializer, so the joined columns must be plain string literals, whereas {@code createJoins()}
   * runs in an instance context and can refer to {@code getColumnName(...)}.
   *
   * @return the generated Java code
   * @throws WurbelException if failed
   * @throws ModelException if the model does not allow the eager joins
   */
  public String createEagerJoins() throws WurbelException, ModelException {
    List<EagerJoin> eagerJoins = getEagerJoinTree();
    if (eagerJoins.isEmpty()) {
      return "                    null);\n";
    }
    StringBuilder buf = new StringBuilder("                    List.of(\n");
    buf.append(String.join(",\n",
                           createEagerJoinTexts(getEntity(), getPdoClassName(), getEntity().getTableAlias(),
                                                "                      ", eagerJoins, true)));
    buf.append("\n                    )\n                );\n");
    return buf.toString();
  }

  /**
   * Creates the java code for one level of the eager join tree.
   *
   * @param entity the entity holding the relations
   * @param pdoName the PDO class name of that entity
   * @param parentName the table alias of that entity within the statement, also the lambda parameter name
   * @param inset the leading whitespace
   * @param eagerJoins the joins of this level
   * @param topLevel true if the joins belong to the entity the class variables are generated for
   * @return one text per join, without the separating comma
   * @throws WurbelException if failed
   * @throws ModelException if the model does not allow the eager joins
   */
  private List<String> createEagerJoinTexts(Entity entity, String pdoName, String parentName, String inset,
                                            List<EagerJoin> eagerJoins, boolean topLevel)
         throws WurbelException, ModelException {

    List<String> texts = new ArrayList<>();
    String poImpl = deriveClassNameForEntity(entity);

    for (EagerJoin eagerJoin: eagerJoins) {
      Relation relation = eagerJoin.getRelation();
      if (relation.getMethodArgs().size() > 1) {
        throw new ModelException("eager relations with 'args=...' are currently not supported", relation);
      }
      Entity foreignEntity = relation.getForeignEntity();
      String joinClass = foreignEntity.getName();
      String sqlAlias = eagerJoin.getAlias();
      // the lambda parameter names must be unique within the lambda: below the top level the table
      // aliases of the statement are used, because a relation may well point back to its own entity
      String joinName = topLevel ? foreignEntity.getTopSuperEntity().getTableAlias() : sqlAlias;
      // an abstract PDO cannot be inferred, and neither can the left side of a cascaded join
      boolean explicitTypes = foreignEntity.isAbstract() || !topLevel;

      String columnName;
      String joinedColumnName;
      if (relation.getRelationType() == RelationType.LIST) {
        columnName = parentName + ".id";
        joinedColumnName = sqlAlias + "." + relation.getForeignAttribute().getColumnName();
      }
      else {
        columnName = parentName + "." + relation.getAttribute().getColumnName();
        joinedColumnName = sqlAlias + ".id";
      }

      /*
       * A join carrying cascaded joins becomes the receiver of addJoin(), and the type of a
       * receiver is inferred on its own: the diamond would leave the owning PDO type unresolved,
       * so it has to be spelled out. A leaf join keeps the diamond, whose type arguments the
       * enclosing List.of() provides.
       */
      String diamond = eagerJoin.getJoins().isEmpty() ? "<>" : ("<" + pdoName + ", " + joinClass + ">");

      StringBuilder buf = new StringBuilder();
      buf.append(inset).append("new Join").append(diamond).append("(JoinType.LEFT, \"").append(columnName).append("\", \"")
         .append(joinedColumnName).append("\", ").append(joinClass).append(".class, \"").append(sqlAlias).append("\",\n")
         .append(inset).append("  (");
      if (explicitTypes) {
        buf.append(pdoName).append(' ');
      }
      buf.append(parentName).append(", ");
      if (explicitTypes) {
        buf.append(joinClass).append(' ');
      }
      buf.append(joinName).append(") -> {\n");

      String body = inset + "    ";
      if (foreignEntity.isAbstract()) {
        if (relation.getRelationType() == RelationType.LIST) {
          buf.append(body).append(poImpl).append(" po = (").append(poImpl).append(") ")
             .append(parentName).append(".getPersistenceDelegate();\n")
             .append(body).append("po.").append(relation.getGetterName()).append("Blunt().add(")
             .append(joinName).append(");\n");
        }
        else {
          buf.append(body).append(parentName).append('.').append(relation.getSetterName())
             .append('(').append(joinName).append(");\n");
        }
      }
      else if (relation.getRelationType() == RelationType.LIST) {
        if (relation.isReversed()) {
          buf.append(body).append("((").append(poImpl).append(") ").append(parentName)
             .append(".getPersistenceDelegate()).").append(relation.getSetterName())
             .append(relation.isSerialized() ? "Blunt" : "").append('(').append(joinName).append(");\n");
        }
        else {
          buf.append(body).append("((").append(poImpl).append(") ").append(parentName)
             .append(".getPersistenceDelegate()).").append(relation.getGetterName())
             .append("Blunt().addBlunt(").append(joinName).append(");\n");
          if (relation.getLinkMethodName() != null) {
            buf.append(body).append("((").append(deriveClassNameForEntity(foreignEntity)).append(") ")
               .append(joinName).append(".getPersistenceDelegate()).")
               .append(createRelationUpdateReferenceCode(relation, parentName, true)).append(";\n");
          }
        }
      }
      else {
        buf.append(body).append(parentName).append('.').append(relation.getSetterName())
           .append(relation.isSerialized() ? "Blunt" : "").append('(').append(joinName).append(");\n");
      }

      buf.append(inset).append("  }\n").append(inset).append(')');

      for (String subText: createEagerJoinTexts(foreignEntity, joinClass, sqlAlias, inset + "    ",
                                                eagerJoin.getJoins(), false)) {
        buf.append('\n').append(inset).append("  .addJoin(\n")
           .append(subText).append('\n')
           .append(inset).append("  )");
      }

      texts.add(buf.toString());
    }

    return texts;
  }


  /**
   * Gets the eager joins as load join terms of the {@code PdoSelect} wurblets.
   * <p>
   * Used to add the eager relations to the generated relation finders so that they load the same
   * object graph as the generic selects do.
   * <p>
   * The tree differs from {@link #getEagerJoinTree()} in two ways, both of which predate the
   * cascade: the <em>inherited</em> eager relations are part of it, because a generated finder gets
   * no merging of the superclass joins the way
   * {@code PersistentObjectClassVariables#getEagerJoins()} does it at runtime; and the joins are
   * kept even when a subclass declares an eager relation.
   *
   * @return the paths, e.g. {@code "*invoice*lines"}, empty if no eager relations
   */
  public List<String> getEagerJoinPaths() {
    List<String> paths = new ArrayList<>();
    appendEagerJoinPaths(paths, "", createEagerJoinTree(getEntity().getRelationsIncludingInherited(), "e", 0));
    return paths;
  }

  /**
   * Collects the load join terms of an eager join tree.
   *
   * @param paths the collected paths
   * @param prefix the path leading to these joins
   * @param eagerJoins the joins of this level
   */
  private void appendEagerJoinPaths(List<String> paths, String prefix, List<EagerJoin> eagerJoins) {
    for (EagerJoin eagerJoin: eagerJoins) {
      String path = prefix + "*" + eagerJoin.getRelation().getName();
      if (eagerJoin.getJoins().isEmpty()) {
        paths.add(path);
      }
      else {
        // only the leaves are added: a load join term already implies all its parents
        appendEagerJoinPaths(paths, path, eagerJoin.getJoins());
      }
    }
  }

}

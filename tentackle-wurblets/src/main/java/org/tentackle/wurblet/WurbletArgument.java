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

package org.tentackle.wurblet;

import org.wurbelizer.wurbel.ArgScanner;
import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.SortType;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A wurblet argument.
 * <p>
 * There are 4 {@link WurbletArgumentType}s:
 * <ul>
 * <li>property condition: refers to an {@link Entity}'s {@link Attribute} that must meet a condition.</li>
 * <li>property update: defines an {@link Entity}'s {@link Attribute} that will be updated.</li>
 * <li>property sorting: define the sorting according to an {@link Entity}'s {@link Attribute}.</li>
 * <li>load join: describes a {@link Relation} that will be eagerly loaded.</li>
 * </ul>
 *
 * <p>
 * An argument is of the form:
 * <p>
 * <code>[<b>*</b>|<b>+</b>|<b>-</b>][<b>{@link Relation}</b>|<b>.{@link Entity}</b>[<b>.{@link Relation}</b>...]][<b>{@link Attribute}[#column]</b>[<b>[name]</b>][<b>?boolean</b>]][<b>:relop</b>[<b>:value</b>|<b>#value</b>]]</code>
 * <ul>
 * <li>argument type:
 *  <ul>
 *    <li>default (missing): attribute as a condition or for update</li>
 *    <li>+ or -: sorting criteria (+ for ascending, - for descending)</li>
 *    <li>*: load join</li>
 *  </ul>
 * </li>
 * <li>relation-path: list of relations.<br>
 * Optional for conditions, required for load joins. Illegal for updates.<br>
 * If the current entity is a root-entity, the path starts with a dot and is followed by the name of
 * a pointsToComponent, the pointsToComponent is replaced by its relation path, if there is only one path.<br>
 * For load joins, asterisks must be used between joins.
 * Each join path element can be filtered by appending a pipe-character followed by the filter expression,
 * which in turn consists of wurblet arguments. Filters with more than one argument must be enclosed in single- or
 * double quotes (see {@link ArgScanner}). Notice that for filtered load joins the returned PDOs are automatically made
 * immutable since they may be incomplete and should not be modified and/or written back to the database!
 * </li>
 * <li>attribute: a property of an entity.<br>
 * Required for conditions, updates, sorting. Illegal for joins.
 * </li>
 * <li>column: the optional column suffix or getter name for multi-column types. If missing and the multi-column type
 * defines a default column, this column is taken. A <code>*</code> will explicitly select all columns.</li>
 * <li>name: optional name of the method argument.<br>
 * Only allowed for conditions and updates.
 * </li>
 * <li>boolean: makes the condition optional.<br>
 * Only allowed for conditions.<br>
 * Adds a boolean method argument of that name to the generated finder. The condition becomes part of the
 * SQL statement and its parameters are set only if the boolean is true. Since the SQL text is no longer
 * fixed, such finders always create a one-shot prepared statement (see the {@code --oneshot} option).<br>
 * More than one argument may refer to the same boolean. Sub-expressions that end up empty are removed
 * from the generated SQL by {@link Backend#reduceSql(StringBuilder)}.
 * </li>
 * <li>relop-expression: optional relop.<br>
 * Only allowed for conditions.<br>
 * Defaults to <code>'='</code>.<br>
 * The special relop <code>':null'</code> will be translated to <code>" IS NULL"</code> and <code>':notnull'</code>
 * translated to <code>" IS NOT NULL"</code>.<br>
 * <code>':like'</code> will be translated to <code>" LIKE "</code>. <code>':notlike'</code> will be translated to <code>" NOT LIKE "</code>.<br>
 * <code>':in'</code>, <code>':notin'</code> or if the relop ends with <code>'any'</code> or <code>'all'</code>, it will be translated to
 * IN(?), NOT IN(?), ANY(?) or ALL(?) operators with its argument being an array instead of a simple value.
 * Notice that backends may not support array operators at all or only a subset. This is verified by the wurblets.<br>
 * The optional <code>':value'</code> will be set as a '?'-parameter of the generated prepared statement.
 * As an alternative, the value may be given as <code>'#value'</code> which will include the value
 * literally in the SQL string.
 * </li>
 * </ul>
 * <pre>
 * Examples:
 *          poolId                      -&gt; attribute=poolId, arg=poolId, relop="="
 *          poolId[from]:&gt;=             -&gt; attribute=poolId, arg=from, relop="&gt;="
 *          code:=:"Hurrz"              -&gt; attribute=code, relop="=", value="Hurz"
 *          code:=#"Hurrz"              -&gt; attribute=code, relop="=", value="Hurz", literally=true
 *
 *          code?withCode               -&gt; condition applied only if the boolean argument withCode is true
 *          code[from]?withCode:&gt;=      -&gt; same with a named method argument and an explicit relop
 *
 *          +poolId -kurzname           -&gt; ORDER BY pool_id ASC, kurzname DESC
 *
 *          invoice.lines.currencyId    -&gt; SQL WHERE condition for currency_id
 *          .InvoiceLine.currencyId     -&gt; equivalent to the above
 *
 *          *invoice*lines              -&gt; joins the invoice and its lines (chained join)
 *          *invoice*lines|no:&lt;:10      -&gt; equivalent to the above with filter "no &lt; 10"
 *          *invoice|date:>=*lines      -&gt; same with filter on invoice date "date >= ?"
 *
 *          blahId:=ANY                 -&gt; attribute blahId as an array, SQL relop is =ANY(?)
 *
 * </pre>
 *
 * @author harald
 */
public class WurbletArgument implements WurbletArgumentOperand {

  /** the entity the wurblet is applied to. */
  private final Entity entity;

  /** the position among all arguments. */
  private final int index;

  /** the original text. */
  private final String text;

  /** the argument type. */
  private final WurbletArgumentType argumentType;

  /** name of the method argument. */
  private String name;

  /** name of the boolean method argument if this is an optional condition, null if mandatory. */
  private String optionalName;

  /** relational operator. */
  private String relop;

  /** array operator (IN, ANY, ALL relops). */
  private String arrayOperator;

  /** predefined value replacing the '?' in prepared statements. */
  private String value;

  /** fixed value inserted literally into sql-statement. */
  private boolean literally;

  /** != null if this is a sorting property. */
  private SortType sortType;

  /** the model attribute. */
  private Attribute attribute;

  /** the attribute's effective datatype. */
  private DataType<?> dataType;

  /** the column index within the datatype. */
  private Integer columnIndex;

  /** the relation path to the attribute. */
  private List<WurbletRelation> wurbletRelations;

  /** embedding relation path if the attribute is embedded. */
  private final List<Relation> embeddingPath;

  /** != null if the first relation points to a component. */
  private Entity component;

  /** relations used in expressions possibly using a leading component. */
  private List<Relation> expressionRelations;

  /** set of relations used in EXISTS-clause. */
  private Set<Relation> existsRelations;

  /** set of components in EXISTS-clause. */
  private Set<Entity> existsComponents;

  /** all path arguments sharing the current EXISTS-clause. */
  private List<WurbletArgument> existsArguments;

  /** true if this is the last argument of the EXISTS-clause. */
  private boolean endOfExistsClause;



  /**
   * Constructs a wurblet argument.<p>
   *
   * @param entity the entity the wurblet is applied to
   * @param index the position among all arguments
   * @param text the wurblet arg
   * @param expressionFinished true if the expression is finished
   * @param argumentGroupingEnabled true if argument grouping is enabled
   *
   * @throws WurbelException if parsing failed
   */
  public WurbletArgument(Entity entity, int index, String text,
                         boolean expressionFinished,
                         boolean argumentGroupingEnabled)

         throws WurbelException {

    this.entity = entity;
    this.index = index;
    this.text = text;

    if (text == null || text.isEmpty()) {
      throw new WurbelException("text must be a non-empty string");
    }

    switch(text.charAt(0)) {
      case '*':
        argumentType = WurbletArgumentType.JOIN;
        text = text.substring(1);
        break;

      case '+':
        argumentType = WurbletArgumentType.SORT;
        sortType = SortType.ASC;
        text = text.substring(1);
        break;

      case '-':
        argumentType = WurbletArgumentType.SORT;
        sortType = SortType.DESC;
        text = text.substring(1);
        break;

      default:
        if (expressionFinished) {
          if (argumentGroupingEnabled) {
            argumentType = WurbletArgumentType.EXTRA;
          }
          else {
            // sorting arg without leading + -> convenience
            argumentType = WurbletArgumentType.SORT;
            sortType = SortType.ASC;
          }
        }
        else {
          argumentType = WurbletArgumentType.CONDITION;
          relop = Backend.SQL_EQUAL;    // preset default
        }
    }

    String str;

    int ndx = text.indexOf(':');
    if (ndx > 0 && argumentType != WurbletArgumentType.JOIN) {
      if (!argumentType.isRelopOptional()) {
        throw new WurbelException("relops not allowed for " + argumentType + "-argument '" + this.text + "'");
      }
      str = text.substring(0, ndx);
      relop = text.substring(ndx + 1);

      ndx = relop.indexOf(':');
      int ndx2 = relop.indexOf('#');
      if (ndx > 0 && (ndx2 == -1 || ndx2 > ndx)) {    // whatever comes first
        value = relop.substring(ndx + 1);
        relop = relop.substring(0, ndx);
      }
      else if (ndx2 > 0) {
        value = relop.substring(ndx2 + 1);
        relop = relop.substring(0, ndx2);
        literally = true;
      }

      String ucRelop = relop.toUpperCase(Locale.ROOT);
      if (ucRelop.endsWith("ANY")) {
        if (relop.length() > 3) {
          relop = relop.substring(0, relop.length() - 3);
        }
        else {
          relop = Backend.SQL_EQUAL;
        }
        arrayOperator = Backend.SQL_ARRAY_ANY;
      }
      else if (ucRelop.endsWith("ALL")) {
        if (relop.length() > 3) {
          relop = relop.substring(0, relop.length() - 3);
        }
        else {
          relop = Backend.SQL_EQUAL;
        }
        arrayOperator = Backend.SQL_ARRAY_ALL;
      }
      else if (ucRelop.endsWith("NOTIN")) {
        if (relop.length() > 5) {
          throw new WurbelException("operator not allowed for WHERE ... NOT IN '" + this.text + "'");
        }
        relop = "";
        arrayOperator = Backend.SQL_ARRAY_NOT_IN;
      }
      else if (ucRelop.endsWith("IN")) {
        if (relop.length() > 2) {
          throw new WurbelException("operator not allowed for WHERE ... IN '" + this.text + "'");
        }
        relop = "";
        arrayOperator = Backend.SQL_ARRAY_IN;
      }
      else {
        switch (ucRelop) {
          case "NULL":
            relop = Backend.SQL_ISNULL;
            value = "";
            literally = true;
            break;
          case "NOTNULL":
            relop = Backend.SQL_ISNOTNULL;
            value = "";
            literally = true;
            break;
          case "LIKE":
            relop = Backend.SQL_LIKE;
            break;
          case "NOTLIKE":
            relop = Backend.SQL_NOTLIKE;
            break;
        }
      }
    }
    else {
      str = text;
    }

    if (value != null && !value.isEmpty() && value.charAt(0) != '"' && value.indexOf('?') >= 0) {
      // catch a misplaced optional marker instead of silently making it part of the value
      throw new WurbelException("the optional marker '?' must precede the relop in argument '" + this.text + "'");
    }

    ndx = str.indexOf('?');
    if (ndx >= 0) {
      if (!argumentType.isConditionOptional()) {
        throw new WurbelException("optional conditions not allowed for " + argumentType + "-argument '" + this.text + "'");
      }
      optionalName = str.substring(ndx + 1);
      str = str.substring(0, ndx);
      if (!StringHelper.isValidJavaIdentifier(optionalName)) {
        throw new WurbelException("'" + optionalName + "' is not a valid name for the boolean method argument in '" +
                                  this.text + "'");
      }
    }

    ndx = str.indexOf('[');
    if (ndx > 0) {
      if (!argumentType.isNameOptional()) {
        throw new WurbelException("method argument name not allowed for " + argumentType + "-argument '" + this.text + "'");
      }
      int ndx2 = str.indexOf(']');
      if (ndx2 > ndx) {
        name = str.substring(ndx + 1, ndx2);
        str = str.substring(0, ndx);
      }
    }

    ndx = str.indexOf('.');
    if (ndx >= 0) {
      // check pathological case
      if (str.length() == 1) {
        throw new WurbelException("single dot not allowed as argument");
      }
      if (!argumentType.isPathAllowed()) {
        throw new WurbelException("relation path not allowed for " + argumentType + "-argument '" + this.text + "'");
      }
    }

    if (ndx == 0) {
      // points to a component?
      if (argumentType == WurbletArgumentType.JOIN) {
        throw new WurbelException("leading dot not allowed for " + argumentType + "-argument '" + this.text + "'");
      }
      if (!entity.isRootEntity()) {
        throw new WurbelException("component not allowed for non-root entity " + entity +
                                  " in argument '" + this.text + "'");
      }

      int ndx2 = str.indexOf('.', ndx + 1);    // length already checked above
      String componentName = str.substring(ndx + 1, ndx2);
      try {
        component = entity.getModel().getByEntityName(componentName);
      }
      catch (ModelException mx) {
        throw new WurbelException("could not load component " + componentName, mx);
      }
      if (component == null) {
        throw new WurbelException("no such component entity '" + componentName + "' in argument '" + this.text + "'");
      }

      boolean isComponent = false;
      for (List<Relation> path: component.getAllCompositePaths()) {
        if (path.getFirst().getEntity().equals(entity)) {
          isComponent = true;
          wurbletRelations = new ArrayList<>();    // empty list: path is valid and starts with component
          break;
        }
      }
      if (!isComponent) {
        throw new WurbelException(component + " is not a component of "+ entity);
      }
      str = str.substring(ndx2 + 1);
    }

    // remaining relations of the path
    Entity currentEntity = component != null ? component : this.entity;

    embeddingPath = new ArrayList<>();
    Relation predecessorRelation = null;
    while ((ndx = nextSeparatorIndex(str)) > 0) {
      String relationName = str.substring(0, ndx);
      str = str.substring(ndx + 1);
      Relation relation = addRelation(currentEntity, relationName, predecessorRelation);
      currentEntity = relation.getForeignEntity();
      if (relation.isEmbedding() || !embeddingPath.isEmpty()) {
        embeddingPath.add(relation);
      }
      predecessorRelation = relation;
    }

    if (argumentType == WurbletArgumentType.JOIN) {
      // rest is relation
      if (!str.isEmpty()) {
        addRelation(currentEntity, str, predecessorRelation);
      }
    }
    else {
      // the rest is the attribute
      if (str.isEmpty()) {
        if (argumentType.isAttributeRequired()) {
          throw new WurbelException("missing attribute in argument '" + this.text + "'");
        }
      }
      else {
        if (!argumentType.isAttributeAllowed()) {
          throw new WurbelException("attribute not allowed for " + argumentType + "-argument '" + this.text + "'");
        }
        String attrName = str;
        String colName = null;
        int ndx3 = str.indexOf('#');
        if (ndx3 > 0) {
          attrName = str.substring(0, ndx3);
          colName = str.substring(ndx3 + 1);
        }

        attribute = currentEntity.getAttributeByJavaName(attrName, true);
        if (attribute == null) {
          throw new WurbelException("no such attribute '" + attrName + "' defined for entity " + currentEntity +
                                    " in argument '" + this.text + "'");
        }

        if (!embeddingPath.isEmpty()) {
          // apply embedding path to attribute
          StringBuilder pathName = new StringBuilder();
          StringBuilder columnName = new StringBuilder();
          boolean lastRelationisEmbedding = true;
          for (Relation relation : embeddingPath) {
            pathName.append(relation.getName()).append('.');
            columnName.append(relation.getColumnPrefix());
            if (!relation.isEmbedding()) {
              lastRelationisEmbedding = false;  // non-composite 1:1 link to another PDO
            }
          }
          pathName.append(attribute.getName());
          if (!lastRelationisEmbedding) {
            columnName.setLength(0);
          }
          columnName.append(attribute.getColumnName());
          attribute = attribute.createEmbedded(embeddingPath.getFirst().getEntity(), pathName.toString(), columnName.toString());
        }

        try {
          dataType = attribute.getEffectiveDataType();
          if (dataType.isColumnCountBackendSpecific()) {
            throw new WurbelException("backend-specific datatype " + dataType + " cannot be used as a wurblet argument");
          }
        }
        catch (ModelException mx) {
          throw new WurbelException("cannot determine datatype of " + attribute, mx);
        }

        if (!"*".equals(colName)) {
          if (colName != null) {
            // find the column index within the datatype
            for (int i = 0; i < dataType.getColumnCount(null); i++) {
              String columnSuffix = dataType.getColumnSuffix(null, i).orElse("");
              String columnAlias = dataType.getColumnAlias(i);
              if (colName.equalsIgnoreCase(columnSuffix) || colName.equals(columnAlias)) {
                columnIndex = i;
                break;
              }
              if (columnSuffix.startsWith("_")) {
                columnSuffix = columnSuffix.substring(1);
                if (colName.equalsIgnoreCase(columnSuffix)) {
                  columnIndex = i;
                  break;
                }
              }
            }
            if (columnIndex == null) {
              throw new WurbelException("datatype " + dataType + " does not map to column suffix '" + colName + "'");
            }
          }
          else if (dataType.getColumnCount(null) > 1 && sortType == null) {
            // no column specified for a multi-column type:
            if (!Backend.SQL_EQUAL.equals(relop) || value != null || literally) {
              int[] sortableColumns = dataType.getSortableColumns();
              if (sortableColumns != null && sortableColumns.length == 1) {
                // if it's sortable by exactly one column, this may work
                columnIndex = sortableColumns[0];
              }
              else {
                throw new WurbelException("datatype " + dataType + " requires a column suffix for relop '" + relop + "'");
              }
            }
            // else: take all columns for "="
          }
        }

        if (isArray()) {
          if (literally) {
            throw new WurbelException("arrays cannot be passed as a literal");
          }
          if (value != null) {
            throw new WurbelException("arrays cannot be passed as a simple value");
          }
          if (dataType.getColumnCount(null) > 1 && columnIndex == null) {
            throw new WurbelException(dataType +
                                      " is a multi column datatype: please specify the column to be used for the array operator '" +
                                      arrayOperator + "'");
          }
        }
        if (literally) {
          if (!dataType.isLiteralSupported(columnIndex)) {
            throw new WurbelException("datatype " + dataType + " does not support literals");
          }
          value = dataType.toLiteral(value, columnIndex);
        }
        else if (value != null && !value.isEmpty() && value.charAt(0) == '"') {
          value = dataType.valueOfLiteralToCode(value, columnIndex);
        }
      }
    }
  }


  /**
   * Gets the entity the wurblet is applied to.
   *
   * @return the entity
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the index within the wurblet anchor.
   *
   * @return the index, lower comes first
   */
  public int getIndex() {
    return index;
  }

  /**
   * Gets the original text.
   *
   * @return the text
   */
  public String getText() {
    return text;
  }

  /**
   * Gets the argument type.
   *
   * @return the type, never null
   */
  public WurbletArgumentType getArgumentType() {
    return argumentType;
  }

  /**
   * Gets the model attribute.
   *
   * @return the attribute
   */
  public Attribute getAttribute() {
    return attribute;
  }

  /**
   * Gets the effective datatype of the attribute.
   *
   * @return the datatype
   */
  public DataType<?> getDataType() {
    return dataType;
  }

  /**
   * Gets the datatype column index.
   *
   * @return the index if column specified, else null
   */
  public Integer getColumnIndex() {
    return columnIndex;
  }

  /**
   * Gets the relation path.
   * <p>
   * If the path starts with a component, the relations start there.
   *
   * @return the relations, null if refers directly to the wurblet's entity
   */
  public List<WurbletRelation> getWurbletRelations() {
    return wurbletRelations;
  }

  /**
   * Gets the embedding relation path to the attribute.<br>
   * Starts at the embedding entity.
   *
   * @return the relation path, if the attribute is embedded, empty list if not
   */
  public List<Relation> getEmbeddingPath() {
    return embeddingPath;
  }

  /**
   * Gets the embedding prefix count.<br>
   * The embedding prefixes are only necessary for chained embedding relations, i.e.,
   * relations from within an embedded entity which is embedded in at least one other embedded entity.
   * This is used in wurblet joins (and fetch joins) only.
   *
   * @return the embedding count, 0 if no prefixing necessary.
   */
  public int getEmbeddingPrefixCount() {
    int count = 0;
    for (Relation relation : embeddingPath) {
      if (relation.isEmbedding()) {   // skip the last non-embedding if this is a 1:1 non-composite
        count++;
      }
    }
    return count > 0 ? count - 1 : 0;   // except the last (== current) embedding relation!
  }

  /**
   * Gets the column prefix for nested embedded relations.
   *
   * @return the column prefix, empty string if none
   * @see #getEmbeddingColumnPrefix()
   */
  public String getEmbeddingColumnPrefix() {
    StringBuilder buf = new StringBuilder();
    int count = getEmbeddingPrefixCount();
    for (int i=0; i < count; i++) {
      buf.append(embeddingPath.get(i).getColumnPrefix());
    }
    return buf.toString();
  }

  /**
   * Gets the getter prefix for nested embedded relations.
   *
   * @return the getter prefix, empty string if none
   * @see #getEmbeddingColumnPrefix()
   */
  public String getEmbeddingGetterPrefix() {
    StringBuilder buf = new StringBuilder();
    int count = getEmbeddingPrefixCount();
    for (int i=0; i < count; i++) {
      buf.append("get").append(StringHelper.firstToUpper(embeddingPath.get(i).getName())).append("().");
    }
    return buf.toString();
  }

  /**
   * Sets the relations used within the current SQL EXISTS clause.
   *
   * @return the relations, null if the argument has no relations or already covered by EXISTS-clause
   */
  public Set<Relation> getExistsRelations() {
    return existsRelations;
  }

  /**
   * Sets the relations used within the current SQL EXISTS clause.
   *
   * @param existsRelations the effective relations
   */
  public void setExistsRelations(Set<Relation> existsRelations) {
    this.existsRelations = existsRelations;
  }

  /**
   * Gets the components for the current SQL EXISTS clause.
   *
   * @return the components, null if argument has no relations or already covered by EXISTS-clause
   */
  public Set<Entity> getExistsComponents() {
    return existsComponents;
  }

  /**
   * Sets the components for the current SQL EXISTS clause.
   *
   * @param existsComponents the components
   */
  public void setExistsComponents(Set<Entity> existsComponents) {
    this.existsComponents = existsComponents;
  }

  /**
   * Gets all path arguments sharing the current SQL EXISTS clause.<br>
   * The list is the same instance for all arguments of the clause.
   *
   * @return the arguments, null if this argument is not part of an EXISTS clause
   */
  public List<WurbletArgument> getExistsArguments() {
    return existsArguments;
  }

  /**
   * Sets all path arguments sharing the current SQL EXISTS clause.
   *
   * @param existsArguments the arguments
   */
  public void setExistsArguments(List<WurbletArgument> existsArguments) {
    this.existsArguments = existsArguments;
  }

  /**
   * Returns whether this is the last argument of the current SQL EXISTS clause.
   *
   * @return true if finish clause
   */
  public boolean isEndOfExistsClause() {
    return endOfExistsClause;
  }

  /**
   * Sets whether this is the last argument of the current SQL EXISTS clause.
   *
   * @param endOfExistsClause true if finish clause
   */
  public void setEndOfExistsClause(boolean endOfExistsClause) {
    this.endOfExistsClause = endOfExistsClause;
  }


  /**
   * Gets the compacted relation path starting with the last component relation in the chain, if any.
   *
   * @return the relation path to be used in expressions, null if refers directly to the Wurblet's entity
   */
  public List<Relation> getExpressionRelations() {
    if (wurbletRelations != null && expressionRelations == null) {
      expressionRelations = new ArrayList<>();
      if (entity.isRootEntity() && component == null) { // check if path to component
        for (WurbletRelation wurbletRelation: wurbletRelations) {
          Relation relation = wurbletRelation.getRelation();
          if (expressionRelations.isEmpty() &&
              relation.isComposite() && relation.getMethodArgs().size() == 1) {
            // in chain of component relations without extra args from the beginning
            component = relation.getForeignEntity(); // remember last component in path
          }
          else {
            // add remaining relations
            expressionRelations.add(relation);
          }
        }
      }
      else {
        // no root entity or component already given -> cannot compact relation path
        expressionRelations = wurbletRelations.stream().map(WurbletRelation::getRelation).toList();
      }
    }
    return expressionRelations;
  }


  /**
   * Gets the component that prepends the relation path.
   *
   * @return the component, null if the path does not start with a component
   */
  public Entity getComponent() {
    getExpressionRelations();
    return component;
  }


  /**
   * Returns whether the argument points to another entity.
   *
   * @return true if argument describes a path to another entity
   */
  public boolean isPath() {
    return wurbletRelations != null;
  }

  /**
   * Gets the sorting type if this a sorting criteria argument.
   *
   * @return ASC or DESC, null if not a sorting argument
   */
  public SortType getSortType() {
    return sortType;
  }

  /**
   * Gets the optional name of the method argument.
   *
   * @return the argument name, null if default attribute name
   */
  public String getName() {
    return name;
  }


  /**
   * Returns whether this is a method argument.
   *
   * @return true if attribute set and value is null
   */
  public boolean isMethodArgument() {
    return attribute != null && value == null;
  }


  /**
   * Returns whether this condition is optional.<br>
   * Optional conditions become part of the generated SQL only if the boolean
   * method argument named {@link #getOptionalName()} is true.
   *
   * @return true if optional
   */
  public boolean isOptional() {
    return optionalName != null;
  }

  /**
   * Gets the name of the boolean method argument determining whether this condition
   * is part of the query.<br>
   * More than one argument may refer to the same boolean.
   *
   * @return the name, null if this condition is mandatory
   */
  public String getOptionalName() {
    return optionalName;
  }

  /**
   * Gets the effective name of the method argument.<br>
   * This is ether the optionally defined {@code [name]} or the name of the {@link Attribute}.
   *
   * @return the method argument name, never null
   * @throws WurbelException if the argument type provides no argument name
   */
  public String getMethodArgumentName() throws WurbelException {
    if (attribute == null) {
      throw new WurbelException("argument " + this + " provides no name");
    }
    return name != null ? name : attribute.getName();
  }


  /**
   * Gets the value to be used in JDBC prepared statements.
   *
   * @return the JDBC value
   * @throws WurbelException if failed
   */
  public String getJdbcValue() throws WurbelException, ModelException {
    String str;
    if (value != null) {
      str = value;
      if (attribute != null) {
        str = attribute.toMethodArgument(value);
      }
    }
    else  {
      str = getMethodArgumentName();
    }
    return str;
  }


  /**
   * Gets the relational operator.
   *
   * @return the relop
   */
  public String getRelop() {
    return relop;
  }


  /**
   * Gets the predefined value.
   *
   * @return the value
   */
  public String getValue() {
    return value;
  }


  /**
   * fixed value (directly put into sql-statement).
   * @return the fixed
   */
  public boolean isValueLiterally() {
    return literally;
  }


  /**
   * Returns whether argument must be treated as an array.
   *
   * @return true if IN, ANY, or ALL operators used
   */
  public boolean isArray() {
    return arrayOperator != null;
  }

  /**
   * Returns the array operator.
   *
   * @return IN, ANY or ALL, null if no array operator used
   */
  public String getArrayOperator() {
    return arrayOperator;
  }


  @Override
  public String toString() {
    return text;
  }


  /**
   * Finds the next relation path separator index.<br>
   * For relations the separator is a dot.<br>
   * For load joins the separator is an asterisk.
   *
   * @param name the argument
   * @return the next index, -1 if none
   */
  private int nextSeparatorIndex(String name) {
    return name.indexOf(argumentType == WurbletArgumentType.JOIN ? '*' : '.');
  }

  /**
   * Adds a relation to the relation path.
   *
   * @param currentEntity the entity the relation belongs to
   * @param name the name of the relation
   * @param predecessorRelation the relation leading to this relation, null if none
   * @return the relation, never null
   * @throws WurbelException if no such relation
   */
  private Relation addRelation(Entity currentEntity, String name, Relation predecessorRelation) throws WurbelException {
    int ndx = name.indexOf('|');
    String filter = null;
    if (ndx > 0) {
      filter = name.substring(ndx + 1);
      name = name.substring(0, ndx);
    }
    Relation relation = currentEntity.getRelation(name, true);
    if (relation == null) {
      throw new WurbelException(
              "no such relation '" + name + "' defined for entity " + currentEntity + " in argument '" + this.text + "'");
    }
    if (relation.isEmbedding()) {
      Entity embeddingEntity = relation.getEmbeddingEntity();   // from parent relation
      String pathName;
      String columnPrefix;
      if (embeddingEntity == null || predecessorRelation == null) {
        embeddingEntity = currentEntity;    // start of embedding path
        pathName = relation.getName();
        columnPrefix = relation.getColumnPrefix();
      }
      else {
        pathName = predecessorRelation.getPathName() + relation.getName();
        columnPrefix = predecessorRelation.getColumnPrefixPath() + relation.getColumnPrefix();
      }
      relation = relation.createEmbedded(embeddingEntity, pathName, columnPrefix);
    }
    else {
      if (relation.isEmbedded() && predecessorRelation != null && predecessorRelation.isEmbedding()) {
        relation = relation.createEmbedded(predecessorRelation.getEmbeddingEntity(),
                                           predecessorRelation.getPathName(),
                                           predecessorRelation.getColumnPrefixPath());
      }
      if (wurbletRelations == null) {
        wurbletRelations = new ArrayList<>();
      }
      wurbletRelations.add(new WurbletRelation(relation, filter));
    }
    return relation;
  }

}

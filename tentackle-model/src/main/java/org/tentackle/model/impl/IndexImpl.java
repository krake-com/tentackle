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

package org.tentackle.model.impl;

import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.CodeFactory;
import org.tentackle.model.Deferrability;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.IndexAttribute;
import org.tentackle.model.Model;
import org.tentackle.model.ModelElement;
import org.tentackle.model.ModelException;
import org.tentackle.model.NameVerifier;
import org.tentackle.model.Relation;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parse.ConfigurationLine;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlNameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Index descriptor.
 * <p>
 * An index line is of the form:
 * <pre>
 *   [optional] [unique] [deferred|deferrable|immediate] index &lt;name&gt; := part1[ part2 ...] [| condition | WHERE condition]
 * </pre>
 *
 * where the keywords before {@code index} may appear in any order and:
 * <ul>
 *   <li>{@code optional}: create if database backend supports it. This applies only to functions and conditions
 *   some databases don't support (usually in-memory for tests). In those cases, it may be acceptable to create the index only
 *   for the production database. Without {@code optional} the build would fail.</li>
 *   <li>{@code unique}: create a unique constraint</li>
 *   <li>{@code deferred}, {@code deferrable}: create a deferrable unique constraint, initially deferred or immediate.
 *   Only for unique indexes without condition and function. Ignored by backends not supporting deferred constraints.</li>
 *   <li>{@code immediate}: not deferrable, even if the model defaults say otherwise</li>
 *   <li>{@code name}: the suffix of the index name. The effective index name is the table name (without schema)
 *   followed by an underscore and then followed by the name. A schema prefix of the name is ignored.</li>
 * </ul>
 *
 * and {@code part} is:
 * <pre>
 *   [+|-]&lt;column&gt; | [+|-]&lt;function&gt;(&lt;column&gt;) [asc|desc]
 * </pre>
 *
 * where:
 * <ul>
 *   <li>{@code column}: the database column name or the Java name of the attribute. Columns of embedded entities
 *   are defined by prepending the corresponding dotted path to the column. For multi-column types the
 *   column consists of the attribute's column name, followed by a hash, followed by the column name within the datatype.</li>
 *   <li>{@code function}: a function name, converted to uppercase. The column within the function is converted
 *   to lowercase.</li>
 *   <li>{@code ASC} or {@code +} for ascending order (default)</li>
 *   <li>{@code DESC} or {@code -} for descending order</li>
 * </ul>
 *
 * and {@code condition} is an expression to create a partial (a.k.a. filtered or conditional) index.
 * <p>
 * Examples:
 * <pre>
 *   unique index udk := code
 *   optional unique index subname := name | type=0
 *   optional unique index subname := name where type=0
 *   index locid := location_id desc name
 *   index ucmt := -upper(comment)
 *   index xpos := position#x
 * </pre>
 *
 * @author harald
 */
public class IndexImpl implements Index {

  /** The WHERE keyword introducing the filter condition. */
  private static final Pattern WHERE_PATTERN = Pattern.compile("\\bwhere\\b", Pattern.CASE_INSENSITIVE);

  private final Entity entity;
  private final EntityFactoryImpl factory;
  private final SourceInfo sourceInfo;
  private final List<IndexAttribute> attributes;
  private ConfigurationLine sourceLine;
  private int ordinal;
  private String name;
  private String comment;
  private String longComment;
  private boolean unique;
  private boolean optional;
  private String filterCondition;
  private Deferrability deferrability = Deferrability.NOT_DEFERRABLE;
  private boolean deferrabilityExplicit;
  private boolean parsed;


  /**
   * Creates an index.
   *
   * @param factory the factory to create indexes
   * @param entity the entity this index belongs to
   * @param sourceInfo the source info
   */
  public IndexImpl(EntityFactoryImpl factory, Entity entity, SourceInfo sourceInfo) {
    this.entity = entity;
    this.sourceInfo = sourceInfo;
    this.factory = factory;
    attributes = new ArrayList<>();
  }

  @Override
  public Model getModel() {
    return factory.getModel();
  }

  @Override
  public Entity getEntity() {
    return entity;
  }

  @Override
  public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  @Override
  public ModelElement getParent() {
    return getEntity();
  }

  @Override
  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  /**
   * Parses the stored index configuration line.
   *
   * @param entity the entity providing the attributes
   * @throws ModelException if parsing the model failed
   */
  public void parse(Entity entity) throws ModelException {

    parsed = true;

    try {

      // parse the index
      StringTokenizer stok = new StringTokenizer(sourceLine.getKey());
      boolean indexFound = false;
      boolean uniqueFound = false;
      while (stok.hasMoreTokens()) {
        String token = stok.nextToken();
        switch (token.toLowerCase(Locale.ROOT)) {

          case "index":
            setUnique(uniqueFound);
            indexFound = true;
            uniqueFound = false;
            break;

          case "unique":
            if (indexFound) {
              throw sourceLine.createModelException("keyword 'unique' not allowed after 'index'");
            }
            uniqueFound = true;
            break;

          case "optional":
            if (indexFound) {
              throw sourceLine.createModelException("keyword 'optional' not allowed after 'index'");
            }
            setOptional(true);
            break;

          case "deferred":
            if (indexFound) {
              throw sourceLine.createModelException("keyword 'deferred' not allowed after 'index'");
            }
            setDeferrability(Deferrability.DEFERRABLE_DEFERRED);
            break;

          case "deferrable":
            if (indexFound) {
              throw sourceLine.createModelException("keyword 'deferrable' not allowed after 'index'");
            }
            setDeferrability(Deferrability.DEFERRABLE_IMMEDIATE);
            break;

          case "immediate":
            if (indexFound) {
              throw sourceLine.createModelException("keyword 'immediate' not allowed after 'index'");
            }
            setDeferrability(Deferrability.NOT_DEFERRABLE);
            break;

          default:
            if (!indexFound) {
              throw sourceLine.createModelException("unknown configuration keyword: " + token);
            }
            int ndx = token.indexOf('.');
            if (ndx > 0) {
              // cut leading schema name
              token = token.substring(ndx + 1);
            }
            setName(token);
        }
      }
      if (!indexFound) {
        throw sourceLine.createModelException("illegal configuration: " + sourceLine.getKey());
      }

      // parse the attributes
      if (entity.getAttributes().isEmpty()) {
        throw sourceLine.createModelException("index configuration line not allowed before attribute section");
      }

      String text = sourceLine.getValue();
      int ndx = text.indexOf('|');
      if (ndx > 0) {
        setFilterCondition(text.substring(ndx + 1));
        text = text.substring(0, ndx);
      }
      else {
        // WHERE as a separate word only, not within a column name such as somewhere_id
        Matcher matcher = WHERE_PATTERN.matcher(text);
        if (matcher.find() && matcher.start() > 0) {
          ndx = matcher.start();
          setFilterCondition(text.substring(matcher.end()));
          text = text.substring(0, ndx);
        }
      }

      stok = new StringTokenizer(text, " \t,");
      IndexAttributeImpl indexAttribute = null;

      while (stok.hasMoreTokens()) {
        String token = stok.nextToken();
        if (token.equalsIgnoreCase("desc")) {
          if (indexAttribute == null) {
            throw sourceLine.createModelException("keyword 'desc' must follow column name");
          }
          indexAttribute.setDescending(true);
        }
        else if (token.equalsIgnoreCase("asc")) {
          if (indexAttribute == null) {
            throw sourceLine.createModelException("keyword 'asc' must follow column name");
          }
          indexAttribute.setDescending(false);
        }
        else {
          if (indexAttribute != null) {
            getAttributes().add(indexAttribute);
            indexAttribute = null;
          }
          // short form for desc or asc is + or - prepended to the column name
          Boolean descending = null;
          if (token.startsWith("+")) {
            descending = Boolean.FALSE;
            token = token.substring(1);
          }
          if (token.startsWith("-")) {
            descending = Boolean.TRUE;
            token = token.substring(1);
          }

          // check if functional index
          String function = null;
          String column = token;
          int ndx1 = token.indexOf('(');
          if (ndx1 >= 0) {
            int ndx2 = token.indexOf(')', ndx1);
            if (ndx2 >= 0) {
              function = token.substring(0, ndx1).trim().toUpperCase(Locale.ROOT);
              column = token.substring(ndx1 + 1, ndx2).trim().toLowerCase(Locale.ROOT);
            }
          }

          // if the column contains dots, it must be a path to an embedded entity's attribute
          Entity attrEntity = entity;
          Relation embeddingRelation = null;
          int dotNdx;
          while ((dotNdx = column.indexOf('.')) >= 0 && dotNdx < column.length() - 1) {
            String relationName = column.substring(0, dotNdx);
            column = column.substring(dotNdx + 1);
            embeddingRelation = attrEntity.getRelation(relationName, true);
            if (embeddingRelation == null) {
              throw sourceLine.createModelException("undefined embedded relation: " + relationName);
            }
            if (!embeddingRelation.isEmbedding()) {
              throw sourceLine.createModelException("not an embedding relation: " + relationName);
            }
            embeddingRelation = embeddingRelation.createEmbedded(attrEntity, relationName, embeddingRelation.getColumnPrefix());
            attrEntity = embeddingRelation.getForeignEntity();
          }

          // in case the column belongs to a multi-column datatype
          String attrName = column;
          int sepNdx = column.indexOf('#');
          if (sepNdx >= 0 && sepNdx < column.length() - 1) {
            attrName = column.substring(0, sepNdx);
            column = column.substring(sepNdx + 1);
          }

          // find the attribute by column or Java name
          for (Attribute attribute : attrEntity.getAttributes()) {
            if (attribute.getColumnName().equals(attrName) ||
                attribute.getName().equals(attrName)) {
              // found
              if (embeddingRelation != null) {
                attribute = attribute.createEmbedded(attrEntity, embeddingRelation.getPathName() + attribute.getName(),
                embeddingRelation.getColumnPrefixPath() + attribute.getColumnName());
              }
              indexAttribute = factory.createIndexAttribute(this);
              DataType<?> dataType = attribute.getEffectiveDataType();
              String columnName = attribute.getColumnName();
              int columnCount = dataType.getColumnCount(null);
              if (columnCount > 1) {
                boolean colFound = false;
                for (int i=0; i < columnCount; i++) {
                  String suffix = dataType.getColumnSuffix(null, i).orElse("");
                  if (suffix.equals(column) || dataType.getColumnAlias(i).equals(column)) {
                    columnName = attribute.getColumnName() + suffix;
                    colFound = true;
                    break;
                  }
                }
                if (!colFound) {
                  throw sourceLine.createModelException("no such column '" + column + "' in datatype " + dataType);
                }
              }
              indexAttribute.setAttributeAndColumnName(attribute, columnName);
              indexAttribute.setFunctionName(function);
              if (descending != null) {
                indexAttribute.setDescending(descending);
              }
              break;
            }
          }

          if (indexAttribute == null) {
            // not found
            throw sourceLine.createModelException("undefined column name: " + token);
          }
        }
      }
      if (indexAttribute != null) {
        getAttributes().add(indexAttribute);
      }
    }
    catch (RuntimeException rx) {
      throw sourceLine.createModelException("syntax error", rx);
    }
  }



  @Override
  public String getName() {
    return name;
  }

  @Override
  public String createDatabaseIndexName(Entity entity) {
    return entity.getTableNameWithoutSchema() + "_" + name;
  }

  @Override
  public String getComment() {
    return comment;
  }

  @Override
  public String getLongComment() {
    return longComment;
  }

  @Override
  public boolean isUnique() {
    return unique;
  }

  @Override
  public boolean isOptional() {
    return optional;
  }

  @Override
  public String getFilterCondition() {
    return filterCondition;
  }

  @Override
  public Deferrability getDeferrability() {
    return deferrability;
  }

  @Override
  public List<IndexAttribute> getAttributes() {
    return attributes;
  }

  /**
   * Returns whether at least one of the index columns is function-based.
   *
   * @return true if function-based index
   */
  public boolean isFunctionBased() {
    for (IndexAttribute attribute : getAttributes()) {
      if (attribute.getFunctionName() != null) {
        return true;
      }
    }
    return false;
  }


  public void setName(String name) {
    this.name = name;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public void setLongComment(String longComment) {
    this.longComment = longComment;
  }

  public void setUnique(boolean unique) {
    this.unique = unique;
  }

  public void setOptional(boolean optional) {
    this.optional = optional;
  }

  /**
   * Sets the filter condition of a partial index.
   *
   * @param filterCondition the condition, null or blank if none (trimmed)
   */
  public void setFilterCondition(String filterCondition) {
    if (StringHelper.isAllWhitespace(filterCondition)) {
      this.filterCondition = null;
    }
    else  {
      this.filterCondition = filterCondition.trim();
    }
  }

  /**
   * Sets the deferrability explicitly.<br>
   * Explicit settings are not overridden by the model defaults.
   *
   * @param deferrability the deferrability
   * @see #applyDefaultDeferrability(Deferrability)
   */
  public void setDeferrability(Deferrability deferrability) {
    this.deferrability = deferrability;
    this.deferrabilityExplicit = true;
  }

  /**
   * Returns whether the deferrability was set explicitly on the index line, as opposed to
   * being inherited from the model defaults.
   *
   * @return true if set explicitly via the {@code deferred}/{@code deferrable}/{@code immediate} keyword
   */
  public boolean isDeferrabilityExplicit() {
    return deferrabilityExplicit;
  }

  /**
   * Applies the model default deferrability, unless the index line already sets its own.
   *
   * @param deferrability the deferrability propagated from the model defaults
   */
  public void applyDefaultDeferrability(Deferrability deferrability) {
    if (!deferrabilityExplicit) {
      this.deferrability = deferrability;
    }
  }

  /**
   * Sets whether the index is already parsed.<br>
   * Only necessary for model converters if the properties are set explicitly to prevent parsing.
   *
   * @param parsed true if already parsed
   */
  public void setParsed(boolean parsed) {
    this.parsed = parsed;
  }

  @Override
  public String toString() {
    return getName();
  }

  /**
   * Returns whether the index is already parsed.<br>
   * Index parsing is delayed because some attributes may be added later when
   * the whole model is known.
   *
   * @return true if parsed
   */
  public boolean isParsed() {
    return parsed;
  }


  @Override
  public void validate() throws ModelException {
    if (getEntity().getOptions().isProvided()) {
      throw createModelException("[PROVIDED] entities cannot have index definitions");
    }
    if (getEntity().isEmbedded()) {
      throw createModelException(getEntity() + " is embedded -> consider index definition for the *embedding* entity");
    }
    if (StringHelper.isAllWhitespace(getName())) {
      throw createModelException("missing index name");
    }
    String diag = NameVerifier.getInstance().verifyIndexName(this);
    if (diag != null) {
      throw createModelException(diag);
    }

    if (deferrability != Deferrability.NOT_DEFERRABLE) {
      if (!isUnique()) {
        throw createModelException("'" + (deferrability == Deferrability.DEFERRABLE_DEFERRED ? "deferred" : "deferrable") +
                                    "' is only allowed on unique indexes");
      }
      if (getFilterCondition() != null) {
        throw createModelException("a filtered/partial unique index cannot be deferrable");
      }
      if (isFunctionBased()) {
        throw createModelException("a function-based unique index cannot be deferrable");
      }
    }

    for (Backend backend: factory.getBackends()) {
      try {
        // check the effective name: a leading underscore in the index name is ok, because the table name is prepended
        backend.assertValidName(SqlNameType.INDEX_NAME, createDatabaseIndexName(entity));
        if (getFilterCondition() != null && !backend.isFilteredIndexSupported() && !isOptional()) {
          throw createModelException("filtered/partial index not supported by " + backend);
        }
        for (IndexAttribute attribute : getAttributes()) {
          if (attribute.getFunctionName() != null && !backend.isFunctionBasedIndexSupported() && !isOptional()) {
            throw createModelException("function-based index not supported by " + backend);
          }
        }
      }
      catch (RuntimeException rex) {
        throw new ModelException(rex.getMessage(), this, rex);
      }
    }

    if (getAttributes().isEmpty()) {
      throw createModelException("index " + this + " does not contain any attributes");
    }

    for (IndexAttribute attribute : getAttributes()) {
      if (!attribute.getAttribute().getEffectiveDataType().isComparable()) {
        throw createModelException(
            "attribute " + attribute.getAttribute() + " has a non-comparable data type and cannot be part of an index");
      }
    }
  }

  /**
   * Gets the source line.
   *
   * @return the line
   */
  public ConfigurationLine getSourceLine() {
    return sourceLine;
  }

  /**
   * Sets the source line.
   *
   * @param sourceLine the line
   */
  public void setSourceLine(ConfigurationLine sourceLine) {
    this.sourceLine = sourceLine;
  }

  /**
   * Creates a model exception.
   * <p>
   * Refers to the source line if set, otherwise to the entity.
   *
   * @param message the message
   * @return the exception
   */
  public ModelException createModelException(String message) {
    ModelException ex;
    if (sourceLine != null) {
      ex = sourceLine.createModelException(message);
    }
    else  {
      ex = new ModelException(message, entity);
    }
    return ex;
  }

  @Override
  public String sqlCreateIndex(Backend backend, Entity entity) {
    return CodeFactory.getInstance().createSqlIndex(backend, entity, this);
  }


  /**
   * Checks whether the other index is logically equal to this index.<br>
   * Compares the filter condition and the order, direction and function of the columns only.
   * The name, uniqueness and deferrability are not compared.
   *
   * @param otherIndex the other index
   * @return true if equal
   */
  public boolean isLogicallyEqualTo(Index otherIndex) {
    boolean logicallyEqual = true;
    if (Objects.equals(getFilterCondition(), otherIndex.getFilterCondition())) {
      if (getAttributes().size() == otherIndex.getAttributes().size()) {
        for (int position = 0; position < getAttributes().size(); position++) {
          IndexAttribute indexAttribute = getAttributes().get(position);
          IndexAttribute otherAttribute = otherIndex.getAttributes().get(position);
          if (indexAttribute.isDescending() != otherAttribute.isDescending() ||
              !Objects.equals(indexAttribute.getColumnName(), otherAttribute.getColumnName()) ||
              !Objects.equals(indexAttribute.getFunctionName(), otherAttribute.getFunctionName())) {
            logicallyEqual = false;
            break;
          }
        }
      }
      else {
        logicallyEqual = false;
      }
    }
    else {
      logicallyEqual = false;
    }
    return logicallyEqual;
  }

}

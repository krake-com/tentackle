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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.tentackle.common.Constants;
import org.tentackle.misc.Convertible;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.session.ModificationTracker;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;
import org.tentackle.sql.DataType;
import org.tentackle.sql.JoinType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Factory generating SQL code to select PDOs.
 */
public class SqlCodeFactory {

  /** Section holding the non-composite references. */
  private static final String SECTION_REFERENCES = "references";

  /** Section holding the component (composite) references. */
  private static final String SECTION_COMPONENTS = "components";

  /** Section holding the components found via their root columns. */
  private static final String SECTION_AGGREGATE = "aggregate members";

  /** All sections in the order they are printed. */
  private static final List<String> SECTIONS = List.of(SECTION_REFERENCES, SECTION_COMPONENTS, SECTION_AGGREGATE);

  /** Leads a comment line. */
  private static final String COMMENT = "-- ";

  /**
   * A single {@code SELECT} finding the PDOs referencing the inspected PDO.
   *
   * @param section the section this select belongs to
   * @param tableName the table to select from
   * @param columnName the referencing column (part of the dedup key)
   * @param condition the where condition
   * @param comment the comment describing the reference
   */
  private record ReferenceSelect(String section, String tableName, String columnName,
                                 String condition, String comment) implements Comparable<ReferenceSelect> {
    @Override
    public int compareTo(ReferenceSelect o) {
      int rv = tableName.compareTo(o.tableName);
      if (rv == 0) {
        rv = columnName.compareTo(o.columnName);
      }
      return rv;
    }
  }

  public static String createSelect(DataNode dataNode) {
    try {
      Backend backend = BackendFactory.getInstance().getBackendByUrl(
        ModificationTracker.getInstance().getSession().getSessionInfo().getProperties()
                           .getPropertyIgnoreCase(Constants.BACKEND_URL));
      DataItem dataItem = null;
      boolean selectAllForItem = false;
      if (dataNode instanceof DataItem item) {
        dataItem = item;
        selectAllForItem = true;
      }
      SqlCondition condition = dataNode.getSqlCondition();
      Entity entity = dataNode.getEntity();
      InheritanceType inheritanceType = entity.getHierarchyInheritanceType();
      String tableName = condition.getTableName() == null ? entity.getTableName() : condition.getTableName();
      String tableAlias = condition.getTableAlias() == null ? entity.getTableAlias() : condition.getTableAlias();

      StringBuilder sql = new StringBuilder();
      sql.append(Backend.SQL_SELECT);

      if (selectAllForItem) {
        entity = dataItem.getAttribute().getEntity();
      }

      if (inheritanceType == InheritanceType.SINGLE) {
        if (entity.getSuperEntity() == null) {
          // attribute in top class -> need all columns anyway
          sql.append(tableAlias).append(Backend.SQL_DOT_STAR);
        }
        else {
          // only columns necessary for entity
          boolean needComma = false;
          for (Attribute attribute : entity.getAllAttributes()) {
            if (needComma) {
              sql.append(',');
            }
            else {
              needComma = true;
            }
            sql.append(getSelectedColumn(attribute, tableAlias));
          }
        }
      }
      else {
        sql.append(tableAlias).append(Backend.SQL_DOT_STAR);
        if (inheritanceType == InheritanceType.MULTI) {
          List<Entity> entities = new ArrayList<>();
          entities.addAll(condition.attribute().getEntity().getInheritanceChain(entity).stream().skip(1).toList());
          entities.addAll(entity.getSubEntities());
          entities.forEach(e -> sql.append(',').append(e.getTableAlias()).append(Backend.SQL_DOT_STAR));
        }
      }

      sql.append(Backend.SQL_FROM).append(tableName).append(backend.sqlAsBeforeTableAlias()).append(tableAlias);

      if (inheritanceType == InheritanceType.MULTI) {
        condition.attribute().getEntity().getInheritanceChain(entity).stream().skip(1).
                  forEach(subEntity -> sql.append(", ").append(subEntity.getTableName()).
                                           append(backend.sqlAsBeforeTableAlias()).append(subEntity.getTableAlias()));
        entity.getSubEntities().forEach(subEntity -> sql.append(' ').append(JoinType.LEFT).append(' ').append(subEntity.getTableName())
                                                        .append(backend.sqlAsBeforeTableAlias()).append(subEntity.getTableAlias()).append(" ON ")
                                                        .append(subEntity.getTableAlias()).append('.').append(Constants.CN_ID).append('=')
                                                        .append(tableAlias).append('.').append(Constants.CN_ID));
      }

      sql.append(Backend.SQL_WHERE);

      if (selectAllForItem) {
        if (inheritanceType == InheritanceType.MULTI) {
          sql.append(dataItem.getAttribute().getEntity().getTableAlias());
        }
        else {
          sql.append(tableAlias);
        }
        sql.append('.').append(dataItem.getAttribute().getColumnName());
        Object orgValue = dataItem.getOrgValue();
        if (orgValue == null && dataItem.getAttribute().getOptions().isMapNull()) {

          orgValue = dataItem.getAttribute().getDataType().getMappedNullValue(backend, 0);
        }
        if (orgValue == null) {
          sql.append(Backend.SQL_ISNULL);
        }
        else {
          String value = orgValue instanceof Convertible<?> convertible ? convertible.toExternal().toString() : orgValue.toString();
          sql.append('=').append(dataItem.getAttribute().getEffectiveDataType().toLiteral(value, null));
        }
      }
      else {
        sql.append(tableAlias).append('.').append(condition.attribute().getColumnName()).
        append('=').append(condition.id());
      }

      if (entity.getSuperEntity() != null) {
        if (inheritanceType == InheritanceType.MULTI) {
          condition.attribute().getEntity().getInheritanceChain(entity).stream().skip(1).
                    forEach(subEntity -> sql.append(Backend.SQL_AND).append(subEntity.getTableAlias()).
                                         append('.').append(Constants.CN_ID).append('=').append(tableAlias).
                                         append('.').append(Constants.CN_ID));
        }
        else if (inheritanceType == InheritanceType.SINGLE) {
          Set<Integer> classIds = new TreeSet<>();    // we want that sorted by classId
          if (selectAllForItem && entity.getClassId() != 0) {
            classIds.add(entity.getClassId());
          }
          for (Entity subEntity : entity.getSubEntities()) {
            if (entity.getClassId() != 0) {
              classIds.add(subEntity.getClassId());
            }
          }
          if (classIds.size() == 1) {
            sql.append(Backend.SQL_AND).append(tableAlias).append('.').append(Constants.CN_CLASSID).
            append('=').append(entity.getClassId());
          }
          else if (classIds.size() > 1) {
            sql.append(Backend.SQL_AND).append(tableAlias).append('.').append(Constants.CN_CLASSID).append(" IN(");
            boolean needComma = false;
            for (Integer classId : classIds) {
              if (needComma) {
                sql.append(',');
              }
              else {
                needComma = true;
              }
              sql.append(classId);
            }
            sql.append(')');
          }
        }
      }

      sql.append(';');
      return sql.toString();
    }
    catch (RuntimeException | ModelException ex) {
      return "-- " + ex.getMessage();
    }
  }


  /**
   * Creates the SQL script finding all PDOs referencing a given PDO.<br>
   * The script consists of one {@code SELECT} per referencing table and column, grouped into the
   * non-composite references, the components of the PDO and, if the PDO is a root entity, its
   * aggregate members found via their root columns.
   *
   * @param dataNode the data node holding the PDO
   * @return the SQL script; a comment if the node is not a PDO or the model is broken
   */
  public static String createReferencingSelects(DataNode dataNode) {
    try {
      if (!(dataNode instanceof DataObject dataObject) || dataObject.getPdo() == null) {
        return COMMENT + "not a persistent object";
      }
      return createReferencingSelects(dataObject.getEntity(), dataObject.getId(), dataObject.getPdo().getClassId());
    }
    catch (RuntimeException | ModelException ex) {
      return COMMENT + ex.getMessage();
    }
  }

  /**
   * Creates the SQL script finding all PDOs referencing a given PDO.
   *
   * @param entity the entity of the referenced PDO
   * @param id the object ID of the referenced PDO
   * @param classId the class ID of the referenced PDO
   * @return the SQL script
   * @throws ModelException if the model is broken
   */
  static String createReferencingSelects(Entity entity, long id, int classId) throws ModelException {
    Map<String, ReferenceSelect> selects = new LinkedHashMap<>();
    addReferencingObjectRelations(selects, entity, id);
    addReferencingListRelations(selects, entity, id);
    addAggregateMembers(selects, entity, id, classId);

    StringBuilder sql = new StringBuilder();
    sql.append(COMMENT).append("PDOs referencing ").append(entity.getName()).append(' ')
       .append(Constants.CN_ID).append('=').append(id).append('\n');

    if (selects.isEmpty()) {
      sql.append(COMMENT).append("nothing references ").append(entity.getName()).append('\n');
    }
    else {
      for (String section : SECTIONS) {
        List<ReferenceSelect> sectionSelects = selects.values().stream()
                                                      .filter(select -> select.section().equals(section))
                                                      .sorted().toList();
        if (!sectionSelects.isEmpty()) {
          sql.append('\n').append(COMMENT).append(section).append('\n');
          for (ReferenceSelect select : sectionSelects) {
            sql.append(COMMENT).append(select.comment()).append('\n')
               .append(Backend.SQL_SELECT_ALL_FROM).append(select.tableName())
               .append(Backend.SQL_WHERE).append(select.condition()).append(";\n");
          }
        }
      }
    }

    return sql.toString();
  }


  /**
   * Adds the {@code SELECT}s for the object relations of other entities pointing to the given entity.<br>
   * The foreign key column is provided by the referencing entity.
   *
   * @param selects the {@code SELECT}s so far, keyed by table and column
   * @param entity the referenced entity
   * @param id the object ID of the referenced PDO
   */
  private static void addReferencingObjectRelations(Map<String, ReferenceSelect> selects, Entity entity, long id) {
    for (Relation relation : entity.getReferencingRelationsIncludingInherited()) {
      if (relation.getRelationType() != RelationType.OBJECT || relation.getAttribute() == null) {
        continue;
      }
      String section = isComponentRelation(relation) ? SECTION_COMPONENTS : SECTION_REFERENCES;
      Entity referencingEntity = relation.getEntity();
      if (referencingEntity.isEmbedded()) {
        // the columns live in the table of the embedding entity, prefixed along the embedding path
        for (List<Relation> embeddingPath : referencingEntity.getEmbeddingPaths()) {
          if (embeddingPath.isEmpty()) {
            continue;
          }
          StringBuilder columnPrefix = new StringBuilder();
          StringBuilder pathName = new StringBuilder();
          for (Relation embeddingRelation : embeddingPath) {
            columnPrefix.append(embeddingRelation.getColumnPrefix());
            pathName.append(embeddingRelation.getName()).append('.');
          }
          Entity embeddingEntity = embeddingPath.getFirst().getEntity();
          Relation embeddedRelation = relation.createEmbedded(embeddingEntity, pathName.toString(), columnPrefix.toString());
          if (!embeddedRelation.getMethodArgs().isEmpty()) {
            // createEmbedded updates the first method argument with the prefixed column, not getAttribute()
            Attribute attribute = embeddedRelation.getMethodArgs().getFirst().getAttribute();
            if (attribute != null) {
              addSelects(selects, section, embeddingEntity, attribute.getColumnName(), id,
                         embeddingEntity.getName() + '.' + pathName + relation.getAttribute().getName());
            }
          }
        }
      }
      else {
        addSelects(selects, section, referencingEntity, relation.getAttribute().getColumnName(), id,
                   referencingEntity.getName() + '.' + relation.getAttribute().getName());
      }
    }
  }

  /**
   * Adds the {@code SELECT}s for the list relations of the given entity.<br>
   * For {@code 1:N} relations the foreign key column is provided by the foreign entity. Such a relation is often
   * declared on the list side only, hence it must be inspected in addition to the referencing object relations.
   *
   * @param selects the {@code SELECT}s so far, keyed by table and column
   * @param entity the referenced entity
   * @param id the object ID of the referenced PDO
   */
  private static void addReferencingListRelations(Map<String, ReferenceSelect> selects, Entity entity, long id) {
    for (Relation relation : entity.getRelationsIncludingInherited()) {
      if (relation.getRelationType() != RelationType.LIST ||
          relation.getForeignEntity() == null || relation.getForeignAttribute() == null) {
        continue;
      }
      String section = isComponentRelation(relation) ? SECTION_COMPONENTS : SECTION_REFERENCES;
      Entity referencingEntity = relation.getForeignEntity();
      addSelects(selects, section, referencingEntity, relation.getForeignAttribute().getColumnName(), id,
                 referencingEntity.getName() + '.' + relation.getForeignAttribute().getName());
    }
  }

  /**
   * Adds the {@code SELECT}s for the components carrying the root columns of the given entity.<br>
   * This finds the members of the whole aggregate, no matter how deep they are nested.
   *
   * @param selects the {@code SELECT}s so far, keyed by table and column
   * @param entity the referenced entity
   * @param id the object ID of the referenced PDO
   * @param classId the class ID of the referenced PDO
   */
  private static void addAggregateMembers(Map<String, ReferenceSelect> selects, Entity entity, long id, int classId) {
    if (!entity.isRootEntity()) {
      return;   // the root columns of its components point to another root
    }
    Set<Entity> components = new LinkedHashSet<>();
    collectComponents(components, entity);
    for (Entity component : components) {
      Attribute rootIdAttribute = component.getAttributeByJavaName(Constants.AN_ROOTID, true);
      if (rootIdAttribute == null) {
        continue;
      }
      StringBuilder condition = new StringBuilder();
      condition.append(rootIdAttribute.getColumnName()).append('=').append(id);
      Attribute rootClassIdAttribute = component.getAttributeByJavaName(Constants.AN_ROOTCLASSID, true);
      if (rootClassIdAttribute != null) {
        condition.append(Backend.SQL_AND).append(rootClassIdAttribute.getColumnName()).append('=').append(classId);
      }
      addSelects(selects, SECTION_AGGREGATE, component, rootIdAttribute.getColumnName(), condition.toString(),
                 component.getName() + '.' + rootIdAttribute.getName());
    }
  }

  /**
   * Recursively collects the components of an entity.<br>
   * Embedded entities are skipped since they don't provide a table of their own, and sub-roots
   * terminate the recursion because their components refer to the sub-root, not to the given entity.
   *
   * @param components the components collected so far
   * @param entity the entity to inspect
   */
  private static void collectComponents(Set<Entity> components, Entity entity) {
    for (Relation relation : entity.getRelationsIncludingInherited()) {
      Entity component = relation.getForeignEntity();
      if (relation.isComposite() && component != null &&
          !component.isEmbedded() && !component.isRootEntity() && components.add(component)) {
        collectComponents(components, component);
      }
    }
  }

  /**
   * Returns whether a relation creates a composite, i.e., the referencing entity is a component.
   *
   * @param relation the relation
   * @return true if composite
   */
  private static boolean isComponentRelation(Relation relation) {
    Relation foreignRelation = relation.getForeignRelation();
    return relation.isComposite() || foreignRelation != null && foreignRelation.isComposite();
  }

  /**
   * Adds a {@code SELECT} for each table the referencing entity maps to.
   *
   * @param selects the {@code SELECT}s so far, keyed by table and column
   * @param section the section the select belongs to
   * @param referencingEntity the referencing entity
   * @param columnName the referencing column
   * @param id the object ID of the referenced PDO
   * @param comment the comment describing the reference
   */
  private static void addSelects(Map<String, ReferenceSelect> selects, String section, Entity referencingEntity,
                                 String columnName, long id, String comment) {
    addSelects(selects, section, referencingEntity, columnName, columnName + "=" + id, comment);
  }

  /**
   * Adds a {@code SELECT} for each table the referencing entity maps to.
   *
   * @param selects the {@code SELECT}s so far, keyed by table and column
   * @param section the section the {@code SELECT} belongs to
   * @param referencingEntity the referencing entity
   * @param columnName the referencing column
   * @param condition the where condition
   * @param comment the comment describing the reference
   */
  private static void addSelects(Map<String, ReferenceSelect> selects, String section, Entity referencingEntity,
                                 String columnName, String condition, String comment) {
    for (Entity tableEntity : getTableEntities(referencingEntity)) {
      String tableName = tableEntity.getTableName();
      selects.putIfAbsent(tableName + '.' + columnName,
                          new ReferenceSelect(section, tableName, columnName, condition, comment));
    }
  }

  /**
   * Gets the entities providing the tables an entity is stored in.<br>
   * This is usually a single entity. Only entities using {@link InheritanceType#PLAIN} are spread
   * over the tables of their concrete sub entities.
   *
   * @param entity the entity
   * @return the table providing entities, empty if the entity maps to no table at all
   */
  private static Set<Entity> getTableEntities(Entity entity) {
    Set<Entity> tableEntities = new LinkedHashSet<>();
    Entity tableEntity = entity.getTableProvidingEntity();
    if (tableEntity == null) {
      for (Entity leafEntity : entity.getLeafEntities()) {
        tableEntity = leafEntity.getTableProvidingEntity();
        if (tableEntity != null) {
          tableEntities.add(tableEntity);
        }
      }
    }
    else {
      tableEntities.add(tableEntity);
    }
    return tableEntities;
  }


  private static String getSelectedColumn(Attribute attribute, String tableAlias) throws ModelException {
    DataType<?> dataType = attribute.getEffectiveDataType();
    StringBuilder sql = new StringBuilder();
    int columnCount = dataType.getColumnCount(null);
    for (int col=0; col < columnCount; col++) {
      if (col > 0) {
        sql.append(',');
      }
      sql.append(tableAlias).append('.').append(attribute.getColumnName(null, col));
    }
    return sql.toString();
  }

}

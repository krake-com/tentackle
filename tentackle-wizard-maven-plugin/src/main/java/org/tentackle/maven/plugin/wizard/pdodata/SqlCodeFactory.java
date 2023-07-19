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
import org.tentackle.session.ModificationTracker;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;
import org.tentackle.sql.DataType;
import org.tentackle.sql.JoinType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Factory generating SQL code to select PDOs.
 */
public class SqlCodeFactory {

  public static String createSelect(DataNode dataNode) {
    try {
      Backend backend = BackendFactory.getInstance().getBackendByUrl(ModificationTracker.getInstance().getSession().
                                                                     getSessionInfo().getProperties().
                                                                     getProperty(Constants.BACKEND_URL));
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
        entity.getSubEntities().forEach(subEntity -> {
          sql.append(' ').append(JoinType.LEFT).append(' ').append(subEntity.getTableName()).
              append(backend.sqlAsBeforeTableAlias()).append(subEntity.getTableAlias()).append(" ON ").
              append(subEntity.getTableAlias()).append('.').append(Constants.CN_ID).append('=').
              append(tableAlias).append('.').append(Constants.CN_ID);
        });
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

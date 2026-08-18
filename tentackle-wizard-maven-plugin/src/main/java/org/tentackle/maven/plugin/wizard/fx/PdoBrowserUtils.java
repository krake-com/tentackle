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

package org.tentackle.maven.plugin.wizard.fx;

import org.tentackle.fx.Fx;
import org.tentackle.maven.plugin.wizard.pdodata.DataItem;
import org.tentackle.maven.plugin.wizard.pdodata.DataNode;
import org.tentackle.maven.plugin.wizard.pdodata.DataObject;
import org.tentackle.maven.plugin.wizard.pdodata.SqlCodeFactory;
import org.tentackle.model.ModelException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.sql.DataType;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;

/**
 * Some common UI methods to reduce code duplication.
 */
public class PdoBrowserUtils {

  public static TypeInfo getTypeInfo(DataNode dataNode) {
    if (dataNode instanceof DataObject object) {
      PersistentDomainObject<?> pdo = object.getPdo();
      if (pdo != null) {
        return new TypeInfo(pdo.getEffectiveClass().getName(), pdo.getTableName(), pdo.getClassId());
      }
    }
    return null;
  }

  public static boolean isCreatingSqlPossible(DataNode dataNode) {
    if (dataNode != null) {
      if (dataNode instanceof DataItem dataItem) {
        try {
          DataType<?> dataType = dataItem.getAttribute().getEffectiveDataType();
          if (dataType.isColumnCountBackendSpecific() || dataType.getColumnCount(null) > 1) {
            return false;
          }
        }
        catch (ModelException mx) {
          return false;   // just ignored...
        }
      }
      else if (dataNode instanceof DataObject dataObject && dataObject.getEntity().isEmbedded()) {
        return false;
      }
      return true;
    }
    return false;
  }

  public static boolean isFindingReferencesPossible(DataNode dataNode) {
    return dataNode instanceof DataObject dataObject && dataObject.getPdo() != null &&
           dataObject.getEntity() != null && !dataObject.getEntity().isEmbedded();
  }

  public static MenuItem createCopySqlItem(EventHandler<ActionEvent> handler) {
    MenuItem copySqlItem = Fx.create(MenuItem.class);
    copySqlItem.setOnAction(handler);
    copySqlItem.setText("copy SQL SELECT... to clipboard");
    return copySqlItem;
  }

  public static MenuItem createFindReferencesItem(EventHandler<ActionEvent> handler) {
    MenuItem findReferencesItem = Fx.create(MenuItem.class);
    findReferencesItem.setOnAction(handler);
    findReferencesItem.setText("copy SQL SELECT statements of referencing PDOs to clipboard");
    return findReferencesItem;
  }

  public static void configureContextMenu(DataNode dataNode, MenuItem copyFQCNItem, MenuItem copyTableItem,
                                          MenuItem copyClassIdItem, MenuItem copySqlItem, MenuItem findReferencesItem) {
    TypeInfo typeInfo = getTypeInfo(dataNode);
    copyFQCNItem.setVisible(false);
    copyTableItem.setVisible(false);
    copyClassIdItem.setVisible(false);
    copySqlItem.setVisible(isCreatingSqlPossible(dataNode));
    findReferencesItem.setVisible(isFindingReferencesPossible(dataNode));
    if (typeInfo != null) {
      if (typeInfo.fqcn() != null) {
        copyFQCNItem.setText("copy FQCN \"" + typeInfo.fqcn() + "\" to clipboard");
        copyFQCNItem.setVisible(true);
      }
      if (typeInfo.tableName() != null) {
        // replace _ by __ to avoid misinterpretation as a mnemonic (even if setMnemonicParsing=false!)
        copyTableItem.setText("copy table name \"" + typeInfo.tableName().replace("_", "__") + "\" to clipboard");
        copyTableItem.setMnemonicParsing(false);    // table names often contain underscores
        copyTableItem.setVisible(true);
      }
      if (typeInfo.classId() != 0) {
        copyClassIdItem.setText("copy class-ID \"" + typeInfo.classId() + "\" to clipboard");
        copyClassIdItem.setVisible(true);
      }
    }
  }

  public static void copyFQCN(DataNode dataNode) {
    TypeInfo typeInfo = getTypeInfo(dataNode);
    if (typeInfo != null) {
      Fx.copyToClipboard(typeInfo.fqcn());
    }
  }

  public static void copyTableName(DataNode dataNode) {
    TypeInfo typeInfo = getTypeInfo(dataNode);
    if (typeInfo != null) {
      Fx.copyToClipboard(typeInfo.tableName());
    }
  }

  public static void copyClassId(DataNode dataNode) {
    TypeInfo typeInfo = getTypeInfo(dataNode);
    if (typeInfo != null) {
      Fx.copyToClipboard(Long.toString(typeInfo.classId()));
    }
  }

  public static void copySql(DataNode dataNode) {
    if (dataNode != null) {
      Fx.copyToClipboard(SqlCodeFactory.createSelect(dataNode));
    }
  }

  public static void copyReferencingSql(DataNode dataNode) {
    if (dataNode != null) {
      Fx.copyToClipboard(SqlCodeFactory.createReferencingSelects(dataNode));
    }
  }


  private PdoBrowserUtils() {}

}

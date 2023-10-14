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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

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

  public static void copyToClipboard(String text) {
    ClipboardContent cbc = new ClipboardContent();
    cbc.putString(text);
    Clipboard.getSystemClipboard().setContent(cbc);
  }

  public static MenuItem createCopySqlItem(EventHandler<ActionEvent> handler) {
    MenuItem copySqlItem = Fx.create(MenuItem.class);
    copySqlItem.setOnAction(handler);
    copySqlItem.setText("copy SQL SELECT... to clipboard");
    return copySqlItem;
  }


  public static void configureContextMenu(DataNode dataNode, MenuItem copyFQCNItem, MenuItem copyTableItem,
                                          MenuItem copyClassIdItem, MenuItem copySqlItem) {
    TypeInfo typeInfo = getTypeInfo(dataNode);
    copyFQCNItem.setVisible(false);
    copyTableItem.setVisible(false);
    copyClassIdItem.setVisible(false);
    copySqlItem.setVisible(isCreatingSqlPossible(dataNode));
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
      copyToClipboard(typeInfo.fqcn());
    }
  }

  public static void copyTableName(DataNode dataNode) {
    TypeInfo typeInfo = getTypeInfo(dataNode);
    if (typeInfo != null) {
      copyToClipboard(typeInfo.tableName());
    }
  }

  public static void copyClassId(DataNode dataNode) {
    TypeInfo typeInfo = getTypeInfo(dataNode);
    if (typeInfo != null) {
      copyToClipboard(Long.toString(typeInfo.classId()));
    }
  }

  public static void copySql(DataNode dataNode) {
    if (dataNode != null) {
      copyToClipboard(SqlCodeFactory.createSelect(dataNode));
    }
  }


  private PdoBrowserUtils() {}

}

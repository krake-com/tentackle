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

package org.tentackle.fx.rdc.security;

import javafx.geometry.Pos;
import javafx.scene.Node;

import org.tentackle.fx.Fx;
import org.tentackle.fx.rdc.DefaultGuiProvider;
import org.tentackle.fx.rdc.GuiProviderService;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.type.BooleanTableCellType;
import org.tentackle.security.pdo.Security;

/**
 * GUI provider for {@link Security}.<br>
 * Edits the security rules with the {@link SecurityEditor} and displays them in a table
 * of grantee, context, permissions, allowed flag, and message.
 *
 * @author harald
 */
@GuiProviderService(Security.class)
public class SecurityGuiProvider extends DefaultGuiProvider<Security> {

  /**
   * Creates the provider.
   *
   * @param pdo the PDO
   */
  public SecurityGuiProvider(Security pdo) {
    super(pdo);
  }

  @Override
  public Node createGraphic() {
    return Fx.createGraphic("security");
  }

  @Override
  public SecurityEditor createEditor() {
    return Fx.load(SecurityEditor.class);
  }

  @Override
  public boolean isEditorAvailable() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public TableConfiguration<Security> createTableConfiguration() {
    TableConfiguration<Security> config = createEmptyTableConfiguration();
    config.addColumn("granteeToString", getBundle().getString("grantee"));
    config.addColumn("contextToString", getBundle().getString("context"));
    // priority not displayed because it corresponds to the sorting order
    config.addColumn(Security.AN_PERMISSIONS, getBundle().getString(Security.AN_PERMISSIONS));
    TableColumnConfiguration<Security, Boolean> allowedConfiguration = (TableColumnConfiguration<Security, Boolean>)
        config.addColumn(Security.AN_ALLOWED, getBundle().getString(Security.AN_ALLOWED));
    // override default checkbox renderer (style configured in SecurityRulesView.css)
    allowedConfiguration.setCellType(new BooleanTableCellType() {
      @Override
      public void updateItem(FxTableCell<?, Boolean> tableCell, Boolean item) {
        tableCell.setText(null);
        if (item) {
          tableCell.setGraphic(Fx.createGraphic("ok"));
          tableCell.getStyleClass().remove(SecurityEditor.DENY_STYLE);
          if (!tableCell.getStyleClass().contains(SecurityEditor.ALLOW_STYLE)) {
            tableCell.getStyleClass().add(SecurityEditor.ALLOW_STYLE);
          }
        }
        else {
          tableCell.setGraphic(Fx.createGraphic("cancel"));
          tableCell.getStyleClass().remove(SecurityEditor.ALLOW_STYLE);
          if (!tableCell.getStyleClass().contains(SecurityEditor.DENY_STYLE)) {
            tableCell.getStyleClass().add(SecurityEditor.DENY_STYLE);
          }
        }
        updateAlignment(tableCell, Pos.BASELINE_CENTER);
      }
    });
    config.addColumn(Security.AN_MESSAGE, getBundle().getString(Security.AN_MESSAGE));
    return config;
  }

}

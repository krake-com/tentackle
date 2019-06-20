/*
 * Tentackle - http://www.tentackle.org
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
 * License along with this library; if not, write to the DFree Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.fx.rdc.security;

import javafx.scene.image.ImageView;

import org.tentackle.common.Bundle;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.rdc.DefaultGuiProvider;
import org.tentackle.fx.rdc.GuiProviderService;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.security.pdo.Security;

/**
 * GUI provider for Security.
 *
 * @author harald
 */
@GuiProviderService(Security.class)
@Bundle
public class SecurityGuiProvider extends DefaultGuiProvider<Security> {

  /**
   * Creates the provider.
   *
   * @param pdo the pdo
   */
  public SecurityGuiProvider(Security pdo) {
    super(pdo);
  }

  @Override
  public ImageView createIcon() {
    return Fx.createImageView("security");
  }

  @Override
  public SecurityEditor createEditor() {
    return Fx.load(SecurityEditor.class);
  }

  @Override
  public boolean editorExists() {
    return true;
  }

  @Override
  public TableConfiguration<Security> createTableConfiguration() {
    TableConfiguration<Security> config = FxFactory.getInstance().createTableConfiguration(getPdo(), null);
    config.addColumn("granteeToString", getBundle().getString("grantee"));
    config.addColumn("contextToString", getBundle().getString("context"));
    // priority not displayed because it corresponds to the sorting order
    config.addColumn(Security.AN_PERMISSIONS, getBundle().getString(Security.AN_PERMISSIONS));
    config.addColumn(Security.AN_ALLOWED, getBundle().getString(Security.AN_ALLOWED));
    config.addColumn(Security.AN_MESSAGE, getBundle().getString(Security.AN_MESSAGE));
    return config;
  }

}

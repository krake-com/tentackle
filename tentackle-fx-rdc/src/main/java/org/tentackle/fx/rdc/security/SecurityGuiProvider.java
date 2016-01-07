/*
 * Copyright (C) 2015 by:
 *
 * Hydro Aluminium Rolled Users GmbH
 * Koblenzer Strasse 122
 * D-41468 Neuss
 * Germany
 * Tel. +49 2131 382 0
 * http://www.hydro.com/Deutschland
 *
 * All Rights Reserved.
 */

package org.tentackle.fx.rdc.security;

import javafx.scene.image.ImageView;
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
  @SuppressWarnings("unchecked")
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
    // priority not displayed because it corresponds to the sorting order
    config.addColumn(Security.AN_PERMISSIONS, getBundle().getString(Security.AN_PERMISSIONS));
    config.addColumn(Security.AN_ALLOWED, getBundle().getString(Security.AN_ALLOWED));
    config.addColumn(Security.AN_MESSAGE, getBundle().getString(Security.AN_MESSAGE));
    return config;
  }

}

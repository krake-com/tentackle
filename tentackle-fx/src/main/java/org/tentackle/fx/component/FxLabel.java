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
 *
 */

package org.tentackle.fx.component;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxUtilities;

import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.stage.Popup;

/**
 * Extended Label.
 *
 * @author harald
 */
public class FxLabel extends Label {

  private ContextMenu contextMenu;

  /**
   * Creates a label.
   */
  public FxLabel() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  public void setContextMenuEnabled(boolean enabled) {
    if (enabled) {
      if (contextMenu == null) {
        contextMenu = createContextMenu();
        setContextMenu(contextMenu);
      }
    }
    else if (contextMenu != null) {
      setContextMenu(null);
      contextMenu = null;
    }
  }

  /**
   * Creates a context menu with items to copy the label's text to the clipboard and
   * to open a scratchpad to select only parts the of the text.
   *
   * @return the context menu
   */
  protected ContextMenu createContextMenu() {
    ContextMenu contextMenu = new ContextMenu();
    MenuItem copyItem = new MenuItem(FxFxBundle.getString("COPY TO CLIPBOARD"));
    copyItem.setOnAction(event -> Fx.copyToClipboard(getText()));
    contextMenu.getItems().add(copyItem);
    MenuItem scratchItem = new MenuItem(FxFxBundle.getString("COPY TO SCRATCHPAD"));
    scratchItem.setOnAction(event -> {
      Popup popup = FxUtilities.getInstance().createNotificationPopup();
      Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                     .content(new TextArea(getText()))
                                     .hide(popup::hide)
                                     .build();
      FxUtilities.getInstance().showNotification(this, popup, notification, null);
    });
    contextMenu.getItems().add(scratchItem);
    return contextMenu;
  }

}

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
 * Extended Label.<br>
 * Optionally provides a context menu to copy the label's text.
 *
 * @author harald
 */
public class FxLabel extends Label {

  private ContextMenu contextMenu;    // != null if items were added

  /**
   * Creates a label.
   */
  public FxLabel() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Enables a context menu for this label.<br>
   * Since the label's text cannot be selected, it cannot be copied to the clipboard.
   * By enabling a context menu, two menu items will be available:
   * <ol>
   *   <li>Copy the label's text to the clipboard.</li>
   *   <li>Copy the label's text to a textarea in a popup window. Allows selecting only
   *   parts of the text.</li>
   * </ol>
   * @param enabled true to install or update a context menu, false to remove it (if previously enabled)
   */
  public void setContextMenuEnabled(boolean enabled) {
    if (enabled) {
      if (!isContextMenuEnabled()) {
        contextMenu = getContextMenu();   // in case there was already some menu defined
        boolean created = false;
        if (contextMenu == null) {
          contextMenu = new ContextMenu();
          created = true;
        }
        configureContextMenu(contextMenu);
        if (created) {
          setContextMenu(contextMenu);
        }
      }
      // else: already appended (hopefully)
    }
    else if (isContextMenuEnabled()) {
      setContextMenu(null);
      contextMenu = null;
    }
  }

  /**
   * Returns whether the context menu has been enabled by {@link #setContextMenuEnabled(boolean)}.
   *
   * @return true if enabled
   */
  public boolean isContextMenuEnabled() {
    return contextMenu != null;
  }


  /**
   * Adds menu items to copy the label's text to the clipboard and
   * to open a scratchpad to select only parts of the text.
   *
   * @param contextMenu the context menu to append the menu items to
   */
  protected void configureContextMenu(ContextMenu contextMenu) {
    MenuItem copyItem = new MenuItem(FxFxBundle.getString("COPY TO CLIPBOARD"));
    copyItem.setOnAction(_ -> Fx.copyToClipboard(getText()));
    contextMenu.getItems().add(copyItem);
    MenuItem scratchItem = new MenuItem(FxFxBundle.getString("COPY TO SCRATCHPAD"));
    scratchItem.setOnAction(_ -> {
      Popup popup = FxFactory.getInstance().createNotificationPopup();
      Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                     .content(new TextArea(getText()))
                                     .hide(popup::hide)
                                     .build();
      FxUtilities.getInstance().showNotification(this, popup, notification, null);
    });
    contextMenu.getItems().add(scratchItem);
  }

}

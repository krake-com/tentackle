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

package org.tentackle.fx.component.config;

import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;

import org.tentackle.fx.ConfiguratorService;

/**
 * Configures a TableView.
 *
 * @author harald
 * @param <T> the tableview type
 */
@ConfiguratorService(TableView.class)
public class TableViewConfigurator<T extends TableView<?>> extends ComponentConfigurator<T> {

  @Override
  public void configure(T control) {
    // no super.configure()... -> no key remapping!

    // Enable cell copy via Crtl-C to the clipboard.
    // If cell selection and multi is enabled, multiple cells are separated via TAB.
    control.setOnKeyPressed(e -> {
      if (e.isControlDown() && e.getCode() == KeyCode.C) {
        StringBuilder buf = new StringBuilder();
        for (TablePosition<?, ?> pos : control.getSelectionModel().getSelectedCells()) {
          Object cell = control.getColumns().get(pos.getColumn()).getCellData(pos.getRow());
          if (cell != null) {
            if (buf.length() > 0) {
              buf.append('\t');
            }
            buf.append(cell);
          }
        }
        if (buf.length() > 0) {
          ClipboardContent cbc = new ClipboardContent();
          cbc.putString(buf.toString());
          Clipboard.getSystemClipboard().setContent(cbc);
        }
      }
    });
  }

}

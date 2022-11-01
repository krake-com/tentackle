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

import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyEvent;

import org.tentackle.fx.ConfiguratorService;
import org.tentackle.fx.component.FxTreeTableView;

/**
 * Configures a {@link TreeTableView}.
 *
 * @author harald
 * @param <T> the treetable view type
 */
@ConfiguratorService(TreeTableView.class)
public class TreeTableViewConfigurator<T extends TreeTableView<?>> extends ComponentConfigurator<T> {

  /**
   * Creates the configurator for a {@link TreeTableView}.
   */
  public TreeTableViewConfigurator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void configure(T control) {
    // no super.configure()... -> no key remapping!

    if (control instanceof FxTreeTableView<?> treeTableView) {
      treeTableView.setCopyToClipboardEnabled(true);
      treeTableView.addEventFilter(KeyEvent.ANY, treeTableView::filterDangerousKeys);
      treeTableView.addEventHandler(KeyEvent.ANY, treeTableView::handleKeyEvent);
    }
  }

}

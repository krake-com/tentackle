/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.fx.container.config;

import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.Window;
import org.tentackle.fx.Configurator;
import org.tentackle.fx.ConfiguratorService;
import org.tentackle.fx.FxUtilities;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;

/**
 * Configures a Window.
 *
 * @author harald
 * @param <T> the window type
 */
@ConfiguratorService(Window.class)
public class WindowConfigurator<T extends Window> implements Configurator<T> {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(WindowConfigurator.class);


  @Override
  public void configure(T window) {
    registerEventFilters(window);
  }


  /**
   * Registers event filters.
   *
   * @param window the window
   */
  protected void registerEventFilters(T window) {

    window.addEventFilter(MouseEvent.ANY, event -> {
       if (event.isShiftDown() && event.isControlDown() && !event.isMetaDown() && !event.isAltDown() &&
           event.isPopupTrigger()) {
        PickResult pickResult = event.getPickResult();
        if (pickResult != null) {
          Node node = pickResult.getIntersectedNode();
          if (node != null) {
            LOGGER.info("\n" + FxUtilities.getInstance().dumpComponentHierarchy(node));
          }
        }
      }
    });
  }

}

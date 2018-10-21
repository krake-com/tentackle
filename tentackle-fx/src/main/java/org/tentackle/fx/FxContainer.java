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

package org.tentackle.fx;

import javafx.collections.ObservableList;

/**
 * Interface all tentackle Fx containers must implement.
 *
 * @author harald
 */
public interface FxContainer extends FxControl {

  /**
   * Gets the fx components maintained by this container.
   *
   * @return list of components
   */
  ObservableList<?> getComponents();

  /**
   * Clears all error messages in all components.
   */
  void clearErrors();

  /**
   * Gets the controller of this container.
   *
   * @return the controller, null if nested container without a controller
   */
  FxController getController();

  /**
   * Sets the controller of this container.
   *
   * @param controller the controller, null if nested container without a controller
   */
  void setController(FxController controller);

  /**
   * Gets the controller along the parent hierarchy that is an instanceof the given class.
   *
   * @param <C> the controller type
   * @param clazz the controller class
   * @return the controller, null if no such controller
   */
  <C extends FxController> C getController(Class<C> clazz);

  /**
   * Updates the view according to the model.<br>
   * The method is the same as {@link #updateView()} but skips any focused component.
   */
  void updateViewNonFocused();

}

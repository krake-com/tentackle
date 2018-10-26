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

import javafx.scene.Parent;
import javafx.stage.Stage;

import org.tentackle.fx.bind.FxComponentBinder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * The controller interface all Tentackle FX-controllers must implement.
 *
 * @author harald
 */
public interface FxController {

  /**
   * Sets the view managed by this controller.
   *
   * @param view the view
   */
  void setView(Parent view);

  /**
   * Gets the view managed by this controller.
   *
   * @return the view
   */
  Parent getView();

  /**
   * Gets the stage of which the controller's view is the root of the scene.
   *
   * @return the stage, null if not a root scene
   */
  Stage getStage();

  /**
   * Gets the top-level container.<br>
   * This is usually the view.
   *
   * @return the container, null if none
   */
  FxContainer getContainer();

  /**
   * Validate @FXML-annotated fields.<br>
   * The fxml-loader does not throw an exception if an fx:id does not correspond
   * to a field annotated with @FXML. This methods verifies that all such fields
   * provide a non-null value.
   */
  void validateInjections();

  /**
   * Gets the binder of this controller.
   *
   * @return the binder, never null.
   */
  FxComponentBinder getBinder();

  /**
   * Configures the controller as the final step in initialization.
   */
  void configure();

  /**
   * Gets all fields in this controller annotated with &#64;FXML.<br>
   * The fields are made accessible, if private.
   *
   * @return the fields
   */
  List<Field> getFXMLFields();

  /**
   * Gets all methods in this controller annotated with &#64;FXML.<br>
   * The methods are made accessible, if private.
   *
   * @return the methods
   */
  List<Method> getFXMLMethods();

}

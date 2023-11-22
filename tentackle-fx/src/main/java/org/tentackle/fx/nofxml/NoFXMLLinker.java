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

package org.tentackle.fx.nofxml;

import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxControllerService;

import javafx.scene.Parent;
import java.util.ResourceBundle;


interface NoFXMLLinkerHolder {
  NoFXMLLinker INSTANCE = ServiceFactory.createService(NoFXMLLinker.class);
}

/**
 * Links a view object with a controller without using {@code FXML}.<br>
 * Takes a view object, parses it for {@link NoFXML}-annotations
 * and injects the components into an {@link org.tentackle.fx.FxController}.
 * Requires {@link FxControllerService#view()} to point to the view class.
 * <p>
 * Example for a view class:
 * <pre>
 * public class InputView extends FxPane {
 *
 *   &#64;NoFXML
 *   private final TextField inputField = new FxTextField();
 *
 *   &#64;NoFXML
 *   private final Button saveButton = new FxButton();
 *
 *   &#64;NoFXML
 *   private ResourceBundle resources;
 *
 *   &#64;NoFXML
 *   private void initialize() {
 *     inputField.setPromptText(resources.getString("input"));
 *     saveButton.setText(resources.getString("save"));
 *   }
 * }
 * </pre>
 *
 * And the controller:
 *
 * <pre>
 * &#64;FxControllerService(view = InputView.class)
 * public class InputController extends AbstractFxController {
 *
 * }
 * </pre>
 */
public interface NoFXMLLinker {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static NoFXMLLinker getInstance() {
    return NoFXMLLinkerHolder.INSTANCE;
  }


  /**
   * Links the components and resources.
   *
   * @param view the view
   * @param controller the controller
   * @param resources the optional resources
   */
  void link(Parent view, FxController controller, ResourceBundle resources);

}

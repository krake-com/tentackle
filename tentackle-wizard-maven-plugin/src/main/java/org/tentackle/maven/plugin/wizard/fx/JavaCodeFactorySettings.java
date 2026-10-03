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

package org.tentackle.maven.plugin.wizard.fx;

import org.tentackle.bind.Bindable;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxCheckBox;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.maven.plugin.wizard.pdodata.JavaCodeFactory;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.stage.Popup;

/**
 * Popup to configure the variable names of the generated Java code.<br>
 * Edits the static settings {@link JavaCodeFactory#distinctVariables} and {@link JavaCodeFactory#variableOffset},
 * which last for the rest of the goal's execution.
 */
@FxControllerService(resources = FxControllerService.RESOURCES_NONE)
public class JavaCodeFactorySettings extends AbstractFxController {

  @Bindable private boolean distinctVariables;
  @Bindable private Integer variableOffset;

  @FXML private FxCheckBox distinctVariablesField;
  @FXML private FxTextField variableOffsetField;

  @FXML
  private void initialize() {
    distinctVariables = JavaCodeFactory.distinctVariables;
    variableOffset = JavaCodeFactory.variableOffset;
    distinctVariablesField.addViewToModelListener(this::distinctVariableUpdated);
  }

  @Override
  public void configure() {
    getContainer().updateView();
    distinctVariableUpdated();
  }

  private void distinctVariableUpdated() {
    if (distinctVariables) {
      variableOffsetField.setChangeable(true);
    }
    else {
      variableOffsetField.setChangeable(false);
      if (variableOffset != null) {
        variableOffset = null;
        variableOffsetField.updateView();
      }
    }
  }


  /**
   * Creates the controller.
   */
  public JavaCodeFactorySettings() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Shows the settings popup.<br>
   * The settings are applied when the popup is hidden.
   *
   * @param owner the owner node
   */
  public static void show(Object owner) {
    JavaCodeFactorySettings controller = Fx.load(JavaCodeFactorySettings.class);

    Popup popup = new Popup();
    Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                   .content(controller.getView())
                                   .hide(popup::hide)
                                   .build();

    FxUtilities.getInstance().showNotification(owner, popup, notification, () -> {
      JavaCodeFactory.distinctVariables = controller.distinctVariables;
      JavaCodeFactory.variableOffset = controller.variableOffset;
    });
  }

}

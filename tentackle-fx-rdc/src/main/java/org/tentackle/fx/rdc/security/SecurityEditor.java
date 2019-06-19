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

package org.tentackle.fx.rdc.security;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

import org.tentackle.bind.Bindable;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxCheckBox;
import org.tentackle.fx.component.FxRadioButton;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.fx.container.FxGridPane;
import org.tentackle.fx.rdc.PdoEditor;
import org.tentackle.pdo.DomainContext;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.pdo.Security;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;



/**
 * Editor for Security.
 *
 * @author harald
 */
@FxControllerService
public class SecurityEditor extends PdoEditor<Security> {

  private static final String DENY_STYLE = "sec-deny";
  private static final String ALLOW_STYLE = "sec-allow";
  private static final String NA_STYLE = "sec-na";

  @Bindable
  private Security sec;

  @FXML
  private FxTextField secGranteeField;
  @FXML
  private FxButton granteeButton;
  @FXML
  private FxTextField secDomainContextObjectField;
  @FXML
  private FxButton contextButton;
  @FXML
  private FxRadioButton allowButton;
  @FXML
  private FxRadioButton denyButton;
  @FXML
  private FxGridPane permissionPane;
  @FXML
  private FxTextArea secMessageField;


  private Class<?> clazz;
  private Map<Permission, FxCheckBox> permissionCheckBoxes;
  private Map<Permission, FxRadioButton> permissionRadioButtons;



  @FXML
  @SuppressWarnings("unchecked")
  private void initialize() {
    secGranteeField.setChangeable(false);
    granteeButton.setOnAction(e -> selectGrantee());
    allowButton.setBindable(false);
    denyButton.setBindable(false);
    ToggleGroup group = new ToggleGroup();
    allowButton.setToggleGroup(group);
    allowButton.getStyleClass().add(ALLOW_STYLE);
    denyButton.setToggleGroup(group);
    denyButton.getStyleClass().add(DENY_STYLE);
    group.selectedToggleProperty().addListener(obs -> {
      if (group.getSelectedToggle() == allowButton) {
        sec.setAllowed(true);
      }
      else if (group.getSelectedToggle() == denyButton) {
        sec.setAllowed(false);
      }
      updateEffectivePermissions();
    });
    permissionCheckBoxes = new TreeMap<>();   // sort by name
    permissionRadioButtons = new HashMap<>();
    for (Class<? extends Permission> permissionIf: SecurityFactory.getInstance().getPermissionInterfaces()) {
      Permission permission = SecurityFactory.getInstance().getPermission(permissionIf);
      FxCheckBox checkBox = Fx.createNode(CheckBox.class);
      checkBox.setText(permission.getName());
      checkBox.setBindable(false);
      checkBox.addViewToModelListener(() -> {
        permissionsToModel();
        updateEffectivePermissions();
      });
      permissionCheckBoxes.put(permission, checkBox);
      FxRadioButton effectiveRadioButton = Fx.createNode(RadioButton.class);
      effectiveRadioButton.setBindable(false);
      effectiveRadioButton.setChangeable(false);
      permissionRadioButtons.put(permission, effectiveRadioButton);
    }
    int row = 1;
    for (Map.Entry<Permission,FxCheckBox> entry: permissionCheckBoxes.entrySet()) {
      permissionPane.add(entry.getValue(), 0, row);
      permissionPane.add(permissionRadioButtons.get(entry.getKey()), 1, row);
      row++;
    }

    if (!SecurityDialogFactory.getInstance().isDomainContextUsed()) {
      contextButton.setDisable(true);
      secDomainContextObjectField.setDisable(true);
    }
    else {
      contextButton.setOnAction(e -> selectContext());
    }
  }

  @Override
  public Security getPdo() {
    return sec;
  }

  @Override
  public void setPdo(Security pdo) {
    this.sec = pdo;
    getBinder().putBindingProperty(DomainContext.class, pdo.getDomainContext());
    if (pdo.isAllowed()) {
      allowButton.setSelected(true);
    }
    else {
      denyButton.setSelected(true);
    }
    for (FxCheckBox checkBox: permissionCheckBoxes.values()) {
      checkBox.setSelected(false);
    }
    Class<?>[] permissions = SecurityFactory.getInstance().stringToPermissionInterfaces(pdo.getPermissions());
    for (Class<?> permission : permissions) {
      @SuppressWarnings("unchecked")
      FxCheckBox box = permissionCheckBoxes.get(SecurityFactory.getInstance().getPermission((Class<Permission>) permission));
      if (box != null) {
        box.setSelected(true);
      }
    }
    getContainer().updateView();
    updateEffectivePermissions();
  }

  /**
   * Sets the securable class.
   *
   * @param clazz the securable class
   */
  public void setSecurableClass(Class<?> clazz) {
    this.clazz = clazz;
    for (Map.Entry<Permission,FxCheckBox> entry: permissionCheckBoxes.entrySet()) {
      Permission permission = entry.getKey();
      FxCheckBox box = entry.getValue();
      boolean changeable = permission.appliesTo(clazz);
      box.setChangeable(changeable);
      if (changeable) {
        box.getStyleClass().remove(NA_STYLE);
      }
      else if (!box.getStyleClass().contains(NA_STYLE)) {
        box.getStyleClass().add(NA_STYLE);
      }
    }
  }

  @Override
  public void requestInitialFocus() {
    allowButton.requestFocus();
  }


  /**
   * Sets the changeability of the editor.
   *
   * @param changeable true if changeable
   */
  @Override
  public void setChangeable(boolean changeable) {
    super.setChangeable(changeable);
    granteeButton.setDisable(!changeable);
  }


  private void selectGrantee() {
    SecurityDialogFactory.getInstance().selectGrantee(getStage(), getDomainContext(), grantee -> {
      sec.setGrantee(grantee);
      secGranteeField.updateView();
      getContainer().getDelegate().fireViewToModelListeners();  // SecurityRulesView listens
    });
  }

  private void selectContext() {
    SecurityDialogFactory.getInstance().selectDomainContextObject(getStage(), getDomainContext(), contextPdo -> {
      sec.setDomainContextObject(contextPdo);
      secDomainContextObjectField.updateView();
      getContainer().getDelegate().fireViewToModelListeners();  // SecurityRulesView listens
    });
  }


  private void updateEffectivePermissions() {
    // map to effective permissions
    Class<?>[] permissions = SecurityFactory.getInstance().stringToPermissionInterfaces(sec.getPermissions());
    for (Map.Entry<Permission, FxRadioButton> entry: permissionRadioButtons.entrySet()) {
      Permission permission = entry.getKey();
      FxRadioButton effectiveRadioButton = entry.getValue();
      if (sec.isAllowed()) {
        if (!effectiveRadioButton.getStyleClass().contains(ALLOW_STYLE)) {
          effectiveRadioButton.getStyleClass().add(ALLOW_STYLE);
        }
        effectiveRadioButton.getStyleClass().remove(DENY_STYLE);
        effectiveRadioButton.setSelected(
                permissions != null &&
                permission.appliesTo(clazz) &&
                permission.isAllowedBy(permissions));
      }
      else {
        if (!effectiveRadioButton.getStyleClass().contains(DENY_STYLE)) {
          effectiveRadioButton.getStyleClass().add(DENY_STYLE);
        }
        effectiveRadioButton.getStyleClass().remove(ALLOW_STYLE);
        effectiveRadioButton.setSelected(
                permissions != null &&
                permission.appliesTo(clazz) &&
                permission.isDeniedBy(permissions));
      }
    }
  }

  private void permissionsToModel() {
    Set<Permission> permissions = new LinkedHashSet<>();
    for (Map.Entry<Permission,FxCheckBox> entry: permissionCheckBoxes.entrySet()) {
      if (entry.getValue().isSelected()) {
        permissions.add(entry.getKey());
      }
    }
    sec.setPermissions(permissions.isEmpty() ? null : SecurityFactory.getInstance().permissionsToString(permissions));
  }

}

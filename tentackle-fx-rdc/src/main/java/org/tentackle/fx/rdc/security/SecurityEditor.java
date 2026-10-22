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
import javafx.scene.control.Tooltip;

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
import org.tentackle.security.permissions.AllPermission;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Security permissions editor.<br>
 * Edits a single security rule: the grantee, the optional domain context object, whether the
 * permissions are allowed or denied, the permissions, and the message.
 * For each permission applying to the securable class, the editor shows whether it is
 * effectively granted or denied, including the permissions implied by others.
 * Permissions implied by other selected permissions are not stored in the rule.
 *
 * @author harald
 */
@FxControllerService
public class SecurityEditor extends PdoEditor<Security> {

  /**
   * CSS style for "denied".
   */
  public static final String DENY_STYLE = "sec-deny";

  /**
   * CSS style for "allowed".
   */
  public static final String ALLOW_STYLE = "sec-allow";

  /**
   * CSS style for "not applicable".
   */
  public static final String NA_STYLE = "sec-na";


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


  private Class<?> clazz;     // the securable class

  // for _all_ permissions! (setSecurableClass selects a subset for the view)
  private Map<Permission, FxCheckBox> permissionCheckBoxes;
  private Map<Permission, FxRadioButton> permissionRadioButtons;

  private Set<Permission> inheritedPermissions;   // selected permissions that are inherited by other selected permissions


  /**
   * Creates the security editor.
   */
  public SecurityEditor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @FXML
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
    allowButton.addViewToModelListener(this::handleSelectionEvent);
    denyButton.addViewToModelListener(this::handleSelectionEvent);

    permissionCheckBoxes = new TreeMap<>();   // sort by name
    permissionRadioButtons = new HashMap<>();
    inheritedPermissions = new HashSet<>();

    for (Class<? extends Permission> permissionIf: SecurityFactory.getInstance().getPermissionInterfaces()) {
      Permission permission = SecurityFactory.getInstance().getPermission(permissionIf);
      FxCheckBox checkBox = Fx.create(CheckBox.class);
      String description = permission.getDescription();
      if (description != null) {
        checkBox.setTooltip(new Tooltip(description));
      }
      checkBox.setText(permission.getName());
      checkBox.setBindable(false);
      checkBox.addViewToModelListener(this::handleSelectionEvent);
      permissionCheckBoxes.put(permission, checkBox);
      FxRadioButton effectiveRadioButton = Fx.create(RadioButton.class);
      effectiveRadioButton.setBindable(false);
      effectiveRadioButton.setChangeable(false);
      permissionRadioButtons.put(permission, effectiveRadioButton);
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
   * Sets the securable class.<br>
   * Adds all permissions applying to the class to the view.
   *
   * @param clazz the securable class
   */
  public void setSecurableClass(Class<?> clazz) {
    this.clazz = clazz;

    permissionPane.getChildren().remove(2, permissionPane.getChildren().size());
    int row = 1;
    for (Map.Entry<Permission, FxCheckBox> entry : permissionCheckBoxes.entrySet()) {
      Permission permission = entry.getKey();
      if (permission.appliesTo(clazz)) {
        permissionPane.add(entry.getValue(), 0, row);
        permissionPane.add(permissionRadioButtons.get(permission), 1, row);
        row++;
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

  private void handleSelectionEvent() {
    updateEffectivePermissions();
    permissionsToModel();
  }

  private void updateEffectivePermissions() {
    inheritedPermissions.clear();

    Set<Class<?>> selectedPermissions = new HashSet<>();
    for (Map.Entry<Permission, FxCheckBox> entry: permissionCheckBoxes.entrySet()) {
      if (entry.getValue().isSelected() && entry.getKey().appliesTo(clazz)) {
        selectedPermissions.add(entry.getKey().getPermissionInterface());
      }
    }
    Class<?>[] permissions = selectedPermissions.toArray(new Class<?>[0]);

    for (Map.Entry<Permission, FxRadioButton> entry: permissionRadioButtons.entrySet()) {
      Permission permission = entry.getKey();
      if (permission.appliesTo(clazz)) {
        Class<?> permissionIf = permission.getPermissionInterface();
        FxRadioButton effectiveRadioButton = entry.getValue();
        FxCheckBox box = permissionCheckBoxes.get(permission);
        boolean appliesEffectively;
        boolean inherited = false;
        boolean isAllPermission = permission instanceof AllPermission;
        if (sec.isAllowed()) {
          if (!effectiveRadioButton.getStyleClass().contains(ALLOW_STYLE)) {
            effectiveRadioButton.getStyleClass().add(ALLOW_STYLE);
          }
          effectiveRadioButton.getStyleClass().remove(DENY_STYLE);
          appliesEffectively = permission.appliesTo(clazz) && permission.isAllowedBy(permissions) &&
                               (!isAllPermission || box.isSelected());
          if (appliesEffectively && !isAllPermission) {
            for (Class<?> iFace : permissions) {
              if (AllPermission.class.isAssignableFrom(iFace) ||
                  permissionIf != iFace && permissionIf.isAssignableFrom(iFace)) {
                inherited = true;
                break;
              }
            }
          }
        }
        else {
          if (!effectiveRadioButton.getStyleClass().contains(DENY_STYLE)) {
            effectiveRadioButton.getStyleClass().add(DENY_STYLE);
          }
          effectiveRadioButton.getStyleClass().remove(ALLOW_STYLE);
          appliesEffectively = permission.appliesTo(clazz) && permission.isDeniedBy(permissions) &&
                               (!isAllPermission || box.isSelected());
          if (appliesEffectively && !isAllPermission) {
            for (Class<?> iFace : permissions) {
              if (AllPermission.class.isAssignableFrom(iFace) ||
                  permissionIf != iFace && iFace.isAssignableFrom(permissionIf)) {
                inherited = true;
                break;
              }
            }
          }
        }
        effectiveRadioButton.setSelected(appliesEffectively);

        if (inherited) {
          if (!isAllPermission) {
            inheritedPermissions.add(permission);
          }
          if (!box.getStyleClass().contains(NA_STYLE)) {
            box.getStyleClass().add(NA_STYLE);
          }
        }
        else {
          box.getStyleClass().remove(NA_STYLE);
        }
      }
    }
  }

  private void permissionsToModel() {
    Set<Permission> permissions = new LinkedHashSet<>();
    for (Map.Entry<Permission, FxCheckBox> entry : permissionCheckBoxes.entrySet()) {
      Permission permission = entry.getKey();
      if (entry.getValue().isSelected() && permission.appliesTo(clazz) && !inheritedPermissions.contains(permission)) {
        permissions.add(permission);
      }
    }
    sec.setPermissions(permissions.isEmpty() ? null : SecurityFactory.getInstance().permissionsToString(permissions));
  }

}

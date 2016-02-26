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
 */

package org.tentackle.swing.rdc;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityFactory;
import org.tentackle.swing.FormCheckBox;
import org.tentackle.swing.FormComponentPanel;

/**
 * An editor for {@link Permission}s.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class PermissionEditor extends FormComponentPanel implements KeyListener {

  private final Map<Permission,FormCheckBox> boxMap;    // map of <permission:checkbox>
  private FormCheckBox firstBox;                        // the first box gets the initial focus
  private String oldPermissions;                        // the old permissions if ESC hit

  /**
   * Creates a permission editor.
   */
  public PermissionEditor() {
    boxMap = new HashMap<>();
    setLayout(new GridBagLayout());
    int y = 0;
    for (Permission permission: SecurityFactory.getInstance().getPermissions()) {
      GridBagConstraints gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridx = 0;
      gridBagConstraints.gridy = y++;
      gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
      FormCheckBox box = new FormCheckBox(permission.getName());
      box.addKeyListener(this);
      add(box, gridBagConstraints);
      if (firstBox == null) {
        firstBox = box;
      }
      boxMap.put(permission, box);
    }
  }

  /**
   * Applies the protected class.
   * <p>
   * Disables all permissions no applicable to given class.
   *
   * @param clazz the protected class
   */
  public void applyClazz(Class<?> clazz) {
    for (Map.Entry<Permission,FormCheckBox> entry: boxMap.entrySet()) {
      entry.getValue().setVisible(entry.getKey().appliesTo(clazz));
    }
  }

  /**
   * Sets the permissions from a comma separated string.
   *
   * @param str the permissions string
   */
  public void setPermissions(String str) {
    // deselect all first
    for (FormCheckBox box: boxMap.values()) {
      box.setSelected(false);
    }
    for (Permission permission: SecurityFactory.getInstance().stringToPermissions(str)) {
      FormCheckBox box = boxMap.get(permission);
      if (box != null) {
        box.setSelected(true);
      }
    }
    if (firstBox != null) {
      firstBox.requestFocusLater();
    }
  }

  /**
   * Gets the selected permissions as a string.
   *
   * @return the permission string, null if no permissions selected at all
   */
  public String getPermissions() {
    Set<Permission> permissions = new LinkedHashSet<>();
    for (Map.Entry<Permission,FormCheckBox> entry: boxMap.entrySet()) {
      if (entry.getValue().isSelected()) {
        permissions.add(entry.getKey());
      }
    }
    return permissions.isEmpty() ? null : SecurityFactory.getInstance().permissionsToString(permissions);
  }

  @Override
  public void saveValue() {
    oldPermissions = getPermissions();
  }

  @Override
  public void keyTyped(KeyEvent e) {
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_TAB) {
      fireActionPerformed(null);
    }
    else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
      setPermissions(oldPermissions);
      fireActionPerformed(null);
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {
  }

}

/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.fx.rdc;


import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.tentackle.bind.Bindable;
import org.tentackle.common.Constants;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.component.FxCheckBox;
import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoMember;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * A default pdo editor.<br>
 * The view is generated from the PDO model via reflection.
 *
 * @param <T> the PDO type
 */
public class DefaultPdoEditor<T extends PersistentDomainObject<T>> extends PdoEditor<T> {

  private T pdo;                                  // the PDO being edited
  private final Map<PdoMember, Node> nodeMap;     // map of attributes to FX nodes


  /**
   * Creates the default editor from a pdo class.
   *
   * @param pdoClass the PDO class
   */
  public DefaultPdoEditor(Class<T> pdoClass) {
    nodeMap = createNodeMap(pdoClass);
    setView(createView(nodeMap));
  }


  @Override
  public void requestInitialFocus() {
    Node node = getFirstFocusableNode(nodeMap);
    if (node != null) {
      Platform.runLater(node::requestFocus);
    }
  }

  @Override
  public T getPdo() {
    return pdo;
  }

  @Override
  public void setPdo(T pdo) {
    this.pdo = pdo;
  }


  /**
   * Gets the first focusable node.
   *
   * @param nodes the nodes map
   * @return the node, null if none
   */
  protected Node getFirstFocusableNode(Map<PdoMember, Node> nodes) {
    Node node = null;
    // focus domain key
    for (Map.Entry<PdoMember, Node> entry: nodes.entrySet()) {
      if (entry.getKey().isDomainKey()) {
        node = entry.getValue();
        break;
      }
    }
    if (node == null) {
      // no domain key? take first editable node
      for (Node n : nodes.values()) {
        if (n instanceof FxComponent && ((FxComponent) n).isChangeable()) {
          node = n;
          break;
        }
      }
    }
    return node;
  }

  /**
   * Creates a map of attributes to nodes.
   *
   * @param pdoClass the PDO class
   * @return the node map
   */
  protected Map<PdoMember, Node> createNodeMap(Class<T> pdoClass) {
    Map<PdoMember, Node> map = new LinkedHashMap<>();
    for (PdoMember attribute: PdoUtilities.getInstance().getAttributes(pdoClass, true)) {
      Bindable bindable = attribute.getGetter().getAnnotation(Bindable.class);
      String bindingOptions = bindable == null ? "" : bindable.options();
      Class<?> type = attribute.getGetter().getReturnType();
      Node node;
      if (attribute.getType() == PdoMember.Type.RELATION) {
        // non-component link to object relation: check cache to decide which type of node we should use
        PersistentDomainObject pdo = Pdo.create((Class<PersistentDomainObject>) type);
        PdoCache cache = pdo.getCache();
        if (cache != null && cache.isPreloading()) {
          node = Fx.createNode(ComboBox.class);
        }
        else {
          node = Fx.createNode(TextField.class);
        }
      }
      else {
        if (Boolean.class.equals(type) || Boolean.TYPE.equals(type)) {
          node = Fx.createNode(FxCheckBox.class);
        }
        else if (Enum.class.isAssignableFrom(type)) {
          node = Fx.createNode(FxChoiceBox.class);
        }
        else {
          int ndx = bindingOptions.indexOf(Constants.BIND_LINES + "=");
          node = ndx >= 0 ? Fx.createNode(TextArea.class) : Fx.createNode(TextField.class);
        }
      }
      map.put(attribute, node);
    }
    return map;
  }

  /**
   * Creates a view from the nodes map.
   *
   * @param nodes the nodes map
   * @return the view
   */
  protected Parent createView(Map<PdoMember, Node> nodes) {
    GridPane grid = Fx.createNode(GridPane.class);
    grid.setVgap(5.0);
    grid.setHgap(5.0);



    return grid;
  }
}

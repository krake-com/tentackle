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

package org.tentackle.fx.rdc;

import org.tentackle.bind.Bindable;
import org.tentackle.bind.BindableElement;
import org.tentackle.bind.BindingMember;
import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.bind.FxBindingFactory;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoMember;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The default PDO editor.<br>
 * The view is generated from the PDO model via reflection: a two-column grid with a label and
 * a bound control for each bindable attribute.
 * The labels are taken from the resource bundle, if provided, else from the attribute comments of the model.
 *
 * @param <T> the PDO type
 */
public class DefaultPdoEditor<T extends PersistentDomainObject<T>> extends PdoEditor<T> {

  private final Class<T> pdoClass;                      // the PDO class
  private final ResourceBundle bundle;                  // optional bundle

  private final Map<PdoMember, FxComponent> nodeMap;    // map of attributes to FX nodes
  private final BindingMember pdoMember;                // the PDO parent binding member
  private final BindingMember[] parents;

  private T pdo;                                        // the PDO being edited
  private static final String PDO = "pdo";              // ... and its variable name

  /**
   * Creates the default editor from a PDO class.
   *
   * @param pdoClass the PDO class
   * @param bundle the optional resource bundle
   */
  public DefaultPdoEditor(Class<T> pdoClass, ResourceBundle bundle) {
    this.pdoClass = pdoClass;
    this.bundle = bundle;

    BindableElement pdoElement = FxBindingFactory.getInstance().createBindableElement(pdoClass, PDO);
    try {
      Field field = DefaultPdoEditor.class.getDeclaredField(PDO);   // fixed class, in case overridden
      field.setAccessible(true);
      pdoElement.setField(field);
    }
    catch (NoSuchFieldException e) {
      throw new RdcRuntimeException("cannot determine parent field", e);
    }
    pdoMember = FxBindingFactory.getInstance().createBindingMember(DefaultPdoEditor.class, null, PDO, PDO, pdoElement);
    parents = new BindingMember[] {pdoMember};
    nodeMap = createNodeMap();
    setView(createView(nodeMap));
  }

  /**
   * Gets the PDO class.
   *
   * @return the PDO class
   */
  public Class<T> getPdoClass() {
    return pdoClass;
  }

  /**
   * Gets the optional resource bundle.
   *
   * @return null if none
   */
  public ResourceBundle getBundle() {
    return bundle;
  }

  @Override
  public void requestInitialFocus() {
    FxComponent component = getFirstFocusableNode(nodeMap);
    if (component instanceof Node node) {
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
    getBinder().putBindingProperty(DomainContext.class, pdo.getDomainContext());
  }

  @Override
  public String getValidationPath() {
    return PDO;
  }

  /**
   * Gets the first focusable node.<br>
   * This is the first changeable domain key attribute, if any, else the first changeable node.
   *
   * @param nodes the nodes map
   * @return the node, null if none
   */
  protected FxComponent getFirstFocusableNode(Map<PdoMember, FxComponent> nodes) {
    FxComponent node = null;
    // focus changeable domain key
    for (Map.Entry<PdoMember, FxComponent> entry: nodes.entrySet()) {
      if (entry.getKey().isDomainKey() && entry.getValue().isChangeable()) {
        node = entry.getValue();
        break;
      }
    }
    if (node == null) {
      // no changeable domain key? take first changeable node
      for (FxComponent comp : nodes.values()) {
        if (comp.isChangeable()) {
          node = comp;
          break;
        }
      }
    }
    return node;
  }


  /**
   * Creates an FX node for a given PDO attribute.<br>
   * Attributes must be bindable. The node type depends on the attribute type:
   * <ul>
   *   <li>a {@code ComboBox} for relations to PDOs with a preloading cache, else a {@code TextField}</li>
   *   <li>a {@code CheckBox} for booleans</li>
   *   <li>a {@code ChoiceBox} for enums</li>
   *   <li>a {@code TextArea} if the binding options contain {@code LINES=}, else a {@code TextField}</li>
   * </ul>
   *
   * @param attribute the PDO attribute
   * @return the node, null to skip the attribute
   * @see Bindable
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected FxComponent createNode(PdoMember attribute) {
    FxComponent node = null;
    Bindable bindable = attribute.getGetter().getAnnotation(Bindable.class);
    if (bindable != null) {
      String bindingOptions = bindable.options();
      Class<?> type = attribute.getGetter().getReturnType();
      if (PersistentDomainObject.class.isAssignableFrom(type)) {
        // non-component link to object relation: check cache to decide which type of node we should use
        PersistentDomainObject object = Pdo.create((Class<PersistentDomainObject>) type);
        PdoCache cache = object.getCache();
        if (cache != null && cache.isPreloading()) {
          node = Fx.create(ComboBox.class);
        }
        else {
          node = Fx.create(TextField.class);
        }
      }
      else {
        if (Boolean.class.equals(type) || Boolean.TYPE.equals(type)) {
          node = Fx.create(CheckBox.class);
        }
        else if (Enum.class.isAssignableFrom(type)) {
          node = Fx.create(ChoiceBox.class);
        }
        else {
          int ndx = bindingOptions.indexOf(Constants.BIND_LINES + "=");
          node = ndx >= 0 ? Fx.create(TextArea.class) : Fx.create(TextField.class);
        }
      }
      bind(pdoMember, attribute, node, bindingOptions);
    }

    return node;
  }

  /**
   * Binds the node to the PDO member.<br>
   * Sets the number of visible columns according to the model, unless already set via the {@code COLS=} option.
   *
   * @param parent the PDO parent binding member
   * @param attribute the PDO attribute
   * @param node the FX node
   * @param bindingOptions the binding options
   */
  protected void bind(BindingMember parent, PdoMember attribute, FxComponent node, String bindingOptions) {

    BindableElement element = FxBindingFactory.getInstance().createBindableElement(
        attribute.getGetter().getReturnType(), PDO + StringHelper.firstToUpper(attribute.getName()));

    element.setGetter(attribute.getGetter());
    element.setSetter(attribute.getSetter());

    BindingMember member = FxBindingFactory.getInstance().createBindingMember(
        pdoClass, parent, attribute.getName(), PDO + "." + attribute.getName(), element);

    FxComponentBinding binding = FxBindingFactory.getInstance().createComponentBinding(
        getBinder(), parents, member, node, bindingOptions);

    getBinder().addBinding(binding);

    node.setBindingPath(member.getMemberPath());
    node.setComponentPath(getClass().getName() + "." + attribute.getName());

    if (FxUtilities.getInstance().getBindingOption(bindingOptions, Constants.BIND_COLS) == null) {
      // if not already set via COLS= in model options
      if (node instanceof FxTextField textField) {
        if (textField.getMaxColumns() > 0) {
          textField.setColumns(textField.getMaxColumns());
        }
      }
      else if (node instanceof FxTextArea textArea) {
        textArea.setColumns(FxUtilities.getInstance().getDefaultTextAreaColumns(textArea));
      }
    }
  }


  /**
   * Creates a map of attributes to nodes.<br>
   * Throws {@link RdcRuntimeException} if no bindable attributes were found.
   *
   * @return the node map
   */
  protected Map<PdoMember, FxComponent> createNodeMap() {
    Map<PdoMember, FxComponent> map = new LinkedHashMap<>();
    PdoUtilities.getInstance().getAttributes(getPdoClass(), true)
                              .forEach(attr -> {
                                FxComponent node = createNode(attr);
                                if (node != null) {
                                  map.put(attr, node);
                                }
                              });
    if (map.isEmpty()) {
      throw new RdcRuntimeException("no visible attributes found for " + getPdoClass().getName());
    }
    return map;
  }

  /**
   * Creates a view from the nodes map.
   *
   * @param nodes the nodes map
   * @return the view
   */
  protected Parent createView(Map<PdoMember, FxComponent> nodes) {
    GridPane grid = Fx.create(GridPane.class);
    grid.setVgap(5.0);
    grid.setHgap(5.0);

    int row = 0;
    for (Map.Entry<PdoMember, FxComponent> entry: nodes.entrySet()) {

      Label label = createLabel(entry.getKey());
      grid.add(label, 0, row);
      GridPane.setHalignment(label, HPos.RIGHT);

      Node node = (Node) entry.getValue();
      grid.add(node, 1, row);
      GridPane.setHalignment(node, HPos.LEFT);
      GridPane.setFillWidth(node, false);

      ++row;
    }

    return grid;
  }

  /**
   * Creates the label.
   *
   * @param attribute the PDO member
   * @return the label node
   */
  @SuppressWarnings("bundle")
  protected Label createLabel(PdoMember attribute) {
    String text;
    if (getBundle() != null) {
      // take the short member name for bundle keys (shared with table column header)
      text = getBundle().getString(attribute.getName());
    }
    else {
      text = attribute.getComment();
    }
    if (!text.endsWith(":")) {
      text += ":";
    }
    Label label = Fx.create(Label.class);
    label.setText(text);
    return label;
  }
}

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

import org.tentackle.bind.BindingVetoException;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.log.Logger;
import org.tentackle.validate.ValidationFailedException;
import org.tentackle.validate.ValidationResult;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.PopupWindow;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

/**
 * Delegate implementing {@link FxComponent}.<br>
 * Holds the state and behavior common to all components, such as binding,
 * mandatory, error and info handling, and view-modified tracking.
 *
 * @author harald
 */
public abstract class FxComponentDelegate extends FxControlDelegate implements FxComponent {

  /**
   * Mandatory CSS style class.
   */
  public static final String MANDATORY_STYLE = "tt-mandatory";

  /**
   * Error CSS style class.
   */
  public static final String ERROR_STYLE = "tt-error";

  /**
   * Info CSS style class.
   */
  public static final String INFO_STYLE = "tt-info";


  private static final Logger LOGGER = Logger.get(FxComponentDelegate.class);

  /**
   * The type handled by the component.
   */
  private Class<?> type;

  /**
   * The optional generic type.
   */
  private Type genericType;

  /**
   * The value translator.
   */
  private ValueTranslator<?,?> valueTranslator;


  /**
   * Mandatory property.
   */
  private boolean mandatory;
  private BooleanProperty mandatoryProperty;

  /**
   * The field path to bind, null if auto-binding.
   */
  private String bindingPath;

  /**
   * The current binding, null if none.
   */
  private FxComponentBinding binding;

  /**
   * The component's declaration path.
   */
  private String componentPath;

  /**
   * The saved view value.
   */
  private Object savedViewObject;

  /**
   * True if saveView() invoked at least once.
   */
  private boolean savedViewObjectValid;

  /**
   * The last set view value.
   */
  private Object lastViewObject;

  /**
   * The error message if conversion failed.<br>
   * This message is shown to the user in a tooltip.
   */
  private String errorMessage;

  /**
   * True if temporary error.
   */
  private boolean errorTemporary;

  /**
   * The error popup shown.<br>
   * null if not shown.
   */
  private PopupWindow errorPopup;

  /**
   * The info message.<br>
   * This message is shown to the user in a tooltip.
   */
  private String infoMessage;

  /**
   * The info popup shown.<br>
   * null if not shown.
   */
  private PopupWindow infoPopup;

  /**
   * True if the model was updated.<br>
   * Will be cleared if the view is updated again.
   */
  private boolean modelUpdated;

  /**
   * Flag to update model only once in recursive invocations.
   */
  private boolean updatingModel;

  /**
   * Flag to update view only once in recursive invocations.
   */
  private boolean updatingView;

  /**
   * The table cell, if this is used as a cell editor in table.
   */
  private FxTableCell<?,?> tableCell;

  /**
   * The tree-table cell, if this is used as a cell editor in a tree-table.
   */
  private FxTreeTableCell<?,?> treeTableCell;

  /**
   * True to suppress the invocation of listeners if the model did not change.
   */
  private boolean listenerSuppressedIfModelUnchanged;

  /**
   * True to suppress the invocation of listeners if the view will not change at all.
   */
  private boolean listenerSuppressedIfViewUnchanged;


  /**
   * Creates a component delegate.
   */
  public FxComponentDelegate() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public FxComponentDelegate getDelegate() {
    return this;
  }

  /**
   * Gets the component of this delegate.
   *
   * @return the component
   */
  public abstract FxComponent getComponent();


  /**
   * Gets the component as a node.
   *
   * @return the node
   */
  public Node getNode() {
    return (Node) getComponent();
  }


  @Override
  public FxContainer getParentContainer() {
    Parent parent = ((Node) getComponent()).getParent();
    return parent instanceof FxContainer ? (FxContainer) parent : null;
  }

  @Override
  public String toGenericString() {
    StringBuilder buf = new StringBuilder();
    buf.append("component ").
        append(getComponent().getClass().getName());
    String id = ((Node) getComponent()).getId();
    if (id != null) {
      buf.append('[').append(id).append(']');
    }
    return buf.toString();
  }

  @Override
  public String toString() {
    return "delegate for " + toGenericString();
  }


  @Override
  protected void updateChangeable(boolean changeable) {
    // the default for components is to use the disabled property
    getNode().setDisable(!changeable);
    // non-changeable components should not be focus traversable
    getNode().setFocusTraversable(changeable);
    // mandatory is only shown if component is changeable as well
    updateMandatoryStyle(isMandatory());
    updateInfoStyle(changeable && infoMessage != null);
    updateErrorStyle(changeable && errorMessage != null);
  }

  @Override
  protected ReadOnlyBooleanWrapper createChangeableProperty(boolean changeable) {
    ReadOnlyBooleanWrapper changeableProperty =  new ReadOnlyBooleanWrapper(changeable);
    // if the disabled property changes, change the controlChangeable property as well
    changeableProperty.bind(getNode().disabledProperty().not());
    return changeableProperty;
  }

  @Override
  public void setContainerChangeable(boolean containerChangeable) {
    if (!isContainerChangeableIgnored()) {
      // don't invoke updateChangeable() as this would overwrite local controlChangeable!
      getNode().setDisable(!containerChangeable || !isControlChangeable());
      // mandatory is only shown if component is changeable as well
      updateMandatoryStyle(isMandatory());
      updateInfoStyle(containerChangeable && infoMessage != null);
      updateErrorStyle(containerChangeable && errorMessage != null);
    }
  }

  @Override
  public void setValueTranslator(ValueTranslator<?,?> valueTranslator) {
    if (this.valueTranslator != null) {
      throw new FxRuntimeException("value translator already set");
    }
    this.valueTranslator = valueTranslator;
  }

  @Override
  public ValueTranslator<?,?> getValueTranslator() {
    return valueTranslator;
  }


  /**
   * Returns a flag to avoid recursive invocations of {@link #updateModel()}.
   *
   * @return true if currently updating the model
   */
  protected boolean isUpdatingModel() {
    return updatingModel;
  }

  /**
   * Returns a flag to avoid recursive invocations of {@link #updateView()}.
   *
   * @return true if currently updating the view
   */
  protected boolean isUpdatingView() {
    return updatingView;
  }

  @Override
  public void updateView() {
    if (!updatingView) {
      try {
        updatingView = true;
        modelUpdated = false;
        if (binding != null) {
          boolean fireListeners = true;
          Object modelValue = binding.getModelValue();
          if (isListenerSuppressedIfViewUnchanged() && Objects.equals(binding.getViewValue(), modelValue)) {
            fireListeners = false;
          }
          if (fireListeners) {
            fireModelToViewListeners();
          }
          binding.setViewValue(modelValue);
        }
        else {
          fireModelToViewListeners();
        }
      }
      finally {
        updatingView = false;
      }
    }
  }

  @Override
  public void updateModel() {
    if (!updatingModel) {
      try {
        updatingModel = true;
        if (!getNode().isDisabled()) {
          try {
            String oldError = getError();
            if (oldError != null && isErrorTemporary()) {
              setError(null);
            }
            boolean fireListeners = true;
            if (binding != null) {
              Object viewValue = binding.getViewValue();
              if (isListenerSuppressedIfModelUnchanged() && Objects.equals(binding.getModelValue(), viewValue)) {
                fireListeners = false;
              }
              binding.setModelValue(viewValue);
              List<ValidationResult> results = binding.validate();
              if (!results.isEmpty()) {
                throw new ValidationFailedException(results);
              }
            }
            if (fireListeners) {
              fireViewToModelListeners();
            }
            modelUpdated = true;
            if (getError() != null && !getError().equals(oldError)) {
              handleInputFailure(getError());
            }
          }
          catch (RuntimeException rex) {
            if (getError() == null) {
              ValidationFailedException vx = ExceptionHelper.extractException(ValidationFailedException.class, true, rex);
              String msg;
              if (vx == null) {
                BindingVetoException bve = ExceptionHelper.extractException(BindingVetoException.class, true, rex);
                if (bve == null) {    // extended component log only if not a rethrown veto exception
                  LOGGER.severe("update model failed for " + FxUtilities.getInstance().dumpComponentHierarchy(getNode()), rex);
                }
                msg = rex.getLocalizedMessage();
                if (msg == null) {
                  msg = rex.getClass().getName();
                }
              }
              else {
                msg = vx.resultsAsMessage();
              }
              // some conversion/mapping failed (usually parse-exception):
              // -> show error popup and stay in component
              handleInputFailure(msg);
            }
          }
          triggerViewModified();
        }
      }
      finally {
        updatingModel = false;
      }
    }
  }

  /**
   * Handles a failure during conversion of the user's input to the model.
   *
   * @param msg the failure message
   */
  protected void handleInputFailure(String msg) {
    setError(msg);
    setErrorTemporary(true);
    Scene scene = getNode().getScene();
    if (scene != null) {
      Node node = scene.getFocusOwner();
      if (node != getNode() &&
          (!(node instanceof FxComponent) || ((FxComponent) node).getError() == null)) {
        // only if other component currently holding the focus has no error (would loop otherwise)
        Platform.runLater(((Node) getComponent())::requestFocus);
      }
    }
  }

  @Override
  public boolean isModelUpdated() {
    return modelUpdated;
  }

  @Override
  public void showErrorPopup() {
    hideErrorPopup();
    errorPopup = FxUtilities.getInstance().showErrorPopup(getComponent());
  }

  @Override
  public void hideErrorPopup() {
    if (errorPopup != null) {
      errorPopup.hide();
      errorPopup = null;
    }
  }

  @Override
  public String getError() {
    return errorMessage;
  }

  @Override
  public void setError(String errorMessage) {
    boolean changed = !Objects.equals(this.errorMessage, errorMessage);
    this.errorMessage = errorMessage;
    if (changed) {
      updateErrorStyle(errorMessage != null && isChangeable());
    }
    if (errorMessage == null) {
      errorTemporary = false;
    }
  }

  @Override
  public void setErrorTemporary(boolean errorTemporary) {
    this.errorTemporary = errorTemporary;
  }

  @Override
  public boolean isErrorTemporary() {
    return errorTemporary;
  }

  /**
   * Updates the error style and shows/hides the popup if component has focus.
   *
   * @param enable true to enable style
   */
  protected void updateErrorStyle(boolean enable) {
    if (!enable) {
      getNode().getStyleClass().remove(ERROR_STYLE);
    }
    else if (!getNode().getStyleClass().contains(ERROR_STYLE)) {
      getNode().getStyleClass().add(ERROR_STYLE);
    }
    if (!enable || getNode().isFocused()) {
      showErrorPopup();   // show/hide error popup, node has focus (see ComponentConfigurator)
    }
  }


  @Override
  public void showInfoPopup() {
    hideInfoPopup();
    infoPopup = FxUtilities.getInstance().showInfoPopup(getComponent());
  }

  @Override
  public void hideInfoPopup() {
    if (infoPopup != null) {
      infoPopup.hide();
      infoPopup = null;
    }
  }

  @Override
  public String getInfo() {
    return infoMessage;
  }

  @Override
  public void setInfo(String infoMessage) {
    boolean changed = !Objects.equals(this.infoMessage, infoMessage);
    this.infoMessage = infoMessage;
    if (changed) {
      updateInfoStyle(infoMessage != null && isChangeable());
    }
  }

  /**
   * Updates the info style and shows/hides the popup if component has focus.
   *
   * @param enable true to enable style
   */
  protected void updateInfoStyle(boolean enable) {
    if (!enable) {
      getNode().getStyleClass().remove(INFO_STYLE);
    }
    else if (!getNode().getStyleClass().contains(INFO_STYLE)) {
      getNode().getStyleClass().add(INFO_STYLE);
    }
    if (!enable || getNode().isFocused()) {
      showInfoPopup();   // show/hide info popup, node has focus (see ComponentConfigurator)
    }
  }

  @Override
  public void triggerViewModified() {
    if (isSavedViewObjectValid()) {
      // application maintains a validation lifecycle
      boolean viewModified = !Objects.equals(getViewObject(), getSavedViewObject()) ||
                             (valueTranslator != null && valueTranslator.isMappingIncomplete() && valueTranslator.isModelModified());
      setViewModified(viewModified);
      if (getParentContainer() != null) {
        getParentContainer().triggerViewModified();
      }
      if (viewModified) {
        if(!isErrorTemporary()) {
          setError(null);   // error will be re-checked by application on operations like "save" or alike
        }
        setInfo(null);
      }
    }
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void setViewValue(Object value) {
    ValueTranslator translator = getValueTranslator();
    Object viewObject = translator.toView(value);
    setViewObject(viewObject);
    setLastViewObject(viewObject);
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <V> V getViewValue() {
    ValueTranslator translator = getValueTranslator();
    return (V) translator.toModel(getViewObject());
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void saveView() {
    savedViewObject = getViewObject();
    savedViewObjectValid = true;
    setViewModified(false);
    if (valueTranslator != null && valueTranslator.isMappingIncomplete()) {
      ((ValueTranslator) valueTranslator).saveModelValue(getBinding().getModelValue());
    }
  }

  @Override
  public void invalidateSavedView() {
    savedViewObjectValid = false;
  }

  @Override
  public boolean isSavedViewObjectValid() {
    return savedViewObjectValid;
  }

  @Override
  public Object getSavedViewObject() {
    if (!savedViewObjectValid) {
      throw new FxRuntimeException("saved value is invalid in " + getComponent());
    }
    return savedViewObject;
  }

  /**
   * Gets the last view object.
   * <p>
   * This is a hidden feature not exposed to the public API.
   *
   * @return the last view object converted from the model
   */
  public Object getLastViewObject() {
    return lastViewObject;
  }

  /**
   * Sets the last view object.<br>
   * The method is invoked from {@link #setViewValue(Object)} when updating from the model.
   * <p>
   * This is a hidden feature not exposed to the public API.
   *
   * @param lastViewObject the last view object
   */
  public void setLastViewObject(Object lastViewObject) {
    this.lastViewObject = lastViewObject;
  }

  @Override
  public void setType(Class<?> type) {
    if (this.type != null && this.type != type) {
      throw new FxRuntimeException("type already set to " + this.type + " (requested " + type + ") for " + getComponent());
    }
    this.type = type;
  }

  @Override
  public Class<?> getType() {
    return type;
  }

  @Override
  public void setGenericType(Type genericType) {
    this.genericType = genericType;
  }

  @Override
  public Type getGenericType() {
    return genericType;
  }

  @Override
  public void setMandatory(boolean mandatory) {
    if (mandatoryProperty != null) {
      mandatoryProperty.set(mandatory);
    }
    else {
      this.mandatory = mandatory;
      updateMandatoryStyle(mandatory);
    }
  }

  @Override
  public boolean isMandatory() {
    if (mandatoryProperty != null) {
      return mandatoryProperty.get();
    }
    return mandatory;
  }

  @Override
  public BooleanProperty mandatoryProperty() {
    if (mandatoryProperty == null) {
      mandatoryProperty = new SimpleBooleanProperty(mandatory);
      mandatoryProperty.addListener((obs, ov, nv) -> updateMandatoryStyle(nv));
    }
    return mandatoryProperty;
  }

  /**
   * Does the physical update of the style.<br>
   * See resources/org/tentackle/fx/tentackle.css.
   *
   * @param mandatory true if mandatory
   */
  protected void updateMandatoryStyle(boolean mandatory) {
    if (mandatory && !getNode().isDisabled()) {
      // node must be editable by the user to
      // show the mandatory color.
      if (!getNode().getStyleClass().contains(MANDATORY_STYLE)) {
        getNode().getStyleClass().add(MANDATORY_STYLE);
      }
    }
    else {
      getNode().getStyleClass().remove(MANDATORY_STYLE);
    }
  }


  @Override
  public void setBindingPath(String bindingPath) {
    this.bindingPath = bindingPath;
  }

  @Override
  public String getBindingPath() {
    return bindingPath;
  }

  @Override
  public void setComponentPath(String componentPath) {
    this.componentPath = componentPath;
  }

  @Override
  public String getComponentPath() {
    return componentPath;
  }

  @Override
  public void setBinding(FxComponentBinding binding) {
    this.binding = binding;
  }

  @Override
  public FxComponentBinding getBinding() {
    return binding;
  }

  @Override
  public FxTableCell<?, ?> getTableCell() {
    return tableCell;
  }

  @Override
  public void setTableCell(FxTableCell<?, ?> tableCell) {
    this.tableCell = tableCell;
  }

  @Override
  public FxTreeTableCell<?, ?> getTreeTableCell() {
    return treeTableCell;
  }

  @Override
  public void setTreeTableCell(FxTreeTableCell<?, ?> treeTableCell) {
    this.treeTableCell = treeTableCell;
  }

  @Override
  public boolean isListenerSuppressedIfModelUnchanged() {
    return listenerSuppressedIfModelUnchanged;
  }

  @Override
  public void setListenerSuppressedIfModelUnchanged(boolean listenerSuppressedIfModelUnchanged) {
    this.listenerSuppressedIfModelUnchanged = listenerSuppressedIfModelUnchanged;
  }

  @Override
  public boolean isListenerSuppressedIfViewUnchanged() {
    return listenerSuppressedIfViewUnchanged;
  }

  @Override
  public void setListenerSuppressedIfViewUnchanged(boolean listenerSuppressedIfViewUnchanged) {
    this.listenerSuppressedIfViewUnchanged = listenerSuppressedIfViewUnchanged;
  }
}

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
 *
 */

package org.tentackle.fx;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.PopupWindow;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.log.Logger;
import org.tentackle.validate.ValidationFailedException;
import org.tentackle.validate.ValidationResult;

/**
 * Delegate implementing FxComponent.
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
   * the field path to bind, null if autobinding
   */
  private String bindingPath;

  /**
   * the current binding, null if none.
   */
  private FxComponentBinding binding;

  /**
   * the component's declaration path.
   */
  private String componentPath;

  /**
   * The saved view value.
   */
  private Object savedObject;

  /**
   * True if saveView() invoked at least once.
   */
  private boolean savedObjectValid;

  /**
   * The error message if conversion failed.<br>
   * This message is shown to the user in a tooltip.
   */
  private String errorMessage;

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
   * true if model was updated.<br>
   * Will be cleared if view is updated again.
   */
  private boolean modelUpdated;



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


  @Override
  public void updateView() {
    modelUpdated = false;
    fireModelToViewListeners();
    if (binding != null) {
      binding.setViewValue(binding.getModelValue());
    }
  }

  @Override
  public void updateModel() {
    if (!getNode().isDisabled()) {
      setError(null);
      try {
        if (binding != null) {
          binding.setModelValue(binding.getViewValue());
          List<ValidationResult> results = binding.validate();
          if (results != null && !results.isEmpty()) {
            throw new ValidationFailedException(results);
          }
        }
        fireViewToModelListeners();
        modelUpdated = true;
      }
      catch (RuntimeException rex) {
        if (getError() == null) {
          ValidationFailedException vx = ExceptionHelper.extractException(ValidationFailedException.class, true, rex);
          String msg;
          if (vx == null) {
            LOGGER.severe("update model failed for " + FxUtilities.getInstance().dumpComponentHierarchy(getNode()), rex);
            msg = rex.getLocalizedMessage();
            if (msg == null) {
              msg = rex.getClass().getName();
            }
          }
          else {
            msg = vx.resultsToMessage();
          }
          // some conversion/mapping failed (usually parse-exception):
          // -> show error popup and stay in component
          setError(msg);
          Node node = getNode().getScene().getFocusOwner();
          if (node != getNode() &&
              (!(node instanceof FxComponent) || ((FxComponent) node).getError() == null)) {
            // only if other component currently holding the focus has no error (would loop otherwise)
            Platform.runLater(() -> ((Node) getComponent()).requestFocus());
          }
        }
      }
      triggerViewModified();
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
      showErrorPopup();   // show/hide info popup, node has focus (see ComponentConfigurator)
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
    if (savedObjectValid) {
      setViewModified(!Objects.equals(getViewObject(), getSavedViewObject()));
      if (getParentContainer() != null) {
        getParentContainer().triggerViewModified();
      }
    }
  }

  @Override
  public void saveView() {
    savedObject = getViewObject();
    savedObjectValid = true;
    setViewModified(false);
  }

  @Override
  public void invalidateSavedView() {
    savedObjectValid = false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object getSavedViewObject() {
    if (!savedObjectValid) {
      throw new FxRuntimeException("saved value is invalid in " + getComponent());
    }
    return savedObject;
  }

  @Override
  public void setType(Class<?> type) {
    if (this.type != null) {
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
   * Does the physical update of the style.
   * See resources/org/tentackle/fx/tentackle.css
   *
   * @param mandatory true if mandatory
   */
  protected void updateMandatoryStyle(boolean mandatory) {
    if (mandatory && !getNode().isDisabled()) {
      // node must be editable by the user to
      // show the mandator color.
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

}

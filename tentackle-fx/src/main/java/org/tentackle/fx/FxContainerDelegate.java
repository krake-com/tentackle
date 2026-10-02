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

import javafx.css.Styleable;
import javafx.scene.Node;
import javafx.scene.Parent;

import java.util.List;

/**
 * Delegate implementing {@link FxContainer}.<br>
 * Propagates the container operations, such as updating the view or model, saving the view,
 * changeability, and clearing errors, to the components of the container.
 *
 * @author harald
 */
public abstract class FxContainerDelegate extends FxControlDelegate implements FxContainer {

  private FxController controller;

  /**
   * Parent constructor.
   */
  public FxContainerDelegate() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the container of this delegate.
   *
   * @return the container
   */
  public abstract FxContainer getContainer();


  @Override
  public FxContainer getParentContainer() {
    Parent parent = getNode().getParent();
    while (parent != null) {
      if (parent instanceof FxContainer) {
        return (FxContainer) parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  @Override
  public FxController getController() {
    return controller;
  }

  @Override
  public void setController(FxController controller) {
    this.controller = controller;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <C extends FxController> C getController(Class<C> clazz) {
    FxContainer cont = getContainer();
    while (cont != null) {
      FxController cntrl = cont.getController();
      if (cntrl != null && clazz.isAssignableFrom(cntrl.getClass())) {
        return (C) cntrl;
      }
      cont = cont.getParentContainer();
    }
    return null;
  }


  @Override
  public String toGenericString() {
    StringBuilder buf = new StringBuilder();
    buf.append("container ").
        append(getContainer().getClass().getName());
    // not necessarily a node, e.g. FxTab
    String id = getContainer() instanceof Styleable styleable ? styleable.getId() : null;
    if (id != null) {
      buf.append('[').append(id).append(']');
    }
    return buf.toString();
  }

  @Override
  public String toString() {
    return "delegate for " + toGenericString();
  }


  /**
   * Gets the container as a node.<br>
   * Containers that are not nodes, such as {@link org.tentackle.fx.container.FxTab}, must override
   * all methods using it, such as {@link #getParentContainer()}.
   *
   * @return the node
   */
  public Node getNode() {
    return (Node) getContainer();
  }

  @Override
  public void updateViewNonFocused() {
    for (Object obj : getContainer().getComponents()) {
      if (obj instanceof FxContainer) {
        ((FxContainer) obj).updateViewNonFocused();
      }
      else if (obj instanceof FxControl &&
               (!(obj instanceof Node) || !((Node) obj).isFocused())) {
        // only if not focused (user might currently be typing...)
        ((FxControl) obj).updateView();
      }
    }
  }

  @Override
  public void updateView() {
    for (Object obj: getContainer().getComponents()) {
      if (obj instanceof FxControl) {
        ((FxControl) obj).updateView();
      }
    }
  }

  @Override
  public void updateModel() {
    for (Object obj: getContainer().getComponents()) {
      if (obj instanceof FxControl) {
        ((FxControl) obj).updateModel();
      }
    }
  }

  @Override
  public void triggerViewModified() {
    boolean modified = false;
    for (Object component: getComponents()) {
      if (component instanceof FxControl &&
          (((FxControl) component).isViewModified())) {
        modified = true;
        break;
      }
    }
    setViewModified(modified);
    if (getParentContainer() != null) {
      getParentContainer().triggerViewModified();
    }
  }

  @Override
  public void saveView() {
    for (Object component: getComponents()) {
      if (component instanceof FxControl) {
        ((FxControl) component).saveView();
      }
    }
    setViewModified(false);
  }

  @Override
  public void invalidateSavedView() {
    for (Object component: getComponents()) {
      if (component instanceof FxControl) {
        ((FxControl) component).invalidateSavedView();
      }
    }
  }

  @Override
  public void setBindable(boolean bindable) {
    super.setBindable(bindable);
    for (Object component: getComponents()) {
      if (component instanceof FxControl) {
        ((FxControl) component).setBindable(bindable);
      }
    }
  }

  @Override
  public void setChangeable(boolean changeable) {
    if (changeable != isChangeable()) {
      super.setChangeable(changeable);
      setContainerChangeable(getComponents(), changeable);
    }
  }

  @Override
  public void setContainerChangeable(boolean containerChangeable) {
    if (!isContainerChangeableIgnored()) {
      // a container that has been set non-changeable itself stays non-changeable,
      // no matter what its own container says (same rule as in FxComponentDelegate)
      setContainerChangeable(getComponents(), containerChangeable && isControlChangeable());
      // leave disable property untouched as disabling the container
      // will gray out all components way too much
      // don't invoke updateChangeable() as this would overwrite local controlChangeable!
    }
  }

  /**
   * Updates the container-changeable flag of all components.
   *
   * @param components the components
   * @param changeable true if changeable
   */
  protected void setContainerChangeable(List<?> components, boolean changeable) {
    for (Object component : components) {
      if (component instanceof FxControl) {
        ((FxControl) component).setContainerChangeable(changeable);
      }
    }
  }

  @Override
  public void clearErrors() {
    for (Object component: getComponents()) {
      if (component instanceof FxComponent) {
        ((FxComponent) component).setError(null);
      }
      else if (component instanceof FxContainer) {
        ((FxContainer) component).clearErrors();
      }
    }
  }

}

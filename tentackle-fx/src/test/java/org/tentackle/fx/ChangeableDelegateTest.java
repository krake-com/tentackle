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

package org.tentackle.fx;

import org.testng.annotations.Test;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the changeable contract of the control- and container delegates.<p>
 * Only delegates are created, no controls, so that the tests run without a JavaFX toolkit.
 * The stubs mimic what the component delegates do for the real FX controls: they map the
 * changeability to a property of the control (disabled or editable) and bind the changeable
 * property to it.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ChangeableDelegateTest {

  /**
   * A leaf control delegate mapping the changeability to a "disabled" property,
   * as {@code FxComponentDelegate} does.
   */
  private static class TestControl extends FxControlDelegate {

    private final BooleanProperty disabled = new SimpleBooleanProperty();

    @Override
    protected ReadOnlyBooleanWrapper createChangeableProperty(boolean changeable) {
      ReadOnlyBooleanWrapper property = new ReadOnlyBooleanWrapper(changeable);
      property.bind(disabled.not());
      return property;
    }

    @Override
    protected void updateChangeable(boolean changeable) {
      disabled.set(!changeable);
    }

    @Override
    public void setContainerChangeable(boolean containerChangeable) {
      if (!isContainerChangeableIgnored()) {
        disabled.set(!containerChangeable || !isControlChangeable());
      }
    }

    /**
     * Returns the changeability effectively shown to the user.
     *
     * @return true if the user can edit the control
     */
    private boolean isEffectivelyChangeable() {
      return !disabled.get();
    }

    @Override
    public FxContainer getParentContainer() {
      return null;
    }

    @Override
    public void updateView() {
      // nothing to do
    }

    @Override
    public void updateModel() {
      // nothing to do
    }

    @Override
    public void saveView() {
      // nothing to do
    }

    @Override
    public void invalidateSavedView() {
      // nothing to do
    }

    @Override
    public void triggerViewModified() {
      // nothing to do
    }

    @Override
    public String toGenericString() {
      return "test control";
    }
  }


  /**
   * A container delegate holding a plain list of components.
   */
  private static class TestContainer extends FxContainerDelegate {

    private final ObservableList<FxControl> components = FXCollections.observableArrayList();

    @Override
    public FxContainer getContainer() {
      return this;
    }

    @Override
    public ObservableList<?> getComponents() {
      return components;
    }
  }


  /**
   * Observing the changeable property must not break {@code setChangeable}.<br>
   * The property is bound to the property the changeability is mapped to, so it must not be set.
   */
  @Test
  public void testSetChangeableWithObservedProperty() {
    TestControl control = new TestControl();
    assertTrue(control.isChangeable());

    control.changeableProperty();   // lazily creates and binds the property

    control.setChangeable(false);
    assertFalse(control.isChangeable());
    assertFalse(control.changeableProperty().get());
    assertFalse(control.isEffectivelyChangeable());

    control.setChangeable(true);
    assertTrue(control.isChangeable());
    assertTrue(control.changeableProperty().get());
    assertTrue(control.isEffectivelyChangeable());
  }


  /**
   * A changeable container passes the container changeability on to its components.
   */
  @Test
  public void testChangeableContainerPropagates() {
    TestControl field = new TestControl();
    TestContainer container = new TestContainer();
    container.components.add(field);

    container.setContainerChangeable(false);
    assertFalse(field.isEffectivelyChangeable());

    container.setContainerChangeable(true);
    assertTrue(field.isEffectivelyChangeable());
  }


  /**
   * A container that has been set non-changeable itself keeps its components non-changeable,
   * even if its own container becomes changeable.
   */
  @Test
  public void testNonChangeableContainerStaysNonChangeable() {
    TestControl field = new TestControl();
    TestContainer inner = new TestContainer();
    inner.components.add(field);

    inner.setChangeable(false);
    assertFalse(field.isEffectivelyChangeable());

    // the outer container becoming changeable must not re-enable the inner container's components
    inner.setContainerChangeable(true);
    assertFalse(field.isEffectivelyChangeable());

    // ... until the inner container itself becomes changeable again
    inner.setChangeable(true);
    assertTrue(field.isEffectivelyChangeable());
  }

}

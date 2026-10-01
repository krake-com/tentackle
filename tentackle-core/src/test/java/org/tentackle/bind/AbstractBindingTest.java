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

package org.tentackle.bind;

import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@link AbstractBinding} value transfer, veto handling and from-method parent walk.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractBindingTest {

  // ---- model classes ------------------------------------------------------

  @SuppressWarnings("missing-explicit-ctor")
  public static class Model {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  /** immutable value with a "with"-method. */
  public record Address(String city) {
    public Address withCity(String newCity) {
      return new Address(newCity);
    }
  }

  /** mutable container holding the immutable address. */
  @SuppressWarnings("missing-explicit-ctor")
  public static class Container {
    private Address address;

    public Address getAddress() {
      return address;
    }

    public void setAddress(Address address) {
      this.address = address;
    }
  }


  // ---- test binding -------------------------------------------------------

  /** a binding whose "view" is just a captured value. */
  static class TestBinding extends AbstractBinding {

    private final Object rootObject;
    private Object viewValue;
    private boolean mandatory;
    private boolean changeable = true;

    TestBinding(Binder binder, BindingMember[] parents, BindingMember member, Object rootObject) {
      super(binder, parents, member);
      this.rootObject = rootObject;
    }

    @Override
    protected Class<?> getViewType() {
      return Object.class;
    }

    @Override
    protected Object getBoundRootObject() {
      return rootObject;
    }

    @Override
    protected boolean isValidationRequired() {
      return true;
    }

    @Override
    protected String viewComponentToString() {
      return "testView";
    }

    @Override
    protected void updateView(Object value) {
      this.viewValue = value;
    }

    @Override
    public Object getViewValue() {
      return viewValue;
    }

    @Override
    public void setMandatory(boolean mandatory) {
      this.mandatory = mandatory;
    }

    @Override
    public boolean isMandatory() {
      return mandatory;
    }

    @Override
    public void setChangeable(boolean changeable) {
      this.changeable = changeable;
    }

    @Override
    public boolean isChangeable() {
      return changeable;
    }
  }


  // ---- helpers ------------------------------------------------------------

  private static Method method(Class<?> clazz, String name, Class<?>... args) {
    try {
      return clazz.getMethod(name, args);
    }
    catch (NoSuchMethodException ex) {
      throw new AssertionError(ex);
    }
  }

  private static Binder newBinder() {
    return new AbstractBinderTest.TestBinder();
  }

  private static BindingMember nameMember() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setGetter(method(Model.class, "getName"));
    element.setSetter(method(Model.class, "setName", String.class));
    return new DefaultBindingMember(Model.class, null, "name", "name", element);
  }


  // ---- simple accessors ---------------------------------------------------

  @Test
  public void testConstructorRejectsNulls() {
    BindingMember member = nameMember();
    expectThrows(NullPointerException.class, () -> new TestBinding(null, null, member, null));
    expectThrows(NullPointerException.class, () -> new TestBinding(newBinder(), null, null, null));
  }

  @Test
  public void testBinderAndMemberExposed() {
    Binder binder = newBinder();
    BindingMember member = nameMember();
    TestBinding binding = new TestBinding(binder, null, member, new Model());
    assertSame(binding.getBinder(), binder);
    assertSame(binding.getMember(), member);
    assertNull(binding.getParents());
  }

  @Test
  public void testMandatoryAndChangeable() {
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), new Model());
    assertTrue(binding.isChangeable());
    binding.setChangeable(false);
    assertFalse(binding.isChangeable());
    binding.setMandatory(true);
    assertTrue(binding.isMandatory());
  }


  // ---- model <-> view value transfer -------------------------------------

  @Test
  public void testGetModelValue() {
    Model model = new Model();
    model.setName("hello");
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), model);
    assertEquals(binding.getModelValue(), "hello");
  }

  @Test
  public void testGetModelValueWithNullRootIsNull() {
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), null);
    assertNull(binding.getModelValue());
  }

  @Test
  public void testSetModelValue() {
    Model model = new Model();
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), model);
    binding.setModelValue("world");
    assertEquals(model.getName(), "world");
  }

  @Test
  public void testSetViewValueUpdatesView() {
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), new Model());
    binding.setViewValue("shown");
    assertEquals(binding.getViewValue(), "shown");
  }


  // ---- veto handling ------------------------------------------------------

  @Test
  public void testToModelVetoBlocksUpdate() {
    Model model = new Model();
    model.setName("original");
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), model);
    binding.addToModelListener(_ -> {
      throw new BindingVetoException("no way");
    });
    binding.setModelValue("changed");
    assertEquals(model.getName(), "original");   // unchanged
  }

  @Test
  public void testToModelVetoWithRethrow() {
    Model model = new Model();
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), model);
    binding.addToModelListener(_ -> {
      throw new BindingVetoException(true, "boom");
    });
    expectThrows(BindingException.class, () -> binding.setModelValue("changed"));
  }

  @Test
  public void testToModelVetoWithResyncUpdatesView() {
    Model model = new Model();
    model.setName("model-value");
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), model);
    binding.addToModelListener(_ -> {
      throw new BindingVetoException("resync", true);
    });
    binding.setModelValue("view-value");
    assertEquals(model.getName(), "model-value");           // model unchanged
    assertEquals(binding.getViewValue(), "model-value");    // view resynced from model
  }

  @Test
  public void testToViewVetoBlocksUpdate() {
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), new Model());
    binding.addToViewListener(_ -> {
      throw new BindingVetoException("no view update");
    });
    binding.setViewValue("attempt");
    assertNull(binding.getViewValue());   // update was vetoed
  }


  /**
   * The binder's to-model listeners are fired regardless of its to-view listeners.
   */
  @Test
  public void testBinderToModelVeto() {
    Model model = new Model();
    model.setName("model-value");
    Binder binder = newBinder();
    binder.addToModelListener(_ -> {
      throw new BindingVetoException("binder says no");
    });
    TestBinding binding = new TestBinding(binder, null, nameMember(), model);
    binding.setModelValue("view-value");
    assertEquals(model.getName(), "model-value");           // vetoed by the binder
  }

  /**
   * The binder's to-view listeners alone must not break the update of the model.
   */
  @Test
  public void testBinderToViewListenerOnly() {
    Model model = new Model();
    Binder binder = newBinder();
    binder.addToViewListener(_ -> { });
    TestBinding binding = new TestBinding(binder, null, nameMember(), model);
    binding.setModelValue("view-value");
    assertEquals(model.getName(), "view-value");
  }

  @Test
  public void testToViewVetoWithResyncUpdatesModel() {
    Model model = new Model();
    model.setName("model-value");
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), model);
    binding.updateView("view-value");       // the view shows something else
    binding.addToViewListener(_ -> {
      throw new BindingVetoException("resync", true);
    });
    binding.setViewValue("model-value");
    assertEquals(binding.getViewValue(), "view-value");     // view unchanged
    assertEquals(model.getName(), "view-value");            // model resynced from view
  }


  // ---- from-method parent walk -------------------------------------------

  @Test
  public void testFromMethodWalksUpToSetter() {
    // parent chain: Container.address (setter) -> Address.city (with-method)
    DefaultBindableElement addressElement = new DefaultBindableElement("Address");
    addressElement.setGetter(method(Container.class, "getAddress"));
    addressElement.setSetter(method(Container.class, "setAddress", Address.class));
    BindingMember addressMember = new DefaultBindingMember(Container.class, null, "address", "address", addressElement);

    DefaultBindableElement cityElement = new DefaultBindableElement("City");
    cityElement.setGetter(method(Address.class, "city"));
    cityElement.setFrom(method(Address.class, "withCity", String.class));
    BindingMember cityMember = new DefaultBindingMember(Address.class, addressMember, "city", "address.city", cityElement);

    Container container = new Container();
    container.setAddress(new Address("old"));

    TestBinding binding = new TestBinding(newBinder(),
                                          new BindingMember[]{ addressMember },
                                          cityMember, container);

    // reading walks down to the immutable member
    assertEquals(binding.getModelValue(), "old");

    // writing produces a new Address and installs it via the container's setter
    binding.setModelValue("new");
    assertEquals(container.getAddress(), new Address("new"));
  }


  // ---- validators ---------------------------------------------------------

  @Test
  public void testValidateWithoutValidatorsReturnsEmpty() {
    Model model = new Model();
    model.setName("x");
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), model);
    List<?> results = binding.validate();
    assertTrue(results.isEmpty());
  }

  @Test
  public void testAddAndRemoveValidatorsList() {
    TestBinding binding = new TestBinding(newBinder(), null, nameMember(), new Model());
    assertNull(binding.getValidators());   // none initially
  }
}

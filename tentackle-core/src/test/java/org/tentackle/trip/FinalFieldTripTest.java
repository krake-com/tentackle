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

package org.tentackle.trip;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.tentackle.bind.BindingVetoException;
import org.tentackle.common.ParameterString;
import org.tentackle.misc.CopyOnWriteList;
import org.tentackle.misc.Identifiable;
import org.tentackle.misc.IdentifiableKey;
import org.tentackle.misc.IdentifiableMap;
import org.tentackle.misc.XUUID;
import org.tentackle.trip.impl.EchoTransceiver;
import org.tentackle.trip.impl.FieldTripComponent;
import org.tentackle.validate.ValidationContextFactory;
import org.tentackle.validate.ValidationFailedException;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.ValidatorCache;
import org.tentackle.validate.severity.FailedValidationResult;
import org.tentackle.validate.severity.InfoValidationResult;
import org.tentackle.validate.validator.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests that TRIP never mutates final fields (JEP-500) unless explicitly enabled.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class FinalFieldTripTest {

  /**
   * A class with a final field and a no-arg constructor, but no canonical constructor.
   */
  public static class FinalFieldClass implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;

    public FinalFieldClass() {
      name = null;
    }

    public FinalFieldClass(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  /**
   * An identifiable with non-final fields.
   */
  public static class Item implements Identifiable {
    @Serial
    private static final long serialVersionUID = 1L;

    private long id;
    private String name;

    public Item() {
    }

    public Item(long id, String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public long getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toGenericString() {
      return name + "[" + id + "]";
    }
  }

  public static class Bean {
    @NotNull public String text;
  }


  @AfterMethod
  void resetFinalFieldMutation() {
    FieldTripComponent.setFinalFieldMutationEnabled(false);
  }

  @Test
  void testFinalFieldRefused() {
    try {
      tunnel(new FinalFieldClass("test"));
      fail("final field mutated");
    }
    catch (TripRuntimeException ex) {
      assertTrue(ex.getMessage().contains("JEP-500"), ex.getMessage());
      assertTrue(ex.getMessage().contains("name"), ex.getMessage());
    }
  }

  @Test
  void testFinalFieldMutationEnabled() {
    FieldTripComponent.setFinalFieldMutationEnabled(true);
    FinalFieldClass tunneled = tunnel(new FinalFieldClass("test"));
    assertEquals(tunneled.getName(), "test");
  }

  @Test
  void testCopyOnWriteList() {
    CopyOnWriteList<String> list = new CopyOnWriteList<>(new ArrayList<>(List.of("one", "two")), true, false);
    CopyOnWriteList<String> tunneled = tunnel(list);
    assertNotSame(tunneled, list);
    assertEquals(new ArrayList<>(tunneled), List.of("one", "two"));
    // iteratorModifying=true: removing via the iterator must work
    var iterator = tunneled.iterator();
    iterator.next();
    iterator.remove();
    assertEquals(new ArrayList<>(tunneled), List.of("two"));
    // listIteratorModifying=false: list iterators are read-only
    ListIterator<String> listIterator = tunneled.listIterator();
    listIterator.next();
    assertThrows(UnsupportedOperationException.class, () -> listIterator.set("three"));
  }

  @Test
  void testValidationResults() {
    Bean bean = new Bean();
    var validator = ValidatorCache.getInstance().getFieldValidators(Bean.class).getFirst();
    var context = ValidationContextFactory.getInstance().create(
        "root.text", String.class, null, bean, ValidationScopeFactory.getInstance().getAllScope(), new ArrayList<>());
    List<ValidationResult> results = new ArrayList<>();
    results.add(new FailedValidationResult(validator, context, "failed"));
    results.add(new InfoValidationResult(validator, context, "info"));
    ValidationFailedException ex = new ValidationFailedException("boom", results);

    ValidationFailedException tunneled = tunnel(ex);
    assertEquals(tunneled.getMessage(), "boom");
    List<ValidationResult> tunneledResults = tunneled.getResults();
    assertEquals(tunneledResults.size(), 2);
    for (int i = 0; i < results.size(); i++) {
      ValidationResult result = results.get(i);
      ValidationResult tunneledResult = tunneledResults.get(i);
      assertEquals(tunneledResult.getClass(), result.getClass());
      assertEquals(tunneledResult.getMessage(), result.getMessage());
      assertEquals(tunneledResult.getErrorCode(), result.getErrorCode());
      assertEquals(tunneledResult.getValidationPath(), result.getValidationPath());
      assertTrue(Arrays.equals(tunneledResult.getConfiguredScopes(), result.getConfiguredScopes()));
      assertEquals(tunneledResult.hasFailed(), result.hasFailed());
    }
    assertFalse(tunneledResults.get(1).hasFailed());
  }

  @Test
  void testParameterString() {
    ParameterString ps = new ParameterString();
    ps.setParameter("a", "1");
    ps.setParameter("b", "2");
    ParameterString tunneled = tunnel(ps);
    assertEquals(tunneled.toString(), ps.toString());
  }

  @Test
  void testBindingVetoException() {
    BindingVetoException tunneled = tunnel(new BindingVetoException("veto", new IllegalStateException("cause"), true, true));
    assertEquals(tunneled.getMessage(), "veto");
    assertEquals(tunneled.getCause().getClass(), IllegalStateException.class);
    assertEquals(tunneled.getCause().getMessage(), "cause");
    assertTrue(tunneled.isResyncRequested());
    assertTrue(tunneled.isRethrowEnabled());

    tunneled = tunnel(new BindingVetoException(true, "rethrow"));
    assertEquals(tunneled.getMessage(), "rethrow");
    assertNull(tunneled.getCause());
    assertFalse(tunneled.isResyncRequested());
    assertTrue(tunneled.isRethrowEnabled());
  }

  @Test
  void testIdentifiableKey() {
    IdentifiableKey<Item> key = new IdentifiableKey<>(Item.class, 42);
    IdentifiableKey<Item> tunneled = tunnel(key);
    assertNotSame(tunneled, key);
    assertEquals(tunneled, key);
    assertEquals(tunneled.getIdentifiableClass(), Item.class);
    assertEquals(tunneled.getIdentifiableId(), 42);
  }

  @Test
  void testIdentifiableMap() {
    IdentifiableMap<Item> map = new IdentifiableMap<>();
    map.add(new Item(1, "one"));
    map.add(new Item(2, "two"));
    IdentifiableMap<Item> tunneled = tunnel(map);
    assertEquals(tunneled.getAll().size(), 2);
    assertEquals(tunneled.get(Item.class, 1).getName(), "one");
    assertEquals(tunneled.get(Item.class, 2).getName(), "two");
  }

  @Test
  void testXUUID() {
    UUID uuid = UUID.randomUUID();
    XUUID xuuid = new XUUID(42, uuid);
    XUUID tunneled = tunnel(xuuid);
    assertNotSame(tunneled, xuuid);
    assertEquals(tunneled, xuuid);
    assertEquals(tunneled.getId(), 42);
    assertEquals(tunneled.toGenericString(), xuuid.toGenericString());

    // the identifiable is not transferred
    xuuid = new XUUID(new Item(7, "seven"), uuid);
    assertEquals(xuuid.toGenericString(), "seven[7]{" + uuid + "}");
    tunneled = tunnel(xuuid);
    assertEquals(tunneled, xuuid);
    assertEquals(tunneled.getId(), 7);
    assertEquals(tunneled.toGenericString(), "[7]{" + uuid + "}");

    tunneled = tunnel(XUUID.NIL);
    assertEquals(tunneled, XUUID.NIL);
  }


  @SuppressWarnings("unchecked")
  private <T> T tunnel(T value) {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripType<T> tripType = TripFactory.getInstance().getType((Class<T>) value.getClass());
    tripType.write(value, transceiver.getStream());
    transceiver.echo();
    OpCode opCode = transceiver.getStream().getDeserializer().deserializeOpCode();
    return tripType.read(transceiver.getStream(), opCode);
  }

}

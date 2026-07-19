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

package org.tentackle.validate;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Tests the {@link DefaultValidationContext} and the {@link ValidationContextFactory}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ValidationContextTest {

  private ValidationContext create(Object object) {
    List<ValidationResult> results = new ArrayList<>();
    return ValidationContextFactory.getInstance().create(
        "o.field", String.class, object, "parent",
        ValidationScopeFactory.getInstance().getAllScope(), results);
  }

  @Test
  public void testAccessors() {
    List<ValidationResult> results = new ArrayList<>();
    ValidationScope scope = ValidationScopeFactory.getInstance().getAllScope();
    ValidationContext ctx = ValidationContextFactory.getInstance().create(
        "o.field", String.class, "value", "parent", scope, results);
    assertEquals(ctx.getValidationPath(), "o.field");
    assertEquals(ctx.getType(), String.class);
    assertEquals(ctx.getObject(), "value");
    assertEquals(ctx.getParentObject(), "parent");
    assertSame(ctx.getEffectiveScope(), scope);
    assertSame(ctx.getValidationResults(), results);
    assertEquals(ctx.toString(), "o.field");
  }

  @Test
  public void testObjectSupplierIsLazyAndCalledEachTime() {
    AtomicInteger calls = new AtomicInteger();
    ValidationContext ctx = ValidationContextFactory.getInstance().create(
        "o.field", String.class, (java.util.function.Supplier<?>) () -> {
          calls.incrementAndGet();
          return "lazy";
        }, "parent", ValidationScopeFactory.getInstance().getAllScope(), new ArrayList<>());
    // not called until getObject()
    assertEquals(calls.get(), 0);
    assertEquals(ctx.getObject(), "lazy");
    assertEquals(calls.get(), 1);
    ctx.getObject();
    assertEquals(calls.get(), 2);
  }

  @Test
  public void testProperties() {
    ValidationContext ctx = create("value");
    assertNull(ctx.get("key"));
    assertNull(ctx.put("key", "v1"));       // put returns previous value (none)
    assertEquals(ctx.get("key"), "v1");
    assertEquals(ctx.put("key", "v2"), "v1");
    assertEquals(ctx.get("key"), "v2");
  }

  @Test
  public void testPropertiesByClass() {
    ValidationContext ctx = create("value");
    ctx.put(Integer.class, 42);
    assertEquals(ctx.get(Integer.class), Integer.valueOf(42));
  }

  @Test
  public void testNullObject() {
    ValidationContext ctx = create(null);
    assertNull(ctx.getObject());
  }
}

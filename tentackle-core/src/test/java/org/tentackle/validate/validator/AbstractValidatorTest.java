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

package org.tentackle.validate.validator;

import org.tentackle.validate.ScopeConfigurator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationContextFactory;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.DefaultScope;
import org.tentackle.validate.scope.PersistenceScope;
import org.tentackle.validate.severity.DefaultSeverity;
import org.tentackle.validate.severity.MinorSeverity;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link AbstractValidator} and the common validator metadata accessors,
 * using {@link GreaterImpl} as a concrete representative wired up with real
 * annotations reflected off the {@link Fixtures} fields.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class AbstractValidatorTest {

  /**
   * Fields carrying the annotations under test.
   */
  static class Fixtures {
    @Greater("10")
    int plain;

    @Greater(value = "10", message = "too small", error = "ERR", priority = 5,
             severity = MinorSeverity.class, condition = "$active")
    int full;

    @Greater(value = "10", scope = PersistenceScope.class)
    int scoped;

    @Greater(value = "10", condition = "$name")
    int nonBooleanCondition;
  }

  /**
   * Parent object providing condition properties.
   */
  public static class ConditionParent {
    private final boolean active;
    ConditionParent(boolean active) {
      this.active = active;
    }
    public boolean isActive() {
      return active;
    }
    public String getName() {
      return "foo";
    }
  }

  /**
   * Parent object that reconfigures the default scope.
   */
  public static class ConfiguringParent implements ScopeConfigurator {
    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends ValidationScope>[] getDefaultScopes() {
      return new Class[] { PersistenceScope.class };
    }
  }


  private static GreaterImpl validator(String fieldName) throws NoSuchFieldException {
    Field field = Fixtures.class.getDeclaredField(fieldName);
    GreaterImpl validator = new GreaterImpl();
    validator.setAnnotation(field.getAnnotation(Greater.class));
    validator.setAnnotatedElement(field);
    validator.setValidatedElementName(field.getName());
    return validator;
  }

  private static ValidationContext context(Object value, Class<?> type, Object parent) {
    return ValidationContextFactory.getInstance().create(
        "o.field", type, value, parent,
        ValidationScopeFactory.getInstance().getAllScope(), new ArrayList<>());
  }


  @Test
  public void testMetadataAccessors() throws Exception {
    GreaterImpl v = validator("full");
    assertEquals(v.getValue(), "10");
    assertEquals(v.getMessage(), "too small");
    assertEquals(v.getErrorCode(), "ERR");
    assertEquals(v.getPriority(), 5);
    assertEquals(v.getSeverity(), MinorSeverity.class);
    assertEquals(v.getCondition(), "$active");
    assertNotNull(v.getAnnotation());
    assertEquals(v.getScope().length, 1);
    assertEquals(v.getScope()[0], DefaultScope.class);
  }

  @Test
  public void testExplicitScope() throws Exception {
    GreaterImpl v = validator("scoped");
    assertEquals(v.getScope()[0], PersistenceScope.class);
  }

  @Test
  public void testDefaultSeverity() throws Exception {
    // the "plain" annotation does not set a severity -> DefaultSeverity
    assertEquals(validator("plain").getSeverity(), DefaultSeverity.class);
  }

  @Test
  public void testAnnotatedElementAndName() throws Exception {
    Field field = Fixtures.class.getDeclaredField("plain");
    GreaterImpl v = validator("plain");
    assertEquals(v.getAnnotatedElement(), field);
    assertEquals(v.getValidatedElementName(), "plain");
  }

  @Test
  public void testValidationIndexDefaultAndSet() throws Exception {
    GreaterImpl v = validator("plain");
    assertEquals(v.getValidationIndex(), -1);   // default: single validator
    v.setValidationIndex(3);
    assertEquals(v.getValidationIndex(), 3);
  }

  @Test
  public void testToStringIsClassName() throws Exception {
    assertEquals(validator("plain").toString(), GreaterImpl.class.getName());
  }


  // ---- getConfiguredScopes -------------------------------------------------

  @Test
  public void testConfiguredScopesDefault() throws Exception {
    GreaterImpl v = validator("plain");
    // parent is not a ScopeConfigurator -> the annotation's own scope
    ValidationContext ctx = context(20, int.class, new ConditionParent(true));
    Class<? extends ValidationScope>[] scopes = v.getConfiguredScopes(ctx);
    assertEquals(scopes.length, 1);
    assertEquals(scopes[0], DefaultScope.class);
  }

  @Test
  public void testConfiguredScopesNullContext() throws Exception {
    GreaterImpl v = validator("plain");
    assertEquals(v.getConfiguredScopes(null)[0], DefaultScope.class);
  }

  @Test
  public void testConfiguredScopesFromScopeConfigurator() throws Exception {
    GreaterImpl v = validator("plain");   // default scope
    ValidationContext ctx = context(20, int.class, new ConfiguringParent());
    Class<? extends ValidationScope>[] scopes = v.getConfiguredScopes(ctx);
    assertEquals(scopes.length, 1);
    assertEquals(scopes[0], PersistenceScope.class);
  }

  @Test
  public void testConfiguredScopesNotOverriddenWhenExplicit() throws Exception {
    GreaterImpl v = validator("scoped");   // explicit PersistenceScope, not DefaultScope
    ValidationContext ctx = context(20, int.class, new ConfiguringParent());
    // an explicitly scoped validator is not affected by a ScopeConfigurator
    assertEquals(v.getConfiguredScopes(ctx)[0], PersistenceScope.class);
  }


  // ---- isConditionValid ----------------------------------------------------

  @Test
  public void testNoConditionAlwaysApplies() throws Exception {
    GreaterImpl v = validator("plain");   // no condition
    assertTrue(v.isConditionValid(context(20, int.class, new ConditionParent(false))));
  }

  @Test
  public void testConditionTrue() throws Exception {
    GreaterImpl v = validator("full");    // condition "$active"
    assertTrue(v.isConditionValid(context(20, int.class, new ConditionParent(true))));
  }

  @Test
  public void testConditionFalse() throws Exception {
    GreaterImpl v = validator("full");
    assertFalse(v.isConditionValid(context(20, int.class, new ConditionParent(false))));
  }

  @Test
  public void testNonBooleanConditionThrows() throws Exception {
    GreaterImpl v = validator("nonBooleanCondition");   // condition "$name" -> String
    assertThrows(ValidationRuntimeException.class,
                 () -> v.isConditionValid(context(20, int.class, new ConditionParent(true))));
  }


  // ---- validate() (compile only) -------------------------------------------

  @Test
  public void testValidateCompilesWithoutError() throws Exception {
    validator("plain").validate();
    validator("full").validate();   // compiles condition, message and value
  }


  // ---- assertComparable ----------------------------------------------------

  @Test
  public void testAssertComparableRejectsNonComparable() throws Exception {
    GreaterImpl v = validator("plain");
    ValidationContext ctx = context(new Object(), Object.class, new ConditionParent(true));
    assertThrows(ValidationRuntimeException.class, () -> v.validate(ctx));
  }

  @Test
  public void testValidateComparablePasses() throws Exception {
    GreaterImpl v = validator("plain");
    List<? extends ValidationResult> results = v.validate(context(20, int.class, new ConditionParent(true)));
    assertTrue(results.isEmpty());
  }
}

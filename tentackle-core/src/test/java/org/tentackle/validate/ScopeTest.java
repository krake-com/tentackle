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

import org.tentackle.validate.scope.AllScope;
import org.tentackle.validate.scope.ChangeableScope;
import org.tentackle.validate.scope.InteractiveScope;
import org.tentackle.validate.scope.MandatoryScope;
import org.tentackle.validate.scope.PersistenceScope;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ValidationScope} implementations and the {@link ValidationScopeFactory}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ScopeTest {

  private final ValidationScopeFactory factory = ValidationScopeFactory.getInstance();

  @Test
  public void testFactoryAccessors() {
    assertNotNull(factory.getAllScope());
    assertNotNull(factory.getInteractiveScope());
    assertNotNull(factory.getPersistenceScope());
    assertNotNull(factory.getMandatoryScope());
    assertNotNull(factory.getChangeableScope());
  }

  @Test
  public void testLookupByName() {
    assertEquals(factory.getValidationScope(AllScope.NAME), AllScope.class);
    assertEquals(factory.getValidationScope(InteractiveScope.NAME), InteractiveScope.class);
    assertEquals(factory.getValidationScope(PersistenceScope.NAME), PersistenceScope.class);
    assertEquals(factory.getValidationScope(MandatoryScope.NAME), MandatoryScope.class);
    assertEquals(factory.getValidationScope(ChangeableScope.NAME), ChangeableScope.class);
  }

  @Test
  public void testNames() {
    assertEquals(factory.getAllScope().getName(), AllScope.NAME);
    assertEquals(factory.getInteractiveScope().getName(), InteractiveScope.NAME);
    assertEquals(factory.getPersistenceScope().getName(), PersistenceScope.NAME);
    assertEquals(factory.getMandatoryScope().getName(), MandatoryScope.NAME);
    assertEquals(factory.getChangeableScope().getName(), ChangeableScope.NAME);
  }

  @Test
  public void testToString() {
    assertEquals(factory.getPersistenceScope().toString(), "PERSISTENCE-scope");
    assertEquals(factory.getInteractiveScope().toString(), "INTERACTIVE-scope");
  }

  @Test
  public void testAllScopeAlwaysApplies() {
    // AllScope applies to anything, even to an empty set
    assertTrue(factory.getAllScope().appliesTo());
    assertTrue(factory.getAllScope().appliesTo(PersistenceScope.class));
    assertTrue(factory.getAllScope().appliesTo(InteractiveScope.class, MandatoryScope.class));
  }

  @Test
  public void testScopeAppliesToItself() {
    assertTrue(factory.getPersistenceScope().appliesTo(PersistenceScope.class));
    assertTrue(factory.getInteractiveScope().appliesTo(InteractiveScope.class));
    assertTrue(factory.getMandatoryScope().appliesTo(MandatoryScope.class));
    assertTrue(factory.getChangeableScope().appliesTo(ChangeableScope.class));
  }

  @Test
  public void testScopeAppliesToAllScope() {
    // every scope applies when the configured scope is AllScope
    assertTrue(factory.getPersistenceScope().appliesTo(AllScope.class));
    assertTrue(factory.getInteractiveScope().appliesTo(AllScope.class));
  }

  @Test
  public void testScopeDoesNotApplyToUnrelatedScopes() {
    assertFalse(factory.getInteractiveScope().appliesTo(
        ChangeableScope.class, MandatoryScope.class, PersistenceScope.class));
    assertFalse(factory.getPersistenceScope().appliesTo(InteractiveScope.class, MandatoryScope.class));
  }

  @Test
  public void testAppliesToRejectsNonInterface() {
    assertThrows(ValidationRuntimeException.class, () -> factory.getPersistenceScope().appliesTo(String.class));
  }
}

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

import org.tentackle.validate.severity.FailedValidationResult;
import org.tentackle.validate.severity.InfoValidationResult;
import org.tentackle.validate.validator.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the stand-alone helper methods of {@link ValidationUtilities}.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ValidationUtilitiesTest {

  public static class Bean {
    @NotNull public String text;
  }

  public static class SomeEntity {
  }

  private final ValidationUtilities utilities = ValidationUtilities.getInstance();

  private ValidationResult result(boolean failed, String message) {
    Validator validator = ValidatorCache.getInstance().getFieldValidators(Bean.class).getFirst();
    ValidationContext ctx = ValidationContextFactory.getInstance().create(
        "o.text", String.class, null, new Bean(),
        ValidationScopeFactory.getInstance().getAllScope(), new ArrayList<>());
    return failed ? new FailedValidationResult(validator, ctx, message)
                  : new InfoValidationResult(validator, ctx, message);
  }


  @Test
  public void testGetDefaultValidationPath() {
    assertEquals(utilities.getDefaultValidationPath(new SomeEntity()), "someEntity");
    assertEquals(utilities.getDefaultValidationPath(null), "null");
  }

  @Test
  public void testHasFailed() {
    assertFalse(utilities.hasFailed(new ArrayList<>()));
    List<ValidationResult> onlyInfo = List.of(result(false, "info"));
    assertFalse(utilities.hasFailed(onlyInfo));
    List<ValidationResult> withFailure = List.of(result(false, "info"), result(true, "boom"));
    assertTrue(utilities.hasFailed(withFailure));
  }

  @Test
  public void testHasMessage() {
    assertFalse(utilities.hasMessage(new ArrayList<>()));
    assertTrue(utilities.hasMessage(List.of(result(true, "boom"))));
  }

  @Test
  public void testResultsToMessage() {
    List<ValidationResult> results = List.of(result(true, "first"), result(true, "second"));
    assertEquals(utilities.resultsToMessage(results), "first\nsecond");
    assertEquals(utilities.resultsToMessage(null), "");
  }

  @Test
  public void testResultsToString() {
    List<ValidationResult> results = List.of(result(true, "boom"));
    String s = utilities.resultsToString(results);
    assertTrue(s.contains("boom"), s);
    assertTrue(s.contains("(FAILED)"), s);
    assertEquals(utilities.resultsToString(null), "");
  }

  @Test
  public void testCreateValidationResult() {
    Validator validator = ValidatorCache.getInstance().getFieldValidators(Bean.class).getFirst();
    ValidationContext ctx = ValidationContextFactory.getInstance().create(
        "o.text", String.class, null, new Bean(),
        ValidationScopeFactory.getInstance().getAllScope(), new ArrayList<>());
    ValidationResult result = utilities.createValidationResult(validator, ctx, "boom");
    assertEquals(result.getMessage(), "boom");
    assertTrue(result.hasFailed());     // NotNull uses the default (major) severity
  }
}

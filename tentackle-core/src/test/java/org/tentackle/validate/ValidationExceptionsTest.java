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
import org.tentackle.validate.validator.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link ValidationRuntimeException} and {@link ValidationFailedException}.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ValidationExceptionsTest {

  public static class Bean {
    @NotNull public String text;
  }

  private static ValidationResult failedResult(String message) {
    Validator validator = ValidatorCache.getInstance().getFieldValidators(Bean.class).getFirst();
    ValidationContext ctx = ValidationContextFactory.getInstance().create(
        "o.text", String.class, null, new Bean(),
        ValidationScopeFactory.getInstance().getAllScope(), new ArrayList<>());
    return new FailedValidationResult(validator, ctx, message);
  }


  // ---- ValidationRuntimeException ------------------------------------------

  @Test
  public void testRuntimeExceptionConstructors() {
    assertNull(new ValidationRuntimeException().getMessage());   // default: null message
    assertEquals(new ValidationRuntimeException("msg").getMessage(), "msg");

    Throwable cause = new IllegalStateException("root");
    ValidationRuntimeException withBoth = new ValidationRuntimeException("msg", cause);
    assertEquals(withBoth.getMessage(), "msg");
    assertSame(withBoth.getCause(), cause);

    ValidationRuntimeException withCause = new ValidationRuntimeException(cause);
    assertSame(withCause.getCause(), cause);
  }


  // ---- ValidationFailedException -------------------------------------------

  @Test
  public void testFailedExceptionMessageAndResults() {
    List<ValidationResult> results = new ArrayList<>();
    results.add(failedResult("boom"));
    ValidationFailedException ex = new ValidationFailedException("aborted", results);
    assertEquals(ex.getMessage(), "aborted");
    assertSame(ex.getResults(), results);
    assertFalse(ex.withStacktrace());
  }

  @Test
  public void testFailedExceptionDerivesMessageFromResults() {
    List<ValidationResult> results = new ArrayList<>();
    results.add(failedResult("boom"));
    ValidationFailedException ex = new ValidationFailedException(results);
    assertTrue(ex.getMessage().contains("boom"), ex.getMessage());
    assertTrue(ex.resultsAsMessage().contains("boom"));
  }

  @Test
  public void testFailedExceptionNullResultsBecomesEmptyList() {
    ValidationFailedException ex = new ValidationFailedException("aborted");
    assertNotNull(ex.getResults());
    assertTrue(ex.getResults().isEmpty());
  }

  @Test
  public void testReThrowMergesPriorResults() {
    List<ValidationResult> exResults = new ArrayList<>();
    exResults.add(failedResult("second"));
    ValidationFailedException ex = new ValidationFailedException("aborted", exResults);

    List<ValidationResult> soFar = new ArrayList<>();
    soFar.add(failedResult("first"));

    ValidationFailedException thrown = null;
    try {
      ex.reThrow(soFar);
    }
    catch (ValidationFailedException vfx) {
      thrown = vfx;
    }
    assertSame(thrown, ex);
    // prior results are prepended
    assertEquals(ex.getResults().size(), 2);
    assertEquals(ex.getResults().get(0).getMessage(), "first");
    assertEquals(ex.getResults().get(1).getMessage(), "second");
  }
}

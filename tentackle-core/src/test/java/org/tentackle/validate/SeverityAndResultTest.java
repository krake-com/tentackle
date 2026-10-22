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

import org.tentackle.validate.severity.DefaultSeverity;
import org.tentackle.validate.severity.FailedValidationResult;
import org.tentackle.validate.severity.InfoValidationResult;
import org.tentackle.validate.severity.MajorSeverity;
import org.tentackle.validate.severity.MinorSeverity;
import org.tentackle.validate.validator.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ValidationSeverity} implementations and the {@link ValidationResult} hierarchy.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class SeverityAndResultTest {

  public static class Bean {
    @NotNull public String text;
  }

  private final ValidationSeverityFactory severityFactory = ValidationSeverityFactory.getInstance();

  private static Validator validator() {
    return ValidatorCache.getInstance().getFieldValidators(Bean.class).getFirst();
  }

  private static ValidationContext context(String path) {
    return ValidationContextFactory.getInstance().create(
        path, String.class, null, new Bean(),
        ValidationScopeFactory.getInstance().getAllScope(), new ArrayList<>());
  }


  @Test
  public void testDefaultSeverityProducesFailure() {
    ValidationSeverity severity = severityFactory.getValidationSeverity(DefaultSeverity.class);
    assertNotNull(severity);
    ValidationResult result = severity.createValidationResult(validator(), context("o.text"), "boom");
    assertTrue(result instanceof FailedValidationResult);
    assertTrue(result.hasFailed());
    assertTrue(result.hasMessage());
    assertEquals(result.getMessage(), "boom");
  }

  @Test
  public void testMajorSeverityProducesFailure() {
    ValidationSeverity severity = severityFactory.getValidationSeverity(MajorSeverity.class);
    ValidationResult result = severity.createValidationResult(validator(), context("o.text"), "boom");
    assertTrue(result instanceof FailedValidationResult);
    assertTrue(result.hasFailed());
  }

  @Test
  public void testMinorSeverityProducesInfo() {
    ValidationSeverity severity = severityFactory.getValidationSeverity(MinorSeverity.class);
    ValidationResult result = severity.createValidationResult(validator(), context("o.text"), "note");
    assertTrue(result instanceof InfoValidationResult);
    assertFalse(result.hasFailed());
    assertTrue(result.hasMessage());          // info still carries a message
    assertEquals(result.getMessage(), "note");
  }

  @Test
  public void testResultAccessors() {
    Validator validator = validator();
    ValidationContext ctx = context("o.text");
    ValidationResult result = new FailedValidationResult(validator, ctx, "boom");
    assertSame(result.getValidator(), validator);
    assertSame(result.getValidationContext(), ctx);
    assertEquals(result.getValidationPath(), "o.text");
    assertNotNull(result.getConfiguredScopes());
    assertTrue(result.getConfiguredScopes().length > 0);
  }

  @Test
  public void testToStringContainsPathAndState() {
    ValidationResult failed = new FailedValidationResult(validator(), context("o.text"), "boom");
    String s = failed.toString();
    assertTrue(s.contains("o.text"), s);
    assertTrue(s.contains("boom"), s);
    assertTrue(s.contains("(FAILED)"), s);

    ValidationResult info = new InfoValidationResult(validator(), context("o.text"), "note");
    assertTrue(info.toString().contains("(PASSED)"), info.toString());
  }

  @Test
  public void testFailedValidationResultRejectsEmptyMessage() {
    assertThrows(IllegalArgumentException.class,
                 () -> new FailedValidationResult(validator(), context("o.text"), ""));
  }

  @Test
  public void testFailedValidationResultRejectsNullMessage() {
    assertThrows(NullPointerException.class,
                 () -> new FailedValidationResult(validator(), context("o.text"), null));
  }

  @Test
  public void testValidationCollectionIndexes() {
    ValidationResult result = new FailedValidationResult(validator(), context("root.items[2].name"), "boom");
    ValidationResult.CollectionIndex[] indexes = result.getValidationCollectionIndexes();
    assertEquals(indexes.length, 1);
    assertEquals(indexes[0].index(), 2);
    assertEquals(indexes[0].pathPrefix(), "root.items");
    assertEquals(indexes[0].pathPostfix(), "name");
  }

  @Test
  public void testValidationCollectionIndexesNested() {
    ValidationResult result = new FailedValidationResult(validator(),
        context("invoice.invoiceLineList[3].referenceList[10].text"), "boom");
    ValidationResult.CollectionIndex[] indexes = result.getValidationCollectionIndexes();
    assertEquals(indexes.length, 2);
    assertEquals(indexes[0], new ValidationResult.CollectionIndex(3, "invoice.invoiceLineList", "referenceList[10].text"));
    assertEquals(indexes[1], new ValidationResult.CollectionIndex(10, "invoice.invoiceLineList[3].referenceList", "text"));
  }

  /**
   * Object validators of collection elements produce paths ending with the index.
   */
  @Test
  public void testValidationCollectionIndexesTrailing() {
    ValidationResult result = new FailedValidationResult(validator(), context("root.items[2]"), "boom");
    ValidationResult.CollectionIndex[] indexes = result.getValidationCollectionIndexes();
    assertEquals(indexes.length, 1);
    assertEquals(indexes[0], new ValidationResult.CollectionIndex(2, "root.items", ""));
  }

  @Test
  public void testValidationCollectionIndexesEmpty() {
    ValidationResult result = new FailedValidationResult(validator(), context("root.name"), "boom");
    assertEquals(result.getValidationCollectionIndexes().length, 0);
  }

  @Test
  public void testSeverityFactoryListsSeverities() {
    List<ValidationSeverity> severities = new ArrayList<>(severityFactory.getValidationSeverities());
    assertFalse(severities.isEmpty());
  }
}

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

import org.tentackle.validate.severity.MinorSeverity;
import org.tentackle.validate.validator.Changeable;
import org.tentackle.validate.validator.ChangeableImpl;
import org.tentackle.validate.validator.Equal;
import org.tentackle.validate.validator.Fail;
import org.tentackle.validate.validator.False;
import org.tentackle.validate.validator.Greater;
import org.tentackle.validate.validator.GreaterOrEqual;
import org.tentackle.validate.validator.Less;
import org.tentackle.validate.validator.LessOrEqual;
import org.tentackle.validate.validator.Mandatory;
import org.tentackle.validate.validator.MandatoryImpl;
import org.tentackle.validate.validator.NotEmpty;
import org.tentackle.validate.validator.NotEqual;
import org.tentackle.validate.validator.NotNull;
import org.tentackle.validate.validator.NotNullImpl;
import org.tentackle.validate.validator.NotZero;
import org.tentackle.validate.validator.Null;
import org.tentackle.validate.validator.Pattern;
import org.tentackle.validate.validator.Size;
import org.tentackle.validate.validator.True;
import org.tentackle.validate.validator.Validate;
import org.tentackle.validate.validator.Zero;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Exercises every individual field validator implementation through the public
 * {@link ValidationUtilities} facade, covering the passing and failing branches.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ValidatorsTest {

  /**
   * Validates an object for the {@code AllScope} and returns the results.
   *
   * @param object the object to validate
   * @return the validation results
   */
  private static List<ValidationResult> validate(Object object) {
    return ValidationUtilities.getInstance().validate(
        object, "o", ValidationScopeFactory.getInstance().getAllScope());
  }

  /**
   * Asserts that validating the object yields exactly one failed result.
   *
   * @param object the object to validate
   */
  private static void assertFails(Object object) {
    List<ValidationResult> results = validate(object);
    assertEquals(results.size(), 1, "expected exactly one result, got " + results);
    assertTrue(results.getFirst().hasFailed(), "result should be a failure: " + results.getFirst());
  }

  /**
   * Asserts that validating the object yields no results.
   *
   * @param object the object to validate
   */
  private static void assertPasses(Object object) {
    List<ValidationResult> results = validate(object);
    assertTrue(results.isEmpty(), "expected no results, got " + results);
  }


  // ---- NotNull / Null ------------------------------------------------------

  public static class NotNullObj {
    @NotNull public String text;
  }

  @Test
  public void testNotNull() {
    assertFails(new NotNullObj());                       // null
    NotNullObj ok = new NotNullObj();
    ok.text = "x";
    assertPasses(ok);
  }

  public static class NullObj {
    @Null public String text;
  }

  @Test
  public void testNull() {
    assertPasses(new NullObj());                         // null passes
    NullObj bad = new NullObj();
    bad.text = "x";
    assertFails(bad);
  }


  // ---- NotEmpty ------------------------------------------------------------

  public static class NotEmptyObj {
    @NotEmpty public String text = "";
  }

  @Test
  public void testNotEmpty() {
    assertFails(new NotEmptyObj());                      // empty string
    NotEmptyObj nul = new NotEmptyObj();
    nul.text = null;
    assertFails(nul);                                    // null is reported too
    NotEmptyObj ok = new NotEmptyObj();
    ok.text = "x";
    assertPasses(ok);
  }

  public static class NotEmptyListObj {
    @NotEmpty public List<String> list = new ArrayList<>();
  }

  @Test
  public void testNotEmptyCollection() {
    assertFails(new NotEmptyListObj());                  // empty list
    NotEmptyListObj ok = new NotEmptyListObj();
    ok.list.add("x");
    assertPasses(ok);
  }


  // ---- NotZero / Zero ------------------------------------------------------

  public static class NotZeroObj {
    @NotZero public int value;
  }

  @Test
  public void testNotZero() {
    assertFails(new NotZeroObj());                       // 0
    NotZeroObj ok = new NotZeroObj();
    ok.value = 5;
    assertPasses(ok);
  }

  public static class ZeroObj {
    @Zero public int value = 7;
  }

  @Test
  public void testZero() {
    assertFails(new ZeroObj());                          // 7
    ZeroObj ok = new ZeroObj();
    ok.value = 0;
    assertPasses(ok);
  }


  // ---- Equal / NotEqual ----------------------------------------------------

  public static class EqualObj {
    @Equal("5") public Integer value;
  }

  @Test
  public void testEqual() {
    EqualObj ok = new EqualObj();
    ok.value = 5;
    assertPasses(ok);
    EqualObj bad = new EqualObj();
    bad.value = 6;
    assertFails(bad);
    // null object with non-null value and ignoreNulls (default) passes
    assertPasses(new EqualObj());
  }

  public static class NotEqualObj {
    @NotEqual("5") public Integer value;
  }

  @Test
  public void testNotEqual() {
    NotEqualObj bad = new NotEqualObj();
    bad.value = 5;
    assertFails(bad);
    NotEqualObj ok = new NotEqualObj();
    ok.value = 6;
    assertPasses(ok);
  }


  // ---- Greater / GreaterOrEqual / Less / LessOrEqual -----------------------

  public static class GreaterObj {
    @Greater("10") public int value;
  }

  @Test
  public void testGreater() {
    GreaterObj bad = new GreaterObj();
    bad.value = 5;
    assertFails(bad);
    GreaterObj eq = new GreaterObj();
    eq.value = 10;
    assertFails(eq);                                     // 10 is not > 10
    GreaterObj ok = new GreaterObj();
    ok.value = 20;
    assertPasses(ok);
  }

  public static class GreaterNullObj {
    @Greater(value = "10", ignoreNulls = false) public Integer value;
  }

  @Test
  public void testGreaterIgnoreNulls() {
    // with ignoreNulls=false a null object fails against a non-null value
    assertFails(new GreaterNullObj());
    GreaterObj defaultNull = new GreaterObj();           // primitive, cannot be null -> irrelevant
    defaultNull.value = 11;
    assertPasses(defaultNull);
  }

  public static class GreaterOrEqualObj {
    @GreaterOrEqual("10") public int value;
  }

  @Test
  public void testGreaterOrEqual() {
    GreaterOrEqualObj eq = new GreaterOrEqualObj();
    eq.value = 10;
    assertPasses(eq);
    GreaterOrEqualObj bad = new GreaterOrEqualObj();
    bad.value = 9;
    assertFails(bad);
  }

  public static class LessObj {
    @Less("10") public int value;
  }

  @Test
  public void testLess() {
    LessObj eq = new LessObj();
    eq.value = 10;
    assertFails(eq);                                     // 10 is not < 10
    LessObj ok = new LessObj();
    ok.value = 5;
    assertPasses(ok);
  }

  public static class LessNullObj {
    @Less(value = "$limit", ignoreNulls = false) public Integer value = 5;
    public Integer getLimit() { return null; }
  }

  @Test
  public void testLessIgnoreNulls() {
    // with ignoreNulls=false null is smaller than any value: nothing is less than null
    assertFails(new LessNullObj());
  }

  public static class SizeArrayObj {
    @Size(min = 1, max = 2) public String[] array = { "a", "b", "c" };
  }

  @Test
  public void testSizeArray() {
    assertFails(new SizeArrayObj());
    SizeArrayObj ok = new SizeArrayObj();
    ok.array = new String[] { "a" };
    assertPasses(ok);
  }

  public static class NotEmptyArrayObj {
    @NotEmpty public int[] array = {};
  }

  @Test
  public void testNotEmptyArray() {
    assertFails(new NotEmptyArrayObj());
    NotEmptyArrayObj ok = new NotEmptyArrayObj();
    ok.array = new int[] { 1 };
    assertPasses(ok);
  }

  public static class LessOrEqualObj {
    @LessOrEqual("10") public int value;
  }

  @Test
  public void testLessOrEqual() {
    LessOrEqualObj eq = new LessOrEqualObj();
    eq.value = 10;
    assertPasses(eq);
    LessOrEqualObj bad = new LessOrEqualObj();
    bad.value = 11;
    assertFails(bad);
  }


  // ---- Pattern -------------------------------------------------------------

  public static class PatternObj {
    @Pattern("bl.*") public String text;
  }

  @Test
  public void testPattern() {
    PatternObj ok = new PatternObj();
    ok.text = "blah";
    assertPasses(ok);
    PatternObj bad = new PatternObj();
    bad.text = "xyz";
    assertFails(bad);
    // null passes because ignoreNulls defaults to true
    assertPasses(new PatternObj());
  }


  // ---- Size ----------------------------------------------------------------

  public static class SizeObj {
    @Size(min = 2, max = 4) public String text;
  }

  @Test
  public void testSize() {
    SizeObj ok = new SizeObj();
    ok.text = "abc";
    assertPasses(ok);
    SizeObj tooShort = new SizeObj();
    tooShort.text = "a";
    assertFails(tooShort);
    SizeObj tooLong = new SizeObj();
    tooLong.text = "abcde";
    assertFails(tooLong);
  }


  // ---- True / False --------------------------------------------------------

  public static class TrueObj {
    @True public boolean flag;
  }

  @Test
  public void testTrue() {
    TrueObj ok = new TrueObj();
    ok.flag = true;
    assertPasses(ok);
    assertFails(new TrueObj());                          // false
  }

  public static class FalseObj {
    @False public boolean flag = true;
  }

  @Test
  public void testFalse() {
    assertFails(new FalseObj());                         // true
    FalseObj ok = new FalseObj();
    ok.flag = false;
    assertPasses(ok);
  }


  // ---- Mandatory / Changeable (no result, but binding evaluators) ----------

  public static class MandatoryObj {
    @Mandatory public String text;
  }

  @Test
  public void testMandatoryProducesNoResult() {
    assertPasses(new MandatoryObj());
    assertPasses(new MandatoryObj() {{ text = "x"; }});
  }

  @Test
  public void testMandatoryBindingEvaluator() {
    MandatoryImpl validator = (MandatoryImpl) singleFieldValidator(MandatoryObj.class);
    ValidationContext ctx = context(new MandatoryObj(), null);
    assertTrue(validator.isMandatory(ctx));
    assertFalse(validator.isMandatoryDynamic());
  }

  public static class ChangeableObj {
    @Changeable public String text;
  }

  @Test
  public void testChangeableBindingEvaluator() {
    ChangeableImpl validator = (ChangeableImpl) singleFieldValidator(ChangeableObj.class);
    ValidationContext ctx = context(new ChangeableObj(), null);
    assertTrue(validator.isChangeable(ctx));
    assertFalse(validator.isChangeableDynamic());
    assertTrue(validator.validate(ctx).isEmpty());
  }

  @Test
  public void testNotNullBindingEvaluator() {
    NotNullImpl validator = (NotNullImpl) singleFieldValidator(NotNullObj.class);
    // null -> considered mandatory (validation would fail)
    assertTrue(validator.isMandatory(context(new NotNullObj(), null)));
    NotNullObj filled = new NotNullObj();
    filled.text = "x";
    assertFalse(validator.isMandatory(context(filled, "x")));
  }


  // ---- lazy objects --------------------------------------------------------

  public static class LazyObj {
    int loads;
    public boolean isSlave() { return false; }
    @NotNull(condition = "$slave")
    public String getLazy() { loads++; return null; }
  }

  /**
   * The element is supplied lazily: a reference condition that does not apply must not retrieve it.
   */
  @Test
  public void testReferenceConditionDoesNotLoadObject() {
    LazyObj obj = new LazyObj();
    assertPasses(obj);
    assertEquals(obj.loads, 0, "lazy object retrieved although the condition does not apply");
  }


  // ---- Validate ------------------------------------------------------------

  public static class ValidateNullObj {
    @Validate public List<String> list;
  }

  @Test
  public void testValidateNull() {
    assertPasses(new ValidateNullObj());                 // null -> nothing to validate
  }

  public static class ValidateNotValidateableObj {
    @Validate public String text = "not validateable";
  }

  @Test
  public void testValidateNotValidateableThrows() {
    assertThrows(ValidationRuntimeException.class, () -> validate(new ValidateNotValidateableObj()));
  }

  /**
   * A component providing its own validation.
   */
  public static class OwnValidation extends NotNullObj implements Validateable {
    boolean ownValidationInvoked;

    @Override
    public List<ValidationResult> validate(String validationPath, ValidationScope scope) {
      ownValidationInvoked = true;
      return Validateable.super.validate(validationPath, scope);
    }
  }

  public static class ValidateValidateableObj {
    @Validate public OwnValidation component = new OwnValidation();
  }

  @Test
  public void testValidateInvokesOwnValidation() {
    ValidateValidateableObj obj = new ValidateValidateableObj();
    List<ValidationResult> results = validate(obj);
    assertTrue(obj.component.ownValidationInvoked, "Validateable.validate() not invoked");
    assertEquals(results.size(), 1);                     // the null text of the component
    assertEquals(results.getFirst().getValidationPath(), "o.component.text");
  }

  public static class ValidateCollectionObj {
    @Validate public List<NotNullObj> children = new ArrayList<>();
  }

  @Test
  public void testValidateCollection() {
    ValidateCollectionObj obj = new ValidateCollectionObj();
    obj.children.add(new NotNullObj());                  // has a null text -> fails
    List<ValidationResult> results = validate(obj);
    assertEquals(results.size(), 1);
    assertTrue(results.getFirst().hasFailed());
    assertTrue(results.getFirst().getValidationPath().contains("[0]"), results.getFirst().getValidationPath());
  }


  // ---- Fail ----------------------------------------------------------------

  public static class FailIgnoredObj {
    @Fail(Fail.Results.IGNORED) public String text;
  }

  @Test
  public void testFailAlwaysAborts() {
    assertThrows(ValidationFailedException.class, () -> validate(new FailIgnoredObj()));
  }

  public static class FailOnFailedObj {
    @NotNull
    @Fail
    public String text;
  }

  @Test
  public void testFailAbortsOnPriorFailure() {
    // text is null: NotNull fails first, then Fail aborts
    assertThrows(ValidationFailedException.class, () -> validate(new FailOnFailedObj()));
    // text set: NotNull passes, Fail(FAILED) sees no failure -> no abort, no results
    FailOnFailedObj ok = new FailOnFailedObj();
    ok.text = "x";
    assertPasses(ok);
  }


  // ---- severity ------------------------------------------------------------

  public static class MinorSeverityObj {
    @NotNull(severity = MinorSeverity.class) public String text;
  }

  @Test
  public void testMinorSeverityDoesNotFail() {
    List<ValidationResult> results = validate(new MinorSeverityObj());
    assertEquals(results.size(), 1);
    ValidationResult result = results.getFirst();
    assertFalse(result.hasFailed());        // minor severity -> informational
    assertTrue(result.hasMessage());
  }


  // ---- helpers -------------------------------------------------------------

  private static Validator singleFieldValidator(Class<?> clazz) {
    List<Validator> validators = ValidatorCache.getInstance().getFieldValidators(clazz);
    assertEquals(validators.size(), 1, "expected a single validator for " + clazz);
    return validators.getFirst();
  }

  private static ValidationContext context(Object parent, Object value) {
    return ValidationContextFactory.getInstance().create(
        "o", value == null ? Object.class : value.getClass(), value, parent,
        ValidationScopeFactory.getInstance().getAllScope(), new ArrayList<>());
  }
}

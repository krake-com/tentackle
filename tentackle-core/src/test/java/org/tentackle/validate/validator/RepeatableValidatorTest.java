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

import org.tentackle.validate.Validator;
import org.tentackle.validate.ValidatorCache;
import org.testng.annotations.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the repeatable-validator support: {@link AbstractRepeatableValidator}
 * and the extraction of repeated annotations into individual validators.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class RepeatableValidatorTest {

  /**
   * A field carrying two repeated {@link Greater} annotations, which the compiler
   * wraps into a {@link GreaterContainer}.
   */
  static class Fixtures {
    @Greater("10")
    @Greater("20")
    int value;
  }

  /**
   * A minimal concrete repeatable validator over the {@link GreaterContainer}.
   */
  static class TestRepeatableValidator extends AbstractRepeatableValidator<GreaterContainer> {
    @Override
    public Annotation[] getAnnotations() {
      return annotation == null ? null : annotation.value();
    }
  }


  @Test
  public void testAbstractRepeatableValidator() throws Exception {
    Field field = Fixtures.class.getDeclaredField("value");
    GreaterContainer container = field.getAnnotation(GreaterContainer.class);
    assertEquals(container.value().length, 2);

    TestRepeatableValidator validator = new TestRepeatableValidator();
    validator.setAnnotation(container);
    assertSame(validator.getAnnotation(), container);
    assertEquals(validator.getAnnotations().length, 2);
    assertEquals(validator.toString(), TestRepeatableValidator.class.getName());
  }

  @Test
  public void testRepeatedAnnotationsBecomeSeparateValidators() {
    // both @Greater annotations must be materialized as two GreaterImpl validators
    var validators = ValidatorCache.getInstance().getFieldValidators(Fixtures.class);
    assertEquals(validators.size(), 2);
    for (Validator validator : validators) {
      assertTrue(validator instanceof GreaterImpl);
      assertEquals(validator.getValidatedElementName(), "value");
    }
    // the two carry the distinct configured values
    assertTrue(validators.stream().anyMatch(v -> "10".equals(v.getValue())));
    assertTrue(validators.stream().anyMatch(v -> "20".equals(v.getValue())));
  }
}

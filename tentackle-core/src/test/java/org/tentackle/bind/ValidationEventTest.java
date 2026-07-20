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

import org.tentackle.validate.ValidationResult;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

/**
 * Tests the {@link ValidationEvent}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ValidationEventTest {

  private static Binding stubBinding() {
    return (Binding) Proxy.newProxyInstance(
        ValidationEventTest.class.getClassLoader(),
        new Class<?>[]{ Binding.class },
        (_, method, _) -> "toString".equals(method.getName()) ? "stubBinding" : null);
  }


  @Test
  public void testAccessors() {
    Binding source = stubBinding();
    List<ValidationResult> results = List.of();

    ValidationEvent event = new ValidationEvent(source, results);

    assertSame(event.getSource(), source);
    assertSame(event.getValidationResults(), results);
  }

  @Test
  public void testToStringDoesNotThrow() {
    ValidationEvent event = new ValidationEvent(stubBinding(), List.of());
    assertNotNull(event.toString());
  }
}

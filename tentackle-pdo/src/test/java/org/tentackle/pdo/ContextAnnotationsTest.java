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

package org.tentackle.pdo;

import org.testng.annotations.Test;

import static org.testng.Assert.assertNull;

/**
 * Unit tests for {@link WithinContext} and {@link NotWithinContext}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ContextAnnotationsTest {

  /**
   * The empty name is always within the context, so a default would turn the bare annotations into a
   * no-op ({@code @WithinContext}) or an unconditional failure ({@code @NotWithinContext}).
   *
   * @throws NoSuchMethodException if no value method
   */
  @Test
  public void testContextNamesAreRequired() throws NoSuchMethodException {
    assertNull(WithinContext.class.getMethod("value").getDefaultValue());
    assertNull(NotWithinContext.class.getMethod("value").getDefaultValue());
  }

}

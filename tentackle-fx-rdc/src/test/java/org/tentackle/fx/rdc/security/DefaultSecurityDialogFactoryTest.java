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

package org.tentackle.fx.rdc.security;

import org.testng.annotations.Test;

import org.tentackle.fx.rdc.RdcRuntimeException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the application-specific hooks of {@link DefaultSecurityDialogFactory},
 * which must be overridden by the application.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultSecurityDialogFactoryTest {

  private final DefaultSecurityDialogFactory factory = new DefaultSecurityDialogFactory();

  @Test
  public void testDomainContextNotUsedByDefault() {
    assertFalse(factory.isDomainContextUsed());
  }

  @Test
  public void testGranteeSelectionNotImplemented() {
    expectThrows(RdcRuntimeException.class, () -> factory.selectGrantee(null, null, _ -> {}));
  }

  @Test
  public void testContextSelectionNotImplemented() {
    expectThrows(RdcRuntimeException.class, () -> factory.selectDomainContextObject(null, null, _ -> {}));
  }

}

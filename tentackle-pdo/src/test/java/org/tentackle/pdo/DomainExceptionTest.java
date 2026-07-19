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

import org.tentackle.session.Session;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for {@link DomainException#extractDomainException(Throwable)}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DomainExceptionTest {

  @Test
  public void testExtractsDirectDomainException() {
    DomainException dex = new DomainException((Session) null, "boom");
    assertSame(DomainException.extractDomainException(dex), dex);
  }

  @Test
  public void testExtractsNestedDomainException() {
    DomainException dex = new DomainException((Session) null, "boom");
    Throwable wrapped = new RuntimeException("outer", new IllegalStateException("middle", dex));
    assertSame(DomainException.extractDomainException(wrapped), dex);
  }

  @Test
  public void testReturnsFirstDomainExceptionInChain() {
    DomainException inner = new DomainException((Session) null, "inner");
    DomainException outer = new DomainException((Session) null, "outer", inner);
    // walking from the outer must return the outer one (first match encountered)
    assertSame(DomainException.extractDomainException(outer), outer);
  }

  @Test
  public void testReturnsNullWhenAbsent() {
    Throwable noDomain = new RuntimeException("outer", new IllegalStateException("no domain here"));
    assertNull(DomainException.extractDomainException(noDomain));
  }

  @Test
  public void testReturnsNullForNull() {
    assertNull(DomainException.extractDomainException(null));
  }

}

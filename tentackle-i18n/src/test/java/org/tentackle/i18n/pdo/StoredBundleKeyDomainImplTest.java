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

package org.tentackle.i18n.pdo;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.pdo.Pdo;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for {@link StoredBundleKeyDomainImpl}.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class StoredBundleKeyDomainImplTest {

  private StoredBundleKey bundleKey;

  @BeforeMethod
  public void setUp() {
    bundleKey = Pdo.create(StoredBundleKey.class);
  }


  @Test
  public void testFactoryWiresTheDomainDelegate() {
    assertEquals(bundleKey.getDomainDelegate().getClass(), StoredBundleKeyDomainImpl.class);
  }

  @Test
  public void testFactoryWiresThePersistenceMock() {
    assertEquals(bundleKey.getPersistenceDelegate().getClass(), StoredBundleKeyPersistenceMock.class);
  }

  @Test
  public void testToStringIsTheKey() {
    bundleKey.setKey("greeting");
    bundleKey.setValue("hello");
    assertEquals(bundleKey.toString(), "greeting");
  }

  @Test
  public void testToStringOfVirginKey() {
    assertNull(bundleKey.toString());
  }

  @Test
  public void testUniqueDomainKeyIsNotProvided() {
    assertFalse(bundleKey.isUniqueDomainKeyProvided());
  }

  @Test
  public void testBundleRelation() {
    StoredBundle bundle = Pdo.create(StoredBundle.class);
    bundleKey.setBundle(bundle);
    assertSame(bundleKey.getBundle(), bundle);
  }

  @Test
  public void testSingularAndPlural() {
    assertEquals(bundleKey.getSingular(), "bundle key");
    assertEquals(bundleKey.getPlural(), "bundle keys");
  }

}

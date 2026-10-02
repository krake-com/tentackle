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

package org.tentackle.app;

import org.testng.annotations.Test;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.misc.Identifiable;
import org.tentackle.pdo.DomainContext;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityManager;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link AbstractApplication}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractApplicationTest {

  /**
   * An application that does nothing.
   */
  private static class TestApplication extends AbstractApplication {

    TestApplication(EncryptedProperties props) {
      super("test", "1.0");
      setProperties(props);
    }

    @Override
    protected void startup() {
      // nothing to do
    }

    @Override
    public <U extends Identifiable> U getUser(DomainContext context, long userId) {
      return null;
    }
  }

  @Test
  public void testSecurityManagerEnabledByDefault() {
    SecurityManager manager = SecurityFactory.getInstance().getSecurityManager();
    boolean wasEnabled = manager.isEnabled();
    try {
      manager.setEnabled(false);
      new TestApplication(new EncryptedProperties()).configureSecurityManager();
      assertTrue(manager.isEnabled());
    }
    finally {
      manager.setEnabled(wasEnabled);
    }
  }

  @Test
  public void testNoSecurityDisablesSecurityManager() {
    SecurityManager manager = SecurityFactory.getInstance().getSecurityManager();
    boolean wasEnabled = manager.isEnabled();
    try {
      EncryptedProperties props = new EncryptedProperties();
      props.setProperty("NoSecurity", "");   // case-insensitive
      new TestApplication(props).configureSecurityManager();
      assertFalse(manager.isEnabled());
    }
    finally {
      manager.setEnabled(wasEnabled);
    }
  }

}

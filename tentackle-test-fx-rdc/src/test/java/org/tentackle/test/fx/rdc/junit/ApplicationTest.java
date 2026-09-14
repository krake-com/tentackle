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

package org.tentackle.test.fx.rdc.junit;

import org.junit.jupiter.api.Test;

import org.tentackle.app.Application;
import org.tentackle.common.Version;
import org.tentackle.pdo.junit.TestApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies the Junit {@link TestApplication} with more than one test method.
 * <p>
 * Lives here rather than in tentackle-test-pdo, because the in-memory database of that module
 * survives the session and is already populated by the AbstractPdoTest.
 */
public class ApplicationTest extends TestApplication {

  /**
   * Creates the test.
   */
  public ApplicationTest() {
    super("junit-application-test", Version.RELEASE);
  }

  @Test
  public void applicationRegistered() {
    assertSame(this, Application.getInstance());
  }

  @Test
  public void sessionAvailable() {
    assertSame(this, Application.getInstance());
    assertNotNull(getSession());
    assertNotNull(getDomainContext());
  }

}

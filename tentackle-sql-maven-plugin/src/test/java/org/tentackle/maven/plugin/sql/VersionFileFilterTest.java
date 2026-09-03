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

package org.tentackle.maven.plugin.sql;

import org.testng.annotations.Test;

import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class VersionFileFilterTest {

  /**
   * Without any {@code @Service}-registered alternative on the classpath, the singleton defaults
   * to {@link SemanticVersionFileFilter} - and stays the same instance across calls.
   */
  @Test
  public void defaultsToSemanticVersioning() {
    VersionFileFilter instance = VersionFileFilter.getInstance();

    assertTrue(instance instanceof SemanticVersionFileFilter);
    assertSame(VersionFileFilter.getInstance(), instance);
  }

}

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

import org.testng.Assert;
import org.testng.annotations.Test;

public class SemanticVersionFileFilterTest {

  @Test
  public void testVersions() {
    SemanticVersionFileFilter fileFilter = new SemanticVersionFileFilter();

    Assert.assertTrue(fileFilter.isHintApplying("0.9.9", "1.0"));
    Assert.assertTrue(fileFilter.isHintApplying("0.9.9", "1.0-SNAPSHOT"));
    Assert.assertTrue(fileFilter.isHintApplying("1.0.0", "1.0.1"));
    Assert.assertFalse(fileFilter.isHintApplying("1.0.0", "1.0-SNAPSHOT"));
    Assert.assertFalse(fileFilter.isHintApplying("1.2.3", "1.2-SNAPSHOT"));
    Assert.assertTrue(fileFilter.isHintApplying("1.2.3", "1.3"));
    Assert.assertFalse(fileFilter.isHintApplying("1.2.3", "1.2.3"));
    Assert.assertFalse(fileFilter.isHintApplying("1.2.3", "1_2_3"));
    Assert.assertTrue(fileFilter.isHintApplying("1.2.3", "1.2.4"));
    Assert.assertFalse(fileFilter.isHintApplying("1.2.3", "1.2.3-SNAPSHOT"));
    Assert.assertTrue(fileFilter.isHintApplying("1.2.3", "1.3-SNAPSHOT"));
    Assert.assertFalse(fileFilter.isHintApplying("3.2.0", "3.2-SNAPSHOT"));
    Assert.assertFalse(fileFilter.isHintApplying("3.0.0", "3-SNAPSHOT"));
    Assert.assertFalse(fileFilter.isHintApplying("3.2.0", "3.2.0-SNAPSHOT"));
    Assert.assertFalse(fileFilter.isHintApplying("3.2.0", "3.1.9"));
    Assert.assertTrue(fileFilter.isHintApplying("3.1.9", "3.2.0-M3"));
    Assert.assertFalse(fileFilter.isHintApplying("3.2.0", ""));
    Assert.assertFalse(fileFilter.isHintApplying("", ""));
    Assert.assertTrue(fileFilter.isHintApplying("", "1.0"));
    Assert.assertTrue(fileFilter.isHintApplying("", "x"));
    Assert.assertFalse(fileFilter.isHintApplying("x", ""));
    Assert.assertFalse(fileFilter.isHintApplying("x", "1.0"));
  }
}

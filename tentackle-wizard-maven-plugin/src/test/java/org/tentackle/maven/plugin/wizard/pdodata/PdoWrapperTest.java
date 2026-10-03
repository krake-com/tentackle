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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.testng.annotations.Test;

import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.Article;
import org.tentackle.maven.plugin.wizard.pdodata.PdoStub.Code;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;

/**
 * Tests the PDO wrapper and the data list.
 */
public class PdoWrapperTest {

  /**
   * Reloading a PDO clears all cached data.
   */
  @Test
  public void testReloadClearsCaches() {
    Article reloaded = PdoStub.create(Article.class, 1, 2, true, new Code("new"), null);
    Article article = PdoStub.create(Article.class, 1, 1, true, new Code("old"), () -> reloaded);
    PdoWrapper wrapper = new PdoWrapper(article);
    assertEquals(wrapper.getUDK(), "old");
    wrapper.reload();
    assertEquals(wrapper.getUDK(), "new");
  }

  /**
   * A data list without a model relation is neither hidden nor tied to an entity.
   */
  @Test
  public void testListWithoutRelation() {
    DataList list = new DataList("Article", "articles", null, List.of(), null, null);
    assertFalse(list.isHidden());
    assertNull(list.getEntity());
    DataList nullList = new DataList("Article", "articles", null, null, null, null);
    assertNull(nullList.getValue());
  }

}

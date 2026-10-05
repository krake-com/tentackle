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

package org.tentackle.fx.rdc;

import org.testng.annotations.Test;

import org.tentackle.fx.rdc.PdoStubs.Alpha;
import org.tentackle.fx.rdc.PdoStubs.Beta;

import javafx.scene.control.TreeItem;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link PdoTreeItem}: parent navigation, resetting the PDO and the lazy
 * loading of the child items.
 * <p>
 * No JavaFX toolkit is needed: {@link TreeItem} is not a control. The GUI provider is
 * replaced by a stub (see {@link StubbedPdoTreeItem}).
 */
@SuppressWarnings("missing-explicit-ctor")
public class PdoTreeItemTest {

  @Test
  public void testPdoAndValue() {
    Alpha alpha = PdoStubs.create(Alpha.class);
    PdoTreeItem<Alpha> item = new StubbedPdoTreeItem<>(alpha);
    assertSame(item.getPdo(), alpha);
    assertSame(item.getValue(), alpha);
  }

  @Test
  public void testNoParent() {
    PdoTreeItem<Alpha> item = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class));
    assertNull(item.getParentPdoItem());
    assertNull(item.getParentPdo());
    assertNull(item.getParentPdo(Alpha.class));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testNonPdoParent() {
    TreeItem parent = new TreeItem<>("not a PDO item");
    PdoTreeItem<Alpha> item = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class));
    parent.getChildren().add(item);
    assertSame(item.getParent(), parent);
    assertNull(item.getParentPdoItem(), "a plain tree item is no PDO parent");
    assertNull(item.getParentPdo());
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testPdoParent() {
    Beta beta = PdoStubs.create(Beta.class);
    PdoTreeItem<Beta> parent = new StubbedPdoTreeItem<>(beta);
    PdoTreeItem<Alpha> item = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class));
    ((TreeItem) parent).getChildren().add(item);

    assertSame(item.getParentPdoItem(), parent);
    assertSame(item.getParentPdo(), beta);
    assertSame(item.getParentPdo(Beta.class), beta);
    assertNull(item.getParentPdo(Alpha.class), "parent of another PDO type must not match");
  }

  @Test
  public void testProviderWithoutChildrenIsLeaf() {
    StubbedPdoTreeItem<Alpha> item = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class), false);
    assertTrue(item.isLeaf());
    assertTrue(item.getChildren().isEmpty());
    assertTrue(item.getChildRequests().isEmpty(), "children must not be requested if not provided");
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testChildrenAreLoadedLazilyAndOnce() {
    Beta beta = PdoStubs.create(Beta.class);
    PdoTreeItem<Beta> parent = new StubbedPdoTreeItem<>(beta);
    StubbedPdoTreeItem<Alpha> item = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class), true);
    ((TreeItem) parent).getChildren().add(item);

    assertFalse(item.isLeaf(), "may have children as long as not loaded");
    assertTrue(item.getChildRequests().isEmpty(), "isLeaf must not load the children");

    assertTrue(item.getChildren().isEmpty());
    assertEquals(item.getChildRequests().size(), 1);
    assertSame(item.getChildRequests().getFirst(), beta, "children are requested with respect to the parent PDO");
    assertTrue(item.isLeaf(), "no children loaded -> leaf");

    item.getChildren();
    assertEquals(item.getChildRequests().size(), 1, "children must be loaded only once");
  }

  @Test
  public void testSetPdoResetsChildren() {
    Alpha first = PdoStubs.create(Alpha.class);
    StubbedPdoTreeItem<Alpha> item = new StubbedPdoTreeItem<>(first, true);
    item.getChildren();
    assertEquals(item.getChildRequests().size(), 1);

    Alpha second = PdoStubs.create(Alpha.class);
    item.setPdo(second);
    assertSame(item.getPdo(), second);
    assertSame(item.getValue(), second);
    assertFalse(item.isLeaf(), "children must be reloaded after the PDO changed");

    item.getChildren();
    assertEquals(item.getChildRequests().size(), 2, "children reloaded for the new PDO");
  }

}

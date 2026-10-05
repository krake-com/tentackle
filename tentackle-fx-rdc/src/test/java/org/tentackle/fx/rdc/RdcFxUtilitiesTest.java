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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the recursion guard {@link RdcFxUtilities#isValueInParentPath(TreeItem)}.
 * <p>
 * For PDO tree items, the guard compares the PDO <em>type</em> instead of the PDO identity,
 * so that expanding a tree does not traverse the whole database.
 * No JavaFX toolkit is needed: {@link TreeItem} is not a control.
 */
@SuppressWarnings("missing-explicit-ctor")
public class RdcFxUtilitiesTest {

  private final RdcFxUtilities utilities = new RdcFxUtilities();

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testSamePdoTypeInParentPath() {
    TreeItem root = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class));
    TreeItem middle = new StubbedPdoTreeItem<>(PdoStubs.create(Beta.class));
    TreeItem leaf = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class));    // another Alpha instance
    root.getChildren().add(middle);
    middle.getChildren().add(leaf);

    assertTrue(utilities.isValueInParentPath(leaf), "an Alpha below an Alpha must be detected");
    assertFalse(utilities.isValueInParentPath(middle), "no Beta above the Beta");
    assertFalse(utilities.isValueInParentPath(root), "the root has no parents");
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testParentsWithoutValueAreSkipped() {
    TreeItem root = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class));
    TreeItem empty = new TreeItem<>();
    TreeItem leaf = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class));
    root.getChildren().add(empty);
    empty.getChildren().add(leaf);

    assertTrue(utilities.isValueInParentPath(leaf), "parents without a value must be skipped");
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testItemWithoutPdo() {
    TreeItem root = new StubbedPdoTreeItem<>(PdoStubs.create(Alpha.class));
    TreeItem leaf = new StubbedPdoTreeItem<>((Alpha) null);
    root.getChildren().add(leaf);
    assertFalse(utilities.isValueInParentPath(leaf));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testPlainTreeItemsCompareByEquality() {
    // the same PDO type, but in plain tree items: the default (equality-based) guard applies
    TreeItem root = new TreeItem<>(PdoStubs.create(Alpha.class));
    TreeItem leaf = new TreeItem<>(PdoStubs.create(Alpha.class));
    root.getChildren().add(leaf);
    assertFalse(utilities.isValueInParentPath(leaf), "different PDOs are not equal");

    TreeItem sameValue = new TreeItem<>(root.getValue());
    leaf.getChildren().add(sameValue);
    assertTrue(utilities.isValueInParentPath(sameValue), "the same PDO must be detected");
  }

}

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

package org.tentackle.fx.table.filter;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.fx.table.filter.FilterTestSupport.Row;
import org.tentackle.fx.table.filter.FilterTestSupport.TestTableFilter;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the lazy per-level tree filtering.<p>
 * Only {@link TreeItem}s are created, no controls, so that the tests run without a JavaFX toolkit.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class TreeItemFilterTest {

  /**
   * A tree item counting how often its children were requested, i.e. how often it would have
   * been loaded lazily.
   */
  private static class CountingTreeItem extends TreeItem<Row> {

    private int childrenRequested;

    CountingTreeItem(Row value) {
      super(value);
    }

    @Override
    public ObservableList<TreeItem<Row>> getChildren() {
      childrenRequested++;
      return super.getChildren();
    }
  }


  private TestTableFilter filter;
  private TableColumnFilter<Row,String> cityFilter;
  private TreeItemFilter<Row> treeFilter;
  private CountingTreeItem root;


  @BeforeMethod
  public void setUp() {
    DefaultTableConfiguration<Row> configuration = FilterTestSupport.createTableConfiguration();
    filter = new TestTableFilter(configuration);
    filter.register("city", Row::city);
    cityFilter = filter.getColumnFilter(
            FilterTestSupport.createColumnConfiguration(configuration, "city", String.class));
    treeFilter = new TreeItemFilter<>(filter);
    root = new CountingTreeItem(null);
    treeFilter.setRoot(root);
  }

  private CountingTreeItem addChild(TreeItem<Row> parent, String city) {
    CountingTreeItem item = new CountingTreeItem(new Row(city, 0, false));
    parent.getChildren().add(item);
    return item;
  }

  private List<String> cities(TreeItem<Row> parent) {
    List<String> cities = new ArrayList<>();
    for (TreeItem<Row> child : parent.getChildren()) {
      cities.add(child.getValue().city());
    }
    return cities;
  }


  @Test
  public void testFiltersTopLevel() {
    addChild(root, "Hamburg");
    addChild(root, "Berlin");
    addChild(root, "München");

    cityFilter.setSelectedTexts(List.of("Hamburg", "München"));
    treeFilter.filterTree();

    assertEquals(cities(root), List.of("Hamburg", "München"));
  }

  @Test
  public void testHiddenItemsReappearAtTheirIndex() {
    addChild(root, "Hamburg");
    addChild(root, "Berlin");
    addChild(root, "München");

    cityFilter.setSelectedTexts(List.of("Hamburg"));
    treeFilter.filterTree();
    assertEquals(cities(root), List.of("Hamburg"));

    cityFilter.clear();
    treeFilter.filterTree();
    assertEquals(cities(root), List.of("Hamburg", "Berlin", "München"));
  }

  @Test
  public void testShowAllRestoresEverything() {
    addChild(root, "Hamburg");
    addChild(root, "Berlin");

    cityFilter.setSelectedTexts(List.of("Hamburg"));
    treeFilter.filterTree();
    assertEquals(cities(root), List.of("Hamburg"));

    treeFilter.showAll();
    assertEquals(cities(root), List.of("Hamburg", "Berlin"));
  }

  @Test
  public void testCollapsedChildrenAreNotLoaded() {
    CountingTreeItem hamburg = addChild(root, "Hamburg");
    CountingTreeItem berlin = addChild(root, "Berlin");
    addChild(hamburg, "Berlin");     // would pass the filter, but must not be looked at
    addChild(berlin, "Hamburg");
    hamburg.childrenRequested = 0;
    berlin.childrenRequested = 0;

    cityFilter.setSelectedTexts(List.of("Hamburg"));
    treeFilter.filterTree();

    assertEquals(cities(root), List.of("Hamburg"));
    assertEquals(hamburg.childrenRequested, 0, "collapsed item must not be materialized");
    assertEquals(berlin.childrenRequested, 0, "hidden item must not be materialized");
  }

  @Test
  public void testChildrenAreFilteredWhenExpanded() {
    CountingTreeItem hamburg = addChild(root, "Hamburg");
    addChild(hamburg, "Hamburg");
    addChild(hamburg, "Berlin");

    cityFilter.setSelectedTexts(List.of("Hamburg"));
    treeFilter.filterTree();
    assertEquals(cities(hamburg), List.of("Hamburg", "Berlin"), "not filtered while collapsed");

    hamburg.setExpanded(true);
    assertEquals(cities(hamburg), List.of("Hamburg"), "filtered on expand");
  }

  @Test
  public void testExpandedChildrenAreRefilteredOnFilterChange() {
    CountingTreeItem hamburg = addChild(root, "Hamburg");
    addChild(hamburg, "Hamburg");
    addChild(hamburg, "Berlin");

    cityFilter.setSelectedTexts(List.of("Hamburg", "Berlin"));
    treeFilter.filterTree();
    hamburg.setExpanded(true);
    assertEquals(cities(hamburg), List.of("Hamburg", "Berlin"));

    cityFilter.setSelectedTexts(List.of("Hamburg"));
    treeFilter.filterTree();
    assertEquals(cities(hamburg), List.of("Hamburg"));
  }

  @Test
  public void testNodeLoopTerminates() {
    // the same row object appears in its own path, as it may happen with self-referencing PDOs
    Row row = new Row("Hamburg", 0, false);
    TreeItem<Row> first = new TreeItem<>(row);
    TreeItem<Row> second = new TreeItem<>(row);
    root.getChildren().add(first);
    first.getChildren().add(second);
    second.getChildren().add(new TreeItem<>(new Row("Berlin", 0, false)));
    first.setExpanded(true);
    second.setExpanded(true);

    cityFilter.setSelectedTexts(List.of("Hamburg"));
    treeFilter.filterTree();    // must terminate

    assertEquals(cities(root), List.of("Hamburg"));
    assertEquals(second.getChildren().size(), 1, "the looping level must be left alone");
  }

  @Test
  public void testCollectRowsCoversHiddenAndMaterializedItems() {
    CountingTreeItem hamburg = addChild(root, "Hamburg");
    addChild(root, "Berlin");
    addChild(hamburg, "München");

    cityFilter.setSelectedTexts(List.of("Hamburg"));
    treeFilter.filterTree();
    List<String> rows = treeFilter.collectRows().stream().map(Row::city).toList();
    // the collapsed child of Hamburg is not materialized, but the hidden Berlin is still offered
    assertEquals(rows, List.of("Hamburg", "Berlin"));

    hamburg.setExpanded(true);
    rows = treeFilter.collectRows().stream().map(Row::city).toList();
    assertTrue(rows.contains("München"), "expanded level must be included: " + rows);
  }

  @Test
  public void testItemsAddedByTheApplicationAreFiltered() {
    addChild(root, "Hamburg");
    cityFilter.setSelectedTexts(List.of("Hamburg"));
    treeFilter.filterTree();

    addChild(root, "Berlin");
    assertEquals(cities(root), List.of("Hamburg", "Berlin"), "not filtered yet");

    treeFilter.invalidate(root);
    treeFilter.filterTree();
    assertEquals(cities(root), List.of("Hamburg"));
  }

}

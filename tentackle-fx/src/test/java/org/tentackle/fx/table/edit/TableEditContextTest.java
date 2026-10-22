/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx.table.edit;

import org.testng.annotations.Test;

import org.tentackle.fx.table.edit.EditTestSupport.Line;
import org.tentackle.fx.table.edit.EditTestSupport.TestEditContext;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class TableEditContextTest {

  @Test
  public void testColumnsNotEditableByDefault() {
    TestEditContext context = new TestEditContext("a", "b");
    Line[] rows = context.addRows("1");
    assertNull(context.getEditHandler());
    assertFalse(context.isEditable(context.cell(rows[0], "a")));
    assertNull(context.firstEditable(rows[0]));
    context.column("b").setEditable(true);
    assertEquals(context.firstEditable(rows[0]).columnName(), "b");
  }

  @Test
  public void testEditHandlerMakesColumnsEditable() {
    TestEditContext context = new TestEditContext("a", "b");
    Line[] rows = context.addRows("1");
    context.column("b").setEditable(false);
    context.configuration.setEditHandler(new SimpleEditHandler<>());
    assertTrue(context.isEditable(context.cell(rows[0], "a")));
    assertFalse(context.isEditable(context.cell(rows[0], "b")), "explicitly not editable");
  }

  @Test
  public void testEditHandler() {
    TestEditContext context = new TestEditContext("a");
    assertNull(context.configuration.getEditHandler());
    TableEditHandler<Line> handler = new SimpleEditHandler<>();
    context.configuration.setEditHandler(handler);
    assertSame(context.configuration.getEditHandler(), handler);
    assertSame(context.getEditHandler(), handler);
    context.configuration.setEditHandler(null);
    assertNull(context.getEditHandler());
  }

  @Test
  public void testRowNavigationSkipsNonEditableCells() {
    TestEditContext context = new TestEditContext("a", "b", "c", "d");
    Line[] rows = context.addRows("1", "2");
    context.column("b").setEditable(false);
    // the handler excludes column c in row 1 only
    context.configuration.setEditHandler(new SimpleEditHandler<>() {
      @Override
      public boolean isCellEditable(TableEditContext<Line> ctx, TableCellRef<Line> cell) {
        return !(cell.row() == rows[0] && cell.columnName().equals("c"));
      }
    });

    assertEquals(context.next(context.cell(rows[0], "a")).columnName(), "d");
    assertEquals(context.next(context.cell(rows[1], "a")).columnName(), "c");
    assertNull(context.next(context.cell(rows[0], "d")));
    assertEquals(context.previous(context.cell(rows[0], "d")).columnName(), "a");
    assertEquals(context.previous(context.cell(rows[1], "d")).columnName(), "c");
    assertNull(context.previous(context.cell(rows[0], "a")));
    assertSame(context.next(context.cell(rows[1], "a")).row(), rows[1]);

    assertEquals(context.firstEditable(rows[0]).columnName(), "a");
    assertEquals(context.lastEditable(rows[0]).columnName(), "d");
  }

  @Test
  public void testColumnNavigationSkipsNonEditableCells() {
    TestEditContext context = new TestEditContext("a", "b");
    Line[] rows = context.addRows("1", "2", "3");
    context.configuration.setEditHandler(new SimpleEditHandler<>() {
      @Override
      public boolean isCellEditable(TableEditContext<Line> ctx, TableCellRef<Line> cell) {
        return cell.row() != rows[1];
      }
    });

    assertSame(context.below(context.cell(rows[0], "a")).row(), rows[2]);
    assertEquals(context.below(context.cell(rows[0], "a")).columnName(), "a");
    assertNull(context.below(context.cell(rows[2], "a")));
    assertSame(context.above(context.cell(rows[2], "b")).row(), rows[0]);
    assertNull(context.above(context.cell(rows[0], "b")));

    assertSame(context.firstEditableBelow(rows[0]).row(), rows[2]);
    assertEquals(context.firstEditableBelow(rows[0]).columnName(), "a");
    assertNull(context.firstEditableBelow(rows[2]));
    assertSame(context.lastEditableAbove(rows[2]).row(), rows[0]);
    assertEquals(context.lastEditableAbove(rows[2]).columnName(), "b");
    assertNull(context.lastEditableAbove(rows[0]));
  }

  @Test
  public void testRowsAreComparedByIdentity() {
    TestEditContext context = new TestEditContext("a");
    Line[] rows = context.addRows("1", "2");
    assertEquals(context.indexOf(rows[1]), 1);
    assertEquals(context.indexOf(new Line("2")), -1);
    assertEquals(context.getRows().size(), 2);
    assertSame(context.getRows().get(1), rows[1]);
    assertNull(context.below(context.cell(new Line("x"), "a")), "unknown row");
  }

}

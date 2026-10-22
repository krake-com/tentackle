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

import org.tentackle.fx.table.edit.EditTestSupport.Line;
import org.tentackle.fx.table.edit.EditTestSupport.TestEditContext;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class TableEditHandlerTest {

  private TestEditContext createContext(TableEditHandler<Line> handler) {
    TestEditContext context = new TestEditContext("a", "b");
    context.configuration.setEditHandler(handler);
    return context;
  }

  @Test
  public void testDefaults() {
    RowWiseEditHandler<Line> rowWise = new RowWiseEditHandler<>();
    assertTrue(rowWise.isWrapping());
    assertNull(rowWise.getRowFactory());
    RuleBasedEditHandler<Line> ruleBased = new RuleBasedEditHandler<>(rowWise);
    assertSame(ruleBased.getDelegate(), rowWise);
  }

  @Test
  public void testSimple() {
    TableEditHandler<Line> handler = new SimpleEditHandler<>();
    TestEditContext context = createContext(handler);
    Line[] rows = context.addRows("1", "2");
    assertNull(handler.committed(context, context.edit(rows[0], "a", TableEditTrigger.NEXT)));
    assertNull(handler.canceled(context, context.cell(rows[0], "a")));
    assertNull(handler.verify(context, context.edit(rows[0], "a", TableEditTrigger.NEXT)));
    assertTrue(handler.isCellEditable(context, context.cell(rows[0], "a")));
    assertFalse(handler.isCommitOnFocusLost(context, context.cell(rows[0], "a")));
  }

  @Test
  public void testRowWiseWrapping() {
    RowWiseEditHandler<Line> handler = new RowWiseEditHandler<>();
    TestEditContext context = createContext(handler);
    Line[] rows = context.addRows("1", "2");

    TableCellRef<Line> next = handler.committed(context, context.edit(rows[0], "a", TableEditTrigger.NEXT));
    assertSame(next.row(), rows[0]);
    assertEquals(next.columnName(), "b");

    next = handler.committed(context, context.edit(rows[0], "b", TableEditTrigger.NEXT));
    assertSame(next.row(), rows[1]);
    assertEquals(next.columnName(), "a");

    // end of table: stay
    next = handler.committed(context, context.edit(rows[1], "b", TableEditTrigger.NEXT));
    assertSame(next.row(), rows[1]);
    assertEquals(next.columnName(), "b");

    next = handler.committed(context, context.edit(rows[1], "a", TableEditTrigger.PREVIOUS));
    assertSame(next.row(), rows[0]);
    assertEquals(next.columnName(), "b");

    // beginning of table: stay
    next = handler.committed(context, context.edit(rows[0], "a", TableEditTrigger.PREVIOUS));
    assertSame(next.row(), rows[0]);
    assertEquals(next.columnName(), "a");

    assertNull(handler.committed(context, context.edit(rows[0], "a", TableEditTrigger.FOCUS_LOST)));
  }

  @Test
  public void testRowWiseNotWrapping() {
    RowWiseEditHandler<Line> handler = new RowWiseEditHandler<Line>().setWrapping(false);
    TestEditContext context = createContext(handler);
    Line[] rows = context.addRows("1", "2");

    assertEquals(handler.committed(context, context.edit(rows[0], "a", TableEditTrigger.NEXT)).columnName(), "b");
    assertNull(handler.committed(context, context.edit(rows[0], "b", TableEditTrigger.NEXT)));
    assertNull(handler.committed(context, context.edit(rows[1], "a", TableEditTrigger.PREVIOUS)));
  }

  @Test
  public void testRowWiseRowFactory() {
    Line created = new Line("new");
    RowWiseEditHandler<Line> handler = new RowWiseEditHandler<Line>().setRowFactory(() -> created);
    TestEditContext context = createContext(handler);
    Line[] rows = context.addRows("1", "2");

    // not the last row: no row is appended
    TableCellRef<Line> next = handler.committed(context, context.edit(rows[0], "b", TableEditTrigger.NEXT));
    assertSame(next.row(), rows[1]);
    assertEquals(context.getRowCount(), 2);

    next = handler.committed(context, context.edit(rows[1], "b", TableEditTrigger.NEXT));
    assertEquals(context.getRowCount(), 3);
    assertSame(context.getRow(2), created);
    assertSame(next.row(), created);
    assertEquals(next.columnName(), "a");

    // the factory may refuse to create a row
    handler.setRowFactory(() -> null);
    next = handler.committed(context, context.edit(created, "b", TableEditTrigger.NEXT));
    assertEquals(context.getRowCount(), 3);
    assertSame(next.row(), created);
    assertEquals(next.columnName(), "b");
  }

  @Test
  public void testColumnWise() {
    ColumnWiseEditHandler<Line> handler = new ColumnWiseEditHandler<>();
    TestEditContext context = createContext(handler);
    Line[] rows = context.addRows("1", "2");

    TableCellRef<Line> next = handler.committed(context, context.edit(rows[0], "b", TableEditTrigger.NEXT));
    assertSame(next.row(), rows[1]);
    assertEquals(next.columnName(), "b");

    // end of column: stay
    next = handler.committed(context, context.edit(rows[1], "b", TableEditTrigger.NEXT));
    assertSame(next.row(), rows[1]);

    next = handler.committed(context, context.edit(rows[1], "a", TableEditTrigger.PREVIOUS));
    assertSame(next.row(), rows[0]);
    assertEquals(next.columnName(), "a");

    assertNull(handler.committed(context, context.edit(rows[0], "a", TableEditTrigger.FOCUS_LOST)));
  }

  @Test
  public void testRuleBased() {
    List<String> log = new ArrayList<>();
    RuleBasedEditHandler<Line> handler = new RuleBasedEditHandler<Line>(new RowWiseEditHandler<>())
        .editable("b", (_, cell) -> !cell.row().name.equals("locked"))
        .commitOnFocusLost("a")
        .verify("a", (_, edit) -> "bad".equals(edit.newValue(String.class)) ? "rejected" : null)
        .onCommit((_, edit) -> log.add("any:" + edit.columnName()))
        .onCommit("a", (_, edit) -> log.add("first:" + edit.row()))
        .onCommit("a", (ctx, edit) -> {
          log.add("second:" + edit.row());
          ctx.insertRowAfter(edit.row(), new Line("inserted"));
        })
        .navigate("a", (ctx, edit) -> ctx.cell(ctx.getRow(ctx.indexOf(edit.row()) + 1), "b"))
        .onCancel("b", (ctx, cell) -> ctx.removeRow(cell.row()));
    TestEditContext context = createContext(handler);
    Line[] rows = context.addRows("1", "locked");

    assertTrue(handler.isCellEditable(context, context.cell(rows[0], "b")));
    assertFalse(handler.isCellEditable(context, context.cell(rows[1], "b")));
    assertTrue(handler.isCellEditable(context, context.cell(rows[1], "a")));
    assertNull(context.next(context.cell(rows[1], "a")), "the context consults the handler");

    assertTrue(handler.isCommitOnFocusLost(context, context.cell(rows[0], "a")));
    assertFalse(handler.isCommitOnFocusLost(context, context.cell(rows[0], "b")));

    assertNull(handler.verify(context, context.edit(rows[0], "a", TableEditTrigger.NEXT)));
    assertEquals(handler.verify(context, new TableCellEdit<>(context.cell(rows[0], "a"), "old", "bad", TableEditTrigger.NEXT)),
                 "rejected");
    assertNull(handler.verify(context, new TableCellEdit<>(context.cell(rows[0], "b"), "old", "bad", TableEditTrigger.NEXT)));

    // column with rules and its own navigation
    TableCellRef<Line> next = handler.committed(context, context.edit(rows[0], "a", TableEditTrigger.NEXT));
    assertEquals(log, List.of("any:a", "first:1", "second:1"));
    assertEquals(context.getRowCount(), 3);
    assertEquals(next.row().name, "inserted");
    assertEquals(next.columnName(), "b");

    // column without navigation rule: the decorated row-wise handler navigates
    log.clear();
    next = handler.committed(context, context.edit(rows[0], "b", TableEditTrigger.NEXT));
    assertEquals(log, List.of("any:b"));
    assertEquals(next.row().name, "inserted");
    assertEquals(next.columnName(), "a");

    assertNull(handler.canceled(context, context.cell(rows[0], "a")));
    assertEquals(context.getRowCount(), 3);
    assertNull(handler.canceled(context, context.cell(rows[0], "b")));
    assertEquals(context.getRowCount(), 2);
    assertEquals(context.indexOf(rows[0]), -1);
  }

  @Test
  public void testRuleBasedCommitOnFocusLostForAllColumns() {
    RuleBasedEditHandler<Line> handler = new RuleBasedEditHandler<Line>(new RowWiseEditHandler<>()).commitOnFocusLost();
    TestEditContext context = createContext(handler);
    Line[] rows = context.addRows("1");
    assertTrue(handler.isCommitOnFocusLost(context, context.cell(rows[0], "a")));
    assertTrue(handler.isCommitOnFocusLost(context, context.cell(rows[0], "b")));
  }

}

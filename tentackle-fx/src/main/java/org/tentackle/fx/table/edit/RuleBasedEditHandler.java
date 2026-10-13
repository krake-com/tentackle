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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Edit handler configured by rules per column.<br>
 * Decorates another handler, usually one that implements the navigation, with lambdas registered for the
 * names of the columns:
 * <pre>
 *   config.setEditHandler(Fx.&lt;InvoiceLine&gt;ruleBasedTableEditHandler()
 *       .editable("price", (ctx, cell) -&gt; !cell.row().isInvoiced())
 *       .commitOnFocusLost("quantity")
 *       .verify("quantity", (ctx, edit) -&gt; edit.newValue(Integer.class) &lt; 0 ? "must not be negative" : null)
 *       .onCommit("quantity", (ctx, edit) -&gt; edit.row().recalculate())
 *       .navigate("product", (ctx, edit) -&gt; ctx.cell(edit.row(), "quantity")));
 * </pre>
 * Whatever is not covered by a rule is delegated to the decorated handler.
 *
 * @param <S> type of the objects contained within the table's item list
 * @author harald
 */
public class RuleBasedEditHandler<S> implements TableEditHandler<S> {

  private final TableEditHandler<S> delegate;
  private final Map<String, BiPredicate<TableEditContext<S>, TableCellRef<S>>> editableRules = new HashMap<>();
  private final Set<String> commitOnFocusLostColumns = new HashSet<>();
  private final Map<String, BiFunction<TableEditContext<S>, TableCellEdit<S, ?>, String>> verifyRules = new HashMap<>();
  private final List<BiConsumer<TableEditContext<S>, TableCellEdit<S, ?>>> commitRules = new ArrayList<>();
  private final Map<String, List<BiConsumer<TableEditContext<S>, TableCellEdit<S, ?>>>> columnCommitRules = new HashMap<>();
  private final Map<String, BiFunction<TableEditContext<S>, TableCellEdit<S, ?>, TableCellRef<S>>> navigateRules = new HashMap<>();
  private final Map<String, BiConsumer<TableEditContext<S>, TableCellRef<S>>> cancelRules = new HashMap<>();
  private boolean commitOnFocusLost;


  /**
   * Creates a rule-based handler.
   *
   * @param delegate the decorated handler
   */
  public RuleBasedEditHandler(TableEditHandler<S> delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }


  /**
   * Gets the decorated handler.
   *
   * @return the handler, never null
   */
  public TableEditHandler<S> getDelegate() {
    return delegate;
  }

  /**
   * Sets the rule deciding whether the cells of a column are editable.
   *
   * @param columnName the name of the column
   * @param rule the rule returning true if the cell is editable
   * @return this handler
   * @see TableEditHandler#isCellEditable(TableEditContext, TableCellRef)
   */
  public RuleBasedEditHandler<S> editable(String columnName, BiPredicate<TableEditContext<S>, TableCellRef<S>> rule) {
    editableRules.put(columnName, rule);
    return this;
  }

  /**
   * Commits the values of the given columns if the editor loses the focus.
   *
   * @param columnNames the names of the columns, none for all columns
   * @return this handler
   * @see TableEditHandler#isCommitOnFocusLost(TableEditContext, TableCellRef)
   */
  public RuleBasedEditHandler<S> commitOnFocusLost(String... columnNames) {
    if (columnNames == null || columnNames.length == 0) {
      commitOnFocusLost = true;
    }
    else {
      commitOnFocusLostColumns.addAll(List.of(columnNames));
    }
    return this;
  }

  /**
   * Sets the rule verifying the values entered in a column.
   *
   * @param columnName the name of the column
   * @param rule the rule returning the error message, null if the value is accepted
   * @return this handler
   * @see TableEditHandler#verify(TableEditContext, TableCellEdit)
   */
  public RuleBasedEditHandler<S> verify(String columnName, BiFunction<TableEditContext<S>, TableCellEdit<S, ?>, String> rule) {
    verifyRules.put(columnName, rule);
    return this;
  }

  /**
   * Adds a rule applied after a value has been committed in any column.<br>
   * The rules are applied in the order they were added, before the rules of the column.
   *
   * @param rule the rule
   * @return this handler
   * @see TableEditHandler#committed(TableEditContext, TableCellEdit)
   */
  public RuleBasedEditHandler<S> onCommit(BiConsumer<TableEditContext<S>, TableCellEdit<S, ?>> rule) {
    commitRules.add(rule);
    return this;
  }

  /**
   * Adds a rule applied after a value has been committed in a column.<br>
   * The rules are applied in the order they were added.
   *
   * @param columnName the name of the column
   * @param rule the rule
   * @return this handler
   * @see TableEditHandler#committed(TableEditContext, TableCellEdit)
   */
  public RuleBasedEditHandler<S> onCommit(String columnName, BiConsumer<TableEditContext<S>, TableCellEdit<S, ?>> rule) {
    columnCommitRules.computeIfAbsent(columnName, _ -> new ArrayList<>()).add(rule);
    return this;
  }

  /**
   * Sets the rule determining the cell to edit after a value has been committed in a column.<br>
   * Replaces the navigation of the decorated handler for that column. The rule is applied after the commit rules.
   *
   * @param columnName the name of the column
   * @param rule the rule returning the cell to edit next, null to end editing
   * @return this handler
   * @see TableEditHandler#committed(TableEditContext, TableCellEdit)
   */
  public RuleBasedEditHandler<S> navigate(String columnName, BiFunction<TableEditContext<S>, TableCellEdit<S, ?>, TableCellRef<S>> rule) {
    navigateRules.put(columnName, rule);
    return this;
  }

  /**
   * Sets the rule applied after the editing of a cell in a column has been canceled.
   *
   * @param columnName the name of the column
   * @param rule the rule
   * @return this handler
   * @see TableEditHandler#canceled(TableEditContext, TableCellRef)
   */
  public RuleBasedEditHandler<S> onCancel(String columnName, BiConsumer<TableEditContext<S>, TableCellRef<S>> rule) {
    cancelRules.put(columnName, rule);
    return this;
  }


  @Override
  public boolean isCellEditable(TableEditContext<S> context, TableCellRef<S> cell) {
    BiPredicate<TableEditContext<S>, TableCellRef<S>> rule = editableRules.get(cell.columnName());
    return (rule == null || rule.test(context, cell)) && delegate.isCellEditable(context, cell);
  }

  @Override
  public boolean isCommitOnFocusLost(TableEditContext<S> context, TableCellRef<S> cell) {
    return commitOnFocusLost || commitOnFocusLostColumns.contains(cell.columnName()) ||
           delegate.isCommitOnFocusLost(context, cell);
  }

  @Override
  public String verify(TableEditContext<S> context, TableCellEdit<S,?> edit) {
    BiFunction<TableEditContext<S>, TableCellEdit<S,?>, String> rule = verifyRules.get(edit.columnName());
    String message = rule == null ? null : rule.apply(context, edit);
    return message == null ? delegate.verify(context, edit) : message;
  }

  @Override
  public TableCellRef<S> committed(TableEditContext<S> context, TableCellEdit<S, ?> edit) {
    for (BiConsumer<TableEditContext<S>, TableCellEdit<S, ?>> rule : commitRules) {
      rule.accept(context, edit);
    }
    List<BiConsumer<TableEditContext<S>, TableCellEdit<S, ?>>> rules = columnCommitRules.get(edit.columnName());
    if (rules != null) {
      for (BiConsumer<TableEditContext<S>, TableCellEdit<S, ?>> rule : rules) {
        rule.accept(context, edit);
      }
    }
    BiFunction<TableEditContext<S>, TableCellEdit<S, ?>, TableCellRef<S>> navigateRule = navigateRules.get(edit.columnName());
    return navigateRule == null ? delegate.committed(context, edit) : navigateRule.apply(context, edit);
  }

  @Override
  public TableCellRef<S> canceled(TableEditContext<S> context, TableCellRef<S> cell) {
    BiConsumer<TableEditContext<S>, TableCellRef<S>> rule = cancelRules.get(cell.columnName());
    if (rule != null) {
      rule.accept(context, cell);
    }
    return delegate.canceled(context, cell);
  }

}

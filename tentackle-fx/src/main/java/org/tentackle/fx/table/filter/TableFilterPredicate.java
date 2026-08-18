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

import java.util.function.Predicate;

/**
 * The predicate of a {@link TableFilter} at a given revision.<p>
 * A {@link javafx.collections.transformation.FilteredList} re-evaluates its source only if the
 * predicate <em>changes</em>. Since the filter itself is mutable, the revision is part of the
 * predicate's identity, which makes every modification of the filter a new predicate.
 *
 * @param <S> type of the objects contained within the table's items list
 * @param filter the table filter
 * @param revision the filter's revision this predicate was created for
 * @author harald
 */
public record TableFilterPredicate<S>(TableFilter<S> filter, int revision) implements Predicate<S> {

  /**
   * Creates a predicate for the filter's current revision.
   *
   * @param <S> type of the objects contained within the table's items list
   * @param filter the table filter
   * @return the predicate
   */
  public static <S> TableFilterPredicate<S> of(TableFilter<S> filter) {
    return new TableFilterPredicate<>(filter, filter.getRevision());
  }

  @Override
  public boolean test(S row) {
    return filter.test(row);
  }

}

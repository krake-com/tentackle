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

package org.tentackle.fx.rdc;

import org.tentackle.fx.AbstractValidateableFxController;
import org.tentackle.fx.table.TotalsTableView;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import java.util.List;

/**
 * Controller to search for PDOs.<br>
 * Holds the view of the selection criteria and runs the query.<br>
 * Created by {@link GuiProvider#createFinder()} and embedded in a {@link org.tentackle.fx.rdc.search.PdoSearch},
 * which displays the result.
 *
 * @author harald
 * @param <T> the PDO type
 */
public abstract class PdoFinder<T extends PersistentDomainObject<T>> extends AbstractValidateableFxController
                implements PdoController<T>, DomainContextProvider {


  private boolean searchRunningInBackground;
  private boolean searchRunningImmediately;
  private boolean scrollingToEnd;
  private boolean treeShowingInitially;
  private boolean visible = true;


  /**
   * Parent constructor.
   */
  public PdoFinder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Runs the search.
   *
   * @return the search result, null is allowed and treated as empty
   */
  public abstract List<T> runSearch();

  /**
   * Requests the initial focus after the stage is shown.
   */
  public abstract void requestInitialFocus();

  /**
   * Gets the search action property.<br>
   * The search dialog sets it to an action that starts the search. Usually it is the {@code onActionProperty}
   * of a text field, so that the enter key runs the search.
   *
   * @return the search action property, null if there is no such property
   */
  public abstract ObjectProperty<EventHandler<ActionEvent>> getSearchActionProperty();


  @Override
  public DomainContext getDomainContext() {
    T pdo = getPdo();
    return pdo == null ? null : pdo.getDomainContext();
  }


  /**
   * Returns whether the query might block the client for a longer time.
   *
   * @return true if better run in the background, false if in the FX thread (default)
   */
  public boolean isSearchRunningInBackground() {
    return searchRunningInBackground;
  }

  /**
   * Defines whether the query might block the client for a longer time.
   *
   * @param searchRunningInBackground true if better run in the background, false if in the FX thread (default)
   */
  public void setSearchRunningInBackground(boolean searchRunningInBackground) {
    this.searchRunningInBackground = searchRunningInBackground;
  }


  /**
   * Returns whether to run the search immediately without any further user interaction.
   *
   * @return true if immediate search
   */
  public boolean isSearchRunningImmediately() {
    return searchRunningImmediately;
  }

  /**
   * Defines whether to run the search immediately without any further user interaction.
   *
   * @param searchRunningImmediately true if immediate search
   */
  public void setSearchRunningImmediately(boolean searchRunningImmediately) {
    this.searchRunningImmediately = searchRunningImmediately;
  }


  /**
   * Returns whether the view should be scrolled to the last item when displaying the search results.
   *
   * @return true if scroll to end, false if scroll to top (default)
   */
  public boolean isScrollingToEnd() {
    return scrollingToEnd;
  }

  /**
   * Sets whether the view should be scrolled to the last item when displaying the search results.
   *
   * @param scrollingToEnd true if scroll to end, false if scroll to top
   */
  public void setScrollingToEnd(boolean scrollingToEnd) {
    this.scrollingToEnd = scrollingToEnd;
  }


  /**
   * Returns whether the search dialog should display the initial search result as a tree instead of a table view.
   *
   * @return true if tree view, else table view (default)
   */
  public boolean isTreeShowingInitially() {
    return treeShowingInitially;
  }

  /**
   * Sets whether the search dialog should display the initial search result as a tree instead of a table view.
   *
   * @param treeShowingInitially true if tree view, else table view
   */
  public void setTreeShowingInitially(boolean treeShowingInitially) {
    this.treeShowingInitially = treeShowingInitially;
  }


  /**
   * Returns whether the finder's view is visible.
   *
   * @return true if visible (default)
   */
  public boolean isVisible() {
    return visible;
  }

  /**
   * Sets the visibility of the finder's view.
   *
   * @param visible true if visible (default)
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
  }


  /**
   * Creates the optional totals from the search results.<br>
   * By default, all numeric columns are candidates for being summed up.
   * If a primitive numeric column must not be shown in the totals view, its column configuration
   * must return {@code isSummable() == false}.
   *
   * @param items the items to create the totals from
   * @return the totals (usually one item), null or empty if no totals (default)
   */
  public ObservableList<T> createTotals(ObservableList<T> items) {
    return null;
  }

  /**
   * Creates the totals table view.<br>
   * Will be invoked only if {@link #createTotals} returned a non-empty list.
   *
   * @return the totals table view
   */
  public TotalsTableView<T> createTotalsTableView() {
    return new TotalsTableView<>();
  }

}

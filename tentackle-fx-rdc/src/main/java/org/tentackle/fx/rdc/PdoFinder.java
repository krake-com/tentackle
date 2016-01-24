/*
 * Tentackle - http://www.tentackle.org.
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

import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PdoHolder;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * FxController to search for PDOs.<br>
 * Holds the view of the selection criteria and runs the query.
 *
 * @author harald
 * @param <T> the pdo type
 */
public abstract class PdoFinder<T extends PersistentDomainObject<T>> extends AbstractFxController
                implements PdoHolder<T>, DomainContextProvider {


  private boolean searchRunningInBackground;
  private boolean searchRunningImmediately;
  private boolean visible = true;


  /**
   * Runs the search.
   *
   * @return the search result
   */
  public abstract ObservableList<T> runSearch();

  /**
   * Requests the initial focus after stage is shown.
   */
  public abstract void requestInitialFocus();

  /**
   * Gets the search action property.
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
   * Returns whether the query might block the client for alonger time.
   *
   * @return true if better run in background, false if in event-thread (default)
   */
  public boolean isSearchRunningInBackground() {
    return searchRunningInBackground;
  }

  /**
   * Defines whether the query might block the client for alonger time.
   *
   * @param searchRunningInBackground true if better run in background, false if in event-thread (default)
   */
  public void setSearchRunningInBackground(boolean searchRunningInBackground) {
    this.searchRunningInBackground = searchRunningInBackground;
  }


  /**
   * Returns whether to run the search immediately without any futher user interaction.
   *
   * @return true if immediate search
   */
  public boolean isSearchRunningImmediately() {
    return searchRunningImmediately;
  }

  /**
   * Defines whether to run the search immediately without any futher user interaction.
   *
   * @param searchRunningImmediately true if immediate search
   */
  public void setSearchRunningImmediately(boolean searchRunningImmediately) {
    this.searchRunningImmediately = searchRunningImmediately;
  }


  /**
   * Returns whether the finder is visible.
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

}

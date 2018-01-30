/**
 * Tentackle - http://www.tentackle.org
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

// Created on September 18, 2002, 3:56 PM

package org.tentackle.swing.rdc;

import java.util.List;
import java.util.function.Consumer;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormPanel;

/**
 * A PDO search.<br>
 * Combines arbitrary search criteria, a panel to edit those criteria, the execution and display of the search.
 *
 * @author harald
 * @param <T> the PDO type
 */
public interface PdoSearch<T extends PersistentDomainObject<T>> extends DomainContextProvider {

  /**
   * Executes the search.
   *
   * @param pdoDisplay the display for the retrieved pdos
   */
  void execute(Consumer<List<T>> pdoDisplay);

  /**
   * Gets the PDO class id.
   *
   * @return the class id
   */
  int getPdoClassId();

  /**
   * Gets the PDO class.
   *
   * @return the pdo class
   */
  Class<T> getPdoClass();

  /**
   * Creates a new PDO for this search.
   *
   * @return the pdo
   */
  T createPdo();

  /**
   * Gets the panel to enter the search criteria.<br>
   *
   * @return the search panel
   */
  FormPanel getSearchPanel();

  /**
   * Gets a short description of the search criteria.
   *
   * @return the criteria description
   */
  String getSearchCriteriaAsString();

  /**
   * Performs a validation of the search criteria.
   *
   * @return true if running the query is ok
   */
  boolean isSearchCriteriaValid();

  /**
   * Resets the search criteria to its default.
   */
  void resetSearchCriteria();

  /**
   * Presets the search parameter with default values from given PDO.
   *
   * @param template the PDO to extract the search criteria from
   */
  void presetSearchCriteria(T template);

  /**
   * Returns whether the search will retrieve all PDOs.<br>
   * Usually, if nothing is entered, all PDOs will be retrieved.
   *
   * @return true if search for all, false otherwise
   */
  boolean isSearchRetrievingAll();

  /**
   * Validates the result.
   *
   * @param list the retrieved PDOs from execute()
   * @return true if results can be displayed, false if not
   */
  boolean isResultValid(List<T> list);

  // ---------- flags to control the behaviour of PdoSearchDialog ------------------------

  /**
   * Start search immediately without asking the user to enter the search criteria.
   *
   * @return true if search immediately
   */
  boolean isSearchImmediate();

  /**
   * Search criteria panel visibility.
   *
   * @return true if search panel should not be visible (makes only sense with isSearchImmediate() == true)
   */
  boolean isSearchPanelInvisible();

  /**
   * Dont show the dialog if exactly a single object is found.<br>
   * Makes only sense with isSearchImmediate().
   *
   * @return true if don't show dialog if exactly single item matches (default = false)
   */
  boolean isDialogNotShownIfSearchImmediateFindsSingleMatch();

  /**
   * Show dialog if searchImmediate fails.
   *
   * @return true if dialog is shown even if immediate search fails (makes only sense with isSearchImmediate() == true)
   */
  boolean isDialogShownIfSearchImmediateFails();

  /**
   * Initially show table-view instead of tree-view.
   *
   * @return true if switch to table view initially
   */
  boolean isTableViewInitiallyShown();

  /**
   * Don't allow the user to change the view mode.
   *
   * @return true if user cannot change between table and tree view
   */
  boolean isViewModeFixed();

  /**
   * View should be rebuilt for each new display of search results.
   *
   * @return true if rebuild the view
   */
  boolean isViewRebuildNecessary();

  /**
   * Overrides the {@link org.tentackle.swing.FormTable} preferences name.
   *
   * @return the preferences name, null if default
   */
  String getFormTableName();

}

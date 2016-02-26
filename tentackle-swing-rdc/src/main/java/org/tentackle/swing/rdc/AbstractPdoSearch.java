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

import java.awt.EventQueue;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.pdo.AbstractSessionTask;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.Session;
import org.tentackle.swing.FormInfo;
import org.tentackle.swing.GUIRuntimeException;
import org.tentackle.task.Task;



/**
 * Common base class for a PDO search.
 *
 * @param <T> the pdo type
 * @author harald
 */
public abstract class AbstractPdoSearch<T extends PersistentDomainObject<T>> implements PdoSearch<T> {

  /** the default fetchsize. */
  public static int defaultFetchSize = 1000;

  /** the default warn row count. */
  public static int defaultWarnRowCount = 5000;

  /** the default max row count. */
  public static int defaultMaxRowCount = 100000;


  private final int pdoClassId;             // the PDO class id
  private final Class<T> pdoClass;          // the PDO class
  private final DomainContext context;      // the domain context
  private final int warnRowCount;           // number of rows displayable without warning, 0 = unlimited
  private final int maxRowCount;            // maximum number of rows displayable, 0 = unlimited
  private final int fetchSize;              // the fetchsize
  private Task searchTask;                  // the search task

  private boolean searchImmediate;
  private boolean searchPanelInvisible;
  private boolean dialogNotShownIfSingleMatch;
  private boolean dialogShownIfSearchImmediateFails;
  private boolean tableViewInitiallyShown;
  private boolean viewModeFixed;
  private boolean viewRebuildNecessary;
  private String formTableName;


  /**
   * Creates a PDO search.
   *
   * @param pdoClass the pdo class
   * @param context the domain context
   * @param fetchSize the fetchsize
   * @param warnRowCount number of rows displayable without warning, 0 = unlimited
   * @param maxRowCount maximum number of rows displayable, 0 = unlimited
   */
  public AbstractPdoSearch(Class<T> pdoClass, DomainContext context, int fetchSize, int warnRowCount, int maxRowCount) {
    this.pdoClass = pdoClass;
    this.pdoClassId = PdoUtilities.getInstance().getClassId(pdoClass.getName());
    this.context = context;
    this.warnRowCount = warnRowCount;
    this.maxRowCount = maxRowCount;
    this.fetchSize = fetchSize;
  }


  /**
   * Creates a PDO search.<br>
   * With {@link #defaultFetchSize}, {@link #defaultWarnRowCount} and {@link #defaultMaxRowCount}.
   *
   * @param pdoClass the pdo class
   * @param context the domain context
   */
  public AbstractPdoSearch(Class<T> pdoClass, DomainContext context) {
    this(pdoClass, context, defaultFetchSize, defaultWarnRowCount, defaultMaxRowCount);
  }


  @Override
  public int getPdoClassId() {
    return pdoClassId;
  }

  @Override
  public Class<T> getPdoClass() {
    return pdoClass;
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  @Override
  public T createPdo() {
    return on(pdoClass);
  }

  @Override
  public void execute(Consumer<List<T>> pdoDisplay) {
    T proxy = createPdo();
    boolean all = isSearchRetrievingAll();
    if (all && proxy.getCache() != null && proxy.getCache().isPreloading()) {
      // just return the cached PDOs
      pdoDisplay.accept(proxy.selectAllCached());
    }
    else  {
      // check that application is well-behaving
      Session.assertCurrentSessionValid();    // will be the case in RDC applications
      if (!EventQueue.isDispatchThread()) {
        throw new GUIRuntimeException("not in AWT dispatch thread");
      }

      // create progress dialog (but don't show it now) if warnRowCount > 0
      PdoCursorProgressDialog cursorDialog = warnRowCount > 0 ? new PdoCursorProgressDialog() : null;

      final boolean runInDispatchThread = isSearchImmediate() &&
              (isDialogNotShownIfSearchImmediateFindsSingleMatch() || !isDialogShownIfSearchImmediateFails());

      // search in PdoTracker
      searchTask = new AbstractSessionTask() {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
          ScrollableResource<T> cursor;
          if (all) {
            // select all PDOs
            cursor = proxy.selectAllAsCursor();
          }
          else {
            cursor = findByCriteria(proxy);
          }

          // retrieve the PDOs from the cursor
          List<T> list = new ArrayList<>();

          List<T> fetched;

          try {
            cursor.setFetchSize(fetchSize);

            while ((cursorDialog == null || !cursorDialog.isAbortRequested()) &&
                   (fetched = cursor.fetch()) != null) {   // get next fetchsize block

              list.addAll(fetched);
              int count = list.size();    // number of PDOs fetched so far (effectively final)

              if (cursorDialog != null) {
                if (cursorDialog.isShowing()) {
                  EventQueue.invokeLater(() -> cursorDialog.updateRowCount(count));
                }
                else if (count > warnRowCount) {
                  EventQueue.invokeLater(cursorDialog::showDialog);
                }
              }

              if (maxRowCount > 0 && count > maxRowCount) {
                // limit reached
                EventQueue.invokeLater(() ->
                        FormInfo.show(MessageFormat.format(RdcSwingRdcBundle.getString("MORE THAN {0} OBJECTS RETRIEVED -> ABORTED!"), maxRowCount)));
                break;
              }
            }
          }
          finally {
            cursor.close();   // close for sure (closing already closed is ok)
          }

          if (cursorDialog != null && cursorDialog.isShowing()) {
            EventQueue.invokeLater(cursorDialog::dispose);
          }

          // show results
          if (runInDispatchThread) {
            pdoDisplay.accept(list);
          }
          else  {
            EventQueue.invokeLater(() -> pdoDisplay.accept(list));
          }
        }
      };

      if (runInDispatchThread) {
        searchTask.run();     // execute in AWT-Thread
      }
      else  {
        PdoTracker.getInstance().addTask(searchTask);
      }
    }
  }


  /**
   * Finds the PDOs by selection criteria.
   *
   * @param proxy the pdo to invoke the method(s) on
   * @return the cursor
   */
  public abstract ScrollableResource<T> findByCriteria(T proxy);



  @Override
  public boolean isSearchImmediate() {
    return searchImmediate;
  }

  public void setSearchImmediate(boolean searchImmediate) {
    this.searchImmediate = searchImmediate;
  }

  @Override
  public boolean isDialogNotShownIfSearchImmediateFindsSingleMatch() {
    return dialogNotShownIfSingleMatch;
  }

  public void setDialogNotShownIfSingleMatch(boolean dialogNotShownIfSingleMatch) {
    this.dialogNotShownIfSingleMatch = dialogNotShownIfSingleMatch;
  }

  @Override
  public boolean isSearchPanelInvisible() {
    return searchPanelInvisible;
  }

  public void setSearchPanelInvisible(boolean searchPanelInvisible) {
    this.searchPanelInvisible = searchPanelInvisible;
  }

  @Override
  public boolean isDialogShownIfSearchImmediateFails() {
    return dialogShownIfSearchImmediateFails;
  }

  public void setDialogShownIfSearchImmediateFails(boolean dialogShownIfSearchImmediateFails) {
    this.dialogShownIfSearchImmediateFails = dialogShownIfSearchImmediateFails;
  }

  @Override
  public boolean isTableViewInitiallyShown() {
    return tableViewInitiallyShown;
  }

  public void setTableViewInitiallyShown(boolean tableViewInitiallyShown) {
    this.tableViewInitiallyShown = tableViewInitiallyShown;
  }

  @Override
  public boolean isViewModeFixed() {
    return viewModeFixed;
  }

  public void setViewModeFixed(boolean viewModeFixed) {
    this.viewModeFixed = viewModeFixed;
  }

  @Override
  public boolean isViewRebuildNecessary() {
    return viewRebuildNecessary;
  }

  public void setViewRebuildNecessary(boolean viewRebuildNecessary) {
    this.viewRebuildNecessary = viewRebuildNecessary;
  }

  @Override
  public String getFormTableName() {
    return formTableName;
  }

  public void setFormTableName(String formTableName) {
    this.formTableName = formTableName;
  }

}

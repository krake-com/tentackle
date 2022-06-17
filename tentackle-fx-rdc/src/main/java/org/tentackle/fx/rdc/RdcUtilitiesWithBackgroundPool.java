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

import javafx.application.Platform;

import org.tentackle.fx.Fx;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionCloseHandler;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolFactory;
import org.tentackle.session.SessionPooledExecutor;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Extended {@link RdcUtilities} with a thread- and session pool for background execution.<br>
 * Replace with application specific implementation via {@link org.tentackle.common.Service} annotation.
 * <p>
 * Example:
 * <pre>
 * &#64;Service(RdcUtilities.class)
 * public class MyRdcUtilities extends RdcUtilitiesWithBackgroundPool {
 *
 *   public static MyRdcUtilities getInstance() {   // to access the extra methods
 *     return (MyRdcUtilities) RdcUtilities.getInstance();
 *   }
 *
 *   &#64;Override
 *   protected SessionPool createSessionPool(Session session) {
 *     // 10 sessions max, at least 1 open (default is 3 and none open)
 *     return SessionPoolFactory.getInstance().create("BG", session, 1, 1, 1, 10, 5, 0);
 *   }
 * }
 * </pre>
 */
public class RdcUtilitiesWithBackgroundPool extends RdcUtilities {

  private final SessionPooledExecutor executor;

  /**
   * Creates the extended RDC utilities.
   */
  public RdcUtilitiesWithBackgroundPool() {
    executor = createExecutor();
  }


  /**
   * Gets the session pool executor.
   *
   * @return the executor
   */
  public SessionPooledExecutor getExecutor() {
    return executor;
  }


  @Override
  public <V> void runInBackground(Supplier<V> runner, Consumer<V> updateUI, Consumer<RuntimeException> failedUI) {
    executor.submit(runner,
                    updateUI == null ? null : value -> Platform.runLater(() -> updateUI.accept(value)),
                    failedUI == null ? null : exception -> Platform.runLater(() -> failedUI.accept(exception)));
  }

  /**
   * Runs a collection of runners in background in parallel.
   *
   * @param runners the runners to invoke
   * @param timeoutMillis the timeout in milliseconds for all tasks to finish, 0 if no timeout
   * @param finishedUI UI updater invoked from within the FX event queue when all runners finished, null if none
   * @param failedUI UI updater invoked from within the FX event queue only if execution of some runner failed, null if log only as error
   * @param <V> the type returned by the runners
   */
  public <V> void runInBackground(Collection<Supplier<V>> runners, long timeoutMillis, Consumer<Map<Supplier<V>, V>> finishedUI, Consumer<Map<Supplier<V>, RuntimeException>> failedUI) {
    executor.submit(runners, timeoutMillis,
                    finishedUI == null ? null : successHandlers -> Platform.runLater(() -> finishedUI.accept(successHandlers)),
                    failedUI == null ? null : failHandlers -> Platform.runLater(() -> failedUI.accept(failHandlers)));
  }

  /**
   * Runs a collection of runners in background in parallel.<br>
   * Failures will be presented to the user in an error dialog.
   *
   * @param runners the runners to invoke
   * @param timeoutMillis the timeout in milliseconds for all tasks to finish, 0 if no timeout
   * @param finishedUI optional UI updater always invoked from within the FX event queue if all runners finished, null if none
   * @param <V> the type returned by the runners
   */
  public <V> void runInBackground(Collection<Supplier<V>> runners, long timeoutMillis, Consumer<Map<Supplier<V>, V>> finishedUI) {
    runInBackground(
        runners,
        timeoutMillis,
        finishedUI,
        failedMap -> {
          StringBuilder buf = new StringBuilder();
          for (RuntimeException rx : failedMap.values()) {
            String msg = rx.getLocalizedMessage();
            if (msg != null && !buf.toString().contains(msg)) {
              if (buf.length() > 0) {
                buf.append('\n');
              }
              buf.append(rx.getLocalizedMessage());
            }
          }
          String msg = buf.toString();
          Platform.runLater(() -> Fx.warning(msg));
        });
  }


  /**
   * Creates the executor.
   *
   * @return the executor
   */
  protected SessionPooledExecutor createExecutor() {
    Session session = ModificationTracker.getInstance().getSession();
    SessionPooledExecutor executor = new SessionPooledExecutor(createSessionPool(session));
    session.registerCloseHandler(new SessionCloseHandler() {
      @Override
      public void beforeClose(Session session) {
        // nothing to do
      }

      @Override
      public void afterClose(Session session) {
        executor.shutdown();
      }
    });

    return executor;
  }

  /**
   * Creates the session pool.
   *
   * @param session the template session
   * @return the session pool
   */
  protected SessionPool createSessionPool(Session session) {
    return SessionPoolFactory.getInstance().create("BG", session, 0, 1, 0,
                                                   3, 5, 0);
  }

}

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
 * Simply replace the standard utilities via {@link org.tentackle.common.Service} annotation.
 * <p>
 * Example:
 * <pre>
 * &#64;Service(RdcUtilities.class)
 * public class MyRdcUtilities extends RdcUtilitiesWithBackgroundPool {
 *
 *   public static MyRdcUtilities getInstance() {
 *     return (MyRdcUtilities) RdcUtilities.getInstance();
 *   }
 *
 *   public MyRdcUtilities() {
 *     super(10, 300000L, 5);
 *   }
 * }
 * </pre>
 */
public class RdcUtilitiesWithBackgroundPool extends RdcUtilities {

  private final int maxParallel;
  private final long timeoutMs;
  private final long idleMinutes;
  private final SessionPooledExecutor pooledExecutor;

  /**
   * Creates the RDC utilities.
   *
   * @param maxParallel the maximum number of parallel background tasks, must be &gt; 1
   * @param timeoutMs the timeout in milliseconds for a bulk of tasks to finish, 0 if no timeout
   * @param idleMinutes the time in minutes to keep idle sessions open, 0 if sessions remain open forever
   */
  public RdcUtilitiesWithBackgroundPool(int maxParallel, long timeoutMs, long idleMinutes) {
    if (maxParallel < 1) {
      throw new RdcRuntimeException("maxParallel must be > 1");
    }
    this.maxParallel = maxParallel;
    this.timeoutMs = timeoutMs;
    this.idleMinutes = idleMinutes;

    Session mainSession = ModificationTracker.getInstance().getSession();
    if (mainSession.isRemote() && mainSession.getSessionInfo().isCloned()) {    // only in running real remote client, not in unit tests
      SessionPool sessionPool = SessionPoolFactory.getInstance().create("bg", mainSession, 1, 1, 1, maxParallel, idleMinutes, 0);
      pooledExecutor = new SessionPooledExecutor(sessionPool);
      mainSession.registerCloseHandler(new SessionCloseHandler() {
        @Override
        public void beforeClose(Session session) {
          // nothing to do
        }

        @Override
        public void afterClose(Session session) {
          pooledExecutor.shutdown();
        }
      });
    }
    else {
      pooledExecutor = null;
    }
  }

  /**
   * Gets the maximum number of tasks running in parallel.
   *
   * @return the number of tasks
   */
  public int getMaxParallel() {
    return maxParallel;
  }

  /**
   * Gets the timeout in milliseconds for a bulk of tasks to finish.
   *
   * @return the timeout, 0 if no timeout
   */
  public long getTimeoutMs() {
    return timeoutMs;
  }

  /**
   * Gets the time in minutes to keep idle sessions open.
   *
   * @return the idle time, 0 if sessions remain open forever
   */
  public long getIdleMinutes() {
    return idleMinutes;
  }

  /**
   * Gets the session pool executor.
   *
   * @return the executor
   */
  public SessionPooledExecutor getPooledExecutor() {
    return pooledExecutor;
  }

  @Override
  public <V> void runInBackground(Supplier<V> runner, Consumer<V> updateUI, Consumer<RuntimeException> failedUI) {
    if (pooledExecutor != null) {
      pooledExecutor.submit(runner,
                            updateUI == null ? null : value -> Platform.runLater(() -> updateUI.accept(value)),
                            failedUI == null ? null : exception -> Platform.runLater(() -> failedUI.accept(exception)));
    }
    else {
      super.runInBackground(runner, updateUI, failedUI);
    }
  }

  /**
   * Runs a collection of runners in background in parallel.
   *
   * @param runners the runners to invoke
   * @param finishedUI UI updater invoked from within the FX event queue when all runners finished, null if none
   * @param failedUI UI updater invoked from within the FX event queue only if execution of some runner failed, null if log only as error
   * @param <V> the type returned by the runners
   */
  public <V> void runInBackground(Collection<Supplier<V>> runners, Consumer<Map<Supplier<V>, V>> finishedUI, Consumer<Map<Supplier<V>, RuntimeException>> failedUI) {
    if (pooledExecutor == null) {
      throw new RdcRuntimeException("unavailable in test mode");
    }
    pooledExecutor.submit(runners, timeoutMs,
                          finishedUI == null ? null : successHandlers -> Platform.runLater(() -> finishedUI.accept(successHandlers)),
                          failedUI == null ? null : failHandlers -> Platform.runLater(() -> failedUI.accept(failHandlers)));
  }

  /**
   * Runs a collection of runners in background in parallel.<br>
   * Failures will be presented to the user in an error dialog.
   *
   * @param runners the runners to invoke
   * @param finishedUI optional UI updater always invoked from within the FX event queue if all runners finished, null if none
   * @param <V> the type returned by the runners
   */
  public <V> void runInBackground(Collection<Supplier<V>> runners, Consumer<Map<Supplier<V>, V>> finishedUI) {
    runInBackground(
        runners,
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

}

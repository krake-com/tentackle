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

package org.tentackle.io;

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


interface ReconnectorHolder {
  Reconnector INSTANCE = ServiceFactory.createService(Reconnector.class, Reconnector.class);
}

/**
 * A simple reconnector.<br>
 * Reconnects resources according to their {@link ReconnectionPolicy}, either in the
 * current thread (blocking) or in a background thread.
 * <p>
 * Replaceable via &#64;{@link Service}.
 *
 * @author harald
 */
@Service(Reconnector.class)
public class Reconnector {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static Reconnector getInstance() {
    return ReconnectorHolder.INSTANCE;
  }



  private static final Logger LOGGER = Logger.get(Reconnector.class);

  private static final int LOG_MODULO = 1000;                       // modulo failed retry to log


  private final AtomicInteger threadNumber = new AtomicInteger();   // thread counter
  private final ExecutorService executorService;                    // thread executor service


  /**
   * Creates the reconnector.
   */
  public Reconnector() {
    this.executorService = createExecutorService();
  }


  /**
   * Submits a reconnection.<br>
   * If the policy is blocking, submit waits until the resource is reconnected or an exception is thrown.
   * Otherwise, the method returns immediately and reconnection occurs in background.
   *
   * @param <T> the resource type
   * @param policy the reconnection policy
   * @param resource the resource to reconnect
   * @throws org.tentackle.common.TentackleRuntimeException if blocking and the reconnection was aborted by the policy
   */
  public <T> void submit(ReconnectionPolicy<T> policy, T resource) {
    if (policy.isBlocking()) {
      reconnect(policy, resource);
    }
    else {
      executorService.execute(() -> reconnect(policy, resource));
    }
  }


  /**
   * Performs the reconnection.<br>
   * Does not return before the resource is reconnected successfully
   * or {@link ReconnectionPolicy#timeToReconnect()} returned a value &le; 0
   * or {@link ReconnectionPolicy#timeToReconnect()} throws a runtime exception.
   * Failed attempts are logged every 1000 attempts.
   *
   * @param <T> the resource type
   * @param policy the reconnection policy
   * @param resource the resource to reconnect
   * @throws TentackleRuntimeException if {@link ReconnectionPolicy#timeToReconnect()} returned a value &le; 0
   * @throws InterruptedRuntimeException if interrupted while waiting for the next attempt
   */
  protected <T> void reconnect(ReconnectionPolicy<T> policy, T resource) {
    for (int count=0; ; count++) {    // until successfully reconnected
      long millis = policy.timeToReconnect();
      if (millis > 0) {
        try {
          Thread.sleep(millis);
          policy.reconnect(resource);
          break;
        }
        catch (InterruptedException ix) {
          throw new InterruptedRuntimeException(ix);
        }
        catch (RuntimeException rx) {
          if (count % LOG_MODULO == 0) {
            LOGGER.severe("reconnection for " + policy + ", attempt " + (count + 1) + " failed, keep on trying...", rx);
          }
        }
      }
      else {
        throw new TentackleRuntimeException("millis=" + millis + " -> reconnection for " + policy + " aborted");
      }
    }
  }


  /**
   * Creates the executor service.
   *
   * @return the executor service
   */
  protected ExecutorService createExecutorService() {
    return Executors.newCachedThreadPool(runnable -> {
      Thread thread = new Thread(runnable, "reconnector " + threadNumber.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    });
  }
}

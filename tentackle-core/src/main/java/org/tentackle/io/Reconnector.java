/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;


interface Reconnector$Singleton {
  Reconnector INSTANCE = ServiceFactory.createService(Reconnector.class, Reconnector.class);
}

/**
 * A simple reconnector.
 *
 * @author harald
 */
@Service(Reconnector.class)
public class Reconnector {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static Reconnector getInstance() {
    return Reconnector$Singleton.INSTANCE;
  }



  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(Reconnector.class);


  private static final int LOG_MODULO = 1000;                       // n'th failed retry to log


  private final AtomicInteger threadNumber = new AtomicInteger();   // thread counter
  private final ExecutorService executorService;                    // thread executor service


  public Reconnector() {
    this.executorService = createExecutorService();
  }


  /**
   * Submits a reconnection.<br>
   * If the policy is blocking, submit waits until the resource is reconnected or an exception is thrown.
   * Otherwise the method returns immediately and reconnection occurs in background.
   *
   * @param <T> the resource type
   * @param policy the reconnection policy
   */
  public <T> void submit(ReconnectionPolicy<T> policy) {
    if (policy.isBlocking()) {
      reconnect(policy);
    }
    else {
      executorService.submit(() -> new Runnable() {
        @Override
        public void run() {
          reconnect(policy);
        }
      });
    }
  }


  /**
   * Performs the reconnection.<br>
   * Does not return before the resource is reconnected successfully
   * or {@link ReconnectionPolicy#timeToReconnect()} returned a value &le; 0
   * or {@link ReconnectionPolicy#timeToReconnect()} throws a runtime exception.
   *
   * @param <T> the resource type
   * @param policy the reconnection policy
   */
  protected <T> void reconnect(ReconnectionPolicy<T> policy) {
    for (int count=0; ; count++) {    // until successfully reconnected
      long millis = policy.timeToReconnect();
      if (millis > 0) {
        try {
          Thread.sleep(millis);
          policy.getConsumer().accept(policy.getConnector().get());
          break;
        }
        catch (Throwable t) {
          if (count % LOG_MODULO == 0) {
            LOGGER.severe("reconnection for " + policy + ", attempt " + (count == 0 ? 1 : count) + " failed, keep on trying...", t);
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
    return Executors.newCachedThreadPool((Runnable runnable) -> {
      return new Thread(runnable, "reconnector " + threadNumber.incrementAndGet());
    });
  }

}

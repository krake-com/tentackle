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

package org.tentackle.misc;

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.TentackleRuntimeException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

/**
 * Methods related to concurrency and multithreading.
 *
 * @author harald
 */
public class ConcurrencyHelper {

  /**
   * Wraps an existing executor by a blocking executor.<br>
   * The returned executor waits until the command has been executed by the given executor.
   * <p>
   * Important: invoking {@code execute} on the blocking executor from the thread
   * the wrapped executor executes the command in results in a deadlock!
   * <p>
   * Exceptions thrown by the command are wrapped in a {@link TentackleRuntimeException},
   * an interruption while waiting in an {@link InterruptedRuntimeException}.
   *
   * @param executor the executor to wrap
   * @return the blocking executor
   */
  public static Executor createBlockingExecutor(Executor executor) {
    return new BlockingExecutor(executor);
  }


  private static class BlockingExecutor implements Executor {

    private final Executor executor;

    private BlockingExecutor(Executor executor) {
      this.executor = executor;
    }

    @Override
    public void execute(Runnable command) {
      FutureTask<Void> task = new FutureTask<>(command, null);
      executor.execute(task);
      try {
        task.get();   // this will block until execution has finished
      }
      catch (InterruptedException ix) {
        throw new InterruptedRuntimeException(ix);
      }
      catch (ExecutionException ex) {
        throw new TentackleRuntimeException("blocking execution failed", ex);
      }
    }
  }


  private ConcurrencyHelper() {
  }

}

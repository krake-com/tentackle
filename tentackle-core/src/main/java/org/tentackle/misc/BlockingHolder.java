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

import java.util.concurrent.CountDownLatch;

/**
 * A blocking holder.
 * <ul>
 *   <li>thread A waits in {@link #get()} until...</li>
 *   <li>thread B invokes {@link #accept(Object)}</li>
 * </ul>
 * The holder blocks only once: after the first {@code accept}, {@code get} never blocks again.
 *
 * @param <T> the object type
 */
public class BlockingHolder<T> extends Holder<T> {

  private final CountDownLatch latch;

  /**
   * Creates a blocking holder.
   */
  public BlockingHolder() {
    latch = new CountDownLatch(1);
  }

  @Override
  public void accept(T t) {
    super.accept(t);
    latch.countDown();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Blocks until a value has been set via {@link #accept(Object)}.
   *
   * @throws InterruptedRuntimeException if the thread was interrupted while waiting
   */
  @Override
  public T get() {
    try {
      latch.await();
    }
    catch (InterruptedException ex) {
      throw new InterruptedRuntimeException(ex);
    }
    return super.get();
  }
}

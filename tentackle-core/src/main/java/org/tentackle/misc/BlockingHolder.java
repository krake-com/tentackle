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

import org.tentackle.common.TentackleRuntimeException;

import java.util.concurrent.CountDownLatch;

/**
 * A blocking holder.<br>
 * <ul>
 *   <li>thread a waits on get() until...</li>
 *   <li>thread b invokes accept()</li>
 * </ul>
 *
 * @param <T> the object type
 */
public class BlockingHolder<T> extends Holder<T> {

  private final CountDownLatch latch;

  public BlockingHolder() {
    latch = new CountDownLatch(1);
  }

  @Override
  public void accept(T t) {
    super.accept(t);
    latch.countDown();
  }

  @Override
  public T get() {
    try {
      latch.await();
    }
    catch (InterruptedException ix) {
      Thread.currentThread().interrupt();
      throw new TentackleRuntimeException(ix);
    }
    return super.get();
  }
}

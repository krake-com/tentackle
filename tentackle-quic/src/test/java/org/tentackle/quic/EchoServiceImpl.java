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

package org.tentackle.quic;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side implementation of {@link EchoService}.
 */
public class EchoServiceImpl implements EchoService {

  private final String name;
  private final AtomicInteger invocationCount = new AtomicInteger();

  /**
   * Creates the service.
   *
   * @param name the name reported by {@link #getName()}
   */
  public EchoServiceImpl(String name) {
    this.name = name;
  }

  /**
   * Gets the number of invocations processed so far.
   *
   * @return the invocation count
   */
  public int getInvocationCount() {
    return invocationCount.get();
  }

  @Override
  public String getName() {
    invocationCount.incrementAndGet();
    return name;
  }

  @Override
  public Payload echo(Payload payload) {
    invocationCount.incrementAndGet();
    return payload;
  }

  @Override
  public Payload duplicate(Payload payload) {
    invocationCount.incrementAndGet();
    return new Payload(payload.text() + "-copy", payload.number(),
                       payload.blob().clone(), List.copyOf(payload.tags()));
  }

  @Override
  public int add(int a, int b) {
    invocationCount.incrementAndGet();
    return a + b;
  }

  @Override
  public void fail(String message) {
    invocationCount.incrementAndGet();
    throw new IllegalStateException(message);
  }

}

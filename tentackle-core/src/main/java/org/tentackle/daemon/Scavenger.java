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

package org.tentackle.daemon;

/**
 * Interface for cleanup services like cleanup threads.
 * <p>
 * Those threads or objects may bypass several checks and thus avoid unnecessary exceptions.
 *
 * @author harald
 */
public interface Scavenger {

  /**
   * Returns whether current thread is scavenging.
   *
   * @return true if scavenging
   */
  default boolean isScavenging() {
    return true;
  }



  /**
   * Returns whether given thread is a scavenger.
   *
   * @param thread the thread
   * @return true if thread implements Scavenger and isScavenging()==true, or thread is a finalizer/cleaner
   */
  static boolean isScavenger(Thread thread) {
    return thread instanceof Scavenger && ((Scavenger) thread).isScavenging() ||
           thread.getClass().getName().startsWith("jdk.internal.ref.Cleaner") ||
           thread.getClass().getName().startsWith("java.lang.ref.Finalizer") ||
           "Secondary finalizer".equals(thread.getName());
  }

}

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


package org.tentackle.wurblet;

import org.wurbelizer.wurbel.WurbelContext;

/**
 * Determines whether Tentackle wurblets may run concurrently.
 * <p>
 * The Tentackle wurblets keep their state in singletons of their classloader, most notably the
 * {@link org.tentackle.model.Model}, which is not thread-safe. The wurbelizer's Maven plugin, however,
 * loads the {@code wurbletDependencies} in a separate classloader for each execution, and each execution
 * processes its sources sequentially. Hence, the wurblets are thread-safe if and only if they were loaded
 * by the wurblet classloader of the current wurbel run. If they are loaded by the plugin's classloader
 * instead (for example, as plugin dependencies), the singletons are shared by all executions of the build
 * and the wurblets must run sequentially.
 *
 * @author harald
 * @see org.wurbelizer.wurblet.Wurblet#isThreadSafe()
 */
public final class WurbletThreadSafety {

  /**
   * Returns whether a wurblet class is isolated in the wurblet classloader of the current wurbel run.
   *
   * @param wurbletClass the wurblet class
   * @return true if isolated and thus thread-safe, false if it must run sequentially
   */
  public static boolean isIsolated(Class<?> wurbletClass) {
    ClassLoader loader = WurbelContext.current().getWurbletClassLoader();
    return loader != null && wurbletClass.getClassLoader() == loader;
  }


  private WurbletThreadSafety() {
    // static methods only
  }

}

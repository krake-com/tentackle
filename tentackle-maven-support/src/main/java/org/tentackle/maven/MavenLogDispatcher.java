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

package org.tentackle.maven;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Root handler of {@code java.util.logging} that dispatches to the {@link MavenLogHandler}
 * bound to the current thread.
 * <p>
 * In parallel builds several mojos run concurrently, each with its own maven logger. The root
 * logger, however, is a JVM-wide singleton. Hence, a single dispatcher is installed once, and
 * each mojo execution binds its handler to its own thread. Threads spawned by a mojo inherit
 * the binding. Log records of threads without a binding are discarded.
 */
public final class MavenLogDispatcher extends Handler {

  private static final InheritableThreadLocal<MavenLogHandler> TARGET = new InheritableThreadLocal<>();

  /**
   * Installs the dispatcher, if not already done, and binds the given handler to the current thread.
   * <p>
   * On first installation within the plugin's class realm, all handlers of the root logger are removed,
   * such as the default console handler, except for the dispatchers installed by other plugin class realms.
   * Each realm installs its own dispatcher, which forwards only to the handlers bound by mojos of its realm.
   *
   * @param handler the handler to bind
   */
  public static void install(MavenLogHandler handler) {
    Logger rootLogger = Logger.getLogger("");
    synchronized (rootLogger) {   // the root logger is shared by all plugin realms
      Handler[] rootHandlers = rootLogger.getHandlers();
      boolean installed = false;
      for (Handler rootHandler : rootHandlers) {
        if (rootHandler instanceof MavenLogDispatcher) {
          installed = true;
          break;
        }
      }
      if (!installed) {
        for (Handler rootHandler : rootHandlers) {
          if (!MavenLogDispatcher.class.getName().equals(rootHandler.getClass().getName())) {
            rootLogger.removeHandler(rootHandler);
          }
          // else: dispatcher of another plugin realm
        }
        rootLogger.addHandler(new MavenLogDispatcher());
      }
    }
    TARGET.set(handler);
  }

  /**
   * Unbinds the handler from the current thread.
   */
  public static void uninstall() {
    TARGET.remove();
  }


  private MavenLogDispatcher() {
  }

  @Override
  public void publish(LogRecord logRecord) {
    MavenLogHandler handler = TARGET.get();
    if (handler != null) {
      handler.publish(logRecord);
    }
  }

  @Override
  public void flush() {
    // nothing to do
  }

  @Override
  public void close() {
    // nothing to do
  }
}

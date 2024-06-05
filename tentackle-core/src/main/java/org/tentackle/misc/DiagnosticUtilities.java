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

import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;
import org.tentackle.task.TaskDispatcher;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;


interface DiagnosticUtilitiesHolder {
  DiagnosticUtilities INSTANCE = ServiceFactory.createService(DiagnosticUtilities.class, DiagnosticUtilities.class);
}


/**
 * Utilities for diagnostic purposes.
 *
 * @author harald
 */
@Service(DiagnosticUtilities.class)   // defaults to self
public class DiagnosticUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static DiagnosticUtilities getInstance() {
    return DiagnosticUtilitiesHolder.INSTANCE;
  }

  private static final Logger LOG = Logger.get(DiagnosticUtilities.class);

  private final AtomicLong dumpCount;       // dump counter



  /**
   * Creates a utility instance.
   */
  public DiagnosticUtilities() {
    dumpCount = new AtomicLong();
  }

  /**
   * Creates the stackdump string for the whole application.
   * <p>
   * Useful for debugging, crash analysis.
   *
   * @return the stackdump
   */
  public String createStackDump() {
    StringBuilder buf = new StringBuilder("\n-------------------- Stackdump ");
    buf.append(dumpCount.incrementAndGet())
       .append(" --------------------\n\n");

    try {
      doCreateStackDump(buf);
    }
    catch (RuntimeException ex) {
      buf.append("CREATING STACKDUMP FAILED:\n")
         .append(ExceptionHelper.getStackTraceAsString(ex));
    }

    buf.append("\n-------------------- End Of Stackdump ")
       .append(dumpCount)
       .append(" --------------------\n\n");

    return buf.toString();
  }

  /**
   * Logs the stackdump.
   *
   * @param level the logging level
   * @param message the header message
   */
  public void logStackDump(Logger.Level level, String message) {
    LOG.log(level, null, () -> message + createStackDump());
  }


  /**
   * Creates the stackdump string for a given thread.
   *
   * @param buf the string buffer
   * @param info the thread info
   * @param thread the thread instance
   */
  protected void doCreateStackDump(StringBuilder buf, ThreadInfo info, Thread thread) {
    buf.append(info.toString());
    if (thread instanceof TaskDispatcher) {
      buf.append("    is a TaskDispatcher:\n    ");
      buf.append(((TaskDispatcher) thread).toDiagnosticString());
      buf.append("\n\n");
    }
  }

  /**
   * Creates the stackdump.
   *
   * @param buf the string buffer
   */
  protected void doCreateStackDump(StringBuilder buf) {
    // build a map of thread id <-> thread
    Map<Long, Thread> threadIdMap = new HashMap<>();
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      threadIdMap.put(thread.threadId(), thread);
    }

    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    ThreadInfo[] infos = bean.dumpAllThreads(true, true);
    for (ThreadInfo info : infos) {
      doCreateStackDump(buf, info, threadIdMap.get(info.getThreadId()));
    }

    long[] threadIds = bean.findDeadlockedThreads();
    if (threadIds != null && threadIds.length > 0) {
      buf.append("\n!!!!!!!!!!!!!!!!!!!!!!!! DEADLOCKS FOUND !!!!!!!!!!!!!!!!!!!!!!!!!!\n\n");
      infos = bean.getThreadInfo(threadIds, true, true);
      for (ThreadInfo info : infos) {
        doCreateStackDump(buf, info, threadIdMap.get(info.getThreadId()));
      }
    }
  }

}

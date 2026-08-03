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
package org.tentackle.session;

import org.tentackle.task.Task;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;

/**
 * Tests the session/task bookkeeping of the keep-alive daemon.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SessionKeepAliveDaemonTest {

  private static final long KEEP_ALIVE_INTERVAL = 60000;
  private static final long MIN_ALIVE_INTERVAL = 1000;


  @Test
  public void testReplacedTaskDoesNotRemoveItsSuccessor() {
    SessionKeepAliveDaemon daemon = createDaemon();
    Session session = createSession("stub");

    daemon.keepAliveIntervalChanged(session);
    Task firstTask = singleTaskOf(daemon);

    // another interval change replaces the task
    daemon.keepAliveIntervalChanged(session);
    Task secondTask = singleTaskOf(daemon);
    Assert.assertNotSame(secondTask, firstTask);

    /*
     * The replaced task is still executing on the daemon thread and now unregisters itself.
     * It must not take its replacement with it, or the session would silently lose its keep-alive.
     */
    Assert.assertFalse(daemon.removeAliveTask(session, firstTask));
    Assert.assertSame(singleTaskOf(daemon), secondTask, "the replacement keep-alive was dropped");

    // the current task, however, can still unregister itself
    Assert.assertTrue(daemon.removeAliveTask(session, secondTask));
    Assert.assertTrue(daemon.getAllTasks().isEmpty());
  }


  @Test
  public void testKeepAliveIsRemovedPerSession() {
    SessionKeepAliveDaemon daemon = createDaemon();
    Session first = createSession("first");
    Session second = createSession("second");

    daemon.keepAliveIntervalChanged(first);
    daemon.keepAliveIntervalChanged(second);
    Assert.assertEquals(daemon.getAllTasks().size(), 2);

    daemon.removeAliveTask(first);
    Assert.assertEquals(daemon.getAllTasks().size(), 1);

    daemon.removeAliveTask(first);    // removing an unregistered session is not an error
    Assert.assertEquals(daemon.getAllTasks().size(), 1);
  }


  /**
   * Creates a daemon that is never started, so no session is ever actually pinged.
   *
   * @return the daemon
   */
  private SessionKeepAliveDaemon createDaemon() {
    return new SessionKeepAliveDaemon(MIN_ALIVE_INTERVAL);
  }

  /**
   * Creates a session providing just what the daemon's bookkeeping needs.
   *
   * @param name the session name
   * @return the session
   */
  private Session createSession(String name) {
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(), new Class<?>[] { Session.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "getKeepAliveInterval" -> KEEP_ALIVE_INTERVAL;
          case "isOpen" -> Boolean.TRUE;
          case "getName", "toString" -> name;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

  /**
   * Gets the daemon's one and only task.
   *
   * @param daemon the daemon
   * @return the task
   */
  private Task singleTaskOf(SessionKeepAliveDaemon daemon) {
    Collection<Task> tasks = daemon.getAllTasks();
    Assert.assertEquals(tasks.size(), 1, "expected exactly one keep-alive task");
    return tasks.iterator().next();
  }

}

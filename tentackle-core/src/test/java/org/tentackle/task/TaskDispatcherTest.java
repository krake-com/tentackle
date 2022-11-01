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


package org.tentackle.task;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tests the task dispatcher.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class TaskDispatcherTest {

  private static final int THREAD_NUM = 10;         // number of concurrent threads
  private static final long TEST_DURATION = 5000;   // in ms
  private static final long LOCK_DURATION = 2;      // in ms
  private static final long SLEEP_INTERVAL = 10;    // in ms

  private TaskDispatcher dispatcher;
  private volatile int lockCount;
  private volatile int taskCount;

  private final String[] failMessages = new String[THREAD_NUM];

  // only for counter locking: the currently pending locks
  private final ConcurrentHashMap<Integer,Integer> counterLocks = new ConcurrentHashMap<>();


  @BeforeClass
  public void initialize() {
    for (int i=0; i < THREAD_NUM; i++) {
      failMessages[i] = null;
    }
  }


  /**
   * Tests the mutex locking, i.e.
   * the same thread locks and unlocks.
   */
  @Test
  public void testMutexLocking() {
    dispatcher = new DefaultTaskDispatcher("mutex", true, SLEEP_INTERVAL, 0);
    runMutexLocking(true);
  }

  /**
   * Tests the counter locking, i.e.
   * lock and unlock is not done by the same thread.
   */
  @Test
  public void testCounterLocking() {
    dispatcher = new DefaultTaskDispatcher("counter", false, SLEEP_INTERVAL, 0);
    runCounterLocking(true);
  }




  private class TestTask extends AbstractTask {

    private static final long serialVersionUID = 1L;

    @Override
    public void run() {
      lockCount++;
      taskCount++;
    }
  }


  private class MutexLockThread extends Thread {

    private final Integer num;
    private final boolean checkLockCount;
    private volatile boolean terminate;
    private int myLockCount;

    public MutexLockThread(int num, boolean checkLockCount) {
      super("mutex lock thread " + num);
      this.num = num;
      this.checkLockCount = checkLockCount;
      setPriority(NORM_PRIORITY);
      setDaemon(true);
    }


    @Override
    public void run() {
      Reporter.log("started mutex lock thread " + num + "<br/>");
      try {
        while (!terminate) {
          dispatcher.lock(num);
          int count = lockCount;
          lockCount++;
          myLockCount++;
          try {
            sleep(LOCK_DURATION);
          }
          catch(InterruptedException ex) {
            Reporter.log("interrupted...");
          }
          int expected = count + 1;
          if (checkLockCount && lockCount != expected) {
            failMessages[num - 1] = "mutex thread " + num + " expected lockCount " + expected + ", got " + lockCount;
            Reporter.log(failMessages[num - 1] + "<br/>");
            return;   // stop immediately
          }
          dispatcher.unlock(num);

          // sleep some time if this thread is going to eat up all resources
          if (myLockCount > lockCount / THREAD_NUM) {
            try {
              sleep(SLEEP_INTERVAL);
            }
            catch(InterruptedException ex) {
              Reporter.log("interrupted...");
            }
          }
        }
      }
      catch (RuntimeException re) {
        failMessages[num - 1] = re.getMessage();
        Reporter.log("mutex thread " + num + " crashed: " + re + "<br/>");
      }
      Reporter.log("finished mutex lock thread " + num + "<br/>");
    }

    public void requestTermination() {
      terminate = true;
      Reporter.log("terminating mutex lock thread " + num + ", " + myLockCount + " locks performed<br/>");
    }
  }




  private class CounterLockThread extends Thread {

    private final Integer num;
    private volatile boolean terminate;
    private int myLockCount;
    private int lockNumber;

    public CounterLockThread(int num) {
      super("counter lock thread " + num);
      this.num = num;
      setPriority(NORM_PRIORITY);
      setDaemon(true);
    }

    public Integer getKey() {
      return num;
    }

    public synchronized int getLockNumber() {
      return lockNumber;
    }

    @Override
    public void run() {
      Reporter.log("started counter lock thread " + num + "<br/>");
      try {
        while (!terminate) {
          synchronized (this) {
            if (counterLocks.put(num, num) == null) {
              // place a new lock
              dispatcher.lock(num);
              lockNumber = ++lockCount;   // this number is expected at unlock!
              myLockCount++;
              notifyAll();    // notify unlocker that we did a lock
            }
          }
          // wait until unlocked
          try {
            sleep(SLEEP_INTERVAL);
          }
          catch (InterruptedException ex) {
            Reporter.log("interrupted...");
          }
        }
      }
      catch (RuntimeException re) {
        failMessages[num - 1] = re.getMessage();
        Reporter.log("counter lock thread " + num + " crashed: " + re + "<br/>");
      }
      Reporter.log("finished counter lock thread " + num + "<br/>");
    }

    public synchronized void requestTermination() {
      terminate = true;
      Reporter.log("terminating counter lock thread " + num + ", " + myLockCount + " locks<br/>");
    }
  }




  private class CounterUnlockThread extends Thread {

    private final CounterLockThread lockThread;
    private final boolean checkLockCount;
    private volatile boolean terminate;
    private int myUnlockCount;

    public CounterUnlockThread(CounterLockThread lockThread, boolean checkLockCount) {
      super("counter unlock thread " + lockThread.getKey());
      this.lockThread = lockThread;
      this.checkLockCount = checkLockCount;
      setPriority(NORM_PRIORITY);
      setDaemon(true);
    }


    @Override
    public void run() {
      Reporter.log("started counter unlock thread " + lockThread.getKey() + "<br/>");
      try {
        while (!terminate) {
          synchronized(lockThread) {
            Integer key = counterLocks.remove(lockThread.getKey());
            if (key != null) {
              // there was a lock: remove it
              int count = lockCount;
              dispatcher.unlock(key);
              myUnlockCount++;
              if (checkLockCount && count != lockThread.getLockNumber()) {
                failMessages[lockThread.getKey() - 1] = "expected locknumber " + lockThread.getLockNumber() + ", got " + count;
                Reporter.log(failMessages[lockThread.getKey() - 1] + "<br/>");
                return;
              }
            }
            else  {
              try {
                lockThread.wait(SLEEP_INTERVAL);    // wait for lock
              }
              catch(InterruptedException ex) {
                Reporter.log("interrupted...");
              }
            }
          }
        }
      }
      catch (RuntimeException re) {
        failMessages[lockThread.getKey() - 1] = re.getMessage();
        Reporter.log("counter unlock thread " + lockThread.getKey() + " crashed: " + re + "<br/>");
      }
      Reporter.log("finished counter unlock thread " + lockThread.getKey() + "<br/>");
    }

    public void requestTermination() {
      terminate = true;
      Reporter.log("terminating counter unlock thread " + lockThread.getKey() + ", " + myUnlockCount + " unlocks<br/>");
    }
  }






  /**
   * Tests the mutex locking, i.e.
   * the same thread locks and unlocks.
   */
  private void runMutexLocking(boolean checkLockCount) {

    dispatcher.start();

    // test if unlocking unlocked failure
    Object lockKey = new Object();
    Assert.assertNotNull(dispatcher.lock(lockKey));
    Assert.assertNull(dispatcher.lock(lockKey));
    Assert.assertFalse(dispatcher.unlock(lockKey));
    Assert.assertTrue(dispatcher.unlock(lockKey));
    try {
      dispatcher.unlock(lockKey);
      Assert.fail("expected exception not thrown");
    }
    catch (RuntimeException re) {
      Reporter.log("expected exception caught: " + re + "<br/>");
    }


    lockCount  = 0;

    final MutexLockThread[] lockThreads = new MutexLockThread[THREAD_NUM];

    // pump in tasks
    for (int i=0; i < TEST_DURATION / SLEEP_INTERVAL; i++) {
      dispatcher.addTask(new TestTask());
    }

    for (int i=0; i < THREAD_NUM; i++) {
      lockThreads[i] = new MutexLockThread(i + 1, checkLockCount);
      lockThreads[i].start();
    }


    try {
      Thread.sleep(TEST_DURATION);
    }
    catch (InterruptedException ex) {
      Reporter.log("main test interrupted: " + ex + "<br/>");
    }


    for (int i=0; i < THREAD_NUM; i++) {
      lockThreads[i].requestTermination();
      lockThreads[i].interrupt();
    }

    for (int i=0; i < THREAD_NUM; i++) {
      try {
        Reporter.log("waiting for thread " + (i+1) + " to terminate<br/>");
        lockThreads[i].join();
      }
      catch (InterruptedException ex) {
        Reporter.log("main test interrupted while waiting for thread " + (i+1) + " to terminate: " + ex + "<br/>");
      }
    }

    for (int i=0; i < THREAD_NUM; i++) {
      Assert.assertNull(failMessages[i], failMessages[i]);
    }

    Reporter.log("**** " + lockCount + " lock operations, " + taskCount + " tasks executed, " + dispatcher.getQueueSize() + " tasks pending ****<br/>");

    Reporter.log("waiting for dispatcher to terminate<br/>");
    dispatcher.terminate();
  }



  /**
   * Tests the counter locking, i.e.
   * lock and unlock is not done by the same thread.
   */
  private void runCounterLocking(boolean checkLockCount) {

    dispatcher.start();

    // test if unlocking unlocked failure
    Object lockKey = new Object();
    Assert.assertNotNull(dispatcher.lock(lockKey));
    Assert.assertNull(dispatcher.lock(lockKey));
    Assert.assertFalse(dispatcher.unlock(lockKey));
    Assert.assertTrue(dispatcher.unlock(lockKey));
    try {
      dispatcher.unlock(lockKey);
      Assert.fail("expected exception not thrown");
    }
    catch (RuntimeException re) {
      Reporter.log("expected exception caught: " + re + "<br/>");
    }

    lockCount  = 0;

    final CounterLockThread[] lockThreads = new CounterLockThread[THREAD_NUM];
    final CounterUnlockThread[] unlockThreads = new CounterUnlockThread[THREAD_NUM];

    for (int i=0; i < THREAD_NUM; i++) {
      lockThreads[i] = new CounterLockThread(i + 1);
      lockThreads[i].start();
      unlockThreads[i] = new CounterUnlockThread(lockThreads[i], checkLockCount);
      unlockThreads[i].start();
    }

    // pump in tasks
    for (int i=0; i < TEST_DURATION / LOCK_DURATION * THREAD_NUM * THREAD_NUM; i++) {
      dispatcher.addTask(new TestTask());
    }

    try {
      Thread.sleep(TEST_DURATION);
    }
    catch (InterruptedException ex) {
      Reporter.log("main test interrupted: " + ex + "<br/>");
    }


    for (int i=0; i < THREAD_NUM; i++) {
      lockThreads[i].requestTermination();
      lockThreads[i].interrupt();
    }
    for (int i=0; i < THREAD_NUM; i++) {
      try {
        Reporter.log("waiting for lock thread " + (i+1) + " to terminate<br/>");
        lockThreads[i].join();
      }
      catch (InterruptedException ex) {
        Reporter.log("main test interrupted while waiting for lock thread " + (i+1) + " to terminate: " + ex + "<br/>");
      }
    }

    for (int i=0; i < THREAD_NUM; i++) {
      unlockThreads[i].requestTermination();
      unlockThreads[i].interrupt();
    }
    for (int i=0; i < THREAD_NUM; i++) {
      try {
        Reporter.log("waiting for unlock thread " + (i+1) + " to terminate<br/>");
        unlockThreads[i].join();
      }
      catch (InterruptedException ex) {
        Reporter.log("main test interrupted while waiting for unlock thread " + (i+1) + " to terminate: " + ex + "<br/>");
      }
    }

    for (Integer key: counterLocks.keySet()) {
      // lock pending: remove it
      int count = 0;
      while (dispatcher.unlock(key)) {
        count++;
      }
      if (count > 0) {
        Reporter.log("removed " + count + " pending locks for key " + key + "<br/>");
      }
    }

    for (int i=0; i < THREAD_NUM; i++) {
      Assert.assertNull(failMessages[i], failMessages[i]);
    }

    Reporter.log("**** " + lockCount + " lock operations, " + taskCount + " tasks executed, " + dispatcher.getQueueSize() + " tasks pending ****<br/>");

    Reporter.log("waiting for dispatcher to terminate<br/>");
    dispatcher.terminate();
  }

}

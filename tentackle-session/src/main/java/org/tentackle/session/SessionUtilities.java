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

import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.ServiceFinder;
import org.tentackle.common.StringHelper;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.Identifiable;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;


interface SessionUtilitiesHolder {
  SessionUtilities INSTANCE = ServiceFactory.createService(SessionUtilities.class, SessionUtilities.class);
}

/**
 * Utility methods for session related stuff.
 *
 * @author harald
 */
@Service(SessionUtilities.class)      // defaults to self
public class SessionUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static SessionUtilities getInstance() {
    return SessionUtilitiesHolder.INSTANCE;
  }



  /**
   * The keep alive thread.<br>
   * null if not started yet
   */
  private SessionKeepAliveDaemon keepAliveDaemon;

  /**
   * map of class names to table names.
   */
  private final Map<String,String> classTableMap;

  /**
   * map of table names to class names.
   */
  private final Map<String,String> tableClassMap;

  /**
   * map of class names to class IDs.
   */
  private final Map<String,Integer> classIdMap;

  /**
   * map of class IDs to class names.
   */
  private final Map<Integer,String> idClassMap;

  /**
   * List of all class names.
   */
  private final Collection<String> classNames;


  /**
   * Creates a utility instance.
   */
  public SessionUtilities() {
    ServiceFinder finder = ServiceFactory.getServiceFinder();
    classTableMap = new HashMap<>();
    tableClassMap = new HashMap<>();

    Set<String> sortedNames = new TreeSet<>();
    for (Map.Entry<String,String> entry: finder.createNameMap(TableName.class.getName()).entrySet()) {
      String tableName = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
      classTableMap.put(entry.getValue(), tableName);
      tableClassMap.put(tableName, entry.getValue());
      sortedNames.add(entry.getValue());
    }

    Map<String,String> idMap = finder.createNameMap(ClassId.class.getName());
    classIdMap = new HashMap<>();
    idClassMap = new HashMap<>();
    for (Map.Entry<String,String> entry: idMap.entrySet()) {
      Integer classId = Integer.valueOf(entry.getKey());
      sortedNames.add(entry.getValue());
      classIdMap.put(entry.getValue(), classId);
      idClassMap.put(classId, entry.getValue());
    }
    classNames = new ArrayList<>(sortedNames);
  }


  /**
   * Gets the table name of a pdo class.
   *
   * @param className the class name of the object
   * @return the tablename, null if no such class or not annotated with &#64;{@link TableName}
   */
  public String getTableName(String className) {
    return classTableMap.get(className);
  }

  /**
   * Gets the classname for tablename.
   *
   * @param tableName the tablename
   * @return the name of the class, null if no such tablename
   */
  public String getClassName(String tableName) {
    return tableClassMap.get(tableName);
  }

  /**
   * Gets the classid of a pdo class.
   *
   * @param pdoClassName the classname of the pdo
   * @return the classid, 0 if no such class or not annotated with &#64;{@link ClassId}.
   */
  public int getClassId(String pdoClassName) {
    Integer classId = classIdMap.get(pdoClassName);
    return classId == null ? 0 : classId;
  }

  /**
   * Gets the classname for a classid.
   *
   * @param classId the classid
   * @return the name of the class, null if no such classid
   */
  public String getClassName(int classId) {
    return idClassMap.get(classId);
  }

  /**
   * Gets a list of all class names.<br>
   * Sorted by name.
   *
   * @return the class names
   */
  public Collection<String> getClassNames() {
    return classNames;
  }


  /**
   * Determines the tablename from the &#64;{@link TableName} annotation.
   *
   * @param clazz the class
   * @return the tablename, null if no such annotation
   */
  public String determineTablename(Class<?> clazz) {
    // determine from @TableName annotation
    TableName tablenameAnno = clazz.getAnnotation(TableName.class);
    if (tablenameAnno != null) {
      String tablename = tablenameAnno.value();   // cannot be null
      if (tablenameAnno.mapSchema()) {
        tablename = tablename.replace('.', '_');
      }
      tablename = tablenameAnno.prefix() + tablename;
      if (!tablename.isEmpty()) {
        return tablename;
      }
    }
    return null;   // may be SINGLE-inherited
  }


  /**
   * Determines the classid from the &#64;{@link ClassId} annotation.
   *
   * @param clazz the class
   * @return the class ID, 0 if no such annotation (maybe an abstract class)
   */
  public int determineClassId(Class<?> clazz) {
    ClassId anno = clazz.getAnnotation(ClassId.class);
    return anno == null ? 0 : anno.value();
  }


  /**
   * Starts the keep alive thread if not already running.
   */
  public synchronized void startKeepAliveDaemonIfNotRunning() {
    if (keepAliveDaemon == null || !keepAliveDaemon.isAlive()) {
      keepAliveDaemon = createKeepAliveDaemon();
      keepAliveDaemon.start();
    }
  }


  /**
   * Stops all helper threads.
   */
  public synchronized void terminateHelperThreads() {
    if (keepAliveDaemon != null && keepAliveDaemon.isAlive()) {
      keepAliveDaemon.terminate();
    }
    keepAliveDaemon = null;
  }


  /**
   * Handles the change of a keep alive interval of a session.
   *
   * @param session the session
   */
  public synchronized void keepAliveIntervalChanged(Session session) {
    startKeepAliveDaemonIfNotRunning();
    keepAliveDaemon.keepAliveIntervalChanged(session);
  }


  /**
   * Inspects an expiration set and determines whether at least one object has been removed.
   *
   * @param lastSerial the last known tableserial, &le;0 if unknown
   * @param expireSet the expiration set
   * @param maxSerial the current tableserial
   * @return true if some object removed
   */
  public boolean isSomeRemoved(long lastSerial, List<IdSerialTuple> expireSet, long maxSerial) {
    // determine whether some objects have been removed or not
    boolean someRemoved = false;
    for (IdSerialTuple idSer: expireSet) {
      if (lastSerial > 0 && idSer.getSerial() - lastSerial > 1) {
        // gap in tableSerials or gap at start: delete() invoked at least once
        someRemoved = true;
        break;
      }
      lastSerial = idSer.getSerial();
    }
    if (maxSerial > lastSerial) {
      someRemoved = true;  // no expired PDOs found or gap at end
    }
    return someRemoved;
  }


  /**
   * Replaces exception cause probably not in the jvm's classpath when the stacktrace gets deserialized
   * in another (usually remote) jvm.
   *
   * @param t the throwable
   * @return the probably replaced throwable
   */
  public Throwable alignExceptionCause(Throwable t) {
    // the default does nothing
    return t;
  }

  /**
   * Sets the temporary flag according to the exception's root cause and the session.
   *
   * @param exception the exception
   */
  public void alignTemporaryExceptionStatus(PersistenceException exception) {
    // default does nothing
  }


  /**
   * Returns whether a rollback should log all statements or not.<br>
   * The rollback should not be logged, if the root-cause is not a {@link PersistenceException},
   * or {@link PersistenceException#isTemporary()} or there is another temporary exception
   * in the exception chain.
   * <p>
   * Notice that {@code LockException}s are usually temporary.
   *
   * @param t the head of the exception chain causing the rollback
   * @return true if rollback silently, false if all statements that were rolled back are logged
   */
  public boolean isSilentRollbackSufficient(Throwable t) {
    PersistenceException rootPex = ExceptionHelper.extractException(PersistenceException.class, false, t);
    return rootPex == null || rootPex.isTemporary() || ExceptionHelper.extractTemporaryException(true, t) != null;
  }


  /**
   * Creates the lazy exception message for a {@link PersistenceException}.
   *
   * @param msg the original message
   * @param cause the cause
   * @param identifiable the identifiable
   * @param session the session
   * @return the extended message string builder
   */
  public StringBuilder createLazyExceptionMessage(String msg, Throwable cause, Identifiable identifiable, Session session) {
    StringBuilder buf = new StringBuilder();
    if (msg == null) {
      msg = "";
    }
    boolean multiline = isMultiLineExceptionMessage(msg, cause, identifiable, session);
    buf.append(msg);

    // append optional extra identifiable and/or session info
    String identStr = null;
    if (identifiable != null) {
      identStr = identifiable.toGenericString();    // toString may fail!
      if (msg.contains(identStr)) {
        identStr = null;
      }
    }
    String sessionStr = null;
    if (session != null) {
      sessionStr = session.toString();
      if (msg.contains(sessionStr)) {
        sessionStr = null;
      }
    }
    if (identStr != null || sessionStr != null) {
      if (multiline) {
        buf.append('\n');
      }
      else {
        buf.append(' ');
      }
      buf.append("[_ ");
      if (identStr != null) {
        buf.append(identStr);
      }
      if (sessionStr != null) {
        if (identStr != null) {
          buf.append(" -- ");
        }
        buf.append(sessionStr);
      }
      buf.append(" _]");
    }

    return buf;
  }


  /**
   * Creates a RuntimeException from a {@link RemoteException}.<br>
   * Returns the first RuntimeException in chain.
   * If the first non-RemoteException is not a RuntimeException, the exception
   * is wrapped by a PersistenceException.
   *
   * @param relatedObject the optional related object, only used if remote cause is not a {@link PersistenceException}
   * @param remoteException the remote exception
   * @return the runtime exception
   */
  public RuntimeException createFromRemoteException(Object relatedObject, RemoteException remoteException) {

    // find the first cause in chain which is not a RemoteException
    Throwable remoteCause = remoteException;

    boolean remoteClosed;
    if (remoteCause instanceof NoSuchObjectException) {
      remoteClosed = true;
    }
    else {
      remoteClosed = false;
      while (remoteCause instanceof RemoteException && remoteCause.getCause() != null) {
        remoteCause = remoteCause.getCause();
      }
    }

    RuntimeException pex;

    if (remoteCause instanceof RuntimeException) {
      pex = (RuntimeException) remoteCause;
    }
    else  {
      // other exception found
      if (remoteClosed) {
        if (relatedObject instanceof Identifiable) {
          pex = new SessionClosedException((Identifiable) relatedObject, remoteCause);
        }
        else if (relatedObject instanceof Session) {
          pex = new SessionClosedException((Session) relatedObject, remoteCause);
        }
        else {
          pex = new SessionClosedException((relatedObject != null ? relatedObject.toString() : ""), remoteCause);
        }
      }
      else {
        if (relatedObject instanceof Identifiable) {
          pex = new PersistenceException((Identifiable) relatedObject, remoteCause);
        }
        else if (relatedObject instanceof Session) {
          pex = new PersistenceException((Session) relatedObject, remoteCause);
        }
        else {
          pex = new PersistenceException((relatedObject != null ? relatedObject.toString() : ""), remoteCause);
        }
      }
    }

    StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    List<StackTraceElement> stack = walker.walk(s -> s.filter(sf -> ExceptionHelper.isClassValuableForStackTrace(sf.getClassName()))
                                                      .map(sf -> new StackTraceElement(sf.getDeclaringClass().getName(), sf.getMethodName(), sf.getFileName(), sf.getLineNumber()))
                                                      .collect(Collectors.toList()));
    if (stack.isEmpty()) {
      // pathological case: don't filter
      walker.walk(s -> s.map(sf -> new StackTraceElement(sf.getDeclaringClass().getName(), sf.getMethodName(), sf.getFileName(), sf.getLineNumber()))
                        .collect(Collectors.toList()));
    }
    StackTraceElement[] localTrace = new StackTraceElement[stack.size()];
    stack.toArray(localTrace);
    StackTraceElement[] remoteTrace = pex.getStackTrace();
    StackTraceElement[] trace = new StackTraceElement[localTrace.length + remoteTrace.length];
    System.arraycopy(remoteTrace, 0, trace, 0, remoteTrace.length);
    trace[remoteTrace.length] = new StackTraceElement(">>>>> REMOTE METHOD INVOCATION >>>>>", "", "", 0);
    System.arraycopy(localTrace, 1, trace, remoteTrace.length + 1, localTrace.length - 1);
    pex.setStackTrace(trace);

    return pex;
  }



  /**
   * Returns whether the exception message spans multiple lines.
   *
   * @param msg the original message
   * @param cause the cause
   * @param identifiable the identifiable
   * @param session the session
   * @return true if multiple lines
   */
  protected boolean isMultiLineExceptionMessage(String msg, Throwable cause, Identifiable identifiable, Session session) {
    return msg.length() > 80 || msg.contains("\n");
  }

  /**
   * Creates the session keep alive daemon.
   *
   * @return the daemon (not started yet!)
   */
  protected SessionKeepAliveDaemon createKeepAliveDaemon() {
    return new SessionKeepAliveDaemon(1000);    // 1 second minAliveInterval
  }

}

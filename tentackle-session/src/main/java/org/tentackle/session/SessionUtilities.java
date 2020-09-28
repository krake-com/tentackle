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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.ServiceFinder;
import org.tentackle.common.StringHelper;
import org.tentackle.misc.IdSerialTuple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


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
   * map of class names to tablenames.
   */
  private final Map<String,String> classTableMap;

  /**
   * map of tablenames to class names.
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
   * Gets the tablename of a pdo class.
   *
   * @param className the classname of the object
   * @return the tablename, null if no such class
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
   * @return the classid, 0 if no such class
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
   * Determines the tablename from the @TableName annotation.
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
   * Determines the classid from the @ClassId annotation.
   *
   * @param clazz the class
   * @return the class ID, 0 if no such annotation (may be an abstract class)
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
      someRemoved = true;  // no expired pdos found or gap at end
    }
    return someRemoved;
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

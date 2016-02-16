/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.pdo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.ServiceFinder;
import org.tentackle.misc.StringHelper;
import org.tentackle.misc.TrackedList;


interface PdoUtilities$Singleton {
  PdoUtilities INSTANCE = ServiceFactory.createService(PdoUtilities.class, PdoUtilities.class);
}

/**
 * Utility methods for PDOs.
 *
 * @author harald
 */
@Service(PdoUtilities.class)      // defaults to self
public class PdoUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static PdoUtilities getInstance() {
    return PdoUtilities$Singleton.INSTANCE;
  }


  /** minimum keep alive interval in milliseconds. */
  public static long MIN_SESSION_KEEPALIVE_MS = 1000;



  /**
   * The keep alive thread.<br>
   * null if not started yet
   */
  private SessionKeepAliveDaemon keepAliveDaemon;

  /**
   * The map of tablenames.
   */
  private final Map<Class<? extends PersistentDomainObject<?>>, String> tableNameMap = new ConcurrentHashMap<>();

  /**
   * The map of singlenames (class -> singlename)
   */
  private final Map<String, String> singularMap;

  /**
   * The map of multinames (class -> multiname)
   */
  private final Map<String, String> pluralMap;

  /**
   * map of pdo class names to tablenames.
   */
  private final Map<String,String> pdoTableMap;

  /**
   * map of tablenames to pdo class names.
   */
  private final Map<String,String> tablePdoMap;

  /**
   * map of pdo class names to class IDs.
   */
  private final Map<String,Integer> pdoIdMap;

  /**
   * map of class IDs to pdo class names.
   */
  private final Map<Integer,String> idPdoMap;

  /**
   * List of all pdo class names.
   */
  private final Collection<String> pdoNames;


  /**
   * Creates a utility instance.
   */
  public PdoUtilities() {
    ServiceFinder finder = ServiceFactory.getServiceFinder();
    pdoTableMap = new HashMap<>();
    tablePdoMap = new HashMap<>();

    Set<String> sortedNames = new TreeSet<>();
    for (Map.Entry<String,String> entry: finder.createNameMap(TableName.class.getName()).entrySet()) {
      String tableName = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
      pdoTableMap.put(entry.getValue(), tableName);
      tablePdoMap.put(tableName, entry.getValue());
      sortedNames.add(entry.getValue());
    }

    Map<String,String> idMap = finder.createNameMap(ClassId.class.getName());
    pdoIdMap = new HashMap<>();
    idPdoMap = new HashMap<>();
    for (Map.Entry<String,String> entry: idMap.entrySet()) {
      Integer classId = new Integer(entry.getKey());
      sortedNames.add(entry.getValue());
      pdoIdMap.put(entry.getValue(), classId);
      idPdoMap.put(classId, entry.getValue());
    }
    pdoNames = new ArrayList<>(sortedNames);

    singularMap = new HashMap<>();
    for (Map.Entry<String,String> entry: finder.createNameMap(Singular.class.getName()).entrySet()) {
      String singleName = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
      singularMap.put(entry.getValue(), singleName);
    }

    pluralMap = new HashMap<>();
    for (Map.Entry<String,String> entry: finder.createNameMap(Plural.class.getName()).entrySet()) {
      String multiName = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
      pluralMap.put(entry.getValue(), multiName);
    }
  }


  /**
   * Gets the tablename of a pdo class.
   *
   * @param pdoClassName the classname of the pdo
   * @return the tablename, null if no such class
   */
  public String getTableName(String pdoClassName) {
    return pdoTableMap.get(pdoClassName);
  }

  /**
   * Gets the pdo classname for tablename.
   *
   * @param tableName the tablename
   * @return the name of the pdo class, null if no such tablename
   */
  public String getPdoClassName(String tableName) {
    return tablePdoMap.get(tableName);
  }

  /**
   * Gets the classid of a pdo class.
   *
   * @param pdoClassName the classname of the pdo
   * @return the classid, 0 if no such class
   */
  public int getClassId(String pdoClassName) {
    Integer classId = pdoIdMap.get(pdoClassName);
    return classId == null ? 0 : classId;
  }

  /**
   * Gets the pdo classname for a classid.
   *
   * @param classId the classid
   * @return the name of the pdo class, null if no such classid
   */
  public String getPdoClassName(int classId) {
    return idPdoMap.get(classId);
  }

  /**
   * Gets a list of all pdo class names.<br>
   * Sorted by name.
   *
   * @return the classnames
   */
  public Collection<String> getPdoClassNames() {
    return pdoNames;
  }


  /**
   * Gets the singular of given class.
   *
   * @param clazz the class
   * @return the singular, null if not annotated with @Singular
   */
  public String getSingular(Class<?> clazz) {
    return singularMap.get(clazz.getName());
  }

  /**
   * Gets the plural of given class.
   *
   * @param clazz the class
   * @return the plural, null if not annotated with @Plural
   */
  public String getPlural(Class<?> clazz) {
    return pluralMap.get(clazz.getName());
  }


  /**
   * Determines the tablename from the @TableName annotation.
   *
   * @param pdoClass the PDO class
   * @return the tablename, null if no such annotation
   */
  public String determineTablename(Class<?> pdoClass) {
    // determine from @TableName annotation
    TableName tablenameAnno = pdoClass.getAnnotation(TableName.class);
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
   * @param pdoClass the PDO class
   * @return the tablename, null if no such annotation
   */
  public int determineClassId(Class<?> pdoClass) {
    // determine from @TableName annotation
    ClassId idAnno = pdoClass.getAnnotation(ClassId.class);
    if (idAnno != null) {
      int classId = idAnno.value();
      if (classId != 0) {
        return classId;
      }
    }
    return 0;   // may be an abstract class
  }


  /**
   * Determines the serviced class according to the annotation.
   *
   * @param implementingClass the implementing class
   * @return the serviced class, null if no annotation found
   * @see PersistentObjectService
   * @see PersistentOperationService
   * @see DomainObjectService
   */
  public Class<?> determineServicedClass(Class<?> implementingClass) {
    PersistentObjectService pobj = implementingClass.getAnnotation(PersistentObjectService.class);
    if (pobj != null) {
      return pobj.value();
    }
    PersistentOperationService popr = implementingClass.getAnnotation(PersistentOperationService.class);
    if (popr != null) {
      return popr.value();
    }
    DomainObjectService dom = implementingClass.getAnnotation(DomainObjectService.class);
    if (dom != null) {
      return dom.value();
    }
    DomainOperationService dop = implementingClass.getAnnotation(DomainOperationService.class);
    if (dop != null) {
      return dop.value();
    }
    return null;
  }

  /**
   * Starts the keep alive thread if not already running.
   */
  public synchronized void startKeepAliveDaemonIfNotRunning() {
    if (keepAliveDaemon == null || !keepAliveDaemon.isAlive()) {
      keepAliveDaemon = new SessionKeepAliveDaemon(MIN_SESSION_KEEPALIVE_MS);
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
  public void keepAliveIntervalChanged(Session session) {
    startKeepAliveDaemonIfNotRunning();
    keepAliveDaemon.keepAliveIntervalChanged(session);
  }



  /**
   * Filters a collection of Objects, returning only
   * those that are persistent domain objects.
   *
   * @param col the collection of objects
   * @return a list of persistent domain objects, the rest is ignored. Never null.
   */
  public List<PersistentDomainObject<?>> filterPersistentDomainObjects(Collection<?> col) {
    List<PersistentDomainObject<?>> list = new ArrayList<>();
    if (col != null)  {
      for (Object obj: col)  {
        if (obj instanceof PersistentDomainObject) {
          list.add((PersistentDomainObject)obj);
        }
      }
    }
    return list;
  }




  /**
   * Deletes a List of objects.
   * Virgin objects are not deleted.
   *
   * @param pdos the list of object to delete
   * @return the number of objects deleted, -1 if error  (some object wasn't deleted)
   */
  public int deleteCollection (Collection<? extends PersistentDomainObject<?>> pdos) {
    int deleted = 0;
    if (pdos != null && pdos.size() > 0) {
      Session session = null;
      long txVoucher = 0;
      try {
        for (PersistentDomainObject<?> obj: pdos) {
          if (obj != null && !obj.isVirgin()) {
            if (session == null) {
              session = obj.getSession();
              if (session == null) {
                throw new PdoRuntimeException(obj, "session is null");
              }
              txVoucher = session.begin("delete collection");
            }
            else if (obj.getSession() != session) {
              // must be the same instance of Db!
              throw new PdoRuntimeException(obj, "unexpected session: " + obj.getSession() + ", expected: " + session);
            }
            obj.delete();
            deleted++;
          }
        }
        if (session != null) {
          session.commit(txVoucher);
        }
        if (pdos instanceof TrackedList<?>) {
          ((TrackedList<?>) pdos).setModified(false);
        }
      }
      catch (RuntimeException rex) {
        if (session != null) {
          session.rollback(txVoucher);
        }
        throw rex;
      }
    }
    return deleted;
  }


  /**
   * Deletes all objects in oldList that are not in newList.<br>
   * The method is handy for deleting cascaded composite lists.
   *
   * @param oldCollection the list of objects stored in db
   * @param newCollection the new list of objects
   *
   * @return the number of objects deleted, -1 if some error
   */
  public int deleteMissingInCollection(Collection<? extends PersistentDomainObject<?>> oldCollection,
                                              Collection<? extends PersistentDomainObject<?>> newCollection) {
    int deleted = 0;
    if (oldCollection != null && oldCollection.size() > 0) {
      Session session = null;
      long txVoucher = 0;
      try {
        for (PersistentDomainObject<?> obj: oldCollection) {
          if (obj != null && !obj.isVirgin() && (newCollection == null || !newCollection.contains(obj))) {
            if (session == null) {
              session = obj.getSession();
              if (session == null) {
                throw new PersistenceException(obj, "session is null");
              }
              txVoucher = session.begin("delete missing in collection");
            }
            else if(obj.getSession() != session) {
              // must be the same instance of Db!
              throw new PersistenceException(obj, "unexpected session: " + obj.getSession() + ", expected: " + session);
            }
            obj.delete();
            deleted++;
          }
        }
        if (session != null) {
          session.commit(txVoucher);
        }
      }
      catch (RuntimeException rex) {
        if (session != null) {
          session.rollback(txVoucher);
        }
        throw rex;
      }
    }
    return deleted;
  }



  /**
   * Checks whether some of the objects in the list are modified.
   * Useful for recursive optimizations.
   *
   * @param pdos the objects
   * @return true if modified
   */
  public boolean isCollectionModified(Collection<? extends PersistentDomainObject<?>> pdos) {
    // TrackedArrayLists are modified if elements added, replaced or removed
    if (pdos instanceof TrackedList<?> &&
        ((TrackedList<?>) pdos).isModified())  {
      return true;
    }
    // check attributes
    if (pdos != null) {
      for (PersistentDomainObject<?> obj: pdos)  {
        if (obj != null && obj.isModified()) {
          return true;
        }
      }
    }
    return false;
  }



  /**
   * Gets the table name of given class.
   *
   * @param <T> the pdo type
   * @param clazz the pdo class
   * @return the name
   */
  public <T extends PersistentDomainObject<T>> String getTableName(Class<T> clazz) {
    String pdoClassName = clazz.getName();
    String tableName = getTableName(pdoClassName);
    if (tableName == null) {  // not annotated with @TableName
      tableName = tableNameMap.get(clazz);  // try extra list
      if (tableName == null) {
        // not in extra list: get from pdo instance
        tableName = Pdo.create(clazz).getTableName();
        tableNameMap.put(clazz, tableName);
      }
    }
    return tableName;
  }


  /**
   * Gets the table name of given classes.
   *
   * @param classes the PDO classes
   * @return the names, null if classes is null or empty
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public String[] getTableNames(Class<?>... classes) {
    String[] names;
    if (classes != null && classes.length > 0) {
      names = new String[classes.length];
      int i=0;
      for (Class<?> clazz: classes) {
        names[i++] = PdoUtilities.getInstance().getTableName((Class<? extends PersistentDomainObject>) clazz);
      }
    }
    else  {
      names = null;
    }
    return names;
  }


  /**
   * Saves a list of PDOs.<br>
   * All objects with isSaveable() == true will be saved.
   * If modifiedOnly is true only isModified() objects will be saved.
   * All objects with isSaveable() == false and isNew() == false
   * are removed!
   * By definition, a {@link TrackedList} must *NOT* contain untracked objects.
   * The errorhandler will be invoked if such an object
   * is detected. This is a quality measure to ensure code consistency.
   * The wurblets automatically take care of that.
   *
   * @param pdos the list to save
   * @param modifiedOnly is true if only modified objects are saved
   * @param usePersist true if use persist() instead of save() and replace objects in collection
   * @return the number of objects saved, -1 if some error
   */
  @SuppressWarnings("unchecked")
  public int saveCollection(Collection<? extends PersistentDomainObject<?>> pdos, boolean modifiedOnly, boolean usePersist) {
    int saved = 0;
    if (pdos != null && pdos.size() > 0) {
      Session session = null;
      long txVoucher = 0;
      List<PersistentDomainObject<?>> persistedObjects = null;
      if (usePersist) {
        persistedObjects = new ArrayList<>();
      }
      try {
        for (PersistentDomainObject<?> obj: pdos) {
          if (obj != null) {
            if (session == null) {
              session = obj.getSession();
              if (session == null) {
                throw new PdoRuntimeException(obj, "session is null");
              }
              txVoucher = session.begin("save collection");
            }
            else if (obj.getSession() != session) {
              // must be the same instance of Db!
              throw new PdoRuntimeException(obj, "unexpected db connection: " + obj.getSession() + ", expected: " + session);
            }
            if (obj.isPersistable()) {
              if (!modifiedOnly || obj.isModified())  {
                if (usePersist) {
                  persistedObjects.add(obj.persist());
                }
                else  {
                  obj.save();
                }
                saved++;
              }
            }
            else  {
              if (!obj.isNew()) {
                // already stored on disk: remove it!
                obj.delete();
              }
            }
          }
        }
        if (session != null) {
          session.commit(txVoucher);
        }
        if (usePersist) {
          pdos.clear();
          ((Collection<PersistentDomainObject<?>>) pdos).addAll(persistedObjects);
        }
        if (pdos instanceof TrackedList<?>) {
          ((TrackedList<?>) pdos).setModified(false);
        }
      }
      catch (RuntimeException rex) {
        if (session != null) {
          session.rollback(txVoucher);
        }
        throw rex;
      }
    }
    return saved;
  }


  /**
   * Saves a list of PDOs.<br>
   * All objects with isSaveable() == true will be saved.
   * If modifiedOnly is true only isModified() objects will be saved.
   * All objects with isSaveable() == false and isNew() == false
   * are removed!
   * By definition, a {@link TrackedList} must *NOT* contain untracked objects.
   * The errorhandler will be invoked if such an object
   * is detected. This is a quality measure to ensure code consistency.
   * The wurblets automatically take care of that.
   *
   * @param pdos the list to save
   * @param modifiedOnly is true if only modified objects are saved
   * @return the number of objects saved, -1 if some error
   */
  public int saveCollection(Collection<? extends PersistentDomainObject<?>> pdos, boolean modifiedOnly) {
    return saveCollection(pdos, modifiedOnly, false);
  }


  /**
   * Saves a collection of PDOs.
   *
   * @param pdos the collection of PDOs
   *
   * @return the number of objects saved, -1 if some error
   */
  public int saveCollection(Collection<? extends PersistentDomainObject<?>> pdos) {
    return saveCollection(pdos, false);
  }

}

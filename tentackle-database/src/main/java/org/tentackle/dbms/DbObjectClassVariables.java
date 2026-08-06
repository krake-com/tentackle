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

package org.tentackle.dbms;

import org.tentackle.dbms.trip.AbstractDbObjectRemoteDelegate;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionUtilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * Holds static class variables for classes derived from AbstractDbObject.<br>
 * This is a "singleton per class".
 *
 * @param <P> the persistence type
 * @author harald
 */
public class DbObjectClassVariables<P extends AbstractDbObject<P>> {

  // all classes register here:
  private static final Map<String,DbObjectClassVariables<? extends AbstractDbObject<?>>> CLASSMAP = new HashMap<>();


  /**
   * Creates a class variable for a db object.
   *
   * @param clazz the class of the derived AbstractDbObject
   * @param classId the unique class id, 0 if abstract
   * @param tableName theSQL table name
   * @param <P> the persistence type
   */
  public static <P extends AbstractDbObject<P>> DbObjectClassVariables<P> create(Class<P> clazz, int classId, String tableName) {
    return DbClassVariablesFactory.getInstance().dbPoCv(clazz, classId, tableName);
  }

  /**
   * Creates a class variable for a db object.<br>
   * ClassId and tablename are derived from mapped services in META-INF.
   *
   * @param clazz the class of the derived AbstractDbObject
   * @param <P> the persistence type
   */
  public static <P extends AbstractDbObject<P>> DbObjectClassVariables<P> create(Class<P> clazz) {
    return DbClassVariablesFactory.getInstance().dbPoCv(clazz);
  }

  /**
   * Gets the class variables for a given tablename.
   *
   * @param <P> the persistence type
   * @param tableName the database tablename
   * @return the class variables or null if no such tablename
   */
  @SuppressWarnings("unchecked")
  public static <P extends AbstractDbObject<P>> DbObjectClassVariables<P> getVariables(String tableName) {
    synchronized(CLASSMAP) {
      return (DbObjectClassVariables<P>) CLASSMAP.get(tableName);
    }
  }

  /**
   * Registers a class variables instance.
   *
   * @param <P> the persistence type
   * @param variables the class variables to register
   */
  public static <P extends AbstractDbObject<P>> void putVariables(DbObjectClassVariables<P> variables) {
    if (variables.tableName != null) {
      synchronized(CLASSMAP) {
        @SuppressWarnings("unchecked")
        DbObjectClassVariables<P> classVar = (DbObjectClassVariables<P>) CLASSMAP.get(variables.tableName);
        if (classVar != null) {
          /*
           * Using an extended class with the same tablename is okay.
           * This is because the class hierarchy will register along the extension path.
           * In all other cases the tablename must be unique.
           */
          if (!classVar.clazz.isAssignableFrom(variables.clazz)) {
            throw new PersistenceException(
                    "class variables for '" + variables.tableName + "' already registered, old=" + classVar.clazz.getName() +
                    ", new=" + variables.clazz.getName());
          }
        }
        else  {
          CLASSMAP.put(variables.tableName, variables);
        }
      }
    }
  }


  /**
   * A reference to a class that is referencing this class.
   *
   * @param methods the method chain
   * @param priority the priority, lower indicates a higher priority
   * @param methodPath the method path name
   */
  protected record ForeignReference(Method[] methods, int priority, String methodPath) implements Comparable<ForeignReference> {

    /**
     * Sorted by priority plus method.
     */
    @Override
    public int compareTo(ForeignReference o) {
      int rv = Integer.compare(priority, o.priority);
      if (rv == 0) {
        rv = methodPath.compareTo(o.methodPath);
        if (rv == 0) {
          rv = Objects.compare(methods, o.methods, (o1, o2) -> {
            int mv = o1.length - o2.length;   // no Integer.compare here!
            if (mv == 0) {
              for (int i=0; i < o1.length && mv == 0; i++) {
                mv = o1[i].toString().compareTo(o2[i].toString());
              }
            }
            return mv;
          });
        }
      }
      return rv;
    }
  }

  /**
   * References from other classes.
   */
  protected final Set<ForeignReference> foreignReferences = new TreeSet<>();


  /**
   * the class.
   */
  public final Class<P> clazz;

  /**
   * The unique class id.
   */
  public final int classId;

  /**
   * the base-classname.
   */
  public final String classBaseName;

  /**
   * database table name.
   */
  public final String tableName;

  /**
   * prepared statement ID for select().
   */
  public final StatementId selectObjectStatementId = new StatementId();

  /**
   * prepared statement ID for selectAll().
   */
  public final StatementId selectAllObjectsStatementId = new StatementId();

  /**
   * prepared statement ID for selectAllIdSerial().
   */
  public final StatementId selectAllIdSerialStatementId = new StatementId();

  /**
   * prepared statement ID for selectForUpdate().
   */
  public final StatementId selectForUpdateStatementId = new StatementId();

  /**
   * prepared statement ID for selectSerial().
   */
  public final StatementId selectSerialStatementId = new StatementId();

  /**
   * prepared statement ID for selectMaxId().
   */
  public final StatementId selectMaxIdStatementId = new StatementId();

  /**
   * prepared statement ID for selectMaxTableSerial().
   */
  public final StatementId selectMaxTableSerialStatementId = new StatementId();

  /**
   * prepared statement ID for insert().
   */
  public final StatementId insertStatementId = new StatementId();

  /**
   * prepared statement ID for update().
   */
  public final StatementId updateStatementId = new StatementId();

  /**
   * prepared statement ID for delete().
   */
  public final StatementId deleteStatementId = new StatementId();

  /**
   * prepared statement ID for dummyUpdate().
   */
  public final StatementId dummyUpdateStatementId = new StatementId();

  /**
   * prepared statement ID for updateSerial().
   */
  public final StatementId updateSerialStatementId = new StatementId();

  /**
   * prepared statement ID for updateSerial(long).
   */
  public final StatementId updateAndSetSerialStatementId = new StatementId();

  /**
   * prepared statement ID for updateTableSerial().
   */
  public final StatementId updateTableSerialStatementId = new StatementId();

  /**
   * prepared statement ID for updateSerialAndTableSerial().
   */
  public final StatementId updateSerialAndTableSerialStatementId = new StatementId();

  /**
   * prepared statement ID for selectExpiredTableSerials().
   */
  public final StatementId selectExpiredTableSerials1StatementId = new StatementId();
  public final StatementId selectExpiredTableSerials2StatementId = new StatementId();

  /**
   * prepared statement ID for selectObjectsWithExpiredTableSerials
   */
  public final StatementId selectObjectsWithExpiredTableSerialsStatementId = new StatementId();

  /**
   * table serial expiration backlog.
   */
  public final TableSerialExpirationBacklog expirationBacklog;


  /**
   * Number of columns in a resultset.
   *
   */
  int columnCount;    // package scope!

  /**
   * true if prepared statements should always be prepared (i.e., if
   * the statement is changing for some reason, e.g., the table name).
   */
  boolean alwaysPrepare;  // package scope!


  /**
   * The source for getting a new ID.
   */
  private IdSource idSource;

  /**
   * ID for the remote delegate for this class.<br>
   * Volatile, since lazily initialized by {@link #getRemoteDelegateId()} from arbitrary threads.
   */
  private volatile int remoteDelegateId;

  /**
   * class properties.
   */
  private Properties properties;


  /**
   * Constructs a class variable.<br>
   * Throws IllegalStateException if already constructed.
   *
   * @param clazz the class of the derived AbstractDbObject
   * @param classId the unique class id, 0 if abstract
   * @param tableName theSQL table name
   */
  public DbObjectClassVariables(Class<P> clazz, int classId, String tableName)  {

    this.clazz = clazz;
    this.classId = classId;
    this.tableName = tableName;

    classBaseName = clazz.getSimpleName();
    expirationBacklog = createBacklog();

    // register and check extension hierarchy
    putVariables(this);
  }


  /**
   * Constructs a class variable.<br>
   * Throws IllegalStateException if already constructed.
   * ClassId and tablename are derived from META-INF.
   *
   * @param clazz the class of the derived AbstractDbObject
   */
  public DbObjectClassVariables(Class<P> clazz) {
    this(clazz,
         SessionUtilities.getInstance().determineClassId(clazz),
         SessionUtilities.getInstance().determineTablename(clazz));
  }


  /**
   * Creates the table serial backlog.
   *
   * @return the backlog
   */
  protected TableSerialExpirationBacklog createBacklog() {
    return new TableSerialExpirationBacklog(tableName);
  }


  /**
   * Lazily gets the properties.<br>
   * Loads and initially processes the properties.
   *
   * @return the properties, never null
   */
  public Properties getProperties() {
    if (properties == null) {
      // load properties, if any, from file or resource
      String propertiesName = clazz.getName().replace('.', '/');
      properties = PropertiesUtilities.getInstance().loadIfExists(propertiesName);
      if (properties == null) {
        // missing props is okay and the default
        properties = PropertiesUtilities.getInstance().create();
      }
    }
    return properties;
  }


  /**
   * Get the IdSource.<br>
   *
   * If the IdSource is null, the method will look for
   * a property file which is the classname + ".properties".
   * The property file must contain an {@code idsource} line
   * similar to that in Db.properties.<br>
   * If there is no such file, the source from the db will be used.
   *
   * @param db the session
   * @return the idSource
   */
  public synchronized IdSource getIdSource(Db db) {
    if (idSource == null) {  // set up IdSource once for this class
      String idConfig = getProperty(Db.IDSOURCE);
      if (idConfig == null) {
        // no such property: use the default id source from Db
        idSource = db.getDefaultIdSource();
      }
      else  {
        // create configurator and source from property
        idSource = IdSourceConfigurator.getInstance().configure(db, idConfig);
      }
    }
    return idSource;
  }



  /**
   * Determine the referencing priority.
   * <p>
   * The default implementation returns 0.
   * Provided to be overridden.
   *
   * @param clazz the referencing class
   * @return the priority, lower numbers indicate a higher priority
   */
  protected int determineReferencePriority(Class<? extends AbstractDbObject<?>> clazz) {
    // all objects get the same priority by default
    return 0;
  }

  /**
   * Adds a PDO class referencing the class of this class variable holder.
   * <p>
   * The referencing class must provide a static method <code>isReferencing&lt;ClassBasename&gt;</code>,
   * while <code>ClassBaseName</code> is the {@link #classBaseName} of the referenced class.
   * The method must provide the objectId as its only parameter and is usually generated
   * by the ???IsReferencing-wurblets.<br>
   * The method name can also contain dots to denote a chain of methods to invoke.
   *
   * @param clazz the class to add
   * @param methodPath the method path name, null if default <code>"isReferencing&lt;ClassBaseName&gt;"</code>
   * @return true if added, false if already in the map
   * @see #removeReferencingClass
   */
  public boolean addReferencingClass(Class<? extends AbstractDbObject<?>> clazz, String methodPath) {
    ForeignReference ref = createForeignReference(clazz, methodPath);
    synchronized(foreignReferences) {
      return foreignReferences.add(ref);
    }
  }

  /**
   * Removes a PDO class referencing the class from this class variable holder.
   *
   * @param clazz the class to be removed
   * @param methodPath the method path name, null if default
   * @return true if removed, false if not in the map
   * @see #addReferencingClass
   */
  public boolean removeReferencingClass(Class<? extends AbstractDbObject<?>> clazz, String methodPath) {
    ForeignReference ref = createForeignReference(clazz, methodPath);
    synchronized(foreignReferences) {
      return foreignReferences.remove(ref);
    }
  }


  private ForeignReference createForeignReference(Class<? extends AbstractDbObject<?>> clazz, String methodPath) {
    if (methodPath == null) {
      methodPath = "isReferencing" + classBaseName;
    }

    try {
      StringTokenizer stok = new StringTokenizer(methodPath, ".");
      List<Method> methods = new ArrayList<>();
      Class<?> methodClass = clazz;
      while (stok.hasMoreTokens()) {
        String methodName = stok.nextToken();
        Method method;
        if (stok.hasMoreTokens()) {   // getter method to another object
          method = methodClass.getMethod(methodName);   // no args
          methodClass = determineImplementingClassOfReturnType(method.getReturnType());
        }
        else {  // isReferencingMethod
          try {
            method = methodClass.getMethod(methodName, Long.TYPE);
          }
          catch (NoSuchMethodException e1) {
            // try Long instead of long
            method = methodClass.getMethod(methodName, Long.class);
          }
        }
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
          throw new PersistenceException(methodClass.getName() + "." + method.getName() + " must be public and non-static");
        }
        methods.add(method);
      }
      return new ForeignReference(methods.toArray(new Method[0]), determineReferencePriority(clazz), methodPath);
    }
    catch (NoSuchMethodException ex) {
      throw new PersistenceException("please add an *IsReferencing-wurblet to generate " + clazz.getName() + "." + methodPath, ex);
    }
  }


  /**
   * Determines the implementing class for a type returned by a method.
   *
   * @param clazz the method's returned class
   * @return the implementing class
   */
  protected Class<?> determineImplementingClassOfReturnType(Class<?> clazz) {
    return clazz;   // same in low-level persistence layer
  }


  /**
   * Determines whether the class of this variable holder is referenced.
   *
   * @param db the session
   * @param id the object id to test whether it is referenced by any other objects
   * @return true if referenced
   */
  public boolean isReferenced(Db db, long id) {
    return isReferencedImpl(db, id, getForeignReferences());
  }


  /**
   * Gets a snapshot of the foreign references.
   * <p>
   * The references are copied while holding the monitor of {@code foreignReferences}, which is the
   * same monitor {@link #addReferencingClass} and {@link #removeReferencingClass} write under.
   * {@link #isReferencedImpl} queries the database for every reference, so it must iterate a copy:
   * holding the monitor for the duration of those queries would block registration of further
   * referencing classes, and iterating the live set without it risks a
   * {@link java.util.ConcurrentModificationException}.
   *
   * @return the unmodifiable collection of foreign references, never null
   */
  protected Collection<ForeignReference> getForeignReferences() {
    synchronized (foreignReferences) {
      return List.copyOf(foreignReferences);    // the TreeSet's order is retained
    }
  }


  /**
   * Determines whether the class of this variable holder is referenced.
   *
   * @param db the session
   * @param id the object id to test whether it is referenced by any other objects
   * @param foreignReferences the foreign references
   * @return true if referenced
   */
  protected boolean isReferencedImpl(Db db, long id, Collection<ForeignReference> foreignReferences) {
    for (ForeignReference ref : foreignReferences) {
      try {
        @SuppressWarnings("unchecked")
        Object obj = AbstractDbObject.newInstance(db, (Class<AbstractDbObject<?>>) ref.methods[0].getDeclaringClass());
        for (int methodIndex = 0; methodIndex < ref.methods.length; methodIndex++) {
          if (methodIndex < ref.methods.length - 1) {
            obj = ref.methods[methodIndex].invoke(obj);
          }
          else if (Boolean.TRUE.equals(ref.methods[methodIndex].invoke(obj, id))) {
            return true;
          }
        }
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
        throw new PersistenceException("couldn't invoke " + ref.methodPath, ex);
      }
    }
    return false;
  }


  /**
   * Gets the delegateId of the class, i.e., subclass of AbstractDbObject.<br>
   * If the remoteDelegateId is 0, it will be prepared
   * The delegateId is unique for each class. It is valid only in remote connections and
   * is the same for all remote sessions.
   * The TRIP-server creates a delegate for each subclass of AbstractDbObject (DbObjectRemoteDelegateImpl resp.)
   *
   * @return the delegate id
   */
  public int getRemoteDelegateId() {
    int id = remoteDelegateId;
    if (id == 0)  {
      synchronized (this) {
        id = remoteDelegateId;
        if (id == 0)  {   // still unprepared: another thread may have won the race
          id = Db.prepareRemoteDelegate(clazz);
          remoteDelegateId = id;
        }
      }
    }
    return id;
  }

  /**
   * Gets the RemoteDelegate for the class and session.
   *
   * @param db the session
   * @return the delegate
   */
  @SuppressWarnings("unchecked")
  public AbstractDbObjectRemoteDelegate<P> getRemoteDelegate(Db db) {
    return (AbstractDbObjectRemoteDelegate<P>) db.getRemoteDelegate(getRemoteDelegateId());
  }


  /**
   * Gets a class property.
   *
   * @param key the property key
   * @return the property value
   */
  public String getProperty(String key) {
    return getProperties().getProperty(key);
  }

  /**
   * Sets a class property.
   *
   * @param key the property key
   * @param value the property value
   * @return the old property value, null if new
   */
  public String putProperty(String key, String value) {
    return (String) getProperties().put(key, value);
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(clazz.getName());
    if (tableName != null) {
      buf.append("/'").append(tableName).append("'");
    }
    return buf.toString();
  }

}

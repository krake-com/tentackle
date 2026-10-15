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

import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.dbms.trip.PreloadRemoteDelegate;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.io.ReconnectionPolicy;
import org.tentackle.session.BackendConfiguration;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionClosedException;
import org.tentackle.session.SessionPoolProvider;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


interface DbUtilitiesHolder {
  DbUtilities INSTANCE = ServiceFactory.createService(DbUtilities.class, DbUtilities.class);
}


/**
 * Persistence utility methods.<br>
 * This singleton is provided mainly to allow a clean separation between the
 * lower- and higher level persistence layer implementations.<br>
 * It is replaced by {@code PersistenceUtilities} from the tentackle-persistence module
 * to make it PDO-aware.
 *
 * @author harald
 */
@Service(DbUtilities.class)   // defaults to self
public class DbUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static DbUtilities getInstance() {
    return DbUtilitiesHolder.INSTANCE;
  }


  private static class BackendKey {
    private volatile int fastHash;
    private final Backend backend;
    private final String url;

    private BackendKey(BackendInfo backendInfo) {
      this.backend = backendInfo.getBackend();
      this.url = backendInfo.getUrl();
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;

      BackendKey that = (BackendKey) o;
      return Objects.equals(backend, that.backend) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
      if (fastHash != 0) {
        return fastHash;
      }
      int result = 31 * Objects.hashCode(backend);
      result = 31 * result + Objects.hashCode(url);
      if (result == 0) {
        result++;
      }
      fastHash = result;
      return result;
    }
  }

  private final Map<BackendKey, Integer> backendIdentityMap = new ConcurrentHashMap<>();

  private ConnectionManager connectionManager;
  private volatile String[] preloadDelegateClassNames;   // cached names of the preload candidates

  /**
   * Creates the db utilities.
   */
  public DbUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the physical backend identity to speed up lookups.
   * <p>
   * The default implementation returns a unique integer for the same backend and url.
   * <p>
   * Important: if the application connects more than once to the same backend with effectively the same
   * URL, but different connection options (for example, readonly), different IDs will be returned.
   * In this case, the backend identity of BackendInfo is used if set, otherwise a unique integer is returned.
   * Use high or negative values for backend identities to avoid conflicts. Created numbers start at 1.
   *
   * @param backendInfo the backend info
   * @return the backend identity
   */
  public int getBackendIdentity(BackendInfo backendInfo) {
    return backendIdentityMap.computeIfAbsent(new BackendKey(backendInfo),
                                              _ -> backendInfo.getBackendIdentity() != 0 ?
                                                     backendInfo.getBackendIdentity() :
                                                     backendIdentityMap.size() + 1);
  }

  /**
   * Creates a session-less object for the given class.
   *
   * @param <T> the object type
   * @param clazz the object class
   * @return the initialized object, null if clazz is not a persistence class
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T> T createObject(Class<T> clazz) {
    T object = null;
    if (AbstractDbObject.class.isAssignableFrom(clazz)) {
      object = (T) AbstractDbObject.newInstance((Class) clazz);
    }
    return object;
  }

  /**
   * Loads an object from the database.
   *
   * @param <T> the object type
   * @param session the session
   * @param clazz the object class
   * @param objectId the object id
   * @param loadLazyReferences true if load lazy references
   * @return the object, null if no such object
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T> T selectObject(Session session, Class<T> clazz, long objectId, boolean loadLazyReferences) {
    T object = null;
    if (AbstractDbObject.class.isAssignableFrom(clazz)) {
      object = (T) AbstractDbObject.newInstance(session, (Class) clazz).selectObject(objectId);
      // load any lazy references that may be necessary for replay on the remote side
      if (object != null && loadLazyReferences) {
        ((AbstractDbObject) object).loadLazyReferences();
      }
    }
    return object;
  }

  /**
   * Determines the name of the table holding the tableserial for a PDO class.
   *
   * @param clazzVar the class variables
   * @return the tablename holding the tableserial, null if no tableserial
   */
  public String determineTableSerialTableName(DbObjectClassVariables<?> clazzVar) {
    try {
      String tableSerialTableName = null;
      AbstractDbObject<?> po = AbstractDbObject.newInstance(clazzVar.clazz);
      if (po.isTableSerialProvided()) {
        tableSerialTableName = po.getTableName();
      }
      return tableSerialTableName;
    }
    catch (RuntimeException ex) {
      throw new IllegalStateException(
              "can't evaluate the name of the table holding the tableserial for " + clazzVar, ex);
    }
  }

  /**
   * Gets the default connection manager.
   *
   * @return the connection manager, never null
   */
  public synchronized ConnectionManager getDefaultConnectionManager() {
    if (connectionManager == null) {
      connectionManager = new DefaultConnectionManager();
    }
    return connectionManager;
  }

  /**
   * Gets the session pool provider.
   *
   * @return the session pool provider, null if none (default)
   */
  public SessionPoolProvider getSessionPoolProvider() {
    return null;
  }

  /**
   * Determines the serviced class.
   *
   * @param implementingClass the implementing class
   * @return the serviced class, null if none
   */
  public Class<?> getServicedClass(Class<?> implementingClass) {
    // overridden in PersistenceUtilities
    return null;
  }

  /**
   * Gets the names of the classes to preload the remote delegates for.<br>
   * By default, these are the classes annotated with {@link PreloadRemoteDelegate}, collected
   * at build time and looked up via the service API. The classes are not loaded.
   *
   * @return the class names, never null
   * @see Db#preloadRemoteDelegates()
   */
  public String[] getPreloadDelegateClassNames() {
    String[] classNames = preloadDelegateClassNames;
    if (classNames == null) {
      Set<String> names = ServiceFactory.getServiceFinder()
                                        .findServiceConfigurations(PreloadRemoteDelegate.class.getName()).keySet();
      classNames = names.toArray(new String[0]);
      preloadDelegateClassNames = classNames;
    }
    return classNames;
  }

  /**
   * Creates a reconnection policy.
   *
   * @param blocking true if reconnection blocks the current thread, else non-blocking in the background
   * @param millis the (minimum) time in milliseconds between retries
   * @return the policy
   */
  public ReconnectionPolicy<Db> createReconnectionPolicy(boolean blocking, long millis) {
    return new DefaultReconnectionPolicy(blocking, millis);
  }

  /**
   * Performs any cleanup when a remote user session is closed.
   *
   * @param remoteSession the remote session
   */
  public void cleanupRemoteSession(RemoteDbSessionImpl remoteSession) {
    // the default does nothing
  }

  /**
   * Adds a session to a session group.<br>
   * Creates a new group if the session with the given groupId does not belong to a group yet.
   *
   * @param session the session to add to a group
   * @param sessionGroupId the session ID or session group ID to group with
   * @param fromRemote true if initiated from the remote client
   */
  public void addToSessionGroup(Db session, int sessionGroupId, boolean fromRemote) {
    if (sessionGroupId <= 0) {
      throw new PersistenceException(session, "invalid requested session group " + sessionGroupId);
    }
    String url = session.getUrl();
    int oldGroup = fromRemote ? session.getExportedSessionGroupId() : session.getSessionGroupId();
    if (oldGroup != 0) {
      if (sessionGroupId != oldGroup) {
        throw new PersistenceException(session, "session already belongs to group " + oldGroup +
                                                " (requested was " + sessionGroupId + ")");
      }
      return;   // nothing to do
    }

    Db session2 = Db.getOpenSession(sessionGroupId, url);
    if (session2 == null) {
      throw new PersistenceException(session, "no such session with ID " + sessionGroupId + " to group with");
    }

    int groupId = fromRemote ? session2.getExportedSessionGroupId() : session2.getSessionGroupId();
    if (groupId != 0) {
      if (groupId != sessionGroupId) {
        throw new PersistenceException(session2, "session already belongs to exported group " + groupId);
      }
    }
    else {
      // not grouped so far: create it
      groupId = sessionGroupId;
    }

    if (fromRemote) {
      session2.setExportedSessionGroupId(groupId);
      session.setExportedSessionGroupId(groupId);
    }
    else {
      session2.setSessionGroupId(groupId);
      session.setSessionGroupId(groupId);
    }
  }

  /**
   * Closes session groups if the session is a root session of a group.
   *
   * @param session the closing session
   */
  public void closeGroupsOfSession(Db session) {
    int sessionId = session.getSessionId();
    boolean isGroupRoot = sessionId == session.getSessionGroupId();
    boolean isExportedGroupRoot = sessionId == session.getExportedSessionGroupId();
    if (isGroupRoot || isExportedGroupRoot) {
      for (Db db: Db.getAllOpenSessions()) {
        try {
          if (isGroupRoot && db.getSessionGroupId() == sessionId) {
            db.setSessionGroupId(0);
          }
          if (isExportedGroupRoot && db.getExportedSessionGroupId() == sessionId) {
            db.setExportedSessionGroupId(0);
          }
        }
        catch (SessionClosedException ex) {
          // closed by another thread in the meantime: nothing to ungroup
        }
      }
    }
  }

  /**
   * Applies the given backend configuration to properties.
   *
   * @param backendConfiguration the backend configuration
   * @param properties the session properties
   */
  public void applyBackendConfiguration(BackendConfiguration backendConfiguration, EncryptedProperties properties) {
    if (backendConfiguration.getUser() != null) {
      properties.setProperty(Constants.BACKEND_USER, backendConfiguration.getUser());
    }
    if (backendConfiguration.getPassword() != null) {
      properties.setEncryptedProperty(Constants.BACKEND_PASSWORD, backendConfiguration.getPassword());
    }
    properties.setProperty(Constants.BACKEND_URL, backendConfiguration.getUrl());
    if (backendConfiguration.getDriver() != null) {
      // ex.: "org.postgresql.Driver:jar:file:/usr/share/java/postgresql.jar!/"
      properties.setProperty(Constants.BACKEND_DRIVER,
          backendConfiguration.getDriver().getDriver() + ":jar:file:" + backendConfiguration.getDriver().getUrl() + "!/");
    }
    if (backendConfiguration.getOptions() != null) {
      for (String line: backendConfiguration.getOptions().split("\n")) {
        String key;
        String value;
        int ndx = line.indexOf('=');
        if (ndx >= 0) {
          key = line.substring(0, ndx).trim();
          value = line.substring(ndx + 1).trim();
        }
        else {
          key = line.trim();
          value = "";
        }
        properties.setProperty(key, value);
      }
    }
  }

  /**
   * Notifies interested parties that a physical rollback has happened.
   *
   * @param session the session
   * @param txNumber the transaction number
   */
  public void notifyRollback(Db session, long txNumber) {
    if (Boolean.TRUE.equals(session.getTransactionProperty(ModificationTally.SESSION_COUNT_PENDING))) {
      // may be costly if a lot of counters are registered
      ModificationTracker.getInstance().rollbackModification(session, txNumber);
    }
    notifyModificationLogSerializer(session);
  }

  /**
   * Notifies interested parties that a rollback to a savepoint has happened.
   *
   * @param session the session
   */
  public void notifySavepointRollback(Db session) {
    notifyModificationLogSerializer(session);
  }

  /**
   * Notifies interested parties that a physical commit has happened.
   *
   * @param session the session
   * @param txNumber the transaction number
   */
  public void notifyCommit(Db session, long txNumber) {
    // nothing to do
  }


  /**
   * Notifies the {@link ModificationLogSerializer} about a rollback if it registered
   * itself as a transaction property.
   *
   * @param session the session
   */
  protected void notifyModificationLogSerializer(Db session) {
    if (session.getTransactionProperty(ModificationLogSerializer.class) instanceof ModificationLogSerializer serializer) {
      serializer.rollback(session);
    }
  }


  /**
   * Holds the mapping between a parameter type and its positions in the resultset.
   */
  private record ColumnMapping(DataType<?> dataType, int[] positions) {

    private Object getParameter(ResultSetWrapper rs) {
      return rs.get(dataType, positions, false, 0);
    }
  }

  /**
   * Holds the mapping of a datatype to a setter or builder method.
   */
  private record MethodMapping(Method method, ColumnMapping columnMapping) {

    private void invoke(Object object, ResultSetWrapper rs) throws InvocationTargetException, IllegalAccessException {
      method.invoke(object, columnMapping.getParameter(rs));
    }
  }


  /**
   * Converts a resultset to a list of DTOs.<br>
   * Works for the builder- and setter pattern and all registered {@link DataType}s.<br>
   * Constructor pattern works for records, but not for classes, since in Java the parameters are unnamed.
   * <p>
   * For the builder pattern see the DTO wurblet.<br>
   * For the setter pattern the DTO class needs a public no-arg constructor.<br>
   * For the record pattern the canonical constructor is used.
   * <p>
   * The method- and column names are matched case-insensitive with all underscores removed.<br>
   * For primitive members of the DTO-class the result set must not contain null values, of course.
   *
   * @param rs the resultset
   * @param clazz the DTO class
   * @param <T> the DTO type
   * @return the list of objects
   * @see DataType
   */
  @SuppressWarnings("unchecked")
  public <T> List<T> resultSetToList(ResultSetWrapper rs, Class<T> clazz) {

    if (rs.isInSkipMode()) {
      throw new PersistenceException("result set must not be in skipMode");
    }
    if (rs.getColumnOffset() != 0) {
      throw new PersistenceException("column offset must be zero");
    }

    String[] columnNames;                                         // converted column names
    Method builderMethod;                                         // static method to create a builder, if the builder pattern is used
    Class<?> builderClass;                                        // the class of the builder
    int columnCount = rs.getColumnCount();                        // number of result columns
    List<T> dtos = new ArrayList<>();                             // returned list of DTOs
    Method buildMethod = null;                                    // the build-method of the builder
    Constructor<T> noArgConstructor = null;                       // != null for the setter pattern
    Constructor<T> recordConstructor = null;                      // != null for the record pattern
    List<MethodMapping> builderMappings = new ArrayList<>();      // mappings for the builder pattern
    List<MethodMapping> setterMappings = new ArrayList<>();       // mappings for the setters
    List<ColumnMapping> recordMappings = null;                    // != null if mappings for the record pattern were found
    Backend backend = rs.getSession().getBackend();               // the backend

    // build converted column names
    columnNames = new String[columnCount];
    for (int i=0; i < columnCount; i++) {
      columnNames[i] = normalizeColumnName(rs.getColumnName(i + 1));
    }

    // find all setters
    if (clazz.isRecord()) {
      RecordComponent[] recordComponents = clazz.getRecordComponents();
      List<ColumnMapping> mappings = new ArrayList<>();
      for (RecordComponent recordComponent : recordComponents) {
        ColumnMapping mapping = createColumnMapping(backend, recordComponent.getName(), columnNames, recordComponent.getType());
        if (mapping != null) {
          mappings.add(mapping);
        }
      }
      if (mappings.size() == recordComponents.length) {
        // all components found in the result set: use the canonical constructor
        Class<?>[] parameterTypes = Arrays.stream(recordComponents).map(RecordComponent::getType).toArray(Class<?>[]::new);
        try {
          recordConstructor = clazz.getDeclaredConstructor(parameterTypes);
          recordMappings = mappings;
        }
        catch (NoSuchMethodException ex) {
          throw new PersistenceException("missing canonical constructor in " + clazz, ex);
        }
      }
    }
    else {
      for (Method method : clazz.getMethods()) {
        if (method.getDeclaringClass() != Object.class &&
            method.getReturnType() == Void.TYPE && method.getParameterCount() == 1 &&
            method.getName().startsWith("set")) {
          String name = method.getName().substring(3);
          if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
            MethodMapping mapping = createMethodMapping(backend, name, columnNames, method);
            if (mapping != null) {
              setterMappings.add(mapping);
            }
          }
        }
      }
    }

    // check for the builder pattern (see DTO.wrbl)
    // This is even possible for records!
    try {
      builderMethod = clazz.getDeclaredMethod("builder");
      if (!Modifier.isStatic(builderMethod.getModifiers()) || !Modifier.isPublic(builderMethod.getModifiers())) {
        builderMethod = null;
      }
      else {
        builderClass = builderMethod.invoke(null).getClass();
        buildMethod = builderClass.getDeclaredMethod("build");
        if (buildMethod.getReturnType() != clazz) {
          builderMethod = null;
        }
        else {
          for (Method method : builderClass.getMethods()) {
            if (method.getDeclaringClass() != Object.class &&
                method.getReturnType() == Void.TYPE && method.getParameterCount() == 1) {
              MethodMapping mapping = createMethodMapping(backend, method.getName(), columnNames, method);
              if (mapping != null) {
                builderMappings.add(mapping);
              }
            }
          }
        }
      }
      // if builder is still null, could be setter pattern
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | NullPointerException ex) {
      // that's ok...
      builderMethod = null;
    }

    if (builderMethod == null && !clazz.isRecord()) {
      try {
        noArgConstructor = clazz.getConstructor();
      }
      catch (NoSuchMethodException ex) {
        throw new PersistenceException("missing no-arg constructor in " + clazz);
      }
    }

    if (builderMappings.isEmpty() && setterMappings.isEmpty() && recordMappings == null) {
      StringBuilder buf = new StringBuilder();
      buf.append("cannot create ").append(clazz.getSimpleName()).append(" from ");
      boolean needComma = false;
      for (String columnName : columnNames) {
        if (needComma) {
          buf.append(", ");
        }
        else {
          needComma = true;
        }
        buf.append(columnName);
      }
      throw new PersistenceException(buf.toString());
    }

    try {
      while (rs.next()) {
        Object builder;
        T dto;
        if (builderMethod != null) {
          builder = builderMethod.invoke(null);
          retrieveMethodMappings(builderMappings, builder, rs);
          dto = (T) buildMethod.invoke(builder);
        }
        else if (recordConstructor != null) {
          dto = retrieveRecordMappings(recordConstructor, recordMappings, rs);
        }
        else if (noArgConstructor != null) {
          dto = noArgConstructor.newInstance();
          retrieveMethodMappings(setterMappings, dto, rs);
        }
        else {
          throw new PersistenceException("no suitable instantiation method found to create a DTO from " + clazz);
        }
        dtos.add(dto);
      }
    }
    catch (IllegalAccessException | InvocationTargetException | InstantiationException ex) {
      throw new PersistenceException("creating DTO failed", ex);
    }

    return dtos;
  }


  private String normalizeColumnName(String name) {
    return name.replace("_", "").toLowerCase(Locale.ROOT); // remove underscores and convert to lowercase
  }

  private int[] determinePositions(Backend backend, String memberName, String[] columnNames, DataType<?> dataType) {
    int[] positions = new int[dataType.getColumnCount(backend)];
    for (int p=0; p < positions.length; p++) {
      String normalizedColumnName = normalizeColumnName(memberName + dataType.getColumnSuffix(backend, p).orElse(""));
      boolean found = false;
      for (int c=0; c < columnNames.length; c++) {
        if (normalizedColumnName.equals(columnNames[c])) {
          positions[p] = c + 1;
          found = true;
          break;
        }
      }
      if (!found) {
        positions = null;
        break;
      }
    }
    return positions;
  }

  private ColumnMapping createColumnMapping(Backend backend, String memberName, String[] columnNames, Class<?> type) {
    ColumnMapping columnMapping = null;
    DataType<?> dataType = DataTypeFactory.getInstance().get(type);
    if (dataType != null) {
      int[] positions = determinePositions(backend, memberName, columnNames, dataType);
      if (positions != null) {
        // all columns found in resultset!
        columnMapping = new ColumnMapping(dataType, positions);
      }
    }
    return columnMapping;
  }

  private MethodMapping createMethodMapping(Backend backend, String memberName, String[] columnNames, Method method) {
    MethodMapping methodMapping = null;
    ColumnMapping columnMapping = createColumnMapping(backend, memberName, columnNames, method.getParameterTypes()[0]);
    if (columnMapping != null) {
      methodMapping = new MethodMapping(method, columnMapping);
    }
    return methodMapping;
  }

  private void retrieveMethodMappings(List<MethodMapping> methodMappings, Object object, ResultSetWrapper rs)
      throws InvocationTargetException, IllegalAccessException {

    for (MethodMapping methodMapping : methodMappings) {
      methodMapping.invoke(object, rs);
    }
  }

  private <T> T retrieveRecordMappings(Constructor<T> recordConstructor, List<ColumnMapping> recordMappings, ResultSetWrapper rs)
      throws InvocationTargetException, InstantiationException, IllegalAccessException {

    Object[] args = new Object[recordMappings.size()];
    int argNdx = 0;
    for (ColumnMapping recordMapping : recordMappings) {
      args[argNdx++] = recordMapping.getParameter(rs);
    }
    return recordConstructor.newInstance(args);
  }

}

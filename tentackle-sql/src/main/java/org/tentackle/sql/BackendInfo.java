/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.sql;

import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Configuration info for a backend.
 *
 * @author harald
 */
public class BackendInfo {

  private final Backend backend;                // the configured backend, null if remote
  private final DataSource jndiDataSource;      // != null if JNDI DataSource
  private final String url;                     // the backend url
  private final String user;                    // the username
  private final char[] password;                // the password
  private final String[] schemas;               // schemas (only for migration)


  /**
   * Creates a backend info from a backend.<br>
   * The info cannot be used to connect.
   *
   * @param backend the backend
   */
  public BackendInfo(Backend backend) {
    this.backend = backend;
    this.url = null;
    this.user = null;
    this.password = null;
    this.jndiDataSource = null;
    this.schemas = null;
  }

  /**
   * Creates a backend info from a backend name.<br>
   * The info cannot be used to connect.
   *
   * @param backendName the backend name
   */
  public BackendInfo(String backendName) {
    this(BackendFactory.getInstance().getBackendByName(backendName));
  }

  /**
   * Creates a backend info.<br>
   * This info is able to create a connection.
   *
   * @param url the backend url
   * @param user the username
   * @param password the password
   * @param schemas the optional schemas, null if no schema check
   */
  public BackendInfo(String url, String user, char[] password, String[] schemas) {
    if (url == null || url.isEmpty()) {
      throw new BackendException("url missing");
    }

    this.user = user;
    this.password = password;
    this.schemas = schemas;

    // cut the optional backend type
    String backendType = null;
    int ndx = url.indexOf('|');
    if (ndx >= 0) {
      backendType = url.substring(ndx + 1);
      url = url.substring(0, ndx);
    }

    if (url.startsWith(Constants.BACKEND_JNDI_URL_INTRO)) {
      String jndiName = url.substring(5);
      try {
        jndiDataSource = (DataSource) new InitialContext().lookup(jndiName);
      }
      catch (NamingException nex) {
        throw new BackendException("lookup failed for " + jndiName, nex);
      }

      if (backendType == null) {
        // backend type not given: open temporary connection to get the URL
        try (Connection tempCon = jndiDataSource.getConnection()) {
          DatabaseMetaData metaData = tempCon.getMetaData();
          if (metaData == null) {
            throw new BackendException(
                    "Metadata of JNDI-connection cannot be determined. Please add the backend type: " +
                            url + "|<backend-type>");
          }
          String jndiURL = metaData.getURL();
          if (jndiURL == null) {
            throw new BackendException(
                    "URL of JNDI-connection cannot be determined. Please add the backend type: " +
                            url + "|<backend-type>");
          }
          backend = BackendFactory.getInstance().getBackendByUrl(jndiURL);
        }
        catch (SQLException sqx) {
          throw new BackendException("cannot retrieve metadata of JNDI source " + jndiName, sqx);
        }
        // return to pool. The connections are managed by the ConnectionManager
        // ignore closing exception
      }
      else  {
        backend = BackendFactory.getInstance().getBackendByName(backendType);
      }
    }
    else if (url.startsWith(Constants.BACKEND_RMI_URL_INTRO)) {
      backend = null;
      jndiDataSource = null;
    }
    else {
      // JDBC
      if (!url.startsWith(Constants.BACKEND_JDBC_URL_INTRO)) {
        url = Constants.BACKEND_JDBC_URL_INTRO + url;
      }
      backend = backendType == null ?
                BackendFactory.getInstance().getBackendByUrl(url) :
                BackendFactory.getInstance().getBackendByName(backendType);
      jndiDataSource = null;
    }

    this.url = url;
  }


  /**
   * Creates a backend info from backend properties.<br>
   * The info is able to create a connection.
   *
   * @param backendProperties the properties
   */
  public BackendInfo(Properties backendProperties) {
    this(backendProperties.getProperty(Constants.BACKEND_URL),
         backendProperties.getProperty(Constants.BACKEND_USER),
         parsePassword(backendProperties.getProperty(Constants.BACKEND_PASSWORD)),
         parseSchemas(backendProperties.getProperty(Constants.BACKEND_SCHEMAS)));
    String driver = backendProperties.getProperty(Constants.BACKEND_DRIVER);
    if (driver != null) {
      DynamicDriver.load(driver);
    }
  }

  /**
   * Gets the backend.
   *
   * @return the backend
   */
  public Backend getBackend() {
    return backend;
  }

  /**
   * Returns whether this backend is remote.
   *
   * @return true if remote
   */
  public boolean isRemote() {
    return backend == null;
  }

  /**
   * Gets the JNDI source.
   *
   * @return the JNDI datasource if this a JNDI connection
   */
  public DataSource getJndiDataSource() {
    return jndiDataSource;
  }

  /**
   * Gets the connection url.
   *
   * @return the url, null if info cannot connect
   */
  public String getUrl() {
    return url;
  }

  /**
   * Gets the username to connect.
   *
   * @return the username
   */
  public String getUser() {
    return user;
  }

  /**
   * Gets the connection password.
   *
   * @return the password
   */
  public char[] getPassword() {
    return password;
  }

  /**
   * Clears all passwords (stored in char[]-arrays) so
   * that they are no more visible in memory.
   */
  public void clearPassword() {
    StringHelper.blank(password);
  }

  /**
   * Gets the schemas.
   *
   * @return the schemas for migration
   */
  public String[] getSchemas() {
    return schemas;
  }

  /**
   * Returns whether backend info can be used to connect.
   *
   * @return true if connectable
   */
  public boolean isConnectable() {
    return url != null;
  }


  /**
   * Creates a connection.
   *
   * @return the connection in autocommit mode
   * @throws SQLException if connection could not be created
   */
  public Connection connect() throws SQLException {
    Connection connection = jndiDataSource != null ?
            jndiDataSource.getConnection() : backend.createConnection(url, user, password);
    try {
      if (!connection.getAutoCommit()) {
        connection.setAutoCommit(true);   // set to autocommit mode for sure
      }
    }
    catch (SQLException sqx) {
      connection.close();
      throw sqx;
    }
    return connection;
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (isRemote()) {
      buf.append("remote");
    }
    else {
      buf.append(backend);
    }
    if (url != null) {
      buf.append('@');
      buf.append(url);
    }
    return buf.toString();
  }


  /**
   * Parses the schemas option.
   *
   * @param str the schemas option
   * @return the schemas, null if no schemas set
   */
  private static String[] parseSchemas(String str) {
    String[] schemas = null;
    if (str != null) {
      StringTokenizer stok = new StringTokenizer(str, ",");
      int count = stok.countTokens();
      if (count > 0) {
        schemas = new String[count];
        for (int i=0; i < count; i++) {
          schemas[i] = stok.nextToken().trim();
        }
      }
    }
    return schemas;
  }


  /**
   * Converts a string to a password char array.
   *
   * @param str the string, null if none
   * @return the char array, null if none
   */
  private static char[] parsePassword(String str) {
    char[] password = null;
    if (str != null) {
      password = str.toCharArray();
    }
    return password;
  }

}

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

package org.tentackle.common;

/**
 * Class holding constants for build- and runtime.
 *
 * @author harald
 */
public final class Constants {

  /** {@code META-INF} directory name. */
  public static final String META_INF = "META-INF/";

  /** the directory for mapped service provider configurations. */
  public static final String MAPPED_SERVICE_PATH = META_INF + "mapped-services/";

  /** the directory for meta service provider configurations. */
  public static final String META_SERVICE_PATH = META_INF + "meta-services/";

  /** the directory for default service provider configurations. */
  public static final String DEFAULT_SERVICE_PATH = META_INF + "services/";

  /** the directory for resource bundle names. */
  public static final String BUNDLE_SERVICE_PATH = META_INF + "bundles/";

  /** suppress warnings for suspicious stateful domain logic. */
  public static final String SUPPRESS_WARNINGS_STATEFUL_DOMAIN_LOGIC = "stateful-domain-logic";

  /** suppress warnings for bundle getString invocations (see tentackle-check:bundles goal). */
  public static final String SUPPRESS_WARNINGS_BUNDLE = "bundle";

  /** name for "none". */
  public static final String NAME_NONE = "NONE";

  /** unknown name. */
  public static final String NAME_UNKNOWN = "<unknown>";

  /** extension for property files. */
  public static final String PROPS_EXTENSION = ".properties";

  /** name of property file holding the backend properties. */
  public static final String BACKEND_PROPS = "backend";

  /** property key for the backend URL. */
  public static final String BACKEND_URL = "url";

  /** explicit driver name and URL to load it from &lt;name:url&gt;. */
  public static final String BACKEND_DRIVER = "driver";

  /** database connection inactivity timeout in minutes. */
  public static final String BACKEND_TIMEOUT = "jdbcIdleTimeout";

  /** send a keep-alive select every BACKEND_TIMEOUT minutes. */
  public static final String BACKEND_KEEP_ALIVE = "jdbcKeepAlive";

  /** optional backend identity to link different URLs to the same physical backend. */
  public static final String BACKEND_IDENTITY = "identity";

  /** property key for the optional schemas. */
  public static final String BACKEND_SCHEMAS = "schemas";

  /** property key for username. */
  public static final String BACKEND_USER = "user";

  /** property key for the password and the default value for {@link #BACKEND_AUTHENTICATION}. */
  public static final String BACKEND_PASSWORD = "password";

  /** property key to enable autologin (only for desktop applications). */
  public static final String BACKEND_AUTOLOGIN = "autologin";

  /**
   * Property key for the name of the client-side authentication method.<br>
   * Selects the {@code CredentialsProvider} used to obtain the credentials before the session is opened.
   * If missing, {@link #BACKEND_PASSWORD} is used (password-based authentication).
   */
  public static final String BACKEND_AUTHENTICATION = "authentication";

  /** intro in URL to denote a JDBC connection. */
  public static final String BACKEND_JDBC_URL_INTRO = "jdbc:";

  /** intro in URL to denote a JNDI connection. */
  public static final String BACKEND_JNDI_URL_INTRO = "jndi:";

  /**
   * Optional backend configuration.<br>
   * Useful to define extra technical user credentials for the physical connection, to load JDBC-drivers at runtime or
   * to switch between different backends.
   * <p>
   * The backend info comes in 3 flavors:
   * <ol>
   *   <li>{@code @user} or {@code @system}: the login dialog allows the user to manage backend parameters
   *   and optional drivers through the UI. The settings are stored in the user-preferences for {@code @user}
   *   and system preferences for {@code @system}</li>
   *   <li>{@code @user:backendname} or {@code @system:backendname}: loads the configuration for the backend
   *   with the given name from the user- or system-preferences, respectively</li>
   *   <li>a property file name: loads the settings from the given property file</li>
   * </ol>
   * If missing, the properties from the BACKEND_PROPS and optional user input are used.
   */
  public static final String BACKEND_INFO = BACKEND_PROPS;    // same (see EncryptedProperties#name)

  /**
   * Value of backendinfo to allow selecting the backend (see Login in fx-rdc) using user preferences.<br>
   * If the value is followed by a colon and a name, that name is treated as the {@code BackendConfiguration}.
   */
  public static final String BACKEND_INFO_USER = "@user";

  /**
   * Value of backendinfo to allow selecting the backend (see Login in fx-rdc) using system preferences.<br>
   * If the value is followed by a colon and a name, that name is treated as the {@code BackendConfiguration}.
   */
  public static final String BACKEND_INFO_SYSTEM = "@system";

  /**
   * Application name.<br>
   * By default, the application name is determined from the main class.
   * For testing, however, the application name needs to be set explicitly if
   * backend infos are to be loaded via {@link #BACKEND_INFO_USER} or {@link #BACKEND_INFO_SYSTEM}.
   */
  public static final String BACKEND_APPLICATION_NAME = "application";



  /** name of ID column. */
  public static final String CN_ID = "id";
  /** java name of ID. */
  public static final String AN_ID = "id";

  /** name of serial column. */
  public static final String CN_SERIAL = "serial";
  /** java name of serial. */
  public static final String AN_SERIAL = "serial";

  /** name of tableserial column. */
  public static final String CN_TABLESERIAL = "tableserial";
  /** java name of tableserial. */
  public static final String AN_TABLESERIAL = "tableSerial";

  /** name of class-ID column. */
  public static final String CN_CLASSID = "classid";
  /** java name of class-ID. */
  public static final String AN_CLASSID = "classId";

  /** name of root-ID column. */
  public static final String CN_ROOTID = "rootid";
  /** java name of root-ID. */
  public static final String AN_ROOTID = "rootId";

  /** name of root-class-ID column. */
  public static final String CN_ROOTCLASSID = "rootclassid";
  /** java name of root-class-ID. */
  public static final String AN_ROOTCLASSID = "rootClassId";

  /** name of the column holding the object ID of the user currently editing this object. */
  public static final String CN_EDITEDBY = "editedby";
  /** java name of the editing user. */
  public static final String AN_EDITEDBY = "editedBy";

  /** name of the column holding the time when editing started. */
  public static final String CN_EDITEDSINCE = "editedsince";
  /** java name of time editing started. */
  public static final String AN_EDITEDSINCE = "editedSince";

  /** name of the column holding the time when the "lock token" will expire. */
  public static final String CN_EDITEDEXPIRY = "editedexpiry";
  /** java name of time the "lock token" will expire. */
  public static final String AN_EDITEDEXPIRY = "editedExpiry";

  /** column name for the normtext. */
  public static final String CN_NORMTEXT = "normtext";
  /** java name for the normtext. */
  public static final String AN_NORMTEXT = "normText";


  /** bind option MAXCOLS=... */
  public static final String BIND_MAXCOLS = "MAXCOLS";

  /** bind option COLS=... */
  public static final String BIND_COLS = "COLS";

  /** bind option SCALE=... */
  public static final String BIND_SCALE = "SCALE";

  /** bind option UNSIGNED. */
  public static final String BIND_UNSIGNED = "UNSIGNED";

  /** bind option digits only. */
  public static final String BIND_DIGITS = "DIGITS";

  /** bind option uppercase. */
  public static final String BIND_UC = "UC";

  /** bind option lowercase. */
  public static final String BIND_LC = "LC";

  /** bind option to turn off stripping. */
  public static final String BIND_STRIP_NONE = "NOSTRIP";

  /** bind option to remove leading filler characters. */
  public static final String BIND_STRIP_LEFT = "LSTRIP";

  /** bind option to remove trailing filler characters. */
  public static final String BIND_STRIP_RIGHT = "RSTRIP";

  /** bind option to remove leading and trailing filler characters (default for text components). */
  public static final String BIND_STRIP_BOTH = "STRIP";

  /** bind option autoselect field. */
  public static final String BIND_AUTOSELECT = "AUTOSELECT";

  /** bind timestamp with UTC option. */
  public static final String BIND_UTC = "UTC";

  /** bind option LINES=... */
  public static final String BIND_LINES = "LINES";

  /** bind option FILLER='...' or FILLER=0x... (see {@link Integer#decode(String)}). */
  public static final String BIND_FILLER = "FILLER";


  /** maximum number of significant digits for BMoney. */
  public static final int BMONEY_DIGITS = 15;

  /** maximum number of significant digits for DMoney. */
  public static final int DMONEY_DIGITS = 19;   // common value supported by all DBMS


  /** a second in milliseconds. */
  public static final long SECOND_MS = 1000L;

  /** a minute in milliseconds. */
  public static final long MINUTE_MS = 60 * SECOND_MS;

  /** an hour in milliseconds. */
  public static final long HOUR_MS = MINUTE_MS * 60;

  /** a day in milliseconds. */
  public static final long DAY_MS = HOUR_MS * 24;


  /**
   * All digits in a string.
   */
  public static final String DIGITS = "0123456789";


  /**
   * no instances.
   */
  private Constants() {
  }

}

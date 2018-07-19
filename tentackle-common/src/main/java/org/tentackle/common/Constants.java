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


package org.tentackle.common;

/**
 * Class holding constants for build- and runtime.
 *
 * @author harald
 */
public class Constants {

  /** the directory for mapped service provider configurations. */
  public static final String MAPPED_SERVICE_PATH = "META-INF/mapped-services/";

  /** the directory for default service provider configurations. */
  public static final String DEFAULT_SERVICE_PATH = "META-INF/services/";

  /** the directory for resource bundle names. */
  public static final String BUNDLE_SERVICE_PATH = "META-INF/bundles/";

  /** suppress warnings for suspicious stateful domain logic. */
  public static final String SUPPRESS_WARNINGS_STATEFUL_DOMAIN_LOGIC = "stateful-domain-logic";

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

  /** explicit driver name and url to load it from &lt;name:url&gt;. */
  public static final String BACKEND_DRIVER = "driver";

  /** property key for the optional schemas. */
  public static final String BACKEND_SCHEMAS = "schemas";

  /** property key for username. */
  public static final String BACKEND_USER = "user";

  /** property key for the password. */
  public static final String BACKEND_PASSWORD = "password";

  /** intro in URL to denote a remote connection. */
  public static final String BACKEND_RMI_URL_INTRO = "rmi:";

  /** intro in URL to denote a JNDI connection. */
  public static final String BACKEND_JNDI_URL_INTRO = "jndi:";

  /**
   * Property file holding the technical backend info.<br>
   * Useful to use different properties for the physical connection.
   * If missing, the properties from the BACKEND_PROPS and optional user input are used.
   */
  public static final String BACKEND_TECHNICAL_INFO = "backendinfo";


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
  /** java name class-ID. */
  public static final String AN_CLASSID = "classId";

  /** name of root-ID column. */
  public static final String CN_ROOTID = "rootid";
  /** java name root-ID. */
  public static final String AN_ROOTID = "rootId";

  /** name of root-class-ID column. */
  public static final String CN_ROOTCLASSID = "rootclassid";
  /** java name root-class-ID. */
  public static final String AN_ROOTCLASSID = "rootClassId";

  /** the object ID of the user currently editing this object. */
  public static final String CN_EDITEDBY = "editedby";
  /** java name of the editing user. */
  public static final String AN_EDITEDBY = "editedBy";

  /** time when editing started. */
  public static final String CN_EDITEDSINCE = "editedsince";
  /** java name of time editing started. */
  public static final String AN_EDITEDSINCE = "editedSince";

  /** time when "lock token" will expire. */
  public static final String CN_EDITEDEXPIRY = "editedexpiry";
  /** java name of time "lock token" will expire. */
  public static final String AN_EDITEDEXPIRY = "editedExpiry";

  /** column name for the normtext. */
  public static final String CN_NORMTEXT = "normtext";
  /** java name for the normtext. */
  public static final String AN_NORMTEXT = "normText";


  /** bind option MAXCOLS = ... */
  public static final String BIND_MAXCOLS = "MAXCOLS";

  /** bind option COLS = ... */
  public static final String BIND_COLS = "COLS";

  /** bind option SCALE = ... */
  public static final String BIND_SCALE = "SCALE";

  /** bind option UNSIGNED. */
  public static final String BIND_UNSIGNED = "UNSIGNED";

  /** bind option uppercase. */
  public static final String BIND_UC = "UC";

  /** bind option lowercase. */
  public static final String BIND_LC = "LC";

  /** bind option autoselect field. */
  public static final String BIND_AUTOSELECT = "AUTOSELECT";

  /** bind timestamp with UTC option. */
  public static final String BIND_UTC = "UTC";

  /** bind option LINES = ... */
  public static final String BIND_LINES = "LINES";


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
   * Heredocs may start with this lead followed by the source info.<br>
   * Same as in wurbelizer's Constants.
   */
  public static final String ORIGIN_INFO_LEAD = "#@";


  /**
   * no instances.
   */
  private Constants() {
  }

}

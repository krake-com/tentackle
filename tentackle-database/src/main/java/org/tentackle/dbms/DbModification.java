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

import org.tentackle.log.Logger;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.session.ClassId;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Persistent;
import org.tentackle.session.TableName;
import org.tentackle.session.ThreadLocalSessionHolder;
import org.tentackle.sql.Backend;

import java.io.Serial;
import java.util.concurrent.ThreadLocalRandom;

/*
 * @> $mapfile
 *
 * # modification tracking table
 * name := $classname
 * id := $classid
 * table := $tablename
 *
 * ## attributes
 * String(64)     trackedName       tablename     the tablename
 *
 * ## indexes
 * unique index tablename := tablename
 *
 * @<
 */

/**
 * Modification information per table.
 * <p>
 * Each tracked name (usually a table name) gets a row in the modification table holding its modification
 * serial. The row with ID 0 holds the master serial, which is incremented with every modification.
 * Its tracked name holds the generation of the table, which changes whenever the table is re-initialized,
 * see {@link #initializeModificationTable(Db)}.
 *
 * @author harald
 */
@ClassId(/*@*/1/*@*/)                   // @wurblet < Inject $classid
@TableName(/*@*/"modification"/*@*/)    // @wurblet < Inject --string $tablename
public class DbModification extends AbstractDbObject<DbModification> {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(DbModification.class);


  /** Variables common to all instances of {@link DbModification}. */
  public static final DbObjectClassVariables<DbModification> CLASSVARIABLES =
      DbObjectClassVariables.create(DbModification.class);


  /**
   * Prefix of the master row's tracked name holding the generation.
   */
  private static final String GENERATION_PREFIX = "#M#";

  /**
   * The generation of a modification table initialized by a former version, i.e., without a generation.
   */
  public static final long LEGACY_GENERATION = 0;


  /**
   * Initializes the modification table.
   * <p>
   * All rows are deleted, and the master row is re-created with a new random generation.
   * The ids of the tracked names change, since the rows are re-created on demand.
   * Other trackers using the table detect the re-initialization by the changed generation.
   * The master serial alone is not sufficient: it is reset to 1 but may have been counted
   * beyond the serial known to another tracker before its next poll.
   *
   * @param db the session
   * @return the new generation
   */
  public static long initializeModificationTable(Db db) {
    LOGGER.info("initializing modification table");
    long generation = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    long txVoucher = db.begin("initialize");
    try {
      try (StatementWrapper st = db.createStatement()) {    // non-prepared statements can only be used once!
        st.executeUpdate(Backend.SQL_DELETE_FROM + CLASSVARIABLES.tableName);
      }
      try (StatementWrapper st = db.createStatement()) {
        st.executeUpdate(Backend.SQL_INSERT_INTO + CLASSVARIABLES.tableName + Backend.SQL_LEFT_PARENTHESIS +
                         CN_TRACKEDNAME + Backend.SQL_COMMA + CN_ID + Backend.SQL_COMMA + AbstractDbObject.CN_SERIAL +
                         Backend.SQL_INSERT_VALUES + "'" + GENERATION_PREFIX + generation + "',0,1)");
      }
      db.commit(txVoucher);
    }
    catch (RuntimeException ex) {
      db.rollback(txVoucher);
      throw ex;
    }
    return generation;
  }

  /**
   * Extracts the generation from the tracked name of the master row.
   *
   * @param masterName the tracked name of the master row
   * @return the generation, {@link #LEGACY_GENERATION} if none
   */
  private static long parseGeneration(String masterName) {
    if (masterName != null && masterName.startsWith(GENERATION_PREFIX)) {
      try {
        return Long.parseLong(masterName.substring(GENERATION_PREFIX.length()));
      }
      catch (NumberFormatException ex) {
        LOGGER.warning("malformed generation in master row of " + CLASSVARIABLES.tableName + ": " + masterName);
      }
    }
    return LEGACY_GENERATION;
  }


  // @wurblet fieldnames ColumnNames --model=$mapfile

  //<editor-fold defaultstate="collapsed" desc="code 'fieldnames' generated by wurblet ColumnNames">//GEN-BEGIN:fieldnames


  /** database column name for 'trackedName'. */
  public static final String CN_TRACKEDNAME = "tablename";

  //</editor-fold>//GEN-END:fieldnames

  // @wurblet fieldlengths ColumnLengths --model=$mapfile

  //<editor-fold defaultstate="collapsed" desc="code 'fieldlengths' generated by wurblet ColumnLengths">//GEN-BEGIN:fieldlengths


  /** maximum number of characters for 'trackedName'. */
  int CL_TRACKEDNAME = 64;

  //</editor-fold>//GEN-END:fieldlengths

  // @wurblet declare Declare --model=$mapfile

  //<editor-fold defaultstate="collapsed" desc="code 'declare' generated by wurblet Declare">//GEN-BEGIN:declare


  /** the tablename. */
  private String trackedName;

  //</editor-fold>//GEN-END:declare


  /**
   * Creates a modification object for a given tablename.
   *
   * @param db the session, null if thread-local
   * @param trackedName the tracked name
   */
  public DbModification(Db db, String trackedName) {
    super(db);
    this.trackedName = trackedName;
    if (db == null) {
      setSessionHolder(new ThreadLocalSessionHolder());
    }
  }

  /**
   * Creates an empty modification object.
   *
   * @param db the session
   */
  public DbModification(Db db) {
    super(db);
  }

  /**
   * Creates an empty modification object.
   */
  public DbModification() {
    super();
  }


  @Override
  public DbObjectClassVariables<DbModification> getClassVariables() {
    return CLASSVARIABLES;
  }

  @Override
  public String toString()  {
    return trackedName;
  }

  // @wurblet methods MethodsImpl --noif --model=$mapfile

  //<editor-fold defaultstate="collapsed" desc="code 'methods' generated by wurblet MethodsImpl">//GEN-BEGIN:methods


  @Override
  public void getFields(ResultSetWrapper rs) {
    super.getFields(rs);
    if (rs.configureSection(CLASSVARIABLES)) {
      rs.configureColumn(CN_TRACKEDNAME);
      rs.configureColumn(CN_ID);
      rs.configureColumn(CN_SERIAL);
    }
    trackedName = rs.getString();
    setId(rs.getLong());
    setSerial(rs.getLong());
  }

  @Override
  public int setFields(PreparedStatementWrapper st) {
    int ndx = super.setFields(st);
    st.setString(++ndx, trackedName);
    st.setLong(++ndx, getId());
    st.setLong(++ndx, getSerial());
    return ndx;
  }

  @Override
  public String createInsertSql(Backend backend) {
    return Backend.SQL_INSERT_INTO + getTableName() + Backend.SQL_LEFT_PARENTHESIS +
           CN_TRACKEDNAME + Backend.SQL_COMMA +
           CN_ID + Backend.SQL_COMMA +
           CN_SERIAL +
           Backend.SQL_INSERT_VALUES +
           Backend.SQL_PAR_COMMA.repeat(2) +
           Backend.SQL_PAR + Backend.SQL_RIGHT_PARENTHESIS;
  }

  @Override
  public String createUpdateSql(Backend backend) {
    return Backend.SQL_UPDATE + getTableName() + Backend.SQL_SET +
           CN_TRACKEDNAME + Backend.SQL_EQUAL_PAR_COMMA +
           CN_SERIAL + Backend.SQL_EQUAL + CN_SERIAL + Backend.SQL_PLUS_ONE +
           Backend.SQL_WHERE + CN_ID + Backend.SQL_EQUAL_PAR +
           Backend.SQL_AND + CN_SERIAL + Backend.SQL_EQUAL_PAR;
  }

  /**
   * Gets the attribute trackedName.
   *
   * @return the tablename
   */
  @Persistent(ordinal=0, comment="the tablename")
  public String getTrackedName()    {
    return trackedName;
  }

  /**
   * Sets the attribute trackedName.
   *
   * @param trackedName the tablename
   */
  public void setTrackedName(String trackedName) {
    assertMutable();
    this.trackedName = trackedName;
  }

  //</editor-fold>//GEN-END:methods


  /**
   * Counts modifications.<br>
   * Adds the count to the table's entry and the master entry.
   *
   * @param count the number of modifications to add
   * @return the number of modification rows updated (should be 2)
   */
  public int countModification(long count) {
    assertNotRemote();
    PreparedStatementWrapper st = getPreparedStatement(COUNT_MODIFICATION_ID, _ ->
        Backend.SQL_UPDATE + getTableName() + Backend.SQL_SET + CN_SERIAL + Backend.SQL_EQUAL + CN_SERIAL +
        "+?" + Backend.SQL_WHERE + CN_TRACKEDNAME + Backend.SQL_EQUAL_PAR + Backend.SQL_OR + CN_ID + Backend.SQL_EQUAL_PAR
    );
    st.setLong(1, count);
    st.setString(2, trackedName);
    st.setLong(3, 0);       // this is for the master!
    return st.executeUpdate();
  }

  private static final StatementId COUNT_MODIFICATION_ID = new StatementId();


  /**
   * Adds an entry for this counter (== database object class)
   * to the modification table.<br>
   * If the referenced table provides a tableserial column, the initial serial is derived from the
   * highest tableserial of all objects in that table.
   *
   * @param tableExists true if related to a PDO, false if just a name
   * @param tableSerialTableName the table to scan for max tableserial if PDO manages a tableserial
   */
  public void addToModificationTable(boolean tableExists, String tableSerialTableName)  {
    try (StatementWrapper st = getSession().createStatement()) {
      int count;
      if (tableExists) {
        count = st.executeUpdate (
            getBackend().isExpressionReferringToTableBeingUpdatedSupported() ?
            // ordinary dbms
            (
                "INSERT INTO " + getTableName() + " (" + CN_TRACKEDNAME + "," +
                CN_ID + "," + CN_SERIAL +
                ") VALUES ('" +
                trackedName +
                "',(SELECT MAX(" + CN_ID + ")+1 FROM " + getTableName() + ")," +
                (tableSerialTableName != null ? ("(SELECT " + getBackend().getCoalesceKeyword() +
                                                 "(MAX(" + CN_TABLESERIAL + "),0) FROM " + tableSerialTableName + ")") : "1") +
                ")"
            ) :

            // Some databases, such as MySQL, do not allow updates with expressions referring to the table being updated.
            // This does the trick, though ;-)
            (
                "INSERT INTO " + getTableName() + " SET " + CN_TRACKEDNAME + "='" + trackedName + "', " +
                CN_ID + "=(SELECT * FROM (SELECT MAX(" + CN_ID + ")+1 FROM " +
                getTableName() + ") AS x), " + CN_SERIAL + "=" +
                (tableSerialTableName != null ? ("(SELECT " + getBackend().getCoalesceKeyword() +
                                                 "(MAX(" + CN_TABLESERIAL + "),0) FROM " + tableSerialTableName + ")") : "1")
            )

        );
      }
      else  {
        // just a name, not related to any PDO or database table
        count = st.executeUpdate (
            getBackend().isExpressionReferringToTableBeingUpdatedSupported() ?
            // ordinary dbms
            (
                "INSERT INTO " + getTableName() + " (" + CN_TRACKEDNAME + "," +
                CN_ID + "," + CN_SERIAL +
                ") VALUES ('" +
                trackedName +
                "',(SELECT MAX(" + CN_ID + ")+1 FROM " + getTableName() + "),0)"
            ) :

            // Some databases, such as MySQL, do not allow updates with expressions referring to the table being updated.
            // This does the trick, though ;-)
            (
                "INSERT INTO " + getTableName() + " SET " + CN_TRACKEDNAME + "='" + trackedName + "', " +
                CN_ID + "=(SELECT * FROM (SELECT MAX(" + CN_ID + ")+1 FROM " +
                getTableName() + ") AS x), " + CN_SERIAL + "=0"
            )
        );
      }

      if (count != 1) {
        throw new PersistenceException(this, "adding modification entry failed");
      }
    }
  }


  /**
   * Refreshes the contents of this modification entry by its name.
   *
   * @return the modification count
   */
  public long refresh() {
    getSession().assertNotRemote();
    PreparedStatementWrapper st = getPreparedStatement(SELECT_MODIFICATION_ID, _ ->
        Backend.SQL_SELECT + CN_ID + Backend.SQL_COMMA + CN_SERIAL + Backend.SQL_FROM + getTableName() +
        Backend.SQL_WHERE + CN_TRACKEDNAME + Backend.SQL_EQUAL_PAR
    );
    st.setParallelOk(true);
    st.setString(1, trackedName);
    try (ResultSetWrapper rs = st.executeQuery()) {
      if (rs.next())  {
        setId(rs.getLong(CN_ID));
        setSerial(rs.getLong(CN_SERIAL));
      }
      else  {
        setId(0);
        setSerial(0);
      }
      return getSerial();
    }
  }

  private static final StatementId SELECT_MODIFICATION_ID = new StatementId();


  /**
   * Selects the id and serial by tablename.
   *
   * @param tableName the database tablename
   * @return the id/serial tuple, null if no such table registered so far
   */
  public IdSerialTuple selectIdSerial(String tableName) {
    getSession().assertNotRemote();
    PreparedStatementWrapper st = getPreparedStatement(SELECT_ID_ID, _ ->
        Backend.SQL_SELECT + CN_ID + Backend.SQL_COMMA + CN_SERIAL +
        Backend.SQL_FROM + getTableName() + Backend.SQL_WHERE +
        CN_TRACKEDNAME + Backend.SQL_EQUAL_PAR
    );
    st.setParallelOk(true);
    st.setString(1, tableName);
    try (ResultSetWrapper rs = st.executeQuery()) {
      if (rs.next()) {
        return new IdSerialTuple(rs.getLong(1), rs.getLong(2));
      }
      return null;
    }
  }

  private static final StatementId SELECT_ID_ID = new StatementId();


  /**
   * Selects the tablename for a given ID.
   *
   * @param id the table ID
   * @return the tablename, null if such an ID does not exist
   */
  public String selectTableNameForId(long id) {
    getSession().assertNotRemote();
    PreparedStatementWrapper st = getPreparedStatement(SELECT_TABLENAME_ID, _ ->
        Backend.SQL_SELECT + CN_TRACKEDNAME + Backend.SQL_FROM + getTableName() +
        Backend.SQL_WHERE + CN_ID + Backend.SQL_EQUAL_PAR
    );
    st.setParallelOk(true);
    st.setLong(1, id);
    try (ResultSetWrapper rs = st.executeQuery()) {
      if (rs.next()) {
        return rs.getString(1);
      }
      return null;
    }
  }

  private static final StatementId SELECT_TABLENAME_ID = new StatementId();


  /**
   * The master serial and the generation of the modification table.
   *
   * @param serial the master serial
   * @param generation the generation, {@link #LEGACY_GENERATION} if none
   */
  public record Master(long serial, long generation) {}

  /**
   * Reads the master-serial.
   *
   * @return the master serial
   */
  public long selectMasterSerial() {
    return selectMaster().serial();
  }

  /**
   * Reads the master-serial and the generation of the modification table.
   *
   * @return the master serial and generation
   */
  public Master selectMaster() {
    getSession().assertNotRemote();
    PreparedStatementWrapper st = getPreparedStatement(SELECT_MASTERSERIAL_ID, _ ->
        Backend.SQL_SELECT + CN_SERIAL + Backend.SQL_COMMA + CN_TRACKEDNAME + Backend.SQL_FROM + getTableName() +
        Backend.SQL_WHERE + CN_ID + Backend.SQL_EQUAL_PAR
    );
    st.setParallelOk(true);
    st.setLong(1, 0);   // ID=0 is the master
    try (ResultSetWrapper rs = st.executeQuery()) {
      if (rs.next()) {
        return new Master(rs.getLong(1), parseGeneration(rs.getString(2)));
      }
      else  {
        throw new PersistenceException(getSession(), "can't read master serial from " + getTableName());
      }
    }
  }

  private static final StatementId SELECT_MASTERSERIAL_ID = new StatementId();

}

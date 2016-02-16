/**
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


package org.tentackle.persist;

import org.tentackle.pdo.PersistenceException;



/**
 * An {@link IdSource} to create unique object IDs from a database sequence.
 * <p>
 * To reduce the number of roundtrips the sequence is multiplied by a sequence-multiplier and a
 * counter just incremented within that range.
 * <p>
 * The creation of the sequence is backend-specific. For example, postgres expects:
 * <pre>
 * CREATE SEQUENCE object_sequence_id;
 * </pre>
 *
 * @author harald
 */
public class ObjectSequenceId extends AbstractIdSource {

  /**
   * serialver.
   */
  private static final long serialVersionUID = -2463189262257268747L;


  /**
   * Default global multiplier to allow application-specific offsets.
   */
  public static final int GLOBAL_MULTIPLIER = 1;

  /**
   * Default sequence multiplier.
   */
  public static final int SEQUENCE_MULTIPLIER = 100;


  /**
   * select statement id.
   */
  private static final StatementId SELECT_ID = new StatementId();

  /**
   * the last retrieved sequence-number.
   */
  private static long sequenceNo;





  /**
   * The sequence multiplier for this id source.
   */
  private final int sequenceMultiplier;

  /**
   * The global multiplier for this id source.
   */
  private final int globalMultiplier;

  /**
   * IDs created for current sequence.
   */
  private int idCount;


  /**
   * Creates a sequence id source.
   *
   * @param name the source and sequence name
   * @param sequenceMultiplier the sequence multiplier
   * @param globalMultiplier the global multiplier
   */
  public ObjectSequenceId(String name, int sequenceMultiplier, int globalMultiplier) {
    super(name);
    this.sequenceMultiplier = sequenceMultiplier;
    this.globalMultiplier = globalMultiplier;

    idCount = sequenceMultiplier; // initially consumed -> force select
  }


  /**
   * Creates a sequence id with default multipliers.
   *
   * @param name the source name
   */
  public ObjectSequenceId(String name) {
    this(name, SEQUENCE_MULTIPLIER, GLOBAL_MULTIPLIER);
  }


  @Override
  public boolean isLockFree() {
    // database sequences are not part of transactions and therefore non-blocking
    return true;
  }


  @Override
  public synchronized long nextId(Db db) {

    assertDbNotRemote(db);

    if (idCount >= sequenceMultiplier) {
      // get new offset from sequence
      try {
        PreparedStatementWrapper selectStatement = db.getPreparedStatement(new StatementKey(SELECT_ID, getClass()), false,
                () -> db.getBackend().sqlNextFromSequene(getName())
        );
        try (ResultSetWrapper rs = selectStatement.executeQuery()) {
          if (rs.next()) {
            sequenceNo = rs.getLong(1);
            idCount = 0;
          }
          else {
            throw new PersistenceException(db, "cannot retrieve ID from sequence");
          }
        }
      }
      catch (RuntimeException e) {
        if (e instanceof PersistenceException) {
          throw e;
        }
        throw new PersistenceException(db, e);
      }
    }

    long nextId = (sequenceNo * sequenceMultiplier + idCount++) * globalMultiplier;

    nextId = addGlobalOffset(nextId);

    return nextId;
  }


  /**
   * Adds some global offset to the id.
   * <p>
   * @param nextId the computed new id
   * <p>
   * @return the id plus offset
   */
  protected long addGlobalOffset(long nextId) {
    return nextId;  // no change
  }

}

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


package org.tentackle.dbms;

import java.util.HashMap;
import java.util.Map;
import org.tentackle.session.PersistenceException;



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
   * Default global multiplier to allow application-specific offsets.
   */
  public static final int GLOBAL_MULTIPLIER = 1;

  /**
   * Default sequence multiplier.
   */
  public static final int SEQUENCE_MULTIPLIER = 100;

  /**
   * Sequences mapped by unique name.
   */
  private static final Map<String,ObjectSequenceId> SEQUENCES = new HashMap<>();

  /**
   * select statement id.
   */
  private static final StatementId SELECT_ID = new StatementId();



  /**
   * Gets a sequence id source.<br>
   * Id sources are unique by name.
   *
   * @param name the unique sequence name
   * @param sequenceMultiplier the sequence multiplier
   * @param globalMultiplier the global multiplier
   * @return the ID source
   */
  public static synchronized ObjectSequenceId get(String name, int sequenceMultiplier, int globalMultiplier) {
    ObjectSequenceId seq = SEQUENCES.get(name);
    if (seq == null) {
      seq = new ObjectSequenceId(name, sequenceMultiplier, globalMultiplier);
      SEQUENCES.put(name, seq);
    }
    else {
      // check multipliers
      if (seq.globalMultiplier != globalMultiplier ||
          seq.sequenceMultiplier != sequenceMultiplier) {
        throw new PersistenceException("attempt to configure id source with different multipliers for '" + name +
                "', configured: " + seq.sequenceMultiplier + "/" + seq.globalMultiplier +
                ", attempt: " + sequenceMultiplier + "/" + globalMultiplier);
      }
    }
    return seq;
  }

  /**
   * Gets a sequence id source.<br>
   * Id sources are unique by name.
   *
   * @param name the unique sequence name
   * @return the ID source
   */
  public static synchronized ObjectSequenceId get(String name) {
    return get(name, SEQUENCE_MULTIPLIER, GLOBAL_MULTIPLIER);
  }




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
   * the last retrieved sequence-number.
   */
  private long sequenceNo;


  /**
   * Creates a sequence id source.
   *
   * @param name the source and sequence name
   * @param sequenceMultiplier the sequence multiplier
   * @param globalMultiplier the global multiplier
   */
  private ObjectSequenceId(String name, int sequenceMultiplier, int globalMultiplier) {
    super(name);
    this.sequenceMultiplier = sequenceMultiplier;
    this.globalMultiplier = globalMultiplier;

    idCount = sequenceMultiplier; // initially consumed -> force select
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

    return (sequenceNo * sequenceMultiplier + idCount++) * globalMultiplier;
  }

}

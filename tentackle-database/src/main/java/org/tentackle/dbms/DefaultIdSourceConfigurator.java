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

import org.tentackle.common.Service;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;

import java.util.StringTokenizer;

/**
 * Default {@link IdSourceConfigurator}.
 * <p>
 * There are 2 configuration options:
 * <ol>
 * <li>table:&lt;name&gt;: an {@link ObjectId} counting in the given table</li>
 * <li>sequence:&lt;name&gt;[:&lt;sequence-multiplier&gt;:&lt;global-multiplier&gt;]: an {@link ObjectSequenceId}
 * on the given sequence</li>
 * </ol>
 * Without a configuration, the default is {@code sequence:object_sequence_id} if the backend supports
 * sequences, otherwise {@code table:object_id}.
 *
 * @author harald
 */
@Service(IdSourceConfigurator.class)
public class DefaultIdSourceConfigurator implements IdSourceConfigurator {

  /** table-based id source. */
  public static final String TABLE_BASED = "table";

  /** sequence-based id source. */
  public static final String SEQUENCE_BASED = "sequence";


  /**
   * Creates an IdSource configurator.
   */
  public DefaultIdSourceConfigurator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public IdSource configure(String idConfig) {
    // parse
    String type = null;
    String name = null;
    Integer sequenceMultiplier = null;
    Integer globalMultiplier = null;

    StringTokenizer stok = new StringTokenizer(idConfig, ":");
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      if (type == null) {
        type = token;
      }
      else if (name == null) {
        name = token;
      }
      else if (sequenceMultiplier == null) {
        sequenceMultiplier = Integer.parseInt(token);
      }
      else if (globalMultiplier == null) {
        globalMultiplier = Integer.parseInt(token);
      }
      else {
        throw new PersistenceException("malformed idSource configuration: " + idConfig);
      }
    }

    if (type == null) {
      throw new PersistenceException("empty id configuration");
    }

    return switch (type) {
      case SEQUENCE_BASED -> {
        if (sequenceMultiplier == null) {   // implies globalMultiplier == null
          yield ObjectSequenceId.get(name);
        }
        else if (globalMultiplier != null) {
          yield ObjectSequenceId.get(name, sequenceMultiplier, globalMultiplier);
        }
        throw new PersistenceException("sequenceMultiplier and globalMultiplier must be defined both or not at all");
      }
      case TABLE_BASED -> ObjectId.get(name);
      default -> throw new PersistenceException("unsupported idSource type: " + type);
    };
  }

  @Override
  public IdSource configure(Backend backend) {
    if (backend.isSequenceSupported()) {
      return configure(SEQUENCE_BASED + ":object_sequence_id");
    }
    else  {
      return configure(TABLE_BASED + ":object_id");
    }
  }

}

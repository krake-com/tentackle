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

package org.tentackle.dbms.dsl;

import org.tentackle.session.PersistenceException;

/**
 * SQL identifier verification.
 * <p>
 * Table-, column- and alias names end up in the SQL-code verbatim, i.e. they are
 * not parameters. Since they may originate from configuration or even from user input,
 * they are verified syntactically as soon as they enter the DSL.
 */
final class Identifiers {

  /**
   * Verifies an identifier.
   *
   * @param identifier the identifier, may be qualified by a schema name
   * @param kind the kind of identifier, for the error message only
   * @return the identifier
   */
  static String check(String identifier, String kind) {
    if (identifier == null || identifier.isEmpty()) {
      throw new PersistenceException("missing " + kind);
    }
    boolean startOfName = true;
    for (int index = 0; index < identifier.length(); index++) {
      char character = identifier.charAt(index);
      if (character == '.') {
        // a qualified name such as <schema>.<table>
        if (startOfName || index == identifier.length() - 1) {
          throw new PersistenceException("malformed " + kind + " '" + identifier + "'");
        }
        startOfName = true;
      }
      else {
        if (startOfName ? !Character.isLetter(character) && character != '_'
                        : !Character.isLetterOrDigit(character) && character != '_' && character != '$') {
          throw new PersistenceException("illegal character '" + character + "' in " + kind + " '" + identifier + "'");
        }
        startOfName = false;
      }
    }
    return identifier;
  }


  private Identifiers() {
  }

}

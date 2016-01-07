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



package org.tentackle.swing.rdc;

import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormTableEntry;

/**
 * A default formtable entry for an {@link PersistentDomainObject}.
 * <p>
 * Contains only one "name" column showing the {@link PersistentDomainObject#getTreeText()}-value.
 *
 * @param <T> the PDO class
 * @author harald
 */
public class DefaultPdoTableEntry<T extends PersistentDomainObject<T>> extends FormTableEntry<T> {


  private static final String[] columnNames = {
    "Name"
  };

  private static final int NAME = 0;



  private final T pdo;                        // the PDO
  private final GuiProvider<T> provider;      // the GUI-provider


  /**
   * Creates a form table entry.
   *
   * @param object the data object
   */
  public DefaultPdoTableEntry(T object) {
    this.pdo = object;
    this.provider = Rdc.createGuiProvider(object);
  }



  @Override
  public int getColumnCount() {
    return columnNames.length;
  }

  @Override
  public String getColumnName(int col) {
    return columnNames[col];
  }

  @Override
  public T getObject() {
    return pdo;
  }

  /**
   * Gets the GUI-service object for the PDO.
   * @return the GUI provider.
   */
  public GuiProvider<T> getGuiProvider() {
    return provider;
  }

  @Override
  public Object getValueAt(int col) {
    switch (col)  {
      case NAME: return provider.getTreeText();
    }
    return null;
  }

  @Override
  public DefaultPdoTableEntry<T> newInstance(T object) {
    return new DefaultPdoTableEntry<>(object);
  }


}

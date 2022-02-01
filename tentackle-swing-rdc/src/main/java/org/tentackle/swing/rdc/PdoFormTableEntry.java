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

import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.swing.FormFieldComponentCellEditor;
import org.tentackle.swing.FormTableEntry;
import org.tentackle.swing.bind.FormTableBinding;

import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * A {@link FormTableEntry} aware of PDOs.
 *
 * @param <T> the PDO type
 * @author harald
 */
public abstract class PdoFormTableEntry<T extends PersistentDomainObject<T>> extends FormTableEntry<T> {

  @Override
  @SuppressWarnings("unchecked")
  public TableCellEditor getCellEditor(int mColumn) {
    TableCellEditor cellEditor = super.getCellEditor(mColumn);
    if (cellEditor == null) {
      FormTableBinding binding = getBinding(mColumn);
      if (binding != null) {
        final Class<?> clazz  = binding.getMember().getType();
        if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
          // is a PDO
          final Class<T> pdoClass = (Class<T>) clazz;
          PdoCache<T> cache = Pdo.getPdoCache(pdoClass);
          if (cache != null && cache.isPreloading()) {
            cellEditor = new FormFieldComponentCellEditor(new PdoComboBox() {
              private static final long serialVersionUID = 1L;
              @Override
              protected void setup() {
                super.setup();
                setPdoClass(pdoClass);
                setDomainContext(getObject().getDomainContext());
              }
            });
          }
          else  {
            cellEditor = new FormFieldComponentCellEditor(new PdoFieldPanel() {
              private static final long serialVersionUID = 1L;
              @Override
              protected void setup() {
                super.setup();
                setEditButtonVisible(false);
                setSearchButtonVisible(false);
                setInfoFieldVisible(false);
                setLink(pdoClass, getObject().getDomainContext(), 0);
              }
            });
          }
        }
      }
    }
    return cellEditor;
  }

  @Override
  public TableCellRenderer getCellRenderer(int mColumn) {
    TableCellRenderer cellRenderer = super.getCellRenderer(mColumn);
    if (cellRenderer == null) {
      FormTableBinding binding = getBinding(mColumn);
      if (binding != null) {
        final Class<?> clazz  = binding.getMember().getType();
        if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
          // is a PDO
          cellRenderer = new PdoTableCellRenderer();
        }
      }
    }
    return cellRenderer;
  }


  @Override
  public Class<?> getColumnClass(int mColumn) {
    FormTableBinding binding = getBinding(mColumn);
    if (binding != null) {
      Class<?> clazz = binding.getMember().getType();
      if (clazz.isPrimitive()) {
        clazz = ReflectionHelper.primitiveToWrapperClass(clazz);
      }
      return clazz;
    }
    else  {
      return super.getColumnClass(mColumn);
    }
  }

}

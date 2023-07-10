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

package org.tentackle.fx.rdc.component;

import org.tentackle.fx.ShortLongTextCellFactory;
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.fx.rdc.RdcShortLongTextCellFactory;
import org.tentackle.fx.rdc.component.delegate.RdcComboBoxDelegate;
import org.tentackle.fx.rdc.translate.PdoComponentAddon;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.util.Callback;
import java.util.Collection;
import java.util.function.Function;

/**
 * PDO-aware combo box.
 *
 * @author harald
 * @param <T> the type
 */
public class RdcComboBox<T> extends FxComboBox<T> {

  /**
   * Creates a combo box.
   */
  public RdcComboBox() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  @Override
  public RdcComboBoxDelegate getDelegate() {
    return (RdcComboBoxDelegate) super.getDelegate();
  }

  /**
   * Gets the callback to load all PDOs.<br>
   * Used by {@link PdoComponentAddon#getAllPdos(PersistentDomainObject)}.
   *
   * @return the callback
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Function<T, Collection<T>> getLoadAllPdosCallback() {
    return (Function) getDelegate().getLoadAllPdosCallback();
  }

  /**
   * Sets the callback to load all PDOs.<br>
   * Used by {@link PdoComponentAddon#getAllPdos(PersistentDomainObject)}.
   *
   * @param loadAllPdosCallback the callback
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void setLoadAllPdosCallback(Callback<T, Collection<T>> loadAllPdosCallback) {
    getDelegate().setLoadAllPdosCallback((Callback) loadAllPdosCallback);
  }

  @Override
  protected ShortLongTextCellFactory<T> createShortLongTextCellFactory() {
    return new RdcShortLongTextCellFactory<>();
  }

  @Override
  protected RdcComboBoxDelegate createDelegate() {
    return new RdcComboBoxDelegate(this);
  }

}

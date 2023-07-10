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

import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.fx.rdc.component.delegate.RdcChoiceBoxDelegate;
import org.tentackle.fx.rdc.translate.PdoComponentAddon;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.util.Callback;
import java.util.Collection;

/**
 * PDO-aware choice box.
 *
 * @author harald
 * @param <T> the type
 */
public class RdcChoiceBox<T> extends FxChoiceBox<T> {

  /**
   * Creates a choice box.
   */
  public RdcChoiceBox() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the callback to load all PDOs.<br>
   * Used by {@link PdoComponentAddon#getAllPdos(PersistentDomainObject)}.
   *
   * @return the callback
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Callback<T, Collection<T>> getLoadAllPdosCallback() {
    return (Callback) getDelegate().getLoadAllPdosCallback();
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
  public RdcChoiceBoxDelegate getDelegate() {
    return (RdcChoiceBoxDelegate) super.getDelegate();
  }


  @Override
  protected RdcChoiceBoxDelegate createDelegate() {
    return new RdcChoiceBoxDelegate(this);
  }

}

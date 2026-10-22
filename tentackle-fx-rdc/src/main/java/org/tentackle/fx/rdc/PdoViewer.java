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

package org.tentackle.fx.rdc;

import org.tentackle.pdo.PersistentDomainObject;

/**
 * A wrapper for a view controller that can be used as a PDO editor.<br>
 * Useful to view PDOs that cannot be edited.
 * <p>
 * Example (in a GuiProvider):
 * <pre>
 *   &#64;Override
 *   public PdoEditor&lt;Incident&gt; createEditor() {
 *     return new PdoViewer&lt;Incident, IncidentView&gt;(Fx.load(IncidentView.class));
 *   }
 *
 *   &#64;Override
 *   public boolean isEditAllowed() {
 *     return false;   // disable the edit item in the context menu
 *   }
 * </pre>
 *
 * @param <T> the PDO type
 * @param <C> the controller type
 */
public class PdoViewer<T extends PersistentDomainObject<T>, C extends PdoController<T>>
       extends PdoEditor<T> {

  private final C viewController;

  /**
   * Creates a view wrapper.
   *
   * @param viewController the view controller
   */
  public PdoViewer(C viewController) {
    this.viewController = viewController;
    setView(viewController.getView());
  }

  /**
   * Gets the view controller.
   *
   * @return the controller
   */
  public C getViewController() {
    return viewController;
  }

  @Override
  public T getPdo() {
    return viewController.getPdo();
  }

  @Override
  public void setPdo(T pdo) {
    viewController.setPdo(pdo);
  }

  @Override
  public void requestInitialFocus() {
    // does nothing by default, assumes that setPdo() does it already
  }

  @Override
  public boolean isEditAllowed() {
    return false;   // this is just a viewer!
  }

}

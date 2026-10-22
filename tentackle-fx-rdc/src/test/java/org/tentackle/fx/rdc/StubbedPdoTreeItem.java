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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PdoTreeItem} with a stubbed {@link GuiProvider}.
 * <p>
 * The provider is created without {@link GuiProviderFactory}, which is necessary even for
 * tests that never ask for it: JavaFX invokes {@link #isLeaf()} as soon as the item is added
 * to a parent. The provider records the requests for the child objects and returns none.
 *
 * @param <T> the PDO type
 */
public class StubbedPdoTreeItem<T extends PersistentDomainObject<T>> extends PdoTreeItem<T> {

  private final boolean providesChildren;
  private final List<Object> childRequests = new ArrayList<>();
  private GuiProvider<T> provider;

  /**
   * Creates a tree item whose PDO provides no children.
   *
   * @param pdo the PDO
   */
  public StubbedPdoTreeItem(T pdo) {
    this(pdo, false);
  }

  /**
   * Creates a tree item.
   *
   * @param pdo the PDO
   * @param providesChildren true if the GUI provider provides tree children
   */
  public StubbedPdoTreeItem(T pdo, boolean providesChildren) {
    super(pdo);
    this.providesChildren = providesChildren;
  }

  /**
   * Gets the parent objects the children were requested for.
   *
   * @return one entry per request
   */
  public List<Object> getChildRequests() {
    return childRequests;
  }

  @Override
  @SuppressWarnings("unchecked")
  public GuiProvider<T> getGuiProvider() {
    if (provider == null) {
      provider = (GuiProvider<T>) Proxy.newProxyInstance(
          GuiProvider.class.getClassLoader(), new Class<?>[] { GuiProvider.class },
          (proxy, method, args) -> switch (method.getName()) {
            case "providesTreeChildObjects" -> providesChildren;
            case "getTreeChildObjects" -> {
              childRequests.add(args[0]);
              yield List.of();
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "GuiProviderStub";
            default -> PdoStubs.defaultValue(method.getReturnType());
          });
    }
    return provider;
  }

}

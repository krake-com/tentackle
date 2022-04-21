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

import javafx.scene.Node;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.util.Callback;

import org.tentackle.common.BundleFactory;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.rdc.search.DefaultPdoFinder;
import org.tentackle.fx.rdc.table.TableUtilities;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.misc.IdentifiableKey;
import org.tentackle.misc.ShortLongText;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Default implementation of a {@link GuiProvider}.<br>
 * Can be used as an adapter.
 *
 * @param <T> the PDOs class
 * @author harald
 */
public class DefaultGuiProvider<T extends PersistentDomainObject<T>> implements GuiProvider<T> {
  
  private T pdo;                    // the PDO
  private Boolean bundleProvided;   // != null if determined according to GuiProviderService annotation or isBundleProvided
  private ResourceBundle bundle;    // optional resource bundle

  /**
   * Creates a default GUI provider.
   *
   * @param pdo the PDO
   */
  public DefaultGuiProvider(T pdo) {
    setPdo(pdo);
    GuiProviderService annotation = getClass().getAnnotation(GuiProviderService.class);
    if (annotation != null) {
      bundleProvided = !annotation.noBundle();
    }
  }

  /**
   * Returns whether a resource bundle is provided.
   *
   * @return true if provided, false if no bundle
   */
  @Override
  public boolean isBundleProvided() {
    if (bundleProvided == null) {
      try {
        getBundle();
        bundleProvided = true;
      }
      catch (MissingResourceException mx) {
        bundleProvided = false;
      }
    }
    return bundleProvided;
  }

  /**
   * Gets the resource bundle for this provider.
   * <p>
   * Throws {@link java.util.MissingResourceException} if no bundle found.
   *
   * @return the resource bundle
   */
  @Override
  public ResourceBundle getBundle() {
    if (bundle == null) {
      bundle = BundleFactory.getBundle(getClass().getName());
    }
    return bundle;
  }

  @Override
  public T getPdo() {
    return pdo;
  }

  /**
   * Sets the PDO.<br>
   *
   * @param pdo the pdo
   */
  public void setPdo(T pdo) {
    this.pdo = Objects.requireNonNull(pdo, "pdo");
  }

  @Override
  public DomainContext getDomainContext() {
    return pdo.getDomainContext();
  }

  @Override
  public Node createGraphic()  {
    return Fx.createGraphic("unknown");
  }

  @Override
  public boolean isEditorAvailable() {
    return false;
  }

  @Override
  public boolean isEditAllowed() {
    return isEditorAvailable() && pdo.isEditAllowed();
  }

  @Override
  public boolean isViewAllowed() {
    return isEditorAvailable() && pdo.isViewAllowed();
  }

  @Override
  public PdoEditor<T> createEditor() {
    if (isEditorAvailable()) {
      return new DefaultPdoEditor<>(getPdo().getEffectiveClass(), isBundleProvided() ? getBundle() : null);
    }
    throw new FxRuntimeException("no controller for " + pdo.getEffectiveClass().getName());
  }

  @Override
  public boolean isFinderAvailable() {
    return false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public PdoFinder<T> createFinder() {
    if (isFinderAvailable()) {
      return Fx.load(DefaultPdoFinder.class);
    }
    throw new FxRuntimeException("no finder for " + pdo.getEffectiveClass().getName());
  }

  @Override
  public Dragboard createDragboard(Node node) {
    return RdcUtilities.getInstance().createDragboard(node, pdo);
  }

  @Override
  public boolean isDragAccepted(DragEvent event) {
    return false;
  }

  /**
   * Gets the PDO key from the dragboard.<br>
   * The method is provided to simplify implementing DND as only
   * the methods {@link #isDragAccepted} and {@link #dropPdo} need to be overridden.
   *
   * @param dragboard the dragboard
   * @return the key, null if dragboard's string is null or empty
   */
  protected IdentifiableKey<PersistentDomainObject<?>> getPdoKeyFromDragboard(Dragboard dragboard) {
    return RdcUtilities.getInstance().getPdoKeyFromDragboard(dragboard);
  }

  @Override
  public void dropDragboard(Dragboard dragboard) {
    PersistentDomainObject<?> pdoToDrop = RdcUtilities.getInstance().getPdoFromDragboard(dragboard, pdo.getDomainContext());
    if (pdoToDrop != null) {
      dropPdo(pdoToDrop);
    }
  }

  /**
   * Drops the given PDO on the serviced pdo.
   *
   * @param pdoToDrop the PDO to drop
   */
  public void dropPdo(PersistentDomainObject<?> pdoToDrop) {
    throw new FxRuntimeException("dropPdo is not implemented for " + pdo.getEffectiveClass().getName());
  }


  /**
   * Creates an empty table configuration.<br>
   * Utility method for subclasses of {@link DefaultGuiProvider}.
   *
   * @param name the table's name, null if basename from effective class of template
   * @return the table configuration
   */
  protected TableConfiguration<T> createEmptyTableConfiguration(String name) {
    TableConfiguration<T> configuration = FxFactory.getInstance().createTableConfiguration(getPdo(), name);
    if (isBundleProvided()) {
      configuration.setBaseBundleName(getClass().getName());
    }
    return configuration;
  }

  /**
   * Creates an empty table configuration.<br>
   * Utility method for subclasses of {@link DefaultGuiProvider}.
   *
   * @return the table configuration
   */
  protected final TableConfiguration<T> createEmptyTableConfiguration() {
    return createEmptyTableConfiguration(null);
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation creates the column configurations by analyzing the PDO class via reflection.<br>
   * Application specific subclasses overriding this method should consider using {@link #createEmptyTableConfiguration(String)} or
   * {@link #createEmptyTableConfiguration()} to create the empty configuration first.
   */
  @Override
  public TableConfiguration<T> createTableConfiguration() {
    TableConfiguration<T> configuration = TableUtilities.getInstance().createTableConfiguration(
                  getPdo().getEffectiveClass(), isBundleProvided() ? getBundle() : null);
    if (isBundleProvided()) {
      configuration.setBaseBundleName(getClass().getName());
    }
    return configuration;
  }

  @Override
  public FxTableView<T> createTableView() {
    TableConfiguration<T> config = createTableConfiguration();
    if (config.getBindingType() == TableConfiguration.BINDING.YES) {
      config.getBinder().bind();
    }
    else if (config.getBindingType() == TableConfiguration.BINDING.INHERITED) {
      config.getBinder().bindAllInherited();
    }
    if (config.getBindingType() != TableConfiguration.BINDING.NO) {
      config.getBinder().assertAllBound();
    }
    FxTableView<T> tableView = Fx.create(TableView.class);
    config.configure(tableView);
    return tableView;
  }

  @Override
  public String toString() {
    return getClass().getName() + " for " + getPdo().toGenericString();
  }

  @Override
  public boolean providesTreeChildObjects() {
    return false;
  }

  @Override
  public <P extends PersistentDomainObject<P>> Collection<? extends PersistentDomainObject<?>> getTreeChildObjects(P parent) {
    return new ArrayList<>();
  }

  @Override
  public int getTreeExpandMaxDepth() {
    return 0;
  }

  @Override
  public boolean providesTreeParentObjects() {
    return false;
  }

  @Override
  public <P extends PersistentDomainObject<P>> Collection<? extends PersistentDomainObject<?>> getTreeParentObjects(P parent) {
    return new ArrayList<>();
  }

  @Override
  public T getTreeRoot() {
    return getPdo();
  }

  @Override
  public <P extends PersistentDomainObject<P>> String getTreeText(P parent) {
    return getPdo().toString();
  }

  @Override
  public <P extends PersistentDomainObject<P>> String getToolTipText(P parent) {
    if (getPdo() instanceof ShortLongText) {
      return ((ShortLongText) getPdo()).getLongText();
    }
    return getTreeText(parent);
  }

  @Override
  public boolean stopTreeExpansion() {
    return false;
  }

  @Override
  public TreeItem<T> createTreeItem() {
    return Rdc.createTreeItem(pdo);
  }

  @Override
  public Callback<TreeView<T>, TreeCell<T>> getTreeCellFactory() {
    return Rdc::createTreeCell;
  }

}

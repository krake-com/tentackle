/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.rdc;

import javafx.scene.Node;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import org.tentackle.common.BundleFactory;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.rdc.search.DefaultPdoFinder;
import org.tentackle.fx.rdc.table.TableUtilities;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.misc.IdentifiableKey;
import org.tentackle.misc.ShortLongText;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.MissingResourceException;
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

  @Override
  public void setPdo(T pdo) {
    if (pdo == null) {
      throw new NullPointerException("pdo must not be null");
    }
    this.pdo = pdo;
  }

  @Override
  public DomainContext getDomainContext() {
    return pdo.getDomainContext();
  }

  @Override
  public ImageView createIcon()  {
    return Fx.createImageView("unknown");
  }

  @Override
  public boolean editorExists() {
    return false;
  }

  @Override
  public boolean isEditAllowed() {
    return editorExists() && pdo.isEditAllowed();
  }

  @Override
  public boolean isViewAllowed() {
    return editorExists() && pdo.isViewAllowed();
  }

  @Override
  public PdoEditor<T> createEditor() {
    if (editorExists()) {
      return new DefaultPdoEditor<>(getPdo().getEffectiveClass(), isBundleProvided() ? getBundle() : null);
    }
    throw new FxRuntimeException("no controller for " + pdo.getEffectiveClass().getName());
  }

  @Override
  public boolean finderExists() {
    return false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public PdoFinder<T> createFinder() {
    if (finderExists()) {
      return Fx.load(DefaultPdoFinder.class);
    }
    throw new FxRuntimeException("no finder for " + pdo.getEffectiveClass().getName());
  }

  @Override
  public Dragboard createDragboard(Node node) {
    if (pdo.getClassId() != 0 && !pdo.isNew()) {
      Dragboard db = node.startDragAndDrop(TransferMode.COPY);
      ClipboardContent content = new ClipboardContent();
      content.putString(pdo.toIdString());
      db.setContent(content);
      return db;
    }
    return null;
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
  public IdentifiableKey<PersistentDomainObject<?>> getPdoKeyFromDragboard(Dragboard dragboard) {
    String idString = dragboard.getString();
    if (idString != null && !idString.isEmpty()) {
      return PdoUtilities.getInstance().idStringToIdentifiableKey(idString);
    }
    return null;
  }

  @Override
  public void dropDragboard(Dragboard dragbord) {
    IdentifiableKey<PersistentDomainObject<?>> key = getPdoKeyFromDragboard(dragbord);
    if (key != null) {
      // load the PDO
      @SuppressWarnings("unchecked")
      PersistentDomainObject<?> pdoToDrop =
              Pdo.create((Class) key.getIdentifiableClass(), pdo.getDomainContext()).select(key.getIdentifiableId());
      if (pdoToDrop != null) {
        dropPdo(pdoToDrop);
      }
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

  @Override
  public TableConfiguration<T> createTableConfiguration() {
    return TableUtilities.getInstance().createTableConfiguration(
                  getPdo().getEffectiveClass(), isBundleProvided() ? getBundle() : null);
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
    @SuppressWarnings("unchecked")
    FxTableView<T> tableView = Fx.createNode(TableView.class);
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
    if (getPdo() instanceof ShortLongText) {
      return ((ShortLongText) getPdo()).getShortText() + " : " + ((ShortLongText) getPdo()).getLongText();
    }
    return getPdo().toString();
  }

  @Override
  public <P extends PersistentDomainObject<P>> String getToolTipText(P parent) {
    return getTreeText(parent);
  }

  @Override
  public boolean stopTreeExpansion() {
    return false;
  }

  @Override
  public TreeItem<T> createTreeItem() {
    return new PdoTreeItem<>(pdo);
  }

  @Override
  public TreeCell<T> createTreeCell() {
    return Rdc.createTreeCell();
  }

}

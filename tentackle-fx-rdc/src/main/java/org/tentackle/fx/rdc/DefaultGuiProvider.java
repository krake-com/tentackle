/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.rdc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import javafx.scene.Node;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.IdentifiableKey;
import org.tentackle.misc.ShortLongText;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Default implementation of a {@link GuiProvider}.<br>
 * Can be used as an adapter.
 *
 * @param <T> the PDOs class
 * @author harald
 */
public class DefaultGuiProvider<T extends PersistentDomainObject<T>> implements GuiProvider<T> {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultGuiProvider.class);

  private T pdo;                    // the PDO
  private ResourceBundle bundle;    // optional resource bundle

  /**
   * Creates a default GUI provider.
   *
   * @param pdo the PDO
   */
  public DefaultGuiProvider(T pdo) {
    setPdo(pdo);
  }

  @Override
  public ResourceBundle getBundle() {
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(getClass().getName());
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
  public <C extends PdoEditor<T>> C createEditor() {
    throw new FxRuntimeException("no controller for " + pdo.toGenericString());
  }

  @Override
  public boolean finderExists() {
    return false;
  }

  @Override
  public <C extends PdoFinder<T>> C createFinder() {
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
   * @return the key, null if invalid dragboard or no such classId
   */
  public IdentifiableKey<PersistentDomainObject<?>> getPdoKeyFromDragboard(Dragboard dragboard) {
    String str = dragboard.getString();
    if (str != null) {
      StringTokenizer stok = new StringTokenizer(str, ":");
      if (stok.hasMoreTokens()) {
        int classId = Integer.parseInt(stok.nextToken());
        if (stok.hasMoreTokens()) {
          int id = Integer.parseInt(stok.nextToken());
          String className = PdoUtilities.getInstance().getPdoClassName(classId);
          if (className != null && id != 0) {
            try {
              return new IdentifiableKey<>(className, id);
            }
            catch (ClassNotFoundException cx) {
              LOGGER.severe("cannot load PDO class " + className + " for classId " + classId, cx);
            }
          }
          else {
            LOGGER.warning("no PDO key for {0}:{1}", classId, id);
          }
        }
      }
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
    throw new FxRuntimeException("dropPdo is not implemented " + pdo.getEffectiveClass().getName());
  }

  @Override
  public TableConfiguration<T> createTableConfiguration() {
    TableConfiguration<T> config = FxFactory.getInstance().createTableConfiguration(getPdo(), null);
    // the default just provides the id and serial
    config.addColumn("id", "ID");
    config.addColumn("serial", "Serial");
    return config;
  }

  @Override
  public TableView<T> createTableView() {
    TableConfiguration<T> config = createTableConfiguration();
    if (config.getBindingType() == TableConfiguration.BINDING.YES) {
      config.getBinder().bind();
    }
    else if (config.getBindingType() == TableConfiguration.BINDING.INHERITED) {
      config.getBinder().bindAllInherited();
    }
    @SuppressWarnings("unchecked")
    TableView<T> tableView = Fx.createNode(TableView.class);
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

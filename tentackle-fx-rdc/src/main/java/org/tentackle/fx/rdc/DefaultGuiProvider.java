/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.fx.rdc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ResourceBundle;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.misc.ShortLongText;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Default implementation of a {@link GuiProvider}.<br>
 * Can be used as an adapter.
 *
 * @param <T> the PDOs class
 * @author harald
 */
public class DefaultGuiProvider<T extends PersistentDomainObject<T>> implements GuiProvider<T> {

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
  public Dragboard createDragboard() {
    return null;
  }

  @Override
  public boolean dropDragboard(Dragboard dragbord) {
    return false;
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
  public <S extends PersistentDomainObject<S>> Collection<S> getTreeChildObjects() {
    return new ArrayList<>();
  }

  @Override
  public <S extends PersistentDomainObject<S>> Collection<S> getTreeChildObjects(Object parentObject) {
    return getTreeChildObjects();
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
  public <S extends PersistentDomainObject<S>> Collection<S> getTreeParentObjects() {
    return new ArrayList<>();
  }

  @Override
  public <S extends PersistentDomainObject<S>> Collection<S> getTreeParentObjects(Object parentObject) {
    return getTreeParentObjects();
  }

  @Override
  public T getTreeRoot() {
    return getPdo();
  }

  @Override
  public String getTreeText() {
    if (getPdo() instanceof ShortLongText) {
      return ((ShortLongText) getPdo()).getShortText() + " : " + ((ShortLongText) getPdo()).getLongText();
    }
    return getPdo().toString();
  }

  @Override
  public String getTreeText(Object parent) {
    return getTreeText();
  }

  @Override
  public String getToolTipText() {
    return getTreeText();
  }

  @Override
  public String getToolTipText(Object parent) {
    return getToolTipText();
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

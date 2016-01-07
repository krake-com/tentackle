/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.swing.rdc;

import java.awt.Component;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.ImageIcon;
import org.tentackle.misc.ObjectFilter;
import org.tentackle.misc.ShortLongText;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormPanel;
import org.tentackle.swing.FormTableEntry;
import org.tentackle.swing.plaf.PlafUtilities;

/**
 * Default implementation of a {@link GuiProvider}.<br>
 * Can be used as an adapter.
 *
 * @param <T> the PDOs class
 * @author harald
 */
public class DefaultGuiProvider<T extends PersistentDomainObject<T>> implements GuiProvider<T> {

  private T pdo;   // the PDO

  /**
   * Creates a default GUI provider.
   *
   * @param pdo the PDO
   */
  public DefaultGuiProvider(T pdo) {
    setPdo(pdo);
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
  public ImageIcon getIcon()  {
    return PlafUtilities.getInstance().getIcon("unknown");
  }

  @Override
  public boolean panelExists() {
    return false;
  }

  @Override
  public FormPanel createPanel() {
    return null;
  }

  @Override
  public PdoEditDialog<T> createDialog(Component comp, boolean modal) {
    return null;
  }

  @Override
  public T getPanelObject()  {
    return getPdo();
  }

  @Override
  public PdoTransferable<T> getTransferable() {
    return getPdo().getPersistenceDelegate().isNew() ? null : new PdoTransferable<>(getPdo());
  }

  @Override
  public boolean dropTransferable(Transferable transferable) {
    return false;
  }

  @Override
  public PdoSearch<T> createPdoSearch()  {
    return new DefaultSearch<>(pdo);
  }

  @Override
  public FormTableEntry<T> getFormTableEntry() {
    return new DefaultPdoTableEntry<>(getPdo());
  }

  @Override
  public String getFormTableName()  {
    return "default";
  }

  @Override
  public String toString() {
    return getClass().getName() + " for " + getPdo().toGenericString();
  }

  @Override
  public ObjectFilter<T> getTreeFilter() {
    return null;
  }

  @Override
  public boolean allowsTreeChildObjects() {
    return true;
  }

  @Override
  public Collection getTreeChildObjects() {
    return new ArrayList<>();
  }

  @Override
  public Collection getTreeChildObjects(Object parentObject) {
    return getTreeChildObjects();
  }

  @Override
  public int getTreeExpandMaxDepth() {
    return 0;
  }

  @Override
  public boolean allowsTreeParentObjects() {
    return false;
  }

  @Override
  public Collection getTreeParentObjects() {
    return new ArrayList<>();
  }

  @Override
  public Collection getTreeParentObjects(Object parentObject) {
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

}

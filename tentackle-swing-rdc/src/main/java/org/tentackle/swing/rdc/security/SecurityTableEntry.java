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

// Created on August 21, 2002, 10:14 AM

package org.tentackle.swing.rdc.security;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.security.pdo.Security;
import org.tentackle.swing.FormButton;
import org.tentackle.swing.FormComponentCellEditor;
import org.tentackle.swing.FormFieldComponentCellEditor;
import org.tentackle.swing.FormQuestion;
import org.tentackle.swing.FormTable;
import org.tentackle.swing.FormTableCellRenderer;
import org.tentackle.swing.StringFormField;
import org.tentackle.swing.rdc.DefaultPdoTableEntry;
import org.tentackle.swing.rdc.PermissionEditor;
import org.tentackle.swing.rdc.Rdc;



/**
 * The default formtable entry for a {@link Security} rule.
 *
 * @author harald
 */
public class SecurityTableEntry extends DefaultPdoTableEntry<Security> {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityTableEntry.class);


  /**
   * By default there is only one grantee-class for the grantee cell editor.
   * Your application *MUST* set this or extend the Security...-classes to
   * add groups, etc...
   */
  public static Class<? extends PersistentDomainObject> granteeClazz;


  /**
   * For multi-tenancy the base context class and all its child context classes may be defined here. If contextClasses
   * is null (default), the context column will be non-editable. Otherwise a selection dialog for the first of the given
   * class is shown in the context of the table entry's object. All classes in the array are selectable.
   */
  public static Class[] contextClasses;


  protected static final int GRANTEE      = 0;
  protected static final int CONTEXT      = 1;
  protected static final int PERMISSIONS  = 2;
  protected static final int TEXT         = 3;
  protected static final int ALLOW        = 4;

  protected static final String[] columnNames = {
    "User",         // NOI18N
    "Context",      // NOI18N
    "Permission",   // NOI18N
    "Remark",       // NOI18N
    "allowed"       // NOI18N
  };




  /**
   * Creates a table entry.
   *
   * @param security the security object
   */
  public SecurityTableEntry(Security security) {
    super(security);
  }


  @Override
  public SecurityTableEntry newInstance(Security object) {
    return new SecurityTableEntry(object);
  }


  @Override
  public String getColumnName(int col) {
    return columnNames[col];
  }

  @Override
  public String getDisplayedColumnName(int col) {
    switch (col) {
      case GRANTEE:
        return "User";
      case CONTEXT:
        return "Context";
      case PERMISSIONS:
        return "Permissions";
      case TEXT:
        return "Remark";
      case ALLOW:
        return "allowed";
    }
    return "?";
  }

  /**
   * {@inheritDoc}
   * <p>
   * All cells are editable, except {@link #CONTEXT} which is only
   * editable if {@link #contextClasses} is not null.
   */
  @Override
  public boolean isCellEditable(int mColumn) {
    return mColumn != CONTEXT || contextClasses != null;
  }

  @Override
  public int getColumnCount() {
    return (columnNames.length);
  }

  @Override
  public Object getValueAt (int col)  {
    try {
      switch (col) {
        case GRANTEE:
          return getObject().getGrantee();
        case CONTEXT:
          return getObject().getDomainContextObject();
        case PERMISSIONS:
          return getObject().getPermissions();
        case TEXT:
          return getObject().getMessage();
        case ALLOW:
          return getObject().isAllowed();
      }
    } catch (Exception e) {
      LOGGER.warning("cannot retrieve value for column " + col, e);
    }
    return null;
  }

  @Override
  public void setValueAt (int col, Object obj)  {
    switch (col) {
      case GRANTEE:
        getObject().setGrantee((PersistentDomainObject) obj);
        break;
      case CONTEXT:
        getObject().setDomainContextObject((PersistentDomainObject) obj);
        break;
      case PERMISSIONS:
        getObject().setPermissions(((String) obj));
        break;
      case TEXT:
        getObject().setMessage((String) obj);
        break;
      case ALLOW:
        getObject().setAllowed(((Boolean) obj));
        break;
    }
  }




  @Override
  public TableCellRenderer getCellRenderer (int col)  {
    if (col == GRANTEE || col == CONTEXT) {   // grantee and context
      return new SecurityObjectCellRenderer();
    }
    return null;
  }


  @Override
  public TableCellEditor getCellEditor(int col)  {
    if (col == GRANTEE) {
      return new SecurityGranteeCellEditor();
    }
    if (col == CONTEXT) {
      return new SecurityContextCellEditor();
    }
    if (col == PERMISSIONS)  {
      return new SecurityPermissionCellEditor();
    }
    return null;    // default editor
  }




  // ---------------------- renderers ---------------------------------

  /**
   * special renderer for the object the rule applies to.
   * If the object is null, the text for "all" is displayed.
   */
  @SuppressWarnings("serial")
  private static class SecurityObjectCellRenderer extends FormTableCellRenderer {

    public SecurityObjectCellRenderer()  {
      setHorizontalAlignment(JLabel.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent (JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                    int row, int column)  {

      return super.getTableCellRendererComponent (table,
                  (value != null ? (((PersistentDomainObject)value).getSingular() + " " + value) : "all"),
                  isSelected, hasFocus, row, column);
    }
  }


  // ---------------------- editors ---------------------------------


  /**
   * Selects the grantee.
   * The default implementation invokes the {@link org.tentackle.swing.rdc.PdoSearchDialog}.
   * for the grantee class.
   *
   * @return the grantee, null if cancelled or no granteeclazz set
   */
  @SuppressWarnings("unchecked")
  public PersistentDomainObject selectGrantee() {
    return granteeClazz == null ? null :
           Rdc.createPdoSearchDialog(
                    getObject().getDomainContext(),
                    granteeClazz, (o) -> granteeClazz.isAssignableFrom(o.getClass()),
                    true, true).showDialog();
  }


  /**
   * Editor for the grantee.
   * Notice that the cell is only editable, if the granteeClazz is valid!
   */
  @SuppressWarnings("serial")
  private class SecurityGranteeCellEditor extends FormComponentCellEditor {

    private final FormButton button;
    private PersistentDomainObject rootObject;

    public SecurityGranteeCellEditor() {
      button = new FormButton();
      button.addActionListener((ActionEvent e) -> {
        try {
          PersistentDomainObject obj = selectGrantee();
          if (obj != null || FormQuestion.yesNo("free for all?")) {
            rootObject = obj;
          }
        }
        catch (Exception ex) {
          LOGGER.severe(ex.toString());
        }
        stopCellEditing();
      });
    }

    @Override
    public Object getCellEditorValue() {
      return rootObject;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                boolean isSelected,
                                                int row, int column) {
      rootObject = (PersistentDomainObject) value;
      button.setText(rootObject == null ? "all" : rootObject.toString());
      return button;
    }
  }



  @SuppressWarnings("serial")
  private class SecurityContextCellEditor extends FormComponentCellEditor  {

    private final FormButton button;
    private PersistentDomainObject contextObject;

    @SuppressWarnings("unchecked")
    public SecurityContextCellEditor()  {
      button = new FormButton();
      button.addActionListener((ActionEvent e) -> {
        try {
          PersistentDomainObject obj = Rdc.createPdoSearchDialog(SecurityTableEntry.this.getObject().getDomainContext(),
                  contextClasses[0], (o) -> {
                    for (Class cls : contextClasses) {
                      if (cls.equals(o.getClass())) {
                        return true;
                      }
                    }
                    return false;
                  }, true, true).showDialog();
          if (obj == null) {
            if (FormQuestion.yesNo("free for all?")) {
              contextObject = null;
            }
          }
          else  {
            contextObject = obj;
          }
        }
        catch (Exception ex)  {
          LOGGER.severe(ex.toString());
        }
        stopCellEditing();
      });
    }

    @Override
    public Object getCellEditorValue() {
      return contextObject;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      contextObject = (PersistentDomainObject) value;
      button.setText(contextObject == null ? "all" : contextObject.toString());
      return button;
    }
  }


  /**
   * Editor for permissions.
   */
  @SuppressWarnings("serial")
  private class SecurityPermissionCellEditor extends FormFieldComponentCellEditor {

    private final PermissionEditor permissionEditor;
    private int width;
    private Popup popup;

    public SecurityPermissionCellEditor() {
      StringFormField field = (StringFormField) getEditorComponent();
      field.setChangeable(false);   // not editable -> no focus lost event
      permissionEditor = new PermissionEditor() {
        @Override
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          size.width = width;
          return size;
        }
      };
      permissionEditor.setOpaque(true);
      permissionEditor.setBorder(BorderFactory.createRaisedSoftBevelBorder());
      permissionEditor.addActionListener((e) -> fireEditingStopped());
    }

    @Override
    public String getCellEditorValue() {
      return permissionEditor.getPermissions();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      permissionEditor.setPermissions((String) value);
      permissionEditor.saveValue();
      SecurityTableEntry entry = (SecurityTableEntry) ((FormTable) table).getEntryAt(row);
      permissionEditor.applyClazz(entry.getObject().getObjectClass());
      Point tLoc = table.getLocationOnScreen();
      Rectangle rect = table.getCellRect(row, column, false);
      width = rect.width;
      popup = PopupFactory.getSharedInstance().getPopup(table, permissionEditor, tLoc.x + rect.x, tLoc.y + rect.y);
      popup.show();
      return (Component) getEditorComponent();
    }

    @Override
    protected void fireEditingCanceled() {
      popup.hide();
      super.fireEditingCanceled();
    }

    @Override
    protected void fireEditingStopped() {
      popup.hide();
      super.fireEditingStopped();
    }

  }

}


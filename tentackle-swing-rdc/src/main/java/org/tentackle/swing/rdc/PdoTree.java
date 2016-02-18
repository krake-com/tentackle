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



package org.tentackle.swing.rdc;

import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.ListSelectionModel;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.permissions.EditPermission;
import org.tentackle.security.permissions.ViewPermission;
import org.tentackle.swing.FileTransferable;
import org.tentackle.swing.FormInfo;
import org.tentackle.swing.FormTree;
import org.tentackle.swing.FormUtilities;


/**
 * An extended {@link FormTree} that implements object navigation,
 * drag and drop, clipboard functionality for {@link PersistentDomainObject}s,
 * and provides a context-sensitive popup menu,
 *
 * @author harald
 */
public class PdoTree extends FormTree implements TreeWillExpandListener, DragSourceListener, DragGestureListener, DropTargetListener {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(PdoTree.class);

  private static final long serialVersionUID = -559339241973400671L;



  /**
   * Helper method for applications to get the object of the parent node.
   *
   * @param node the child node
   * @return the object of the parent node, null if none or no parent
   */
  public static Object getObjectInParentNode(DefaultMutableTreeNode node) {
    if (node != null) {
      DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
      if (parentNode != null) {
        Object object = parentNode.getUserObject();
        if (object instanceof PdoTreeObject)  {
          return ((PdoTreeObject)object).getObject();
        }
      }
    }
    return null;
  }



  /** true if popup allowed. */
  private boolean popupEnabled;

  /** default max. depth to show "extract path" button. */
  private int maxDepthForExtractPath;

  /** List of objects in tree. */
  private Collection<?> objCollection;



  /** current object for popup. */
  protected PersistentDomainObject popupObject;

  /** current GUI provider. */
  protected GuiProvider guiProvider;

  /** current node for popup. */
  protected DefaultMutableTreeNode popupNode;

  /** current path for popup. */
  protected TreePath popupPath;

  /** != null if object provides special editor. */
  protected Runnable openEditor;

  /** != null if usage toggle node available. */
  protected PdoTreeExtensionUsageToggleNode usageToggleNode;

  /** != null if usage menu item available. */
  protected JMenuItem usageMenuItem;

  /** optional togglenode menu items. */
  protected JMenuItem[] toggleItems;

  /** != null if sep. exists. */
  protected JSeparator toggleSeparator;

  /** additional popup menu items from TreeExtension. */
  protected JMenuItem[] extraItems;

  /** != null if sep. exists. */
  protected JSeparator extraSeparator;




  /**
   * Creates a tree.<br>
   * If the given object is a {@link Collection} the objects of the collection
   * will be shown in the tree. If it is some other object, only that
   * object is shown.<br>
   * Notice that the objects need not necessarily be {@link PersistentDomainObject}s.
   *
   * @param objects the objects, null if empty tree
   */
  public PdoTree(Collection<?> objects) {

    // don't use default DND! This would cause linux desktops to hang!
    setDragEnabled(false);

    maxDepthForExtractPath = 5;
    popupEnabled = true;
    addTreeWillExpandListener(this);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        checkPopup(e, true);
      }
      @Override
      public void mouseReleased(MouseEvent e) {
        checkPopup(e, true);
      }
    });

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e)  {
        int keyCode = e.getKeyCode();
        if (e.isControlDown())  {
          switch (keyCode) {
            case KeyEvent.VK_X:
            case KeyEvent.VK_Z:
            case KeyEvent.VK_N:
            case KeyEvent.VK_E:
            case KeyEvent.VK_O:
            case KeyEvent.VK_D:
            case KeyEvent.VK_C:
            case KeyEvent.VK_V:
            case KeyEvent.VK_U:
              checkPopup(e, false);   // determine items, but don't show menu
              if (keyCode == KeyEvent.VK_X && expandItem.isEnabled() && expandItem.isVisible())  {
                expandItem.doClick();
              }
              else if (keyCode == KeyEvent.VK_Z && collapseItem.isEnabled() && collapseItem.isVisible())  {
                collapseItem.doClick();
              }
              else if (keyCode == KeyEvent.VK_N && showItem.isEnabled() && showItem.isVisible())  {
                showItem.doClick();
              }
              else if (keyCode == KeyEvent.VK_E && editItem.isEnabled() && editItem.isVisible())  {
                editItem.doClick();
              }
              else if (keyCode == KeyEvent.VK_O && openItem.isEnabled() && openItem.isVisible())  {
                openItem.doClick();
              }
              else if (keyCode == KeyEvent.VK_D && deleteItem.isEnabled() && deleteItem.isVisible())  {
                deleteItem.doClick();
              }
              else if (keyCode == KeyEvent.VK_C && copyItem.isEnabled() && copyItem.isVisible())  {
                copyItem.doClick();
              }
              else if (keyCode == KeyEvent.VK_V && insertItem.isEnabled() && insertItem.isVisible())  {
                insertItem.doClick();
              }
          }
        }
        else if (keyCode == KeyEvent.VK_SPACE)  {
          checkPopup(e, true);
        }
      }
    });


    /**
     * override transferhandler
     */
    setTransferHandler(new TransferHandler() {

      private static final long serialVersionUID = 6532199703856836746L;

      @Override
      public int getSourceActions(JComponent c) {
        return COPY;
      }

      @Override
      @SuppressWarnings("unchecked")
      protected Transferable createTransferable(JComponent c) {
        TreePath[] paths = getSelectionPaths();
        if (paths != null)  {
          List<PersistentDomainObject> objectList = new ArrayList<>();
          for (TreePath path : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) (path.getLastPathComponent());
            Object obj = ((PdoTreeObject)node.getUserObject()).getObject();
            if (obj instanceof PersistentDomainObject) {
              objectList.add((PersistentDomainObject)obj);
            }
          }
          return new PdoTransferable(objectList);
        }
        // else: default implementation
        return super.createTransferable(c);
      }
    });


    initComponents();

    // setup cell renderers
    setCellRenderer(createDefaultRenderer());

    // default to single selection
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    // createPdo drag source
    DragSource dnd = DragSource.getDefaultDragSource();
    dnd.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, this);

    // createPdo drop target
    DropTarget target = new DropTarget (this, this);
    target.setDefaultActions(DnDConstants.ACTION_COPY_OR_MOVE);

    // build the tree
    buildTree(objects);

    // show tooltips of renderers
    // "unregister" not necessary cause registerComponent alters only "this",
    // no references in the ToolTipManager!
    ToolTipManager.sharedInstance().registerComponent(this);
  }


  /**
   * Creates a tree.<br>
   * If the given object is a {@link Collection} the objects of the collection
   * will be shown in the tree. If it is some other object, only that
   * object is shown.<br>
   * Notice that the objects need not necessarily be {@link PersistentDomainObject}s.
   *
   * @param object the object or collection of objects, null if empty tree
   */
  public PdoTree(Object object) {
    this(createCollectionFromObject(object));
  }

  /**
   * Creates an empty tree.
   */
  public PdoTree()  {
    this(null);
  }


  private static Collection<?> createCollectionFromObject(Object object) {
    Collection<Object> list = new ArrayList<>();
    list.add(object);
    return list;
  }


  /**
   * Gets the objects (first level) of this tree.
   *
   * @return the objects
   */
  public Collection<?> getObjects() {
    return objCollection;
  }


  /**
   * Creates the default tree cell renderer.<br>
   * The default implementation returns a {@link PdoTreeCellRenderer}.
   *
   * @return the renderer
   */
  public TreeCellRenderer createDefaultRenderer() {
    return new PdoTreeCellRenderer();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to render the domainkey if value is a PDO.<br>
   * Useful if the toString() method, for whatever reason, does not return
   * the domain key.
   */
  @Override
  public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (value instanceof PersistentDomainObject) {
      try {
        super.convertValueToText(((PersistentDomainObject) value).getUniqueDomainKey(), selected, expanded, leaf, row, hasFocus);
      }
      catch (RuntimeException ex) {
        // if anything went wrong, fall through into default implementation
      }
    }
    return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
  }



  /**
   * Enables/disables the popup-menu for nodes.
   *
   * @param enabled true to enable popup menus (default)
   */
  public void setPopupEnabled(boolean enabled)  {
    popupEnabled = enabled;
  }

  /**
   * Returns whether popup-menu for nodes are enabled.
   *
   * @return true if popup menus are enabled (default)
   */
  public boolean isPopupEnabled() {
    return popupEnabled;
  }


  /**
   * Sets the maximum treepath depth up to which the "extract path"-button
   * is displayed in the popupmenu. Default is 5.
   *
   * @param maxDepth the maximum path depth
   */
  public void setMaxDepthForExtractPath(int maxDepth) {
    this.maxDepthForExtractPath = maxDepth;
  }

  /**
   * Gets the maximum treepath depth up to which the "extract path"-button
   * is displayed in the popupmenu.
   *
   * @return the maximum path depth
   */
  public int getMaxDepthForExtractPath() {
    return maxDepthForExtractPath;
  }


  /**
   * Shows the popup menu at current selection.
   */
  public void showPopup() {
    checkPopup(null, true);
  }


  /**
   * Builds the tree from a collection of objects.
   *
   * @param col the collection, null to set the empty collection
   */
  @SuppressWarnings("unchecked")
  public void buildTree (Collection<?> col)  {

    this.objCollection = col == null ? new ArrayList<>() : col;

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();

    if (col != null) {
      for (Object obj: col) {
        if (obj != null)  {
          GuiProvider guiProvider = null;
          Object treeObject;
          if (obj instanceof PersistentDomainObject) {
            guiProvider = Rdc.createGuiProvider((PersistentDomainObject) obj);
            treeObject = guiProvider.getTreeRoot();    // replace by root-object, if not the same
          }
          else  {
            treeObject = obj;
          }
          DefaultMutableTreeNode node = new DefaultMutableTreeNode (new PdoTreeObject(treeObject, null));
          node.setAllowsChildren(
            (treeObject instanceof PdoTreeExtension && ((PdoTreeExtension) treeObject).allowsTreeChildObjects()) ||
            (guiProvider != null && guiProvider.allowsTreeChildObjects()));
          root.add(node);
        }
      }
    }

    DefaultTreeModel tModel = new DefaultTreeModel (root);
    tModel.setAsksAllowsChildren(true);
    setModel (tModel);
    putClientProperty("JTree.lineStyle", "Angled");
  }


  /**
   * Expands all items in this tree.
   *
   * @param maxLevel is the maximum number of levels to expand, 0 = all
   */
  public void expandTree(int maxLevel)  {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)(treeModel.getRoot());
    if (node != null) {
      doExpandPath(0, maxLevel, null, new TreePath(node));
    }
  }

  /**
   * Expands all items in this tree, unlimited.
   */
  public void expandTree()  {
    expandTree(0);
  }


  /**
   * Transfer the focus to the first item in tree.
   */
  public void requestFocusForFirstItem() {
    setSelectionRow(0);
    scrollRowToVisible(0);
    FormUtilities.getInstance().requestFocusLater(this);
  }


  /**
   * Checks whether the given object is appendable.<br>
   * An object is appendable if it is not null, not an PersistentDomainObject
   * or an PersistentDomainObject with granted read permission.
   *
   * @param childObject the object to append
   * @return true if appendable
   */
  public boolean isObjectAppendable(Object childObject)  {
    return childObject != null &&                       // don't append null objects
                      (!(childObject instanceof PersistentDomainObject) ||    // append if not an PersistentDomainObject
                        // if an PersistentDomainObject: append if read-permission is not denied
                        ((PersistentDomainObject)childObject).isPermissionAccepted(SecurityFactory.getInstance().getReadPermission()));
  }


  /**
   * Checks whether given object is in path (parents to root) or not.<br>
   * Used to detect recursion loops.
   *
   * @param childObject the object to check
   * @param node the node to start the search up to the root
   * @return true if object is in already in path
   */
  public boolean isObjectInParents(Object childObject, DefaultMutableTreeNode node)  {
    while (node != null) {
      // check if childobject is already part of the path (avoids recursion and confusion of user)
      Object pathObject = node.getUserObject();
      if (pathObject instanceof PdoTreeObject &&
          ((PdoTreeObject)pathObject).getObject() != null &&
          ((PdoTreeObject)pathObject).getObject().equals(childObject))  {
        return true; // already in this path
      }
      node = (DefaultMutableTreeNode)node.getParent();
    }
    return false;
  }


  /**
   * Checks whether given object is in some of the child paths down to the leafs.<br>
   * Used to detect recursion loops.
   *
   * @param childObject the object to check
   * @param node the node to start the search up to the root
   * @return true if object is in already in path
   */
  public boolean isObjectInChilds(Object childObject, DefaultMutableTreeNode node)  {
    if (childObject != null && node != null)  {
      Object pathObject = node.getUserObject();
      if (pathObject instanceof PdoTreeObject &&
          ((PdoTreeObject)pathObject).getObject() != null &&
          ((PdoTreeObject)pathObject).getObject().equals(childObject))  {
        return true;
      }
      Enumeration<?> ce = node.children();
      while (ce.hasMoreElements())  {
        if (isObjectInChilds(childObject, ((DefaultMutableTreeNode) ce.nextElement()))) {
          return true;
        }
      }
    }
    return false;
  }


  /**
   * Recursively expands the path.
   *
   * @param level is the current tree level, 0 = top
   * @param maxLevel is the maximum level not to exceed, 0 = unlimited
   * @param stopObject stops expansion if object met, null = unlimited
   * @param path is the path to expand
   */
  public void doExpandPath(int level, int maxLevel, PersistentDomainObject<?> stopObject, TreePath path)  {

    // expand this path
    expandPath(path);

    if (maxLevel == 0 || level + 1 < maxLevel)  {
      // expand all other objects
      DefaultMutableTreeNode  node     = (DefaultMutableTreeNode) path.getLastPathComponent();
      Enumeration<?>          children = node.children();

      while (children.hasMoreElements())  {
        node = (DefaultMutableTreeNode)(children.nextElement());
        if (node.getUserObject() instanceof PdoTreeObject)  {
          PdoTreeObject userObject = (PdoTreeObject)node.getUserObject();
          if ((level > 0 && userObject.isStopExpandPath()) ||
              (stopObject != null && userObject.getObject().equals(stopObject))) {
            continue;
          }
        }
        // add node, it's new
        if (node.getAllowsChildren()) {
          // recursively expand
          doExpandPath(level + 1, maxLevel, stopObject, new TreePath(node.getPath()));
        }
      }
    }
  }


  /**
   * Collapses a given path.<br>
   * The method invokes {@code collapsePath} and set {@code expanded=false}
   * in all {@link PdoTreeObject}-nodes. Furthermore, all nodes
   * referring to {@link PdoTreeToggleNodeObject}s will get their childs removed.
   * Tentackle applications should not use {@code collapsePath} directly.
   *
   * @param path the tree path to collapse
   * @see javax.swing.JTree#collapsePath(javax.swing.tree.TreePath)
   */
  public void doCollapsePath(TreePath path)  {
    collapsePath(path);
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)(path.getLastPathComponent());
    PdoTreeObject tobj = (PdoTreeObject)(node.getUserObject());
    tobj.setExpanded(false);
    if (!(tobj instanceof PdoTreeToggleNodeObject))  {
      node.removeAllChildren();
    }
    ((DefaultTreeModel)treeModel).reload(node);
  }


  /**
   * Checks whether a path contains only {@link PersistentDomainObject}s.
   *
   * @param path the tree path
   * @return true if only {@link PersistentDomainObject}s.
   */
  public boolean pathConsistsOfPdos(TreePath path) {
    int depth = path.getPathCount();
    for (int i = 1; i < depth; i++) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getPathComponent(i);
      if (!(((PdoTreeObject)node.getUserObject()).getObject() instanceof PersistentDomainObject)) {
        return false;
      }
    }
    return true;
  }





  // ------------- implements TreeWillExpandListener --------------------------

  /**
   * {@inheritDoc}
   * <p>
   * Loads child objects from the database.
   * @param e the expansion event
   * @throws javax.swing.tree.ExpandVetoException if expanding not allowed
   */
  @Override
  @SuppressWarnings("unchecked")
  public void treeWillExpand (TreeExpansionEvent e) throws ExpandVetoException {

    // get node which will be expanded
    TreePath path = e.getPath();
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)(path.getLastPathComponent());

    if (node != null) {

      PdoTreeObject tobj = (PdoTreeObject)(node.getUserObject());

      if (tobj != null && !tobj.isExpanded()) {

        if (tobj.isStopTreeWillExpand()) {
          tobj.setStopTreeWillExpand(false);
          throw new ExpandVetoException(e);
        }

        // load child objects
        List<Object> childList = null;

        Object object = tobj.getObject();

        FormUtilities.getInstance().setWaitCursor(this);

        if (object instanceof PdoTreeExtension)  {
          childList = ((PdoTreeExtension)object).getTreeChildObjects(tobj.getParentObject());
        }

        if (object instanceof PersistentDomainObject)  {
          childList = (List<Object>) Rdc.createGuiProvider((PersistentDomainObject) object).getTreeChildObjects(tobj.getParentObject());
        }

        if (childList != null)  {
          for (Object obj: childList)  {
            if (isObjectAppendable(obj))  {
              PdoTreeObject to = new PdoTreeObject(obj, object);
              to.setStopTreeWillExpand(isObjectInParents(obj, node));
              DefaultMutableTreeNode childnode = new DefaultMutableTreeNode(to);
              childnode.setAllowsChildren(
                (obj instanceof PdoTreeExtension && ((PdoTreeExtension)obj).allowsTreeChildObjects()) ||
                (obj instanceof PersistentDomainObject && Rdc.createGuiProvider((PersistentDomainObject)obj).allowsTreeChildObjects()));
              ((DefaultTreeModel)treeModel).insertNodeInto(childnode, node, node.getChildCount());
            }
          }
        }

        FormUtilities.getInstance().setDefaultCursor(this);

        // mark it expanded
        tobj.setExpanded(true);
      }
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing.
   * Applications may override.
   *
   * @param e the expansion event
   */
  @Override
  public void treeWillCollapse (TreeExpansionEvent e) {}



  // ------------------ implements DragGestureListener interface ---------------------

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation creates the transferable and starts the drag
   * if the node refers to an {@link PersistentDomainObject} or {@link PdoTreeExtension}.
   *
   * @param event the gesture event
   */
  @Override
  @SuppressWarnings("unchecked")
  public void dragGestureRecognized (DragGestureEvent event) {
    PersistentDomainObject dragObject = null;
    TreePath path = getSelectionPath();
    if (path != null)  {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)(path.getLastPathComponent());
      Object obj = ((PdoTreeObject)node.getUserObject()).getObject();
      Transferable tr = null;
      if (obj instanceof PdoTreeExtension) {
        tr = ((PdoTreeExtension)obj).getTransferable();
      }
      if (tr == null && obj instanceof PersistentDomainObject) {
        dragObject = (PersistentDomainObject) obj;
        tr         = Rdc.createGuiProvider(dragObject).getTransferable();
      }
      // start the drag
      if (tr instanceof FileTransferable) {
        event.startDrag(event.getDragAction() == DnDConstants.ACTION_MOVE ?
                            DragSource.DefaultMoveDrop : DragSource.DefaultCopyDrop, tr, this);
      }
      else if (tr != null)  {
        event.startDrag (null, tr, this);
      }
    }
  }



  // ------------- implements DragSourceListener interface ---------------

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing. Provided to be overridden.
   * @param event the drag source event
   */
  @Override
  public void dragEnter (DragSourceDragEvent event) {
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing. Provided to be overridden.
   * @param event the drag source event
   */
  @Override
  public void dragOver (DragSourceDragEvent event)  {
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing. Provided to be overridden.
   * @param event the drag source event
   */
  @Override
  public void dragExit (DragSourceEvent event)  {
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing. Provided to be overridden.
   * @param event the drag source event
   */
  @Override
  public void dropActionChanged (DragSourceDragEvent event) {
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing. Provided to be overridden.
   * @param event the drag source event
   */
  @Override
  public void dragDropEnd (DragSourceDropEvent event) {
  }



  // ----------------------- implements the DropTargetListener: -----------------------

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing. Provided to be overridden.
   * @param dtde the drop target drag event
   */
  @Override
  public void dragEnter(DropTargetDragEvent dtde) {
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to check whether to accept the drag or not.
   *
   * @param dtde the drop target drag event
   */
  @Override
  @SuppressWarnings("unchecked")
  public void dragOver(DropTargetDragEvent dtde) {
    Point p = dtde.getLocation();
    TreePath path = getPathForLocation(p.x, p.y);
    if (path != null)  {
      DefaultMutableTreeNode node  = (DefaultMutableTreeNode)(path.getLastPathComponent());
      PdoTreeObject mto = (PdoTreeObject)(node.getUserObject());
      Object obj = mto.getObject();
      if (obj instanceof PersistentDomainObject)  {
        Object transData = ((PersistentDomainObject)obj).getTransientData();
        if (!((PersistentDomainObject)obj).isNew())  {
          obj = ((PersistentDomainObject)obj).reload();   // reload it to check whether deleted in the meantime
        }
        if (obj != null)  {
          ((PersistentDomainObject) obj).setTransientData(transData);
          mto.setObject(obj);   // update in case changed
          if (Rdc.createGuiProvider((PersistentDomainObject) obj).allowsTreeChildObjects())  {
            setSelectionPath(path);
            popupPath = path;
            popupObject = (PersistentDomainObject)obj;
            popupNode = node;
            dtde.acceptDrag (dtde.getDropAction());
            return;
          }
        }
      }
    }
    // nicht akzeptieren!
    clearSelection();
    popupPath = null;
    popupObject = null;
    popupNode = null;
    dtde.rejectDrag();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing. Provided to be overridden.
   * @param dtde the drop target drag event
   */
  @Override
  public void dropActionChanged(DropTargetDragEvent dtde) {
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing. Provided to be overridden.
   * @param dte the drop target event
   */
  @Override
  public void dragExit(DropTargetEvent dte) {
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to insert the object.
   * @param dtde the drop target drop event
   * @see GuiProvider#dropTransferable(java.awt.datatransfer.Transferable)
   */
  @Override
  public void drop(DropTargetDropEvent dtde) {
    try {
      Transferable tr = dtde.getTransferable();
      insertDndOrCb(tr, dtde);
    }
    catch (UnsupportedFlavorException | IOException e) {
      LOGGER.severe(e.toString());
    }
  }





  /**
   * Hides the "in use" tree.
   *
   * @param the child index of the node to be remove from the parent
   * @see PdoTreeExtensionUsageToggleNode
   */
  void hideInUseTree(int childIndex) {
    ((DefaultTreeModel)treeModel).removeNodeFromParent((DefaultMutableTreeNode)popupNode.getChildAt(childIndex));
  }

  /**
   * Shows the "in use" tree.
   * (package scope!)
   * @see PdoTreeExtensionUsageToggleNode
   */
  void showInUseTree()  {

    FormUtilities.getInstance().setWaitCursor(this);

    // build the "root" parents, i.e. all unique parent with no more
    List<DefaultMutableTreeNode> rootNodes = new ArrayList<>();
    // TreeParentObjects.
    @SuppressWarnings("unchecked")
    List<Object> parentList = (List<Object>) Rdc.createGuiProvider(popupObject).getTreeParentObjects(((PdoTreeObject) popupNode.getUserObject()).getParentObject());
    if (parentList != null && parentList.size() > 0)  {
      for (Object obj: parentList) {
        if (obj instanceof PersistentDomainObject) {
          // build new path reverse from object to root
          addTreeParentRootNodes((PersistentDomainObject)obj, new DefaultMutableTreeNode(new PdoTreeObject(popupObject, null)), rootNodes);
        }
      }
    }
    // add rootnodes
    if (rootNodes.size() > 0) {
      DefaultMutableTreeNode inUseNode = new DefaultMutableTreeNode(usageToggleNode.getToggleNodeObject(popupObject));
      for (DefaultMutableTreeNode node: rootNodes)  {
        Enumeration<?> ce = inUseNode.children();
        DefaultMutableTreeNode insNode = node;    // node to insert
        while (ce.hasMoreElements())  {
          DefaultMutableTreeNode childnode = (DefaultMutableTreeNode)ce.nextElement();
          if (childnode.getUserObject().equals(node.getUserObject())) {
            // merge childs (can be only one!!!)
            if (node.getChildCount() > 0) {
              DefaultMutableTreeNode firstChild = (DefaultMutableTreeNode)node.getChildAt(0);
              childnode.add(firstChild);
              insNode = null;
              break;
            }
          }
        }
        if (insNode != null) {
          inUseNode.add(insNode);
        }
      }
      ((DefaultTreeModel) treeModel).insertNodeInto(inUseNode, popupNode, 0);
      doExpandPath(0, 1, null, popupPath);    // expand one level
      TreePath inUsePath = popupPath.pathByAddingChild(inUseNode);
      doExpandPath(0, 0, popupObject, inUsePath);   // expand inUseNode recursively up to popupObject
      Enumeration<?> ce = inUseNode.children();
      while (ce.hasMoreElements())  {
        collapsePath(inUsePath.pathByAddingChild(ce.nextElement()));
      }

      FormUtilities.getInstance().setDefaultCursor(this);
    }

    else  {
      FormUtilities.getInstance().setDefaultCursor(this);
      FormInfo.show(RdcSwingRdcBundle.getString("NO REFERENCES FOUND"));
    }
  }




  /**
   * Inserts a transferable.
   * Same for clipboard and DnD operations.
   */
  @SuppressWarnings("unchecked")
  private boolean insertDndOrCb(Transferable tr, DropTargetDropEvent dtde) throws UnsupportedFlavorException, IOException {

    if (popupPath != null && popupNode != null &&
       // check if we can drop on that target, i.e. target is writable
              popupObject.isPermissionAccepted(SecurityFactory.getInstance().getWritePermission())) {

      if (dtde != null) {
        dtde.acceptDrop (dtde.getDropAction());
      }

      if (Rdc.createGuiProvider(popupObject).dropTransferable(tr)) {
        // show changes
        if (((PdoTreeObject)popupNode.getUserObject()).isExpanded())  {
          // already expanded: collapse first and then refresh
          doCollapsePath(popupPath);
        }
        doExpandPath(0, 1, null, popupPath);    // recursively expand one level
        if (dtde != null) {
          dtde.dropComplete(true);
        }
        return true;
      }
      else  {
        if (dtde != null) {
          dtde.dropComplete(false);
        }
      }
    }
    return false;
  }


  /**
   * Adds tree parents "subtrees" as nodes.
   *
   * @param parentObj the parent object
   * @param childNode the node
   * @param rootNodes the parent subtrees to add
   */
  private void addTreeParentRootNodes(PersistentDomainObject parentObj, DefaultMutableTreeNode childNode, List<DefaultMutableTreeNode> rootNodes) {
    // end of path is always current object
    while (isObjectAppendable(parentObj)) {
      PdoTreeObject tobj = new PdoTreeObject(parentObj, null);
      tobj.setExpanded(true);
      DefaultMutableTreeNode parentNode = new DefaultMutableTreeNode(tobj);
      parentNode.add(childNode);
      if (!isObjectInChilds(parentObj, childNode))  {
        @SuppressWarnings("unchecked")
        Collection p = Rdc.createGuiProvider(parentObj).getTreeParentObjects(tobj.getParentObject());
        if (p != null && !p.isEmpty())  {
          if (p.size() > 1) {
            // more than one parent: continue for each on a copy of the path so far
            for (Object obj: p)  {
              if (obj instanceof PersistentDomainObject) {
                // duplicate path starting at childnode
                addTreeParentRootNodes((PersistentDomainObject) obj, duplicateTreeNode(parentNode), rootNodes);
              }
            }
          }
          // continue with single/first parent node
          childNode = parentNode;
          Object obj = p.iterator().next();
          if (obj instanceof PersistentDomainObject) {
            parentObj = (PersistentDomainObject)obj;
            continue;
          }
        }
      }
      // root reached
      rootNodes.add(parentNode);

      break;
    }
  }


  /**
   * Checks whether to display the popup and displays it.
   *
   * @param e the input event (key press, mouse, etc...)
   * @param show true if show, else check only (used to determine some local vars)
   */
  @SuppressWarnings("unchecked")
  private void checkPopup(InputEvent e, boolean show) {

    if (popupEnabled) {   // usually right mouse button pressed

      Point p = null;

      if (e instanceof MouseEvent)  {
        if (!((MouseEvent) e).isPopupTrigger()) {
          return; // do nothing
        }
        p = ((MouseEvent)e).getPoint();
        popupPath = getPathForLocation(p.x, p.y);
      }
      else {  // KeyEvent
        popupPath = this.getSelectionPath();
        if (popupPath != null)  {
          Rectangle r = this.getPathBounds(popupPath);
          if (r != null)  {
            p = new Point(r.x + r.width/2, r.y + r.height/2);
          }
          else  {
            popupPath = null;
          }
        }
      }

      if (show) {
        // remove old toggle- and extra items if any
        if (usageMenuItem != null)  {
          popupMenu.remove(usageMenuItem);
          usageMenuItem = null;
        }
        if (toggleItems != null) {
          for (JMenuItem toggleItem : toggleItems) {
            popupMenu.remove(toggleItem);
          }
          toggleItems = null;
        }
        if (toggleSeparator != null) {
          popupMenu.remove(toggleSeparator);
          toggleSeparator = null;
        }
        if (extraItems != null) {
          for (JMenuItem extraItem : extraItems) {
            popupMenu.remove(extraItem);
          }
          extraItems = null;
        }
        if (extraSeparator != null) {
          popupMenu.remove(extraSeparator);
          extraSeparator = null;
        }
      }

      if (popupPath != null && p != null)  {

        popupNode  = (DefaultMutableTreeNode)(popupPath.getLastPathComponent());
        PdoTreeObject mto = (PdoTreeObject)(popupNode.getUserObject());
        Object obj = mto.getObject();

        selectAllItem.setVisible(getSelectionModel().getSelectionMode() != ListSelectionModel.SINGLE_SELECTION);

        if (popupNode.getAllowsChildren())  {
          expandItem.setText(RdcSwingRdcBundle.getString("EXPAND"));
          if (mto.isExpanded()) {
            expandItem.setEnabled(true);
            expandItem.setText(RdcSwingRdcBundle.getString("EXPAND_AGAIN"));
            collapseItem.setEnabled(true);
          }
          else  {
            expandItem.setEnabled(true);
            collapseItem.setEnabled(false);
          }
        } else  {
          expandItem.setEnabled(false);
          collapseItem.setEnabled(false);
        }


        openEditor = null;
        // enable open?, extra items?
        if (obj instanceof PdoTreeExtension) {
          openEditor = ((PdoTreeExtension)obj).getOpenEditor();
          if (show) {
            extraItems = ((PdoTreeExtension)obj).getExtraMenuItems(this, popupNode);
            if (extraItems != null && extraItems.length > 0)  {
              extraSeparator = new JSeparator();
              popupMenu.add(extraSeparator);
              for (JMenuItem extraItem : extraItems) {
                popupMenu.add(extraItem);
              }
            }

            PdoTreeExtensionToggleNode[] toggleNodes = ((PdoTreeExtension)obj).getToggleNodes(this, popupNode);
            if (toggleNodes != null)  {
              toggleItems = new JMenuItem[toggleNodes.length];
              for (int i=0; i < toggleNodes.length; i++) {
                toggleItems[i] = toggleNodes[i].getMenuItem(this, popupNode, popupPath);
              }
              if (toggleItems.length > 0)  {
                toggleSeparator = new JSeparator();
                popupMenu.add(toggleSeparator, 0);
                for (JMenuItem toggleItem : toggleItems) {
                  popupMenu.add(toggleItem, 0);
                }
              }
            }
          }
        }
        openItem.setVisible(openEditor != null);

        subTreeItem.setVisible(false);
        makeTableItem.setVisible(false);

        boolean editAllowed = false;
        boolean viewAllowed = false;

        if (obj instanceof PersistentDomainObject) {

          Object transData = ((PersistentDomainObject)obj).getTransientData();

          if (!FormUtilities.getInstance().isParentWindowModal(this))  {
            int depth = popupPath.getPathCount() - 1;
            subTreeItem.setVisible(depth > 1 || objCollection.size() > 1);
            if (depth > 1) {
              makeTableItem.setText(MessageFormat.format(RdcSwingRdcBundle.getString("CREATE TABLE"), ((PersistentDomainObject)obj).getPlural()));
              makeTableItem.setVisible(true);
            }
          }

          if (((PersistentDomainObject) obj).isNew())  {
            popupObject = (PersistentDomainObject) obj;
            copyItem.setEnabled(false);
            insertItem.setEnabled(false);
          }
          else  {
            popupObject = ((PersistentDomainObject) obj).reload();   // reload for sure
            copyItem.setEnabled(true);
            insertItem.setEnabled(true);
          }

          mto.setObject(popupObject);

          if (popupObject == null) {
            return; // vanished (i.e. someone deleted the object in the meantime)
          }

          popupObject.setTransientData(transData);
          obj = popupObject;

          guiProvider = Rdc.createGuiProvider(popupObject);
          if (guiProvider.allowsTreeParentObjects())  {
            if (toggleSeparator == null) {
              toggleSeparator = new JSeparator();
              popupMenu.add(toggleSeparator, 0);
            }
            usageToggleNode = new PdoTreeExtensionUsageToggleNode();
            usageMenuItem = usageToggleNode.getMenuItem(this, popupNode, popupPath);
            popupMenu.add(usageMenuItem, 0);
          }
          else  {
            usageToggleNode = null;
          }

          // check permission of object
          viewAllowed = popupObject.isPermissionAccepted(SecurityFactory.getInstance().getPermission(ViewPermission.class));
          editAllowed = viewAllowed && popupObject.isPermissionAccepted(SecurityFactory.getInstance().getPermission(EditPermission.class));

          if (Rdc.createGuiProvider(popupObject).panelExists() &&
              popupObject.isPermissionAccepted(SecurityFactory.getInstance().getReadPermission()))  {
            editItem.setVisible(true);
            editItem.setEnabled(editAllowed);
            showItem.setVisible(true);
            showItem.setEnabled(viewAllowed);
          }
          else  {
            editItem.setVisible(false);
            showItem.setVisible(false);
          }

          // refresh display (bec. of reload())
          ((DefaultTreeModel)treeModel).nodeChanged(popupNode);
          // Objekt erneut selektieren
          setSelectionPath(popupPath);
        }

        else  {
          copyItem.setEnabled(false);
          insertItem.setEnabled(false);
          editItem.setVisible(false);
          showItem.setVisible(false);
        }

        // enable delete?
        boolean deleteAllowed = editAllowed;
        if (deleteAllowed) {
          if (obj != null && !((PersistentDomainObject) obj).isRemovable() ||
              obj instanceof PdoTreeExtension && !((PdoTreeExtension) obj).isRemovable()) {
            deleteAllowed = false;
          }
        }
        deleteItem.setEnabled(deleteAllowed);

        // show popup menu
        if (show) {
          popupMenu.show(this, p.x, p.y);
        }
      }
    }
  }


  /**
   * Duplicates a node and all its childnodes.
   */
  private DefaultMutableTreeNode duplicateTreeNode(DefaultMutableTreeNode node) {
    DefaultMutableTreeNode parent = new DefaultMutableTreeNode(node.getUserObject(), node.getAllowsChildren());
    for (int i=0; i < node.getChildCount(); i++)  {
      parent.add(duplicateTreeNode((DefaultMutableTreeNode)node.getChildAt(i)));
    }
    return parent;
  }



  /**
   * Finds the treenode for an object which is part of the displayed collection.<br>
   *
   * @param object the object
   * @return the child of the root node if found, null if no such node
   */
  public DefaultMutableTreeNode findNodeInCollection(Object object) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
    // find object in childnodes
    Enumeration<?> childs = root.children();
    while (childs.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) childs.nextElement();
      if (node.getUserObject() instanceof PdoTreeObject &&
          ((PdoTreeObject)node.getUserObject()).getObject().equals(object)) {
        return node;
      }
    }
    return null;
  }



  /**
   * Find the treepath for an object which is part of the displayed collection.<br>
   * @param object the object
   * @return the path to the child of the root node if found, null if no such node
   */
  public TreePath findPathInCollection(Object object) {
    DefaultMutableTreeNode node = findNodeInCollection(object);
    if (node != null) {
      return new TreePath(new Object[] { getModel().getRoot(), node });
    }
    return null;
  }


  /**
   * Collapses all childnodes of the root.
   */
  public void collapseAll() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
    // find object in childnodes
    Enumeration<?> childs = root.children();
    while (childs.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) childs.nextElement();
      collapsePath(new TreePath(new Object[] { getModel().getRoot(), node }));
    }
    TreePath path = getSelectionPath();   // gets the first selection, if any
    if (path != null) {
      scrollPathToVisible(path);
    }
  }


  /**
   * Refreshes the view of the current node.
   */
  public void refreshCurrentNode() {
    if (popupNode != null && popupPath != null) {
      PdoTreeObject treeObject = (PdoTreeObject) popupNode.getUserObject();
      if (treeObject.isExpanded()) {
        doCollapsePath(popupPath);
        doExpandPath(0, 1, null, popupPath);
      }

      // refresh display
      ((DefaultTreeModel) treeModel).nodeChanged(popupNode);
    }
  }


  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    popupMenu = new javax.swing.JPopupMenu();
    expandItem = new javax.swing.JMenuItem();
    collapseItem = new javax.swing.JMenuItem();
    subTreeItem = new javax.swing.JMenuItem();
    makeTableItem = new javax.swing.JMenuItem();
    showItem = new javax.swing.JMenuItem();
    editItem = new javax.swing.JMenuItem();
    openItem = new javax.swing.JMenuItem();
    deleteItem = new javax.swing.JMenuItem();
    copyItem = new javax.swing.JMenuItem();
    selectAllItem = new javax.swing.JMenuItem();
    insertItem = new javax.swing.JMenuItem();

    popupMenu.setName("popupMenu"); // NOI18N

    expandItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_MASK));
    expandItem.setText(RdcSwingRdcBundle.getString("EXPAND")); // NOI18N
    expandItem.setName("expand"); // NOI18N
    expandItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        expandItemActionPerformed(evt);
      }
    });
    popupMenu.add(expandItem);

    collapseItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_MASK));
    collapseItem.setText(RdcSwingRdcBundle.getString("COLLAPSE")); // NOI18N
    collapseItem.setName("collapse"); // NOI18N
    collapseItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        collapseItemActionPerformed(evt);
      }
    });
    popupMenu.add(collapseItem);

    subTreeItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
    subTreeItem.setText(RdcSwingRdcBundle.getString("SUBTREE")); // NOI18N
    subTreeItem.setName("subTree"); // NOI18N
    subTreeItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        subTreeItemActionPerformed(evt);
      }
    });
    popupMenu.add(subTreeItem);

    makeTableItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, java.awt.event.InputEvent.CTRL_MASK));
    makeTableItem.setText("create table"); // NOI18N
    makeTableItem.setName("makeTable"); // NOI18N
    makeTableItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        makeTableItemActionPerformed(evt);
      }
    });
    popupMenu.add(makeTableItem);

    showItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
    showItem.setText(RdcSwingRdcBundle.getString("VIEW")); // NOI18N
    showItem.setName("show"); // NOI18N
    showItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        showItemActionPerformed(evt);
      }
    });
    popupMenu.add(showItem);

    editItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, java.awt.event.InputEvent.CTRL_MASK));
    editItem.setText(RdcSwingRdcBundle.getString("EDIT")); // NOI18N
    editItem.setName("edit"); // NOI18N
    editItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        editItemActionPerformed(evt);
      }
    });
    popupMenu.add(editItem);

    openItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
    openItem.setText(RdcSwingRdcBundle.getString("OPEN")); // NOI18N
    openItem.setName("open"); // NOI18N
    openItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        openItemActionPerformed(evt);
      }
    });
    popupMenu.add(openItem);

    deleteItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.CTRL_MASK));
    deleteItem.setText(RdcSwingRdcBundle.getString("DELETE")); // NOI18N
    deleteItem.setName("delete"); // NOI18N
    deleteItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteItemActionPerformed(evt);
      }
    });
    popupMenu.add(deleteItem);

    copyItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_MASK));
    copyItem.setText(RdcSwingRdcBundle.getString("COPY")); // NOI18N
    copyItem.setName("copy"); // NOI18N
    copyItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        copyItemActionPerformed(evt);
      }
    });
    popupMenu.add(copyItem);

    selectAllItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_MASK));
    selectAllItem.setText(RdcSwingRdcBundle.getString("SELECT ALL")); // NOI18N
    selectAllItem.setName("select"); // NOI18N
    selectAllItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        selectAllItemActionPerformed(evt);
      }
    });
    popupMenu.add(selectAllItem);

    insertItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_MASK));
    insertItem.setText(RdcSwingRdcBundle.getString("INSERT")); // NOI18N
    insertItem.setName("insert"); // NOI18N
    insertItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        insertItemActionPerformed(evt);
      }
    });
    popupMenu.add(insertItem);

    setRootVisible(false);
  }// </editor-fold>//GEN-END:initComponents

  private void makeTableItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_makeTableItemActionPerformed
    // createPdo a table from childobjects of given type
    List<PersistentDomainObject> list = new ArrayList<>();
    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) popupPath.getParentPath().getLastPathComponent();
    int childNum = parentNode.getChildCount();
    for (int childIndex=0; childIndex < childNum; childIndex++) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)parentNode.getChildAt(childIndex);
      PdoTreeObject to = (PdoTreeObject)childNode.getUserObject();
      if (to.getObjectClass() == popupObject.getEffectiveClass()) {
        list.add((PersistentDomainObject)to.getObject());
      }
    }
    if (!list.isEmpty()) {
      @SuppressWarnings("unchecked")
      PdoNavigationDialog d = Rdc.createPdoNavigationDialog(list, null, true);
      d.setTitle(popupObject.getPlural());
      d.getNaviPanel().getNaviTable().getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      d.setVisible(true);
    }
  }//GEN-LAST:event_makeTableItemActionPerformed

  private void subTreeItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subTreeItemActionPerformed
    PersistentDomainObject dbObject = popupObject;
    @SuppressWarnings("unchecked")
    PdoNavigationDialog d = Rdc.createPdoNavigationDialog(dbObject, null);
    d.setTitle(dbObject.getSingular() + " " + dbObject);
    d.getNaviPanel().getNaviTree().getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    d.getNaviPanel().getNaviTree().expandTree(2);
    d.setVisible(true);
  }//GEN-LAST:event_subTreeItemActionPerformed

  private void insertItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_insertItemActionPerformed
    try {
      Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
      Transferable trans = clip.getContents(this);
      insertDndOrCb(trans, null);
    }
    catch (HeadlessException | UnsupportedFlavorException | IOException e) {
      LOGGER.logStacktrace(Level.WARNING, e);
      FormInfo.show(RdcSwingRdcBundle.getString("COULDN'T INSERT"));
    }
  }//GEN-LAST:event_insertItemActionPerformed

  private void selectAllItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllItemActionPerformed
    this.setSelectionInterval(0, getRowCount());
  }//GEN-LAST:event_selectAllItemActionPerformed

  private void copyItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyItemActionPerformed
    try {
      Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
      @SuppressWarnings("unchecked")
      PdoTransferable trans = new PdoTransferable(popupObject);
      clip.setContents(trans, trans);
    }
    catch (RuntimeException e) {
      LOGGER.logStacktrace(Level.WARNING, e);
      // nothing to do
    }
  }//GEN-LAST:event_copyItemActionPerformed

  private void openItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openItemActionPerformed
    if (openEditor != null) {
      openEditor.run();
    }
  }//GEN-LAST:event_openItemActionPerformed

  private void deleteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemActionPerformed
    if (PdoEditDialogPool.getInstance().delete(popupObject)) {
      ((DefaultTreeModel)treeModel).removeNodeFromParent(popupNode);
    }
  }//GEN-LAST:event_deleteItemActionPerformed

  @SuppressWarnings("unchecked")
  private void editItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editItemActionPerformed

    if (popupObject instanceof PdoTreeExtension)  {
      PdoTreeExtensionEditor editor = ((PdoTreeExtension)popupObject).getEditor();
      if (editor != null) {
        // edit object modal
        editor.showEditor(this, popupNode, true, false);
        return;
      }
    }

    if (FormUtilities.getInstance().isParentWindowModal(this)) {
      // edit modal if parent is modal too
      if (PdoEditDialogPool.getInstance().editModal(popupObject) == null) {
        // cancelled
        return;
      }
      refreshCurrentNode();
    }
    else  {
      // non-modal (with close on save or delete)
      final PdoEditDialog d = PdoEditDialogPool.getInstance().useNonModalDialog(popupObject, true, true);
      if (objCollection instanceof List && popupPath.getPathCount() == 2) {
        d.setLinkedPdoList((List) objCollection);
      }

      d.addActionListener(e -> {
        switch (e.getActionCommand()) {
          case PdoEditDialog.ACTION_SAVE:
            {
              PersistentDomainObject obj = d.getLastPdo();
              // reload to get the real data stored in DB (in case of CANCEL)
              if (!obj.isNew()) {
                obj = obj.reload();
              }
              if (obj != null)  {
                DefaultMutableTreeNode node = findNodeInCollection(obj);
                if (node != null) {
                  ((PdoTreeObject)node.getUserObject()).setObject(obj);
                  ((DefaultTreeModel)treeModel).nodeChanged(node);  // refresh display
                }
              }
              break;
            }
          case PdoEditDialog.ACTION_DELETE:
            {
              PersistentDomainObject obj = d.getLastPdo();
              if (obj != null)  {
                DefaultMutableTreeNode node = findNodeInCollection(obj);
                if (node != null) {
                  ((DefaultTreeModel)treeModel).removeNodeFromParent(node);  // remove node
                }
              }
              break;
            }
          case PdoEditDialog.ACTION_PREVIOUS:
          case PdoEditDialog.ACTION_NEXT:
            TreePath path = findPathInCollection(d.getPdo());
            if (path != null) {
              scrollPathToVisible(path);
              setSelectionPath(path);
            }
            break;
        }
      });
    }
  }//GEN-LAST:event_editItemActionPerformed

  @SuppressWarnings("unchecked")
  private void showItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showItemActionPerformed
    // show objects only, always non-modal
    if (popupObject instanceof PdoTreeExtension)  {
      PdoTreeExtensionEditor editor = ((PdoTreeExtension)popupObject).getEditor();
      if (editor != null) {
        // call it non-modal
        editor.showEditor(this, popupNode, false, true);
        return;
      }
    }
    if (FormUtilities.getInstance().isParentWindowModal(this)) {
      PdoEditDialogPool.getInstance().viewModal(popupObject, this);
    }
    else  {
      // non-modal
      final PdoEditDialog d = PdoEditDialogPool.getInstance().useNonModalDialog(popupObject, false, false);
      if (objCollection instanceof List && popupPath.getPathCount() == 2) {
        d.setLinkedPdoList((List)objCollection);
      }

      collapseAll();

      d.addActionListener(e -> {
        if (PdoEditDialog.ACTION_PREVIOUS.equals(e.getActionCommand()) ||
            PdoEditDialog.ACTION_NEXT.equals(e.getActionCommand())) {
          TreePath path = findPathInCollection(d.getPdo());
          if (path != null) {
            scrollPathToVisible(path);
            setSelectionPath(path);
          }
        }
      });
    }
  }//GEN-LAST:event_showItemActionPerformed

  private void collapseItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_collapseItemActionPerformed
    doCollapsePath(popupPath);
  }//GEN-LAST:event_collapseItemActionPerformed

  @SuppressWarnings("unchecked")
  private void expandItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_expandItemActionPerformed
    if (((PdoTreeObject)popupNode.getUserObject()).isExpanded())  {
      // already expanded: collapse first and then refresh
      doCollapsePath(popupPath);
    }
    doExpandPath(0, Rdc.createGuiProvider(popupObject).getTreeExpandMaxDepth(), null, popupPath);    // recursively expand all
  }//GEN-LAST:event_expandItemActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  protected javax.swing.JMenuItem collapseItem;
  protected javax.swing.JMenuItem copyItem;
  protected javax.swing.JMenuItem deleteItem;
  protected javax.swing.JMenuItem editItem;
  protected javax.swing.JMenuItem expandItem;
  protected javax.swing.JMenuItem insertItem;
  protected javax.swing.JMenuItem makeTableItem;
  protected javax.swing.JMenuItem openItem;
  protected javax.swing.JPopupMenu popupMenu;
  protected javax.swing.JMenuItem selectAllItem;
  protected javax.swing.JMenuItem showItem;
  protected javax.swing.JMenuItem subTreeItem;
  // End of variables declaration//GEN-END:variables

}


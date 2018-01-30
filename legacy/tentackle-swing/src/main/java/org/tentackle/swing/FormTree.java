/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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


package org.tentackle.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTree;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;



/**
 * Extended {@link JTree}.
 * <p>
 * Provides action listeners for mouse clicks and enter key.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormTree extends JTree {

  // action events
  /** action code for mouse clicked **/
  public static final String CLICK_ACTION = "click";
  /** action code for enter key pressed **/
  public static final String ENTER_ACTION = "enter";



  private int clicks;       // number of mouse-clicks


  /**
   * Creates a <code>FormTree</code> with a sample model.
   * @see JTree#JTree()
   */
  public FormTree() {
    super();
    setup();
  }

  /**
   * Creates a <code>FormTree</code> which displays the root node
   * -- the tree is created using the specified data model.
   *
   * @param model  the <code>TreeModel</code> to use as the data model
   * @see JTree#JTree(javax.swing.tree.TreeModel)
   */
  public FormTree (TreeModel model) {
    super(model);
    setup();
  }

  /**
   * Creates a <code>FormTree</code> with the specified
   * <code>TreeNode</code> as its root,
   * which displays the root node.
   * By default, the tree defines a leaf node as any node without children.
   *
   * @param root  a <code>TreeNode</code> object
   * @see JTree#JTree(javax.swing.tree.TreeNode)
   */
  public FormTree (TreeNode root) {
    super(root);
    setup();
  }



  /**
   * Gets the number of mouse clicks that led to the selection.
   * @return the number of clicks
   */
  public int getClickCount()  {
    return clicks;
  }


  /**
   * Adds an action listener (usually double click on a selection).
   * @param listener the listener to add
   */
  public synchronized void addActionListener (ActionListener listener)  {
    listenerList.add (ActionListener.class, listener);
  }

  /**
   * Removes an action Listener
   * @param listener the listener to remove
   */
  public synchronized void removeActionListener (ActionListener listener) {
     listenerList.remove (ActionListener.class, listener);
  }

  /**
   * Notifies all Listeners that the selection should be performed
   * @param evt the action event
   */
  public void fireActionPerformed (ActionEvent evt) {
    Object[] listeners = this.listenerList.getListenerList();
    if (listeners != null)  {
      for (int i = listeners.length-2; i >= 0; i -= 2)  {
        if (listeners[i] == ActionListener.class)  {
          ((ActionListener)listeners[i+1]).actionPerformed(evt);
        }
      }
    }
  }




  /**
   * adds the listeners
   */
  private void setup()  {
    // mouse-listener to check for mouse clicks
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        clicks = e.getClickCount();
        doFireActionPerformed(CLICK_ACTION);
      }
    });
    // key listener to check for enter key
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e)  {
        if (e.getKeyCode() == KeyEvent.VK_ENTER)  {
          clicks = 2; // same as double-click
          doFireActionPerformed(ENTER_ACTION);
        }
      }
    });
  }


  /**
   * fires action performed if double click on a valid selection
   */
  private void doFireActionPerformed(String actionCommand)  {
    if (clicks == 2 && getSelectionCount() > 0) {
      fireActionPerformed (new ActionEvent(this, ActionEvent.ACTION_PERFORMED, actionCommand));
    }
  }


}
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

// Created on August 22, 2002, 7:41 PM

package org.tentackle.swing;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.Vector;

/**
 * A transferable for files.<br>
 * Returned to the drop target (if we are the drag source)
 * and if the dragged object is a document (either File or Directory).
 */
public class FileTransferable extends Vector<File> implements Transferable  {

  private static final long serialVersionUID = 316070171066467593L;

  private final static int FILE   = 0;
  private final static int STRING = 1;

  private DataFlavor flavors[] = {DataFlavor.javaFileListFlavor,
                                  DataFlavor.stringFlavor};


  /**
   * Creates a FileTransferable.
   *
   * @param file the file
   */
  public FileTransferable(File file) {
    addElement(file);
  }

  public synchronized DataFlavor[] getTransferDataFlavors() {
    return flavors;
  }

  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor.equals(flavors[FILE]) || flavor.equals(flavors[STRING]);
  }

  public synchronized Object getTransferData(DataFlavor flavor)
         throws UnsupportedFlavorException, java.io.IOException {
    if (flavor.equals(flavors[FILE])) {
      return this;
    }
    else if (flavor.equals(flavors[STRING])) {
      return elementAt(0).getAbsolutePath();
    }
    else {
      throw new UnsupportedFlavorException(flavor);
    }
  }
}

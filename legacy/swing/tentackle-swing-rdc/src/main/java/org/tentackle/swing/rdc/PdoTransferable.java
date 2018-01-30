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

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.tentackle.pdo.PersistentDomainObject;



/**
 * Transferable for Drag and Drop of {@link PersistentDomainObject}s or lists of.
 *
 * @author harald
 * @param <T> the pdo type
 */
public class PdoTransferable<T extends PersistentDomainObject<T>> implements Transferable, ClipboardOwner {

  // the first or single object
  private T object;
  private DataFlavor  objectFlavor;

  // the list of objects
  private final List<T> objectList;

  // supported flavours
  private DataFlavor[] flavors;

  /** flavor for a single {@link PersistentDomainObject} **/
  public static final DataFlavor PDO_FLAVOR = new DataFlavor(PersistentDomainObject.class, "PDO");
  /** flavor for a list of {@link PersistentDomainObject}s **/
  public static final DataFlavor PDO_LIST_FLAVOR = new DataFlavor(List.class, "PDO-List");


  /**
   * Creates a transfertable for an object.
   *
   * @param pdo the PersistentDomainObject
   */
  public PdoTransferable(T pdo) {

    this.object = pdo;

    objectList = new ArrayList<>();
    objectList.add(pdo);

    // the MIME-type of the transferable is derived from the class of the object
    // (which must be serializable which is checked by DataFlavor to allow cross-JVM
    // DnD. However, in fact we transfer only the Id, i.e. the class need not
    // necessarily be serializable.
    objectFlavor = new DataFlavor (pdo.getEffectiveClass(), pdo.getClassBaseName());

    flavors = new DataFlavor[] { DataFlavor.stringFlavor, PDO_FLAVOR, PDO_LIST_FLAVOR, objectFlavor };
  }


  /**
   * Creates a transferable for a collection of objects.<br>
   * Non-PDOs are skipped.
   *
   * @param objects the items
   */
  @SuppressWarnings("unchecked")
  public PdoTransferable(Collection<?> objects) {

    objectList = new ArrayList<>();

    if (objects != null && objects.size() > 0)  {
      Iterator<?> iter = objects.iterator();
      boolean first = true;
      while (iter.hasNext())  {
        Object obj = iter.next();
        if (obj instanceof PersistentDomainObject)  {
          objectList.add((T) obj);
          DataFlavor flavor = new DataFlavor (obj.getClass(), ((PersistentDomainObject) obj).getClassBaseName());
          if (first)  {
            object = (T) obj;
            objectFlavor = flavor;
            first = false;
          }
        }
      }
      flavors = new DataFlavor[] { DataFlavor.stringFlavor, PDO_FLAVOR, PDO_LIST_FLAVOR, objectFlavor };
    }
  }




  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return flavors;
  }


  @Override
  public boolean isDataFlavorSupported(DataFlavor flv) {
    for (DataFlavor flavor : flavors) {
      if (flv.equals(flavor)) {
        return true;
      }
    }
    return false;
  }


  @Override
  public Object getTransferData(DataFlavor flv)
         throws UnsupportedFlavorException {
    if (flv.equals(PDO_FLAVOR) ||
        flv.equals(objectFlavor)) {
      // construct the transfer data
      return new PdoTransferData<>(object);
    }
    else if (flv.equals(PDO_LIST_FLAVOR))  {
      List<PdoTransferData<T>> transferList = new ArrayList<>();
      for (T pdo: objectList)  {
        transferList.add(new PdoTransferData<>(pdo));
      }
      return transferList;
    }
    else if (flv.equals(DataFlavor.stringFlavor)) {
      return object.toString();
    }
    throw new UnsupportedFlavorException(flv);
  }


  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    // nothing to do
  }

}

/*
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

package org.tentackle.swing;

import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import org.tentackle.io.BlockingByteArrayInputStream;
import org.tentackle.io.NotifyingByteArrayOutputStream;

/**
 * A console-like text area.
 * <p>
 * Useful to implement interactive shells, for example.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormConsole extends FormTextArea {

  private int maxRows;                                // max. number of rows. 0 if no limit
  private final BlockingByteArrayInputStream in;      // stream to read typed characters
  private final OutputStream out;                     // stream to write to the text area


  private class AppendingOutputStream extends ByteArrayOutputStream {

    @Override
    public synchronized void write(int b) {
      super.write(b);
      append(toString());
      reset();
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
      super.write(b, off, len);
      append(toString());
      reset();
    }

  }


  /**
   * Creates a {@code FormTextArea}.
   *
   * @param doc the document model, null if default {@link FormFieldDocument}.
   * @param text the text to be displayed, null if none
   * @param rows the number of rows &ge; 0
   * @param columns the number of columns &ge; 0
   */
  public FormConsole(Document doc, String text, int rows, int columns) {
    super (doc, text, rows, columns);
    out = new AppendingOutputStream();
    in = new BlockingByteArrayInputStream(new NotifyingByteArrayOutputStream());
  }


  /**
   * Creates a {@code FormTextArea} with the default document model.
   *
   * @param text the text to be displayed, null if none
   * @param rows the number of rows &ge; 0
   * @param columns the number of columns &ge; 0
   */
  public FormConsole (String text, int rows, int columns)  {
    this (null, text, rows, columns);
  }

  /**
   * Creates a {@code FormTextArea} with the default document model.
   *
   * @param text the text to be displayed, null if none
   */
  public FormConsole (String text) {
    this (null, text, 0, 0);
  }

  /**
   * Creates an empty {@code FormTextArea} with the default document model.
   *
   * @param rows the number of rows &ge; 0
   * @param columns the number of columns &ge; 0
   */
  public FormConsole (int rows, int columns)  {
    this (null, null, rows, columns);
  }

  /**
   * Creates an empty {@code FormTextArea} with the default document model.
   */
  public FormConsole () {
    this (null, null, 0, 0);
  }


  /**
   * Gets the maximum number of rows.
   *
   * @return the maximum number of rows, 0 if unlimited
   */
  public int getMaxRows() {
    return maxRows;
  }

  /**
   * Sets the maximum number of rows.<p>
   * This is the maximum number of lines kept in the document.
   * Not to be mixed up with the visible rows.
   *
   * @param maxRows number of rows to keep, 0 if unlimited
   */
  public void setMaxRows(int maxRows) {
    this.maxRows = maxRows;
  }


  @Override
  public void append(String str) {
    super.append(str);
    alignToMaxRows();
    setCaretPosition(getDocument().getLength());    // scroll to end
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    // just pass the event to the input stream
    if (e.getID() == KeyEvent.KEY_TYPED) {
      int code = e.getKeyChar();
      if (code != KeyEvent.CHAR_UNDEFINED) {
        in.getOutputStream().write(code);
      }
    }
    e.consume();
  }


  /**
   * Gets the output stream to write to.
   *
   * @return the output stream
   */
  public OutputStream getOutputStream() {
    return out;
  }

  /**
   * Gets the input stream to read from.
   *
   * @return the input stream containing keystrokes
   */
  public InputStream getInputStream() {
    return in;
  }


  /**
   * Aligns the number of lines in the document to max rows.
   */
  public void alignToMaxRows() {
    if (maxRows > 0) {
      Element root = getDocument().getDefaultRootElement();
      int lines = root.getElementCount();
      int tooMuch = lines - maxRows;
      if (tooMuch > 0) {
        try {
          getDocument().remove(0, root.getElement(tooMuch - 1).getEndOffset());
        }
        catch (BadLocationException ex) {
          // ??
        }
      }
    }
  }

}

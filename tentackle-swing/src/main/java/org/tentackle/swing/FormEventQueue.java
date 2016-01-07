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


package org.tentackle.swing;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;

/**
 * A replacement for the standard event queue providing
 * support for dropping key events a given amount of time.
 *
 * @author harald
 */
public class FormEventQueue extends EventQueue {


  private static final Logger LOGGER = LoggerFactory.getLogger(FormEventQueue.class);

  /** default milliseconds to drop keyevents for FormError, etc... **/
  public static long dropKeyEventDefaultTime = 250;


  private long dropKeyEventTime;    // drop keys in ms


  /**
   * Creates an event queue.
   */
  public FormEventQueue() {
    super();
  }


  /**
   * Drops all key events so far up to a given time from now.<br>
   * Useful for dialogs to prevent accidently ack (usually ENTER) by user.
   *
   * @param millis the time in milliseconds to add the current system time
   */
  public void dropKeyEvents(long millis) {
    dropKeyEventTime = System.currentTimeMillis() + millis;
  }

  /**
   * Drops all key events so far up to a given time from now.<br>
   * @see #dropKeyEventDefaultTime
   */
  public void dropKeyEvents() {
    dropKeyEvents(dropKeyEventDefaultTime);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to drop keyboard events.
   */
  @Override
  @SuppressWarnings("nls")
  public AWTEvent getNextEvent() throws InterruptedException {

    AWTEvent event;
    do  {
      event = super.getNextEvent();
    }
    while (event instanceof KeyEvent && ((KeyEvent)event).getWhen() <= dropKeyEventTime);

    /**
     * Check for debug feature shift+ctrl+rightear:
     * this dumps the component hierarchy below the mouse
     */
    if (event instanceof MouseEvent) {
      MouseEvent me = (MouseEvent) event;
      if (me.isShiftDown() && me.isControlDown() && me.isPopupTrigger()) {
        Object object = me.getSource();
        if (object instanceof Component) {
          Point p = me.getPoint();
          Component comp = SwingUtilities.getDeepestComponentAt((Component) object, p.x, p.y);
          LOGGER.info("Component Hierarchy:\n" + FormUtilities.getInstance().dumpComponentHierarchy(comp, me));
        }
      }
    }

    return event;
  }

}

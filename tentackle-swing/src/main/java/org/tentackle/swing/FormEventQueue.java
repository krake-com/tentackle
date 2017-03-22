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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.reflect.ReflectionHelper;

/**
 * A replacement for the standard event queue providing
 * support for dropping key events a given amount of time.
 *
 * @author harald
 */
public class FormEventQueue extends EventQueue {


  private static final Logger LOGGER = LoggerFactory.getLogger(FormEventQueue.class);

  /** default milliseconds to drop events. **/
  public static long dropEventDefaultTime = 250;


  /**
   * Filter to drop events.<br>
   * null if none or timed out (default).
   */
  private Predicate<InputEvent> eventFilter;

  /**
   * Epochal time in ms until events described by eventFilter should be dropped.<br>
   * 0 if none or timed out (default).
   */
  private long dropEventTime;


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
   * @param eventFilter the filter describing the events to drop
   */
  public void dropEvents(long millis, Predicate<InputEvent> eventFilter) {
    dropEventTime = System.currentTimeMillis() + millis;
    this.eventFilter = eventFilter;
  }

  /**
   * Drops all key events so far up {@link #dropEventDefaultTime} ms from now.
   */
  public void dropKeyEvents() {
    dropEvents(dropEventDefaultTime, (e) -> e instanceof KeyEvent);
  }

  /**
   * Drops all mouse events so far up {@link #dropEventDefaultTime} ms from now.
   */
  public void dropMouseEvents() {
    dropEvents(dropEventDefaultTime, (e) -> e instanceof MouseEvent);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to drop keyboard events.
   */
  @Override
  public AWTEvent getNextEvent() throws InterruptedException {

    AWTEvent event;

    if (dropEventTime != 0 && eventFilter != null) {
      while ((event = super.getNextEvent()) instanceof InputEvent) {
        InputEvent ie = (InputEvent) event;
        if (ie.getWhen() <= dropEventTime) {
          if (!eventFilter.test(ie)) {
            break;  // pass to application
          }
          // else: skip this event
        }
        else {  // time reached
          eventFilter = null;
          dropEventTime = 0;
          break;  // pass to application
        }
      }
    }
    else {
      event = super.getNextEvent();
    }

    /**
     * Check for debug feature shift+ctrl+rightear:
     * this dumps the component hierarchy below the mouse
     */
    if (event instanceof MouseEvent) {
      MouseEvent me = (MouseEvent) event;
      if (me.isShiftDown() && me.isControlDown() && me.isPopupTrigger() && LOGGER.isInfoLoggable()) {
        Object object = me.getSource();
        if (object instanceof Component) {
          Point p = me.getPoint();
          List<Component> dumpedComponents = new ArrayList<>();
	        if (object instanceof JFrame) {
            Component glass = ((JFrame) object).getGlassPane();
            if (glass != null) {
              Component comp = SwingUtilities.getDeepestComponentAt(glass, p.x - glass.getX(), p.y - glass.getY());
              if (comp != null) {
                LOGGER.info("component hierarchy for glass pane " +
                            ReflectionHelper.getClassBaseName(glass.getClass()) +
                            (glass.getName() == null ? "" : (" (" + glass.getName() + ")")) +
                            ":\n" +
                            FormUtilities.getInstance().dumpComponentHierarchy(comp, me));
                dumpedComponents.add(comp);
              }
            }
            Component pane = ((JFrame) object).getContentPane();
            if (pane != null) {
              Component comp = SwingUtilities.getDeepestComponentAt(pane, p.x - pane.getX(), p.y - pane.getY());
              if (comp != null && !dumpedComponents.contains(comp)) {
                LOGGER.info("component hierarchy for content pane " +
                            ReflectionHelper.getClassBaseName(pane.getClass()) +
                            (pane.getName() == null ? "" : (" (" + pane.getName() + ")")) +
                            ":\n" +
                            FormUtilities.getInstance().dumpComponentHierarchy(comp, me));
                dumpedComponents.add(comp);
              }
            }
          }
          Component comp = SwingUtilities.getDeepestComponentAt((Component) object, p.x, p.y);
          if (comp != null && !dumpedComponents.contains(comp)) {
            LOGGER.info("component hierarchy for " +
                        ReflectionHelper.getClassBaseName(comp.getClass()) +
                        (comp.getName() == null ? "" : (" (" + comp.getName() + ")")) +
                        ":\n" +
                        FormUtilities.getInstance().dumpComponentHierarchy(comp, me));
          }
        }
      }
    }

    return event;
  }

}

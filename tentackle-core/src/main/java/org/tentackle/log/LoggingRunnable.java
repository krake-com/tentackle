/*
 * Tentackle - https://tentackle.org
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


package org.tentackle.log;

import org.tentackle.log.Logger.Level;

/**
 * A logging runnable.<br>
 * Wraps a runnable and logs its start and duration, if the level is loggable.
 *
 * @author harald
 */
public class LoggingRunnable implements Runnable {

    private final Logger logger;
    private final Level level;
    private final Runnable runnable;

    /**
     * Creates a logging runnable.
     *
     * @param logger the logger
     * @param level the logging level
     * @param runnable the runnable
     */
    public LoggingRunnable(Logger logger, Level level, Runnable runnable) {
      this.logger = logger;
      this.level = level;
      this.runnable = runnable;
    }

    @Override
    public void run() {
      if (logger.isLoggable(level)) {
        long startMillis = System.currentTimeMillis();
        logger.log(level, "start runnable " + runnable);
        runnable.run();
        long duration = System.currentTimeMillis() - startMillis;
        logger.log(level, "done runnable " + runnable + " (" + duration + "ms)");
      }
      else  {
        runnable.run();
      }
    }

}

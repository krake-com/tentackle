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


package org.tentackle.wurblet;

import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurbel.WurbelHelper;
import org.wurbelizer.wurblet.AbstractWurblet;


/**
 * Base class for the {@code @Include}-wurblet.
 * <p>
 * Necessary because the run-method is overridden.
 *
 * @author harald
 */
public class IncludeWurblet extends AbstractWurblet {

  protected String filename;
  protected boolean asComment;
  protected boolean missingOk;
  protected boolean translate;
  protected boolean delete;


  public IncludeWurblet() {
    super();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Thread-safe if loaded by the wurblet classloader of the current wurbel run.
   *
   * @see WurbletThreadSafety#isIsolated(Class)
   */
  @Override
  public boolean isThreadSafe() {
    return WurbletThreadSafety.isIsolated(getClass());
  }

  @Override
  public void run() throws WurbelException {
    super.run();

    int argCount = 0;

    for (String arg: getContainer().getArgs())  {
      if (arg.startsWith("--"))  {
        switch (arg) {
          case "--comment":
            asComment = true;
            break;
          case "--missingok":
            missingOk = true;
            break;
          case "--translate":
            translate = true;
            break;
          case "--delete":
            delete = true;
            break;
        }
      }
      else {
        argCount++;
        if (argCount == 1)  {
          filename = arg;
        }
      }
    }

    if (filename == null) {
      throw new WurbelException("usage: @wurblet <guardname> Include <filename> [--comment] [--missingok] [--translate]");
    }
  }

  @Override
  public void cleanup() throws WurbelException {
    if (delete && WurbelHelper.deleteFile(filename)) {
      getContainer().getLogger().info("included file '" + filename + "' deleted");
    }
  }

}

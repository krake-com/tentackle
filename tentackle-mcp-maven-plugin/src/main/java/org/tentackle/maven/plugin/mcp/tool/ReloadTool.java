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

package org.tentackle.maven.plugin.mcp.tool;

import org.tentackle.maven.plugin.mcp.ModelSession;
import org.tentackle.model.ModelException;

import java.util.List;

/**
 * Reloads the model.
 * <p>
 * Changed, added and removed files are picked up automatically before every call, at most once per staleness
 * interval, so this is only needed to pick up a change right away, to learn which entities changed, or to
 * force a clean reload.
 *
 * @author harald
 */
public class ReloadTool implements ModelTool {

  /** The tool name. */
  public static final String NAME = "model_reload";

  /**
   * Creates the tool.
   */
  public ReloadTool() {
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return "Reload model";
  }

  @Override
  public String getDescription() {
    return """
        Reloads the model from disk and lists the entities whose source changed. Changed, added and removed \
        files are picked up automatically before every call, so you rarely need this -- reach for it right \
        after a build, to see what it changed, or when a result looks stale. 'full' clears the model and \
        loads it from scratch, which is slower but also drops entities whose source is gone.""";
  }

  @Override
  public String getInputSchema() {
    return new SchemaBuilder()
        .bool("full", "Clear the model and load it from scratch instead of refreshing changed files.", false)
        .build();
  }

  @Override
  public String execute(ModelSession session, ToolArguments args) throws ModelException {
    boolean full = args.bool("full", false);
    List<String> changed = session.reload(full);
    StringBuilder buf = new StringBuilder(256);
    buf.append(full ? "full reload" : "refresh").append(": ")
       .append(session.getModel().getAllEntities().size()).append(" entities, ")
       .append(session.getModelFileCount()).append(" files\n");
    buf.append(changed.isEmpty() ? "nothing changed" : "changed: " + String.join(", ", changed));
    return buf.toString();
  }

  @Override
  public boolean isReadOnly() {
    return false;     // changes what the other tools see, though it writes nothing
  }

}

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

package org.tentackle.maven.plugin.mcp.resource;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.extensions.mcp.server.McpResource;
import io.helidon.extensions.mcp.server.McpResourceRequest;
import io.helidon.extensions.mcp.server.McpResourceResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the model-definition DSL reference.
 * <p>
 * The grammar is the one thing an agent cannot derive from the model itself, and guessing it produces
 * definitions that look plausible and do not parse. The document is copied into the plugin jar at build time
 * from {@code tentackle-model/src/main/docs/model-definition.md}, so there is only ever one copy of it.
 *
 * @author harald
 */
public class GrammarResource implements McpResource {

  /** The resource URI. */
  public static final String URI = "tentackle-model://grammar/model-definition";

  /** The absolute classpath location of the packaged document, see the {@code copy-grammar} execution. */
  public static final String DOCUMENT = "/org/tentackle/maven/plugin/mcp/model-definition.md";

  /**
   * Creates the resource.
   */
  public GrammarResource() {
  }

  @Override
  public String uri() {
    return URI;
  }

  @Override
  public String name() {
    return "model-definition-grammar";
  }

  @Override
  public String description() {
    return "The complete reference of the Tentackle model-definition DSL: block delimiters, configuration " +
           "lines, global options, attribute, index and relation lines, and worked examples. Read this " +
           "before writing or changing a '@> ... @<' block.";
  }

  @Override
  public MediaType mediaType() {
    return MediaTypes.create("text/markdown");
  }

  @Override
  public McpResourceResult resource(McpResourceRequest request) {
    return McpResourceResult.create(load());
  }

  /**
   * Loads the packaged document.
   *
   * @return the document text
   */
  public static String load() {
    try (InputStream stream = GrammarResource.class.getResourceAsStream(DOCUMENT)) {
      if (stream == null) {
        return "the model-definition reference is not packaged with this plugin. See " +
               "https://tentackle.org/model-definition.html";
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
    catch (IOException iox) {
      throw new UncheckedIOException("cannot read " + DOCUMENT, iox);
    }
  }

}

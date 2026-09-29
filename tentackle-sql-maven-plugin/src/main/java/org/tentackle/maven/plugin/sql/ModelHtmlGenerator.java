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

package org.tentackle.maven.plugin.sql;

import org.tentackle.model.Entity;
import org.tentackle.sql.Backend;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

/**
 * Renders a loaded model as cross-linked HTML pages, one per entity, plus an index page.<br>
 * Every entity name mentioned is rendered as a hyperlink to that entity's own page.
 * The pages are styled by the generated {@code base.css} and {@code style.css}.
 *
 * @author harald
 */
class ModelHtmlGenerator extends AbstractModelDocGenerator {

  private final File cssFile;


  /**
   * Creates the generator.
   *
   * @param entities the entities to render, one page each
   * @param outputDir the directory to write the pages into
   * @param title the heading for the index page
   * @param cssFile an optional project-specific stylesheet, copied verbatim to {@code style.css};
   *                if null, a default theme is generated instead
   * @param backend the backend the attribute tables' SQL types are rendered for
   * @param mapSchemas whether schema-qualified table names were folded into flat table names
   */
  ModelHtmlGenerator(Collection<Entity> entities, File outputDir, String title, File cssFile, Backend backend,
                     boolean mapSchemas) {
    super(entities, outputDir, title, backend, mapSchemas);
    this.cssFile = cssFile;
  }


  @Override
  String fileExtension() {
    return ".html";
  }

  @Override
  void writeAssets() throws IOException {
    writeBaseStylesheet();
    writeThemeStylesheet();
  }

  @Override
  String link(Entity entity) {
    String name = escape(entity.getName());
    return "<a href=\"" + name + ".html\">" + name + "</a>";
  }

  @Override
  void pageHeader(StringBuilder buf, String heading, boolean withIndexLink) {
    buf.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<title>")
       .append(escape(heading))
       .append("</title>\n<link rel=\"stylesheet\" href=\"base.css\">\n<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n");
    if (withIndexLink) {
      buf.append("<p><a href=\"index.html\">&#8592; Index</a></p>\n");
    }
    buf.append("<h1>").append(escape(heading)).append("</h1>\n");
  }

  @Override
  void pageFooter(StringBuilder buf) {
    buf.append("</body>\n</html>\n");
  }

  @Override
  void heading(StringBuilder buf, int level, String text) {
    buf.append("<h").append(level).append('>').append(escape(text)).append("</h").append(level).append(">\n");
  }

  @Override
  void beginParagraph(StringBuilder buf) {
    buf.append("<p>");
  }

  @Override
  void endParagraph(StringBuilder buf) {
    buf.append("</p>\n");
  }

  @Override
  void lineBreak(StringBuilder buf) {
    buf.append("<br>");
  }

  @Override
  void comment(StringBuilder buf, String text) {
    buf.append("<p class=\"comment\">").append(escape(text)).append("</p>\n");
  }

  @Override
  void flag(StringBuilder buf, String text) {
    buf.append("<p class=\"flag\">").append(escape(text)).append("</p>\n");
  }

  @Override
  void beginList(StringBuilder buf) {
    buf.append("<ul>\n");
  }

  @Override
  void endList(StringBuilder buf) {
    buf.append("</ul>\n");
  }

  @Override
  void beginItem(StringBuilder buf) {
    buf.append("<li>");
  }

  @Override
  void endItem(StringBuilder buf) {
    buf.append("</li>\n");
  }

  @Override
  void itemNewline(StringBuilder buf) {
    buf.append('\n');
  }

  @Override
  void beginMetaTable(StringBuilder buf) {
    buf.append("<table class=\"meta\">\n");
  }

  @Override
  void metaRow(StringBuilder buf, String label, String renderedValue) {
    buf.append("<tr><td>").append(escape(label)).append("</td><td>").append(renderedValue).append("</td></tr>\n");
  }

  @Override
  void endMetaTable(StringBuilder buf) {
    buf.append("</table>\n");
  }

  @Override
  void beginTable(StringBuilder buf, String... headers) {
    buf.append("<table>\n<tr>");
    for (String header : headers) {
      buf.append("<th>").append(escape(header)).append("</th>");
    }
    buf.append("</tr>\n");
  }

  @Override
  void tableRow(StringBuilder buf, String... cells) {
    buf.append("<tr>");
    for (String cell : cells) {
      buf.append("<td>").append(escape(cell)).append("</td>");
    }
    buf.append("</tr>\n");
  }

  @Override
  void endTable(StringBuilder buf) {
    buf.append("</table>\n");
  }

  @Override
  void codeBlock(StringBuilder buf, String sql) {
    buf.append("<pre>").append(escape(sql)).append("</pre>\n");
  }

  @Override
  String dash() {
    return " &mdash; ";
  }

  @Override
  String escape(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder buf = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> buf.append("&amp;");
        case '<' -> buf.append("&lt;");
        case '>' -> buf.append("&gt;");
        case '"' -> buf.append("&quot;");
        default -> buf.append(c);
      }
    }
    return buf.toString();
  }


  // ------------------------------------------------------------------------------------- stylesheets

  /**
   * Writes the structural stylesheet (tables, lists, spacing).<br>
   * Always generated, regardless of {@link #cssFile} - a project overrides the *look* via
   * {@code style.css}, not the layout. Colors and fonts are taken from the
   * {@code --tentackle-doc-*} custom properties, so a project's stylesheet can override just those
   * or override selectors outright for full control since {@code style.css} is loaded after this one.
   */
  private void writeBaseStylesheet() throws IOException {
    String css =
        "body {\n" +
        "  font-family: var(--tentackle-doc-font, sans-serif);\n" +
        "  margin: 2em;\n" +
        "  color: var(--tentackle-doc-text-color, #222);\n" +
        "  background: var(--tentackle-doc-background-color, #fff);\n" +
        "}\n" +
        "table { border-collapse: collapse; margin: 0.5em 0 1.5em; }\n" +
        "table.meta td { padding: 2px 8px; }\n" +
        "table:not(.meta) th, table:not(.meta) td {\n" +
        "  border: 1px solid var(--tentackle-doc-border-color, #ccc);\n" +
        "  padding: 4px 8px;\n" +
        "  text-align: left;\n" +
        "}\n" +
        "table:not(.meta) th {\n" +
        "  background-color: var(--tentackle-doc-table-header-background-color, transparent);\n" +
        "  color: var(--tentackle-doc-table-header-text-color, inherit);\n" +
        "}\n" +
        "pre {\n" +
        "  border: 1px solid var(--tentackle-doc-border-color, #ccc);\n" +
        "  padding: 8px;\n" +
        "  margin: 0.5em 0 1.5em;\n" +
        "  overflow-x: auto;\n" +
        "}\n" +
        "h1 { color: var(--tentackle-doc-heading-color, #333); }\n" +
        "h2, h3 {\n" +
        "  color: var(--tentackle-doc-accent-color, var(--tentackle-doc-heading-color, #333));\n" +
        "  border-top: 1px solid var(--tentackle-doc-accent-color, transparent);\n" +
        "  border-bottom: 1px solid var(--tentackle-doc-accent-color, transparent);\n" +
        "  padding: 0.2em 0;\n" +
        "}\n" +
        "a { color: var(--tentackle-doc-link-color, #0b5fa5); text-decoration: none; }\n" +
        "a:hover, a:focus {\n" +
        "  color: var(--tentackle-doc-accent-color, var(--tentackle-doc-link-color, #0b5fa5));\n" +
        "  text-decoration: underline;\n" +
        "}\n" +
        ".comment { font-style: italic; color: var(--tentackle-doc-comment-color, #555); }\n";
    writeFile(new File(getOutputDir(), "base.css"), css);
  }

  /**
   * Writes the theme stylesheet (colors, fonts) as {@code style.css}.<br>
   * Copied verbatim from {@link #cssFile} if given, otherwise the default theme, which mimics the
   * brand of <a href="https://tentackle.org/">tentackle.org</a> itself - dark blue headings and
   * links, the signature orange-framed section headings, Roboto - including its dark-mode palette,
   * so a project can start from a copy of this and override only what it wants to change.
   */
  private void writeThemeStylesheet() throws IOException {
    if (cssFile != null) {
      Files.copy(cssFile.toPath(), new File(getOutputDir(), "style.css").toPath(), StandardCopyOption.REPLACE_EXISTING);
      return;
    }
    String css =
        "/*\n" +
        " * Default theme - mimics tentackle.org (see src/mkdocs/theme/stylesheets/extra.css and\n" +
        " * src/site/resources/css/site.css for the same palette applied to the docs site and the\n" +
        " * generated Maven site). Provide a project-specific stylesheet via the document goal's\n" +
        " * cssFile parameter to replace this.\n" +
        " */\n" +
        ":root {\n" +
        "  --tentackle-doc-font: \"Roboto\", \"Helvetica Neue\", Helvetica, Arial, sans-serif;\n" +
        "  --tentackle-doc-text-color: #1a1a1a;\n" +
        "  --tentackle-doc-background-color: #ffffff;\n" +
        "  --tentackle-doc-border-color: #dddddd;\n" +
        "  --tentackle-doc-heading-color: #00305e;\n" +
        "  --tentackle-doc-accent-color: #f18319;\n" +
        "  --tentackle-doc-link-color: #1663b0;\n" +
        "  --tentackle-doc-comment-color: #555555;\n" +
        "  --tentackle-doc-table-header-background-color: #00305e;\n" +
        "  --tentackle-doc-table-header-text-color: #ffffff;\n" +
        "}\n" +
        "@media (prefers-color-scheme: dark) {\n" +
        "  :root {\n" +
        "    --tentackle-doc-text-color: #dcdcdc;\n" +
        "    --tentackle-doc-background-color: #1a1c22;\n" +
        "    --tentackle-doc-border-color: #444444;\n" +
        "    --tentackle-doc-heading-color: #e8e8e8;\n" +
        "    --tentackle-doc-accent-color: #f6a24f;\n" +
        "    --tentackle-doc-link-color: #5b8fc7;\n" +
        "    --tentackle-doc-comment-color: #a8a8a8;\n" +
        "    --tentackle-doc-table-header-background-color: #00203f;\n" +
        "    --tentackle-doc-table-header-text-color: #dcdcdc;\n" +
        "  }\n" +
        "}\n";
    writeFile(new File(getOutputDir(), "style.css"), css);
  }

}

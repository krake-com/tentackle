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
import java.util.Collection;

/**
 * Renders a loaded model as cross-linked Markdown pages, one per entity, plus an index page.<br>
 * Provides the same content and navigation as the {@link ModelHtmlGenerator}: every entity name
 * mentioned is a relative link to that entity's own {@code .md} page, and each entity page links
 * back to {@code index.md}. The pages use GitHub-flavored Markdown (pipe tables, fenced code blocks)
 * and 4-space indentation for nested lists, which both CommonMark and Python-Markdown (MkDocs)
 * understand.
 *
 * @author harald
 */
class ModelMarkdownGenerator extends AbstractModelDocGenerator {

  private static final String INDENT = "    ";

  private int listDepth;


  /**
   * Creates the generator.
   *
   * @param entities the entities to render, one page each
   * @param outputDir the directory to write the pages into
   * @param title the heading for the index page
   * @param backend the backend the attribute tables' SQL types are rendered for
   * @param mapSchemas whether schema-qualified table names were folded into flat table names
   */
  ModelMarkdownGenerator(Collection<Entity> entities, File outputDir, String title, Backend backend,
                         boolean mapSchemas) {
    super(entities, outputDir, title, backend, mapSchemas);
  }


  @Override
  String fileExtension() {
    return ".md";
  }

  @Override
  void writeAssets() {
    // no stylesheets: styling is up to the Markdown renderer
  }

  @Override
  String link(Entity entity) {
    return "[" + escape(entity.getName()) + "](" + entity.getName() + ".md)";
  }

  @Override
  void pageHeader(StringBuilder buf, String heading, boolean withIndexLink) {
    listDepth = 0;
    if (withIndexLink) {
      buf.append("[← Index](index.md)\n\n");
    }
    buf.append("# ").append(escape(heading)).append("\n\n");
  }

  @Override
  void pageFooter(StringBuilder buf) {
    // exactly one trailing newline
    while (!buf.isEmpty() && buf.charAt(buf.length() - 1) == '\n') {
      buf.setLength(buf.length() - 1);
    }
    buf.append('\n');
  }

  @Override
  void heading(StringBuilder buf, int level, String text) {
    buf.append("#".repeat(level)).append(' ').append(escape(text)).append("\n\n");
  }

  @Override
  void beginParagraph(StringBuilder buf) {
    // paragraphs are separated by blank lines only
  }

  @Override
  void endParagraph(StringBuilder buf) {
    buf.append("\n\n");
  }

  @Override
  void lineBreak(StringBuilder buf) {
    // a new paragraph renders the same in all flavors, unlike trailing-space or backslash hard breaks
    buf.append("\n\n");
  }

  @Override
  void comment(StringBuilder buf, String text) {
    buf.append('*').append(escape(text.strip())).append("*\n\n");
  }

  @Override
  void flag(StringBuilder buf, String text) {
    buf.append("**").append(escape(text)).append("**\n\n");
  }

  @Override
  void beginList(StringBuilder buf) {
    if (listDepth > 0) {
      // nested within the current item
      newline(buf);
    }
    listDepth++;
  }

  @Override
  void endList(StringBuilder buf) {
    listDepth--;
    if (listDepth == 0) {
      buf.append('\n');
    }
  }

  @Override
  void beginItem(StringBuilder buf) {
    buf.append(INDENT.repeat(listDepth - 1)).append("- ");
  }

  @Override
  void endItem(StringBuilder buf) {
    newline(buf);
  }

  @Override
  void itemNewline(StringBuilder buf) {
    // line breaks within an item are determined by nested lists only
  }

  @Override
  void beginMetaTable(StringBuilder buf) {
    buf.append("| Property | Value |\n| --- | --- |\n");
  }

  @Override
  void metaRow(StringBuilder buf, String label, String renderedValue) {
    buf.append("| ").append(escape(label)).append(" | ").append(renderedValue).append(" |\n");
  }

  @Override
  void endMetaTable(StringBuilder buf) {
    buf.append('\n');
  }

  @Override
  void beginTable(StringBuilder buf, String... headers) {
    buf.append('|');
    for (String header : headers) {
      buf.append(' ').append(escape(header)).append(" |");
    }
    buf.append("\n|");
    buf.append(" --- |".repeat(headers.length));
    buf.append('\n');
  }

  @Override
  void tableRow(StringBuilder buf, String... cells) {
    buf.append('|');
    for (String cell : cells) {
      buf.append(' ').append(escape(cell)).append(" |");
    }
    buf.append('\n');
  }

  @Override
  void endTable(StringBuilder buf) {
    buf.append('\n');
  }

  @Override
  void codeBlock(StringBuilder buf, String sql) {
    buf.append("```sql\n").append(sql);
    newline(buf);
    buf.append("```\n\n");
  }

  @Override
  String dash() {
    return " — ";
  }

  /**
   * {@inheritDoc}
   * <p>
   * Markdown syntax characters are backslash-escaped, HTML-relevant characters are replaced by entities
   * (Markdown passes inline HTML through), and line breaks are folded into blanks, since the text may
   * end up in a table cell or list item.
   */
  @Override
  String escape(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder buf = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\\', '`', '*', '_', '[', ']', '#', '+', '|', '!' -> buf.append('\\').append(c);
        case '&' -> buf.append("&amp;");
        case '<' -> buf.append("&lt;");
        case '>' -> buf.append("&gt;");
        case '\r' -> { }
        case '\n' -> buf.append(' ');
        default -> buf.append(c);
      }
    }
    return buf.toString();
  }


  private void newline(StringBuilder buf) {
    if (!buf.isEmpty() && buf.charAt(buf.length() - 1) != '\n') {
      buf.append('\n');
    }
  }

}

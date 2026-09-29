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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.sql.SqlTestSupport.RecordingLog;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code document} goal.
 * <p>
 * The fixture is the {@code Bundle}/{@code BundleKey}/{@code Locale} shape: {@code Bundle} is a root
 * entity composite with the component {@code BundleKey} (mirroring the real {@code StoredBundle}/
 * {@code StoredBundleKey} example from {@code model-definition.md}), and {@code Locale} is a separate
 * root entity with a plain, non-composite reference to {@code Bundle} (mirroring the {@code Order}/
 * {@code Customer} fixture used by {@link CreateSqlMojoTest}). This exercises the "referenced by",
 * "composite structure", "references" and "composite membership" sections all at once.
 * <p>
 * All tests configure {@code backendNames = "h2"} (H2 is already a reactor test dependency), so pages
 * are read back from {@code docDir/H2/...} ({@link org.tentackle.sql.backends.H2#getName()}),
 * mirroring {@code create}'s per-backend output layout.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class DocumentSqlMojoTest {

  private File basedir;
  private File modelDir;
  private File docDir;
  private RecordingLog log;


  @BeforeMethod
  public void setUp() {
    basedir = SqlTestSupport.createTempDir("document-sql");
    modelDir = SqlTestSupport.createDir(basedir, "model");
    docDir = SqlTestSupport.createDir(basedir, "doc");
    log = new RecordingLog();
  }

  @AfterMethod
  public void tearDown() {
    SqlTestSupport.deleteRecursively(basedir);
  }


  @Test
  public void compositeAndReferenceProduceCrossLinkedPages() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("compositeAndReferenceProduceCrossLinkedPages");
    run(mojo);

    String bundleHtml = html("Bundle");
    assertTrue(bundleHtml.contains("href=\"Locale.html\""), bundleHtml);
    assertTrue(bundleHtml.contains("href=\"BundleKey.html\""), bundleHtml);
    assertTrue(bundleHtml.contains("This is a root entity."), bundleHtml);

    String keyHtml = html("BundleKey");
    assertTrue(keyHtml.contains("href=\"Bundle.html\""), keyHtml);
    assertTrue(keyHtml.contains("Component of: "), keyHtml);

    String localeHtml = html("Locale");
    assertTrue(localeHtml.contains("href=\"Bundle.html\""), localeHtml);
    assertTrue(localeHtml.contains("Not extended."), localeHtml);
  }

  @Test
  public void indexLinksToEveryEntityPage() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("indexLinksToEveryEntityPage");
    run(mojo);

    String index = html("index");
    assertTrue(index.contains("href=\"Bundle.html\""), index);
    assertTrue(index.contains("href=\"BundleKey.html\""), index);
    assertTrue(index.contains("href=\"Locale.html\""), index);
    assertTrue(index.contains("Aggregates"), index);
  }

  @Test
  public void attributesTableListsNonImplicitAttributes() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("attributesTableListsNonImplicitAttributes");
    run(mojo);

    String bundleHtml = html("Bundle");
    assertTrue(bundleHtml.contains("<td>name</td>"), bundleHtml);
    assertTrue(bundleHtml.contains("the bundle&#39;s name") || bundleHtml.contains("the bundle's name"), bundleHtml);
  }

  /**
   * The SQL type is backend-specific, which is why pages live under {@code docDir/H2/...} in the
   * first place - a {@code String(128)} attribute must render as an H2 {@code VARCHAR} column.
   */
  @Test
  public void attributesTableShowsSqlType() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("attributesTableShowsSqlType");
    run(mojo);

    String bundleHtml = html("Bundle");
    assertTrue(bundleHtml.contains("VARCHAR"), bundleHtml);
  }

  /**
   * A non-nullable attribute (here: the primitive {@code long bundleId} of {@code BundleKey}) must
   * be suffixed with {@code NOT NULL} in the SQL type column, while a nullable one (the
   * {@code String name} of {@code Bundle}) must not be.
   */
  @Test
  public void attributesTableSuffixesNotNullableColumnsWithNotNull() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("attributesTableSuffixesNotNullableColumnsWithNotNull");
    run(mojo);

    String keyHtml = html("BundleKey");
    assertTrue(keyHtml.contains("BIGINT NOT NULL"), keyHtml);

    String bundleHtml = html("Bundle");
    assertTrue(bundleHtml.contains("VARCHAR(128)</td>"), bundleHtml);
    assertFalse(bundleHtml.contains("VARCHAR(128) NOT NULL"), bundleHtml);
  }

  /**
   * An entity without an {@code ## indexes} section must render an empty "Indexes" section, not
   * omit it - a reader must be able to tell "no indexes" from "the section was forgotten".
   */
  @Test
  public void indexesSectionIsEmptyWhenEntityDeclaresNoIndexes() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("indexesSectionIsEmptyWhenEntityDeclaresNoIndexes");
    run(mojo);

    String bundleHtml = html("Bundle");
    assertTrue(bundleHtml.contains("<h2>Indexes</h2>\n<p>No additional indexes.</p>"), bundleHtml);
  }

  /**
   * An {@code index} line in the model's {@code ## indexes} section must render as the actual
   * backend-specific {@code CREATE INDEX} statement, mirroring what {@code create} emits for the
   * same model.
   */
  @Test
  public void indexesSectionShowsCreateIndexStatement() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n" +
        "\n" +
        "## indexes\n" +
        "unique index itemname := name\n");

    DocumentSqlMojo mojo = configure("indexesSectionShowsCreateIndexStatement");
    run(mojo);

    String itemHtml = html("Item");
    assertTrue(itemHtml.contains("<h2>Indexes</h2>\n<pre>"), itemHtml);
    assertTrue(itemHtml.toUpperCase().contains("CREATE UNIQUE INDEX") && itemHtml.contains("itemname")
               && itemHtml.contains("ON item") && itemHtml.contains("(name)"), itemHtml);
  }

  /**
   * The meta table's "Table" row must show the table name relative to its schema, not the raw,
   * schema-qualified name - the schema is already shown in the separate "Schema" row below it.
   */
  @Test
  public void metaTableShowsTableNameWithoutSchema() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := myschema.item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n");

    DocumentSqlMojo mojo = configure("metaTableShowsTableNameWithoutSchema");
    run(mojo);

    String itemHtml = html("Item");
    assertTrue(itemHtml.contains("<td>Table</td><td>item</td>"), itemHtml);
    assertTrue(itemHtml.contains("<td>Schema</td><td>myschema</td>"), itemHtml);
  }

  /**
   * With {@code mapSchemas}, the meta table's "Table" row must show the effective, flattened table
   * name actually used at runtime, and the "Schema" row must be omitted since a mapped entity no
   * longer has a separate schema.
   */
  @Test
  public void mapSchemasShowsTheEffectiveFlattenedTableName() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := myschema.item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n");

    DocumentSqlMojo mojo = configure("mapSchemasShowsTheEffectiveFlattenedTableName");
    SqlTestSupport.setField(mojo, "mapSchemas", true);
    run(mojo);

    String itemHtml = html("Item");
    assertTrue(itemHtml.contains("<td>Table</td><td>myschema_item</td>"), itemHtml);
    assertFalse(itemHtml.contains("<td>Schema</td>"), itemHtml);
  }

  /**
   * {@code id}/{@code serial} are added implicitly by the framework on every entity - they must show
   * up in the separate "Framework Attributes" section, not in the regular, user-declared one.
   */
  @Test
  public void frameworkAttributesSectionListsImplicitAttributes() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("frameworkAttributesSectionListsImplicitAttributes");
    run(mojo);

    String bundleHtml = html("Bundle");
    assertTrue(bundleHtml.contains("Framework Attributes"), bundleHtml);

    int frameworkSectionStart = bundleHtml.indexOf("Framework Attributes");
    String beforeFrameworkSection = bundleHtml.substring(0, frameworkSectionStart);
    String frameworkSection = bundleHtml.substring(frameworkSectionStart);

    assertFalse(beforeFrameworkSection.contains("<td>id</td>"), beforeFrameworkSection);
    assertFalse(beforeFrameworkSection.contains("<td>serial</td>"), beforeFrameworkSection);
    assertTrue(frameworkSection.contains("<td>id</td>"), frameworkSection);
    assertTrue(frameworkSection.contains("<td>serial</td>"), frameworkSection);
  }

  @Test
  public void baseStylesheetIsStructuralWithNeutralFallbacksOnly() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("baseStylesheetIsStructuralWithNeutralFallbacksOnly");
    run(mojo);

    String base = css("base");
    assertTrue(base.contains("var(--tentackle-doc-link-color, #0b5fa5)"), base);
    assertFalse(base.contains(":root"), base);
    assertFalse(base.contains("#00305e"), base);
    assertFalse(base.contains("#f18319"), base);

    String bundleHtml = html("Bundle");
    assertTrue(bundleHtml.contains("<link rel=\"stylesheet\" href=\"base.css\">"), bundleHtml);
    assertTrue(bundleHtml.contains("<link rel=\"stylesheet\" href=\"style.css\">"), bundleHtml);
  }

  /**
   * The default theme (no {@code cssFile} given) mimics tentackle.org's own brand palette - dark
   * blue headings/links and the signature orange accent - in both the light and the dark-mode
   * variant, same as {@code src/mkdocs/theme/stylesheets/extra.css} and
   * {@code src/site/resources/css/site.css} apply to the docs site and the generated Maven site.
   */
  @Test
  public void defaultThemeMimicsTentackleDotOrg() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("defaultThemeMimicsTentackleDotOrg");
    run(mojo);

    String style = css("style");
    assertTrue(style.contains(":root"), style);
    assertTrue(style.contains("--tentackle-doc-heading-color: #00305e;"), style);
    assertTrue(style.contains("--tentackle-doc-accent-color: #f18319;"), style);
    assertTrue(style.contains("--tentackle-doc-link-color: #1663b0;"), style);
    assertTrue(style.contains("Roboto"), style);
    assertTrue(style.contains("@media (prefers-color-scheme: dark)"), style);
    assertTrue(style.contains("--tentackle-doc-accent-color: #f6a24f;"), style);
  }

  /**
   * A project-specific stylesheet replaces the generated theme verbatim, while the structural
   * {@code base.css} - which a project has no reason to touch - is still generated as usual.
   */
  @Test
  public void cssFileReplacesTheGeneratedTheme() throws Exception {
    writeBundleFixture();
    File projectCss = SqlTestSupport.writeFile(basedir, "project.css", ":root { --tentackle-doc-link-color: #ff00ff; }\n");

    DocumentSqlMojo mojo = configure("cssFileReplacesTheGeneratedTheme");
    SqlTestSupport.setField(mojo, "cssFile", projectCss);
    run(mojo);

    String style = css("style");
    assertTrue(style.contains("#ff00ff"), style);
    assertFalse(style.contains("#00305e"), style);
    assertFalse(style.contains("#f18319"), style);

    String base = css("base");
    assertTrue(base.contains("var(--tentackle-doc-link-color, #0b5fa5)"), base);
  }

  /**
   * Entity and attribute comments are free text authored in the model, not identifiers - they must
   * be escaped, or a comment containing {@code <}/{@code &} would break the generated page.
   */
  @Test
  public void commentContainingHtmlIsEscaped() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "comment := a <b>bold</b> & tricky comment\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            a <i>tricky</i> comment too\n");

    DocumentSqlMojo mojo = configure("commentContainingHtmlIsEscaped");
    run(mojo);

    String itemHtml = html("Item");
    assertFalse(itemHtml.contains("<b>bold</b>"), itemHtml);
    assertFalse(itemHtml.contains("<i>tricky</i>"), itemHtml);
    assertTrue(itemHtml.contains("&lt;b&gt;bold&lt;/b&gt;"), itemHtml);
    assertTrue(itemHtml.contains("&lt;i&gt;tricky&lt;/i&gt;"), itemHtml);
    assertTrue(itemHtml.contains("&amp;"), itemHtml);
  }


  // ------------------------------------------------------------------------------------- markdown

  /**
   * The Markdown pages must be navigable just like the HTML ones: every entity mentioned links to the
   * entity's own page, and each entity page links back to the index.
   */
  @Test
  public void markdownPagesAreCrossLinked() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configureMarkdown("markdownPagesAreCrossLinked");
    run(mojo);

    String bundleMd = md("Bundle");
    assertTrue(bundleMd.startsWith("[← Index](index.md)\n\n# Bundle\n"), bundleMd);
    assertTrue(bundleMd.contains("[Locale](Locale.md)"), bundleMd);          // referenced by
    assertTrue(bundleMd.contains("[BundleKey](BundleKey.md)"), bundleMd);    // composite structure
    assertTrue(bundleMd.contains("**This is a root entity.**"), bundleMd);

    String keyMd = md("BundleKey");
    assertTrue(keyMd.contains("[← Index](index.md)"), keyMd);
    assertTrue(keyMd.contains("Component of: [Bundle](Bundle.md)"), keyMd);
    assertTrue(keyMd.contains("Belongs to the root entity: [Bundle](Bundle.md)"), keyMd);

    String localeMd = md("Locale");
    assertTrue(localeMd.contains("[← Index](index.md)"), localeMd);
    assertTrue(localeMd.contains("### References\n\n- [Bundle](Bundle.md)"), localeMd);
    assertTrue(localeMd.contains("Not extended."), localeMd);

    for (String page : new String[] { "index", "Bundle", "BundleKey", "Locale" }) {
      assertFalse(md(page).contains(".html"), page);
    }
  }

  @Test
  public void markdownIndexLinksToEveryEntityPage() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configureMarkdown("markdownIndexLinksToEveryEntityPage");
    run(mojo);

    String index = md("index");
    assertTrue(index.startsWith("# Test Model\n"), index);
    assertTrue(index.contains("## Aggregates\n\n- [Bundle](Bundle.md)\n    - [BundleKey](BundleKey.md)\n"), index);
    assertTrue(index.contains("## All Entities\n"), index);
    assertTrue(index.contains("- [Locale](Locale.md)\n"), index);
    assertFalse(index.contains("[← Index]"), index);
  }

  /**
   * Inheritance is navigable in both directions: the sub entity's meta table links the super entity,
   * the super entity's "Inheritance" section links the sub entity.
   */
  @Test
  public void markdownInheritanceIsCrossLinked() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Base.txt",
        "name := Base\n" +
        "table := base\n" +
        "integrity := none\n" +
        "inheritance := multi\n" +
        "\n" +
        "## attributes\n" +
        "[]\n" +
        "\n" +
        "String(30)      code            code            the code\n");
    SqlTestSupport.writeFile(modelDir, "Invoice.txt",
        "name := Invoice\n" +
        "id := 2\n" +
        "table := invoice\n" +
        "integrity := none\n" +
        "extends := Base\n" +
        "\n" +
        "## attributes\n" +
        "[]\n" +
        "\n" +
        "String(30)      number          number          the invoice number\n");

    DocumentSqlMojo mojo = configureMarkdown("markdownInheritanceIsCrossLinked");
    run(mojo);

    String invoiceMd = md("Invoice");
    assertTrue(invoiceMd.contains("| Extends | [Base](Base.md) |"), invoiceMd);

    String baseMd = md("Base");
    assertTrue(baseMd.contains("Extended by:\n\n- ^ [Invoice](Invoice.md)"), baseMd);
  }

  @Test
  public void markdownAttributesTableShowsSqlType() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configureMarkdown("markdownAttributesTableShowsSqlType");
    run(mojo);

    String bundleMd = md("Bundle");
    assertTrue(bundleMd.contains("| Name | Column | Java Type | SQL Type | Comment |\n| --- | --- | --- | --- | --- |\n"), bundleMd);
    assertTrue(bundleMd.contains("| name | bname | String | VARCHAR(128) | the bundle's name |"), bundleMd);
    assertTrue(bundleMd.contains("| Table | bundle |"), bundleMd);

    String keyMd = md("BundleKey");
    assertTrue(keyMd.contains("BIGINT NOT NULL"), keyMd);
  }

  @Test
  public void markdownIndexesSectionShowsSqlCodeBlock() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            the item's name\n" +
        "\n" +
        "## indexes\n" +
        "unique index itemname := name\n");

    DocumentSqlMojo mojo = configureMarkdown("markdownIndexesSectionShowsSqlCodeBlock");
    run(mojo);

    String itemMd = md("Item");
    assertTrue(itemMd.contains("## Indexes\n\n```sql\n"), itemMd);
    assertTrue(itemMd.toUpperCase().contains("CREATE UNIQUE INDEX") && itemMd.contains("itemname"), itemMd);
    assertTrue(itemMd.contains("\n```\n"), itemMd);
  }

  @Test
  public void markdownCommentIsEscaped() throws Exception {
    SqlTestSupport.writeFile(modelDir, "Item.txt",
        "name := Item\n" +
        "id := 1\n" +
        "table := item\n" +
        "comment := a <b>bold</b> & *tricky* comment\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(30)      name            name            a | pipe and a <i>tag</i>\n");

    DocumentSqlMojo mojo = configureMarkdown("markdownCommentIsEscaped");
    run(mojo);

    String itemMd = md("Item");
    assertTrue(itemMd.contains("*a &lt;b&gt;bold&lt;/b&gt; &amp; \\*tricky\\* comment*"), itemMd);
    assertTrue(itemMd.contains("a \\| pipe and a &lt;i&gt;tag&lt;/i&gt;"), itemMd);
  }

  /**
   * Markdown needs no stylesheets, and no HTML pages must be left behind.
   */
  @Test
  public void markdownWritesNoHtmlOrCss() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configureMarkdown("markdownWritesNoHtmlOrCss");
    run(mojo);

    String[] files = new File(docDir, "H2").list();
    assertTrue(files != null && files.length == 4);
    for (String file : files) {
      assertTrue(file.endsWith(".md"), file);
    }
  }

  @Test
  public void formatIsCaseInsensitive() throws Exception {
    writeBundleFixture();

    DocumentSqlMojo mojo = configure("formatIsCaseInsensitive");
    SqlTestSupport.setField(mojo, "format", "MarkDown");
    run(mojo);

    assertTrue(new File(new File(docDir, "H2"), "index.md").isFile());
  }

  @Test
  public void unsupportedFormatIsRejected() {
    DocumentSqlMojo mojo = configure("unsupportedFormatIsRejected");
    SqlTestSupport.setField(mojo, "format", "pdf");
    MojoExecutionException mex = expectThrows(MojoExecutionException.class, () -> {
      mojo.prepareExecute();
      SqlTestSupport.callValidate(mojo);
    });
    assertTrue(mex.getMessage().contains("pdf"), mex.getMessage());
  }


  private void writeBundleFixture() {
    SqlTestSupport.writeFile(modelDir, "Bundle.txt",
        "name := Bundle\n" +
        "id := 1\n" +
        "table := bundle\n" +
        "\n" +
        "## attributes\n" +
        "[root, tracked]\n" +
        "\n" +
        "String(128)     name            bname           the bundle's name\n" +
        "\n" +
        "## relations\n" +
        "BundleKey:\n" +
        "    relation = composite list,\n" +
        "    select   = eager,\n" +
        "    name     = Keys,\n" +
        "    method   = BundleId\n");

    SqlTestSupport.writeFile(modelDir, "BundleKey.txt",
        "name := BundleKey\n" +
        "id := 2\n" +
        "table := bundle_key\n" +
        "\n" +
        "## attributes\n" +
        "[tracked]\n" +
        "\n" +
        "long            bundleId        bundle_id       the bundle id\n" +
        "String          code            code            the key code\n" +
        "\n" +
        "## relations\n" +
        "Bundle:\n" +
        "    relation = serialized,\n" +
        "    name     = Bundle\n");

    SqlTestSupport.writeFile(modelDir, "Locale.txt",
        "name := Locale\n" +
        "id := 3\n" +
        "table := locale\n" +
        "\n" +
        "## attributes\n" +
        "[root]\n" +
        "\n" +
        "String(8)       code            code            the locale code\n" +
        "Long            bundleId        bundle_id       object ID of the bundle\n" +
        "\n" +
        "## relations\n" +
        "Bundle:\n" +
        "    select = lazy\n");
  }

  /**
   * The container never boots, so no {@code @Parameter(defaultValue = ...)} is ever applied -
   * every default the test relies on has to be set explicitly, matching the mojo's own defaults.
   */
  private DocumentSqlMojo configure(String modelName) {
    DocumentSqlMojo mojo = new DocumentSqlMojo();
    mojo.setLogger(log);
    SqlTestSupport.setField(mojo, "mavenProject", SqlTestSupport.createProject(basedir));
    SqlTestSupport.setField(mojo, "modelDir", modelDir);
    SqlTestSupport.setField(mojo, "docDir", docDir);
    SqlTestSupport.setField(mojo, "modelName", modelName);
    SqlTestSupport.setField(mojo, "title", "Test Model");
    SqlTestSupport.setField(mojo, "backendNames", "h2");
    SqlTestSupport.setField(mojo, "format", "html");
    return mojo;
  }

  private DocumentSqlMojo configureMarkdown(String modelName) {
    DocumentSqlMojo mojo = configure(modelName);
    SqlTestSupport.setField(mojo, "format", "markdown");
    return mojo;
  }

  private void run(DocumentSqlMojo mojo) throws Exception {
    mojo.prepareExecute();
    assertTrue(SqlTestSupport.callValidate(mojo));
    mojo.executeImpl();
    mojo.finishExecute();
  }

  private String html(String pageName) {
    return SqlTestSupport.readFile(new File(new File(docDir, "H2"), pageName + ".html"));
  }

  private String md(String pageName) {
    return SqlTestSupport.readFile(new File(new File(docDir, "H2"), pageName + ".md"));
  }

  private String css(String fileName) {
    return SqlTestSupport.readFile(new File(new File(docDir, "H2"), fileName + ".css"));
  }

}

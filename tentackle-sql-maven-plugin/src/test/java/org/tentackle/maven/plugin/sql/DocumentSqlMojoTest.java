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

import java.io.File;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

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
 * are read back from {@code htmlDir/h2/...}, mirroring {@code create}'s per-backend output layout.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class DocumentSqlMojoTest {

  private File basedir;
  private File modelDir;
  private File htmlDir;
  private RecordingLog log;


  @BeforeMethod
  public void setUp() {
    basedir = SqlTestSupport.createTempDir("document-sql");
    modelDir = SqlTestSupport.createDir(basedir, "model");
    htmlDir = SqlTestSupport.createDir(basedir, "html");
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
   * The SQL type is backend-specific, which is why pages live under {@code htmlDir/h2/...} in the
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
    mojo.setLog(log);
    SqlTestSupport.setField(mojo, "mavenProject", SqlTestSupport.createProject(basedir));
    SqlTestSupport.setField(mojo, "modelDir", modelDir);
    SqlTestSupport.setField(mojo, "htmlDir", htmlDir);
    SqlTestSupport.setField(mojo, "modelName", modelName);
    SqlTestSupport.setField(mojo, "title", "Test Model");
    SqlTestSupport.setField(mojo, "backendNames", "h2");
    return mojo;
  }

  private void run(DocumentSqlMojo mojo) throws Exception {
    mojo.prepareExecute();
    assertTrue(SqlTestSupport.callValidate(mojo));
    mojo.executeImpl();
    mojo.finishExecute();
  }

  private String html(String pageName) {
    return SqlTestSupport.readFile(new File(new File(htmlDir, "h2"), pageName + ".html"));
  }

  private String css(String fileName) {
    return SqlTestSupport.readFile(new File(new File(htmlDir, "h2"), fileName + ".css"));
  }

}

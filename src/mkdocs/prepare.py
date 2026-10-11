#!/usr/bin/env python3
"""Stage the Tentackle documentation into ./docs for the MkDocs build.

The page content lives in the repository's real docs — the core docs under
``src/main/docs`` and every module's ``<module>/src/main/docs/*.md`` — plus the
repo README. This script assembles a clean MkDocs ``docs_dir`` from them without
ever modifying the sources, so Bitbucket rendering keeps working. Run it via
``make prepare`` (automatically invoked by ``make serve`` / ``make build``).

The module docs are spread across the reactor and cross-reference each other
with deep relative paths. Because every doc's filename is unique across the whole
reactor, we flatten them into a single directory and rewrite links to bare
basenames. Links to Java source files (``../java/.../Foo.java``) are rewritten to
the corresponding Bitbucket source URL.

Standard library only — no third-party dependencies.
"""

from __future__ import annotations

import re
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent                  # src/mkdocs
REPO_ROOT = (HERE / ".." / "..").resolve()              # repository root
CORE_DOCS = REPO_ROOT / "src" / "main" / "docs"         # src/main/docs
REPO_README = REPO_ROOT / "README.md"
THEME = HERE / "theme"
OUT = HERE / "docs"                                     # generated docs_dir (git-ignored)

# Absolute source-browse base for links that point at repository files (e.g. the
# ``../java/.../Foo.java`` API links) which cannot live inside a static doc site.
BITBUCKET_SRC = "https://bitbucket.org/krake-oss/tentackle/src/master/"

# toc.md is intentionally excluded — Material provides the nav. The alphabetical
# index (src/main/docs/index.md) is staged as concept-index.md so it does not
# clash with the README-derived home page (index.md).
EXCLUDE = {"toc.md"}
# index.md (alphabetical concept index) is staged as concept-index.md so it does
# not clash with the README-derived home page. toc.md is not staged (the Material
# nav replaces it), so links pointing at it are redirected to the A–Z index.
RENAME = {"index.md": "concept-index.md", "toc.md": "concept-index.md"}

IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp")

# Captures the target of a Markdown link or image: ](target)
LINK_RE = re.compile(r"\]\(([^)]+)\)")


def out_name(basename: str) -> str:
    """Map a source doc basename to its flattened output filename."""
    return RENAME.get(basename, basename)


def rewrite_target(target: str, src_file: Path) -> str:
    """Rewrite a single Markdown link/image target for the flat docs layout.

    - in-page anchors and absolute/mailto URLs are left untouched;
    - links to ``*.md`` (at any depth) collapse to the flattened basename,
      preserving any ``#anchor``;
    - image/asset links collapse to ``assets/<file>``;
    - any other repo-relative link (Java sources, etc.) becomes an absolute
      Bitbucket source URL.
    """
    # Split off an optional link title:  url "Some title"
    m = re.match(r"(\S+)(\s+.*)?$", target, re.S)
    if not m:
        return target
    url, title = m.group(1), m.group(2) or ""

    if url.startswith(("http://", "https://", "//", "mailto:", "#")):
        return target

    # Separate the path from a trailing #anchor.
    path, sep, anchor = url.partition("#")
    anchor = sep + anchor
    base = Path(path).name.lower()

    if base.endswith(".md"):
        # The source docs encode em-dash headings as a double dash in anchors
        # (e.g. "#foo--bar"), but MkDocs' slugifier collapses them to a single
        # dash. MkDocs-generated anchors never contain "--", so collapsing
        # dash-runs realigns these in-page jumps.
        anchor = re.sub(r"-{2,}", "-", anchor)
        return f"{out_name(Path(path).name)}{anchor}{title}"

    if path.lower().endswith(IMAGE_EXTS) or "/assets/" in path:
        return f"assets/{Path(path).name}{anchor}{title}"

    # Some other repo file (e.g. a Java source). Resolve it against the source
    # doc's real location and point at the Bitbucket source browser.
    try:
        resolved = (src_file.parent / path).resolve().relative_to(REPO_ROOT)
        return f"{BITBUCKET_SRC}{resolved.as_posix()}{anchor}{title}"
    except ValueError:
        # Path escapes the repo — leave it untouched.
        return target


def rewrite_links(text: str, src_file: Path) -> str:
    return LINK_RE.sub(
        lambda mo: f"]({rewrite_target(mo.group(1), src_file)})", text)


def readme_to_index(text: str, src_file: Path) -> str:
    """Turn the repo README into the site home page (index.md).

    - promote the leading logo image to the page H1 (so it becomes the hero and
      MkDocs does not inject the nav title "Home" as a heading);
    - drop the "Table of Contents" bullet (Material's nav replaces it);
    - drop the Maven Central version badge (a third-party image that would be
      frozen at build time);
    - rewrite all links for the flat layout (the Index bullet then points at
      concept-index.md, feature links at their basenames, the logo at assets/).
    """
    # Drop the toc.md helper line — the nav supersedes a table of contents —
    # and the version badge: it is a third-party image that the privacy plugin
    # would freeze at build time, so it would show a stale version.
    lines = [ln for ln in text.splitlines()
             if "toc.md" not in ln and "img.shields.io" not in ln]
    text = "\n".join(lines)

    # Promote the leading logo image to the page H1 (the title).
    text = re.sub(r"^(!\[[^\]]*\]\([^)]*\))", r"# \1", text.lstrip(), count=1)

    return rewrite_links(text, src_file).rstrip() + "\n"


def collect_sources() -> list[Path]:
    """All staged source docs: core docs + every module's docs (minus EXCLUDE)."""
    sources = sorted(CORE_DOCS.glob("*.md"))
    sources += sorted(REPO_ROOT.glob("tentackle-*/src/main/docs/*.md"))
    return [p for p in sources if p.name not in EXCLUDE]


def main() -> None:
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    # 1. Flatten every module/core doc, rewriting its links. Guard against the
    #    (verified-impossible) case of a basename collision.
    seen: dict[str, Path] = {}
    for src in collect_sources():
        name = out_name(src.name)
        if name in seen:
            raise SystemExit(f"basename collision: {src} vs {seen[name]} -> {name}")
        seen[name] = src
        (OUT / name).write_text(
            rewrite_links(src.read_text(encoding="utf-8"), src), encoding="utf-8")

    # 2. Home page from the repo README.
    (OUT / "index.md").write_text(
        readme_to_index(REPO_README.read_text(encoding="utf-8"), REPO_README),
        encoding="utf-8")

    # 3. Images from the core docs assets directory.
    shutil.copytree(CORE_DOCS / "assets", OUT / "assets")
    # ... plus the favicon / header logo (an MkDocs-only brand asset).
    shutil.copy2(THEME / "assets" / "favicon.png", OUT / "assets" / "favicon.png")
    # ... plus the self-hosted ResizeObserver polyfill loaded by overrides/main.html,
    # so the site never requests it from a third party (unpkg).
    js_dir = OUT / "assets" / "javascripts"
    js_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(THEME / "vendor" / "resize-observer-polyfill.js",
                 js_dir / "resize-observer-polyfill.js")

    # 4. Brand stylesheet.
    (OUT / "stylesheets").mkdir()
    shutil.copy2(THEME / "stylesheets" / "extra.css", OUT / "stylesheets" / "extra.css")

    # 5. Bilingual legal pages.
    for name in ("impressum.md", "datenschutz.md"):
        shutil.copy2(THEME / "content" / name, OUT / name)

    pages = len(seen) + 3  # flattened docs + index + 2 legal
    print(f"staged {pages} pages + assets into {OUT.relative_to(HERE)}/")


if __name__ == "__main__":
    main()

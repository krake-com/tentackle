# Tentackle documentation website

This directory builds the public documentation site for Tentackle with
[MkDocs](https://www.mkdocs.org/) and the
[Material](https://squidfunk.github.io/mkdocs-material/) theme. It is styled to
mimic [tentackle.org](https://tentackle.org) and is built to be GDPR-compliant
(no third-party requests, no tracking).

## How it works

The page content is **not** stored here. It is generated from the repository's
real documentation by [`prepare.py`](prepare.py):

- the project [`README.md`](../../README.md) becomes the site home page (`index.md`),
  minus its Maven Central version badge (a third-party image that the privacy
  plugin would freeze at build time),
- the core docs under [`../main/docs`](../main/docs) and every module's
  `<module>/src/main/docs/*.md` become the documentation pages (flattened, so
  every doc basename must be unique across the whole reactor),
- cross-document links are rewritten to the flat layout; links to Java source
  files (`../java/.../Foo.java`) are rewritten to the corresponding
  [GitHub](https://github.com/krake-com/tentackle) source URL,
- the brand stylesheet, favicon and the bilingual legal pages under
  [`theme/`](theme) are copied in.

The generated `docs/` and the built `site/` directories are **git-ignored** — they
are produced on demand. The real documentation sources are never modified.

```
prepare.py  ──>  docs/   ──(mkdocs)──>  site/
   ^                ^                      ^
 staging        generated             static HTML
```

## Prerequisites

- Python 3.9+
- The build dependencies:

```bash
make install        # = pip install -r requirements.txt
```

> `mkdocs-material`'s privacy plugin downloads the Roboto web font once at build
> time and serves it locally, so the published site makes no requests to Google.
> The **first** build therefore needs network access. On a fully offline host,
> set `font: false` in [`mkdocs.yml`](mkdocs.yml) to fall back to system fonts.

## Build & preview

```bash
make serve          # live preview at http://127.0.0.1:8000
make build          # production build into ./site (strict: fails on broken links)
make clean          # remove generated docs/, site/ and caches
```

Both `serve` and `build` run `prepare.py` first, so edits to the real docs, the
repo `README.md` or anything under `theme/` are always picked up.

## Editing

- **Page content:** edit the Markdown under `../main/docs` and the module
  `<module>/src/main/docs` directories (or the repo `README.md`). Do not edit
  `docs/` here — it is overwritten on every build.
- **Colors / fonts / look:** edit [`theme/stylesheets/extra.css`](theme/stylesheets/extra.css).
- **Navigation, theme options, consent banner:** edit [`mkdocs.yml`](mkdocs.yml).
  The `nav` mirrors `../main/docs/toc.md`; keep them in sync when adding a doc.
- **Legal pages:** edit [`theme/content/impressum.md`](theme/content/impressum.md)
  and [`theme/content/datenschutz.md`](theme/content/datenschutz.md). Review the
  text before publishing.

## GDPR notes

- Roboto is localized at build time by the Material **privacy** plugin — no Google
  Fonts requests at runtime.
- No analytics, no tracking cookies. Only `localStorage` is used (color-scheme
  choice + consent), disclosed in the bilingual consent banner and the privacy page.
- The **ResizeObserver polyfill is self-hosted** (see below) — no `unpkg.com`
  request is ever made.
- The **repository facts are not fetched**: for a github.com `repo_url`, Material
  would load the stars/forks from `api.github.com` in the visitor's browser. The
  override [`overrides/partials/source.html`](overrides/partials/source.html)
  keeps the repository link but not the fetch, and `extra.consent.cookies.github`
  is off in `mkdocs.yml`.
- An Impressum and a Datenschutzerklärung (both German/English) are linked from
  the navigation and the footer.

### Self-hosted ResizeObserver polyfill

Material's JavaScript bundle would, on browsers that lack a native
`ResizeObserver` (pre-2020 browsers), inject `<script src="https://unpkg.com/
resize-observer-polyfill">`. To avoid that third-party request, a copy of the
polyfill is vendored in [`theme/vendor/`](theme/vendor) and served from this
site's own origin. The theme override [`overrides/main.html`](overrides/main.html)
loads it in `<head>`, **only when `ResizeObserver` is missing**, synchronously, so
the global is defined before Material's bundle runs — Material therefore never
reaches its `unpkg.com` fallback. Native implementations are never overwritten and
modern browsers download nothing extra.

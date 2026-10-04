# Resource Bundles and Internationalization (i18n)

## Overview and Motivation

Tentackle applications are localized from the ground up: every user-visible text
is looked up in a **resource bundle** and rendered in the user's language, dates
and money respect the locale, and — in a multi-tier deployment — a single server
serves many clients each in their own language at the same time.

The framework's i18n story has three parts, and this document is the map of all
three:

| Layer                       | Where                                                         | What it does                                                                                                                                          |
|-----------------------------|---------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Foundation**              | `tentackle-common`                                            | The bundle convention, the module-aware `BundleFactory`, the `LocaleProvider`, and the multilingual `I18NText` value type.                            |
| **Database-backed bundles** | `tentackle-i18n`                                              | Serve and live-edit translations from the database instead of `*.properties` files — see [i18n.md](../../../../tentackle-i18n/src/main/docs/i18n.md). |
| **Build-time tooling**      | `tentackle-i18n-maven-plugin`, `tentackle-check-maven-plugin` | Sync property files ↔ database, and verify that every key used in the code actually resolves.                                                         |

This page focuses on the **foundation** in `tentackle-common`, on which the other
two build.

---

## The Bundle Convention

Application code never calls `ResourceBundle.getBundle(...)` directly. Instead, each
package that needs texts has a small **bundle class** annotated with
[`@Bundle`](../java/org/tentackle/common/Bundle.java), exposing a static
`getString(key)`. The pattern (from
[`FxFxBundle`](../../../../tentackle-fx/src/main/java/org/tentackle/fx/FxFxBundle.java)):

```java
@Bundle
public class FxFxBundle {
  public static ResourceBundle getBundle() {
    return BundleFactory.getBundle(FxFxBundle.class.getName(),
                                   LocaleProvider.getInstance().getLocale());
  }
  @SuppressWarnings("bundle")
  public static String getString(String key) {
    return getBundle().getString(key);
  }
  private FxFxBundle() { }
}
```

> Note: the static `getString(key)` must be annotated with `@SuppressWarnings("bundle")`.
> Its key is a parameter, not a compile-time constant, so the
> [`tentackle-check:bundles`](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md#what-is-not-covered)
> goal cannot verify it and would issue a warning otherwise. The keys passed by the call sites
> are verified instead.

Call sites read as plain, legible text:

```java
copyButton.setTooltip(new Tooltip(FxFxBundle.getString("COPY TO CLIPBOARD")));
throw new FxRuntimeException(
    MessageFormat.format(FxFxBundle.getString("CANT OPEN URL {0}"), uri), ex);
```

Three conventions are worth calling out:

- **Keys are the source text itself.** The key `COPY TO CLIPBOARD` *is* the
  English phrase (usually upper-cased). The default `FxFxBundle.properties` maps it to a
  readable English string. By default, the framework provides translations for
  German as well, i.e., `FxFxBundle_de.properties` in this case. Via the
  [i18n Maven plugin](../../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md)
  (`mvn tentackle-i18n:push` / `:pull`), the [tentackle-i18n](../../../../tentackle-i18n/src/main/docs/i18n.md)
  module, and the [BundleMonkey](../../../../tentackle-i18n/src/main/docs/bundlemonkey.md) translation tool,
  other languages can be added and deployed via the database without code changes.

  ```properties
  # FxFxBundle.properties
  CANT\ OPEN\ URL\ {0}=cannot open URL: {0}
  WARNING=Warning!
  ```
  ```properties
  # FxFxBundle_de.properties
  CANT\ OPEN\ URL\ {0}=URL konnte nicht geöffnet werden: {0}
  WARNING=Achtung!
  ```

  This means code is readable even before a translation exists. A key missing in a
  translation falls back to the English text of the default `.properties` file
  (a key missing there as well throws a `MissingResourceException`).
- **Parameters use `MessageFormat`.** Keys may contain `{0}`, `{1}`, … placeholders
  filled with `MessageFormat.format(...)`.
- **Bundle class naming** follows `<Package><Module>Bundle` — e.g., `FxFxBundle`
  (package `fx`, module *fx*), `RdcFxRdcBundle` (package `rdc`, module *fx-rdc*),
  `CommonCommonBundle`. The bundle's base name is the class name, so the `.properties`
  files live in the same package as the class — usually under `src/main/resources`, e.g.,
  `src/main/resources/org/tentackle/fx/FxFxBundle.properties`.

> **FXML and properties co-location.** Controllers are the exception: per the framework's
> FX conventions, FXML and `*.properties` files sit beside their controller in `src/main/java`
> and are copied as resources. An `@FxControllerService` controller's bundle is loaded the same
> way (`DefaultFxFactory` resolves it through `BundleFactory.getBundle(...)`).

---

## The Bundle Factory

[`BundleFactory`](../java/org/tentackle/common/BundleFactory.java) is the framework's
replacement for `ResourceBundle.getBundle(...)`. Using it instead of the JDK call
solves three problems at once (see the factory's own Javadoc):

1. **Modular (JPMS) applications.** Under the module system, a resource bundle is by
   default only visible *from its own module*, and `ResourceBundle.Control`s and
   `ResourceBundleControlProvider`s are not supported for named modules. A
   framework-level factory could not otherwise load a bundle that lives in an
   application module. Tentackle's factory uses
   [`ModuleSorter`](../java/org/tentackle/common/ModuleSorter.java) /
   [`ModuleHook`](services.md#the-modulehook) to know **which module a bundle belongs to** and
   loads it via that module's hook.
2. **Non-modular (classpath) applications.** Tentackle uses the same
   `ResourceBundleControlProvider` SPI semantics as the JDK, but locates the providers via
   its own [service finder](services.md), so the providers can be contributed by any
   application artifact.
3. **Build-time awareness.** `@Bundle`-annotated classes are recorded under
   `META-INF/bundles` during the analyze step, so the same set of bundles is visible to the
   running app *and* to the build-time plugins.

[`DefaultBundleFactory`](../java/org/tentackle/common/DefaultBundleFactory.java) is
the default implementation (registered via the [service SPI](services.md)):

- It builds a map of bundle base names → [`BundleSupport`](../java/org/tentackle/common/BundleSupport.java)
  for every `@Bundle` (or bundle-providing annotation such as `@FxControllerService`)
  found through the service finder **in a named module**.
- `findBundle(baseName, locale)` routes a **named-module** bundle through that
  module's `ModuleHook.getBundle(...)`, and everything else through the standard
  `ResourceBundle.getBundle(...)` — with the classloader set via `setClassLoader(...)`,
  if any, and optionally with a `ResourceBundle.Control` contributed by a registered
  provider (this is the hook the
  [database-backed module](../../../../tentackle-i18n/src/main/docs/i18n.md) plugs
  into for classpath applications). Controls are used only in non-modular applications:
  the JDK rejects them when `ResourceBundle.getBundle(...)` is invoked from a named
  module, so in a modular application, `getControl(...)` returns `null` and the bundle is
  loaded the standard way.
- Leading slashes are stripped from base names, so `/org/example/Texts` and
  `org/example/Texts` denote the same bundle.
- `clearCache()` flushes the JDK bundle cache of the factory's classloader — used to
  make live translation edits visible without a restart.
- `getProviders()` / `getControl(baseName)` expose the control providers and the
  control chosen for a bundle (the first provider returning one).

The factory itself is replaceable: register your own `@Service(BundleFactory.class)`
to change bundle resolution globally (which is exactly what `tentackle-i18n`'s
`StoredBundleFactory` does).

### How bundles are registered

For each annotation that marks a class as a bundle (`@Bundle`, `@FxControllerService`,
`@GuiProviderService`, …), the analyze step writes the annotated classes to
`META-INF/bundles/<annotation classname>`, one per line. A line is either just the bundle
name, or `keyName = bundleName`, if the bundle is registered under an additional key name
(such as `@Bundle("…")` or the value of a GUI provider annotation) — the bundle can then be
loaded under either name. The annotations themselves are listed in
`META-INF/services/org.tentackle.common.BundleSupport` of the module declaring them,
which is maintained by hand.

`tentackle-common` cannot run the analyze step on itself (the plugin is built later).
Hence, its own `CommonCommonBundle` is not annotated but listed manually in
`src/main/resources/META-INF/bundles/org.tentackle.common.Bundle`.

---

## The Locale Provider

[`LocaleProvider`](../java/org/tentackle/common/LocaleProvider.java) is the single
source of truth for "what language is *now*". It is a replaceable `@Service`, so an
application can override its policies — and a multilingual application must, since the
default supports English only (see "Supported locales" below).

- **Current locale (thread-local).** `getLocale()` returns the thread-local locale,
  or the JVM default if none is set. `getCurrentLocale()` returns the thread-local locale
  only (`null` if none), and `assertCurrentLocaleValid()` throws if there is none.
  `setCurrentLocale(locale)` sets it for the current thread (`null` removes it). This thread-local design is what lets a **multi-tier server**
  serve every client in its own language: each remote request sets the locale from
  the caller's `SessionInfo` and clears it afterward —

  ```java
  // RemoteDbDelegateInvocationHandler, per invocation
  LocaleProvider.getInstance().setCurrentLocale(session.getSessionInfo().getLocale());
  try { ... } finally {
    LocaleProvider.getInstance().setCurrentLocale(null);
  }
  ```

- **Effective locale.** `getEffectiveLocale(locale)` lets an application **narrow**
  the many possible request locales to the few it actually translates. The default
  is identity; an app that ships only `en` and `de` would map every `de*` to `de`
  and everything else to `en`.
- **Fallback locale.** `getFallbackLocale()` (default `Locale.ENGLISH`,
  language-only) is the locale of the keyless default `.properties` file — the last
  resort when no translation matches.
- **Supported locales.** `getEffectiveLocales()` / `isLocaleSupported(locale)`
  enumerate what the app can render; `I18NText` and the translation editor use this.
  The default supports `Locale.ENGLISH` only, and `isLocaleSupported` checks for an
  *exact* match. An application translating to `de` as well overrides all three methods,
  `getEffectiveLocale`, `getEffectiveLocales` and `isLocaleSupported`, so that, e.g.,
  `de_DE` is accepted and mapped to `de`.
- **Initial locale.** `getInitialLocale()` is the JVM default locale at the time the
  provider was created; `getEffectiveInitialLocale()` is its effective locale.
- **Locale tags & currency.** `toTag` / `fromTag` give a guaranteed round-trip for
  supported locales (unlike `Locale.toLanguageTag`), used for serialization. The tag
  joins language, country and variant with underscores (`de_DE`); it is not a BCP 47 tag.
  `getCurrency(locale)` derives the locale's currency, taking the country from the system
  property `user.country` if the locale has none; `getCurrency()` caches the currency of
  the current locale.

> Bundle locale fallback (`Texts_de_DE` → `Texts_de` → `Texts`) is the standard
> `ResourceBundle` chain — a more specific locale only needs to override the keys
> that actually differ.

---

## The `I18NText` Value Type

Resource bundles localize *static* texts known at build time. For **data** that is
itself multilingual — a product description, a category name a user types in several
languages — Tentackle provides
[`I18NText`](../java/org/tentackle/common/I18NText.java): an **immutable,
serializable** value object holding one string per locale.

```java
I18NText d = I18NText.valueOf("invoice")     // in the current locale
                     .with(Locale.GERMAN, "Rechnung");
d.get();                 // translation for the current locale
d.get(Locale.GERMAN);    // "Rechnung"
```

The example requires a `LocaleProvider` supporting `de` (see above): `valueOf`, the
constructors and `with` throw a `TentackleRuntimeException` for an unsupported locale.
Since the object is immutable, `with` returns a new `I18NText`.

Key properties:

- It is a **model attribute type**, persisted via the SQL data type
  [`I18NTextType`](../../../../tentackle-sql/src/main/java/org/tentackle/sql/datatypes/I18NTextType.java)
  (one wide column where the backend supports it, e.g., Postgres `TEXT`, otherwise a
  `VARCHAR` + overflow `CLOB`). In a model:

  ```
  I18NText  description  description  internationalized description [COLS=30]
  ```

- It implements **`CharSequence` and `Comparable` for the current locale only**
  (so it can be used like a `String` in the UI), but **`equals`/`hashCode` cover all
  translations** — so beware of sorted sets.
- `get(locale)` resolves a translation, falling back to the text's **default locale**
  (the first one set, if the fallback language wasn't provided) and then to the
  `LocaleProvider`'s fallback locale. Translations narrow to *language only*.
  Once a translation for the fallback locale is added, it replaces the default locale.
- Setting a blank or `null` text removes the translation for that locale; `isVacant()`
  tells whether there are no translations at all.
- It round-trips to/from a `ParameterString` (`default='de', de='…', en='…'`) for storage
  and transport. Translations for unsupported locales are silently dropped when parsed.

The FX layer offers an
[`I18NEditor`](../../../../tentackle-fx/src/main/java/org/tentackle/fx/translate/I18NEditor.java)
that presents one input per supported locale for editing an `I18NText`.

---

## Build-Time Discovery and Verification

i18n is wired up at build time, not by runtime scanning:

- **Discovery.** The [`@Bundle`](../java/org/tentackle/common/Bundle.java) annotation
  carries `@Analyze("…BundleAnalyzeHandler")`. During the
  [analyze](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)
  step every `@Bundle` (and other bundle-providing annotations such as
  `@FxControllerService`) is recorded under `META-INF`, so `BundleSupport` and the
  factory see exactly the same bundles at runtime and at build time, in both modular
  and classpath mode.
- **Verification.** The `bundles` goal of the
  [check plugin](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md)
  (bound to the `process-classes` phase by default) verifies that every bundle
  **key referenced in the source** actually resolves to an entry in a property file —
  catching typos and missing translations before they reach production.
- **Translation sync.** The
  [i18n Maven plugin](../../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md)
  (`mvn tentackle-i18n:push` / `:pull`) moves translations between the `*.properties`
  files and the database in both directions.

---

## Database-Backed Bundles (runtime)

The optional `tentackle-i18n` module lets translations be served from — and edited
live in — the **database** instead of (or on top of) the property files, with **no
change to calling code**. It registers a replacement `BundleFactory` /
`ResourceBundle.Control` that returns a stored translation when one exists and falls
back to the property file otherwise, and it invalidates the bundle cache through the
`ModificationTracker` so a translator's edit appears on every client and server
immediately. See **[i18n.md](../../../../tentackle-i18n/src/main/docs/i18n.md)** for
the full story.

---

## Putting It Together

1. Give each package a `@Bundle` class (`<Package><Module>Bundle`) with a default
   `.properties` plus one per locale (`…_de.properties`, …); use the source text as
   the key.
2. Look texts up with `XxxBundle.getString("…")`, formatting parameters with
   `MessageFormat`.
3. For multilingual *data*, use `I18NText` model attributes.
4. Set the locale per thread (`LocaleProvider.setCurrentLocale`) — the server does
   this automatically per remote request from the client's `SessionInfo`.
5. At build time, `analyze` records the bundles, the check plugin verifies the keys,
   and — optionally — the i18n plugin and `tentackle-i18n` module move translations
   into the database for live editing.

## See Also

- [common.md](common.md#internationalization-and-resource-bundles) — this module's overview.
- [services.md](services.md) — the `@Service` / `ModuleHook` machinery behind module-aware bundle loading.
- [i18n.md](../../../../tentackle-i18n/src/main/docs/i18n.md) — database-backed, live-editable bundles.
- [bundlemonkey.md](../../../../tentackle-i18n/src/main/docs/bundlemonkey.md) — the translation tool.
- [tentackle-i18n-maven-plugin.md](../../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md) — property-file ↔ database synchronization.
- [tentackle-check-maven-plugin.md](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md) — build-time bundle-key verification.
- [model-definition.md](../../../../tentackle-model/src/main/docs/model-definition.md) — `I18NText` as a model attribute.

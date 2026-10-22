# GraphicProvider — Icons, Images, and Symbolic Names

## Overview and Motivation

A `GraphicProvider` turns a **name** into a JavaFX `Node` with graphical content:

```java
button.setGraphic(Fx.createGraphic("save"));
```

The framework never names an icon file, an icon font, or an icon pack — it asks for `"save"`. That
indirection is the entire point, and it buys two things:

- **The application can replace the icon pack without touching framework code.** Tentackle's own
  views ask for `"save"`, `"delete"`, `"search"`; what those look like is decided by whichever
  provider is registered for the realm. An application that prefers a different icon font, or bitmaps
  instead of a font, supplies its own provider and the framework's buttons follow.
- **Icons stay themeable.** The default provider returns Ikonli `FontIcon`s, whose size and color are
  deliberately *not* set in Java code — they come from CSS, so icons track the theme (including dark
  mode) the same way the rest of the UI does.

Graphics are namespaced into **realms**. The framework uses the default realm; applications should
use their own.

> **Not to be confused with [`GuiProvider`](../../../../tentackle-fx-rdc/src/main/docs/gui-provider.md)**
> (tentackle-fx-rdc), which supplies the UI *for a PDO* — its views, tables, and so on. A
> `GuiProvider` typically *uses* a `GraphicProvider` to answer `createGraphic()` for its PDO.


## Key Concepts

### The interface

[`GraphicProvider`](../java/org/tentackle/fx/GraphicProvider.java) has a single method:

```java
Node createGraphic(String name);     // never null; throws FxRuntimeException if unknown or unloadable
```

A **new node on every call** — JavaFX nodes cannot be shared between parents, so providers must not
hand out the same instance twice. (Caching the underlying `Image` is fine and is exactly what
`ImageGraphicProvider` does.)

### Realms

A realm is a namespace for graphic names, so an application's `"object"` need not mean the
framework's `"object"`. A provider declares its realm with
[`@GraphicProviderService`](../java/org/tentackle/fx/GraphicProviderService.java):

```java
@GraphicProviderService("data")      // default "" = the tentackle realm
public class DataNodeGraphicProvider extends IkonliGraphicProvider { ... }
```

`FxFactory` discovers the annotated providers through the
[service](../../../../tentackle-common/src/main/docs/services.md) mechanism and instantiates **one
instance per realm** at startup, using the no-arg constructor. If several providers claim the same
realm, **the first one along the class-path or module-path wins** and replaces the others — which is
how an application overrides the framework's default realm. A provider found without the annotation
is logged as an error and ignored.

### Asking for a graphic

The [`Fx`](../java/org/tentackle/fx/Fx.java) facade is the front door:

```java
Fx.createGraphic("save");            // the default (tentackle) realm
Fx.createGraphic("data", "DataList"); // an application realm
```

Both delegate to `FxFactory.createGraphic(realm, name)`. A `null` realm is treated as the default
one. An unknown *realm* raises `IllegalArgumentException`; an unknown *name* is the provider's
business and raises `FxRuntimeException`.

### The two base implementations

[`IkonliGraphicProvider`](../java/org/tentackle/fx/IkonliGraphicProvider.java) produces a `FontIcon`
from an [Ikonli](https://kordamp.org/ikonli/) icon font. Subclasses implement one method:

```java
protected abstract String translateName(String name);   // symbolic name -> icon pack name
```

By convention `translateName` returns unknown names **unchanged**, so an application can also pass
native icon-pack names (`"mdi2c-cog"`) straight through alongside the symbolic ones.

[`ImageGraphicProvider`](../java/org/tentackle/fx/ImageGraphicProvider.java) produces an `ImageView`
from image files instead. It looks up `<imagePath><name><ext>`, trying `.png`, `.gif` and `.jpg` in
that order (`getExtensions()` to change). The image path defaults to `<package-of-the-provider>/images/`,
or is given to the constructor. Any path and extension in the requested *name* are stripped first, so
`"icons/save.png"` and `"save"` resolve identically. Resources are loaded via the provider's own
class, which is what makes it work under JPMS — the images live in **your** module, and only your
module can read them.

Loaded images are cached in a `ConcurrentHashMap`. Note that **misses are cached too**: a name that
resolves to no file is remembered as absent, so a later-arriving resource won't be picked up. That is
fine for packaged icons and worth knowing if you ever generate them at runtime.

### The default realm's vocabulary

[`DefaultGraphicProvider`](../java/org/tentackle/fx/DefaultGraphicProvider.java) serves the default
realm, mapping symbolic names to the Ikonli **materialdesign2** pack (a compile-scope dependency of
tentackle-fx, `requires transitive` in its `module-info`). These are the names the framework itself
asks for — the vocabulary any replacement for the default realm should answer:

|               |               |            |             |
|---------------|---------------|------------|-------------|
| `about`       | `add`         | `browser`  | `cancel`    |
| `checked`     | `close`       | `collapse` | `copy`      |
| `cut`         | `delete`      | `down`     | `edit`      |
| `exit`        | `expand`      | `file`     | `filter`    |
| `filter-active` | `generate`  | `help`     | `hide`      |
| `id`          | `login`       | `new`      | `object`    |
| `ok`          | `password`    | `preferences` | `print`  |
| `reload`      | `run`         | `save`     | `script`    |
| `search`      | `security`    | `session`  | `stop`      |
| `subtract`    | `table`       | `theme`    | `tree`      |
| `unchecked`   | `unknown`     | `up`       | `view`      |

To swap the pack while keeping the framework's icons working, subclass `IkonliGraphicProvider` (or
`ImageGraphicProvider`), annotate it `@GraphicProviderService` with the default realm, cover these
names, and make sure it precedes tentackle-fx on the path. Overriding `DefaultGraphicProvider` and
its `createNameMap()` is the lighter option when you only want to re-point a few names.

### Styling

Icon geometry and color belong in CSS, not in provider code. `tentackle.css` sets the baseline for
every font icon:

```css
.ikonli-font-icon {
  -fx-icon-size: 16;
  -fx-icon-color: black;
}
```

Because it's ordinary CSS, context can override it — as the security rules view does to color the
same symbolic icon by meaning:

```css
.sec-allow .ikonli-font-icon { -fx-icon-color: green; }
.sec-deny  .ikonli-font-icon { -fx-icon-color: red; }
```

This is why `IkonliGraphicProvider` sets neither size nor color: doing so in Java would pin the icon
against the theme.

**macOS caveat:** font icons cannot be rendered in the system menu bar. `MacUtilities.toSystemMenuBar(...)`
walks a `MenuBar` and converts every `FontIcon` graphic to an `ImageView` (sized, and colored for the
current light/dark mode) before enabling the system menu bar. Because the images are rendered for the
theme that is active at that moment, the menu bar is registered and its images are rendered again
whenever the light/dark mode or the theme changes — otherwise the icons would keep the old color
until the menu bar is rebuilt.

The conversion renders the glyph with `Node#snapshot`, which performs a CSS pass first. Size and color
are therefore applied as an *inline* style: `FontIcon` reports `StyleOrigin.USER_AGENT` for
`iconSize` and `iconColor` even when they were set programmatically, so a matching rule of the user
agent stylesheet — the AtlantaFX themes define `.ikonli-font-icon { -fx-icon-size: 18px; ... }` —
would otherwise win over the values chosen for the menu bar and yield mis-sized images that macOS
has to rescale. The glyph is snapshotted centered in a square of exactly the system menu icon size.


## How It Fits Together

An application adding its own icons for its own domain objects:

1. Write a provider — extend `IkonliGraphicProvider` to reuse the materialdesign2 pack that is
   already on the path, or `ImageGraphicProvider` to ship image files under
   `<your-package>/images/`.
2. Annotate it `@GraphicProviderService("myrealm")` and map your symbolic names in
   `translateName(...)`. Keep the realm constant public — callers need it.
3. The [tentackle-maven-plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)
   picks the annotated class up at build time and writes the `META-INF/services` entry; no runtime
   scanning happens.
4. At startup `FxFactory` instantiates one provider per realm.
5. Views ask for `Fx.createGraphic(MyGraphicProvider.REALM, "Invoice")`, or — in an RDC application —
   return it from the PDO's `GuiProvider.createGraphic()`, which is what `PdoTreeCell` and the table
   cells call.
6. Sizes and colors come from your CSS; the framework's own icons keep working because they resolve
   through the default realm, untouched.


## Package Layout

| Type                                                                  | Role                                            | Module           |
|-----------------------------------------------------------------------|-------------------------------------------------|------------------|
| `GraphicProvider`, `GraphicProviderService`                           | The contract and its service annotation         | tentackle-fx     |
| `IkonliGraphicProvider`, `ImageGraphicProvider`                       | Base classes for font-icon and image providers  | tentackle-fx     |
| `DefaultGraphicProvider`                                              | The default realm (materialdesign2)             | tentackle-fx     |
| `Fx`, `FxFactory`                                                     | `createGraphic(...)` entry points, realm registry | tentackle-fx   |
| `MacUtilities`                                                        | Font icon → image conversion for the macOS system menu | tentackle-fx |
| `GuiProvider`                                                         | Per-PDO UI, including its `createGraphic()`     | tentackle-fx-rdc |


## Related Documentation

- [tentackle-fx](fx.md) — the extended JavaFX layer this belongs to.
- [GuiProvider](../../../../tentackle-fx-rdc/src/main/docs/gui-provider.md) — the per-PDO UI provider that supplies a PDO's graphic.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — how `@GraphicProviderService` is discovered.
- [tentackle-fx-atlanta](../../../../tentackle-fx-atlanta/src/main/docs/atlanta.md) — theming, including dark mode.
- [Tables and Trees](tables.md) — cell types that render symbolic icons (`checked` / `unchecked`).

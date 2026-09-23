# tentackle-fx-atlanta — The AtlantaFX Theme and Component Layer

## Overview and Motivation

`tentackle-fx-atlanta` integrates [AtlantaFX](https://github.com/mkpaz/atlantafx)
into Tentackle's desktop UI stack. It provides two things:

1. **A modern look and feel** — a `ThemeUtilities` implementation that drives the
   AtlantaFX user-agent stylesheets (Primer, Cupertino, Nord, each in a light and
   a dark variant) plus the Tentackle-specific CSS that colors mandatory, error,
   and info states.
2. **Extended AtlantaFX controls** — a handful of AtlantaFX controls and
   containers wrapped as first-class Tentackle FX components, so they bind to the
   model, validate, and switch read-only exactly like the standard
   [`tentackle-fx`](../../../../tentackle-fx/src/main/docs/fx.md) components.

The module lives in package `org.tentackle.fx.atlanta` (JPMS module
`org.tentackle.fx.atlanta`). It `requires transitive org.tentackle.fx.rdc` — so
pulling it in transitively brings the whole extended FX layer and the Rich Data
Components — and `requires transitive atlantafx.base` for the AtlantaFX controls
themselves. The `atlantafx-base` dependency comes from `io.github.mkpaz`.

### Why a separate module?

AtlantaFX is an optional, third-party styling and control library. Keeping it in
its own module means:

- An application opts into the AtlantaFX theme by adding a single dependency; the
  core FX layer carries no AtlantaFX coupling.
- The extra controls AtlantaFX ships (a `Calendar` popup, a `ToggleSwitch`,
  masked/custom text fields, `Card`/`Tile`/`InputGroup` containers) become
  available as bindable Tentackle components without bloating `tentackle-fx`.
- The theme is selected via the SPI (`@Service(ThemeUtilities.class)`), so simply
  having the module on the path makes it the active theme.

### Design principles

The module follows the exact conventions of `tentackle-fx` — read
[fx.md](../../../../tentackle-fx/src/main/docs/fx.md) first; everything there
about components, delegates, builders, configurators, value translators, and SPI
discovery applies here unchanged. This module only adds new instances of those
patterns for the AtlantaFX controls:

- **Wrap, don't replace.** Each component *extends* its AtlantaFX control
  (`FxToggleSwitch extends ToggleSwitch`), adding the `FxComponent`/`FxContainer`
  contract on top.
- **Delegate over inheritance.** The cross-cutting behavior lives in a delegate;
  the component is a thin wurblet-generated shell forwarding to it.
- **Configure by convention, override by service.** Builders, configurators, and
  value translators are all SPI services discovered by `FxFactory`.

---

## The Theme — `AtlantaThemeUtilities`

`AtlantaThemeUtilities` is the heart of the module. It is registered as
`@Service(ThemeUtilities.class)` and extends `RdcThemeUtilities`, so it inherits
the RDC theming behavior and overrides the AtlantaFX-specific parts.

| Aspect                | Behaviour                                                                                                                                                              |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Supported themes      | `Primer`, `Cupertino`, `Nord` (`getSupportedThemes()`)                                                                                                                 |
| Default theme         | `Cupertino` on macOS, otherwise `Primer` (chosen in the constructor)                                                                                                   |
| Default font size     | 14                                                                                                                                                                     |
| User-agent stylesheet | `applyUserAgentStylesheet()` selects `/atlantafx/base/theme/<theme>-light.css` or `-dark.css` according to `isDarkMode()`                                              |
| Scene stylesheets     | `applyStylesheets(scene)` sets `tentackle.css` (or `tentackle-dark.css` in dark mode) plus `pdo-style.css`                                                             |
| Resource redirection  | `getResourceURL(...)` redirects the CSS lookups of `NotificationBuilder`, `Note` and `TotalsTableView` to this module, so they pick up the AtlantaFX-tuned stylesheets |
| Auto-adjust padding   | `getAutoAdjustPadding()` returns 16                                                                                                                                    |

### Bundled stylesheets and assets

The CSS and image resources live in `src/main/resources/org/tentackle/fx/atlanta`:

| Resource                               | Role                                                                                                                                                                                                                                                            |
|----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `tentackle.css` / `tentackle-dark.css` | Tentackle extensions on top of the AtlantaFX theme: defines the `-tt-mandatory-color`, `-tt-error-color`, `-tt-alert-color` and `-tt-info-color` variables (mapped to the AtlantaFX warning/danger/success palette), the `.tt-mandatory`, `.tt-error`, `.tt-info` style classes and the `:tt-alert` pseudo-class |
| `pdo-style.css` / `pdo-style.png`      | The `.tt-pdo-style` decoration marking a field that references a PDO                                                                                                                                                                                            |
| `note.css` / `note-dark.css`           | Styling for the `Note` speech-bubble component                                                                                                                                                                                                                  |
| `notification.css`                     | Styling for the toast/notification overlay built by `DefaultNotificationBuilder`                                                                                                                                                                                |
| `totalstable.css`                      | Styling for `TotalsTableView` (fixed row height, hidden header and scrollbars)                                                                                                                                                                                  |
| `i18n-style.png`                       | Icon marking an internationalised field                                                                                                                                                                                                                         |

---

## Extended Components

All four components implement the standard `FxComponent` / `FxTextComponent`
contract through a delegate, with the forwarding methods generated by the
`delegate`/`component`/`textcomponent` wurblets (see the delegate pattern in
[fx.md](../../../../tentackle-fx/src/main/docs/fx.md)).

| Component           | Extends (AtlantaFX) | View type   | Notes                                                                                                                                                            |
|---------------------|---------------------|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `FxCalendar`        | `Calendar`          | `LocalDate` | A pop-up calendar/date picker. Its delegate auto-creates a `ValueTranslator` from the model type to `LocalDate`.                                                 |
| `FxToggleSwitch`    | `ToggleSwitch`      | `Boolean`   | A modern on/off switch; translates the model type to/from `Boolean`.                                                                                             |
| `FxCustomTextField` | `CustomTextField`   | `String`    | A text field that can carry leading/trailing nodes (icons, buttons). Full `FxTextComponent` feature set (columns, pattern, case conversion, auto-completion, …). |
| `FxMaskTextField`   | `MaskTextField`     | `String`    | A text field with an input mask, otherwise identical to `FxCustomTextField`.                                                                                     |

### Component delegates

The delegates supply the toolkit-specific glue:

- **`FxCalendarDelegate`** (`extends FxComponentDelegate`) — reads/writes
  `Calendar.valueProperty()` as a `LocalDate`; on `setType(...)` it creates a
  translator from the model type to `LocalDate` (unless the application already
  set one).
- **`FxToggleSwitchDelegate`** (`extends FxComponentDelegate`) — reads/writes
  `ToggleSwitch.selectedProperty()` as a `Boolean`; creates a model↔`Boolean`
  translator on `setType(...)`.
- **`FxCustomTextFieldDelegate`** / **`FxMaskTextFieldDelegate`** (both
  `extends AbstractTextFieldDelegate<…>`) — treat all-whitespace text as `null`,
  map `columns`/`maxColumns` to the control's `prefColumnCount`, and apply the
  configured text alignment when a value is set.

### Configurators

Per-type `Configurator`s (registered with `@ConfiguratorService`) run against
every instance:

- **`CalendarConfigurator`** and **`ToggleSwitchConfigurator`** subscribe to the
  control's `valueProperty()` / `selectedProperty()` and push the change into the
  model immediately — so a switch toggle or a calendar pick updates the bound
  value instantly, not only on focus-lost.
- **`CustomTextFieldConfigurator`** and **`MaskTextFieldConfigurator`** extend
  `TextInputControlConfigurator`, inheriting the standard text-field setup.

### Builders

Each component has an `@BuilderService` builder (`CalendarBuilder`,
`ToggleSwitchBuilder`, `CustomTextFieldBuilder`, `MaskTextFieldBuilder`) that the
FXML `BuilderFactory` uses to instantiate the Tentackle component whenever the
FXML references the corresponding AtlantaFX control — so an `<Calendar/>` in FXML
becomes an `FxCalendar` automatically.

---

## Extended Containers

Three AtlantaFX layout controls are wrapped as `FxContainer`s, with the
container behavior supplied by a delegate (`fxcontainer` wurblet):

| Container      | Extends (AtlantaFX) | Role                                                                 |
|----------------|---------------------|----------------------------------------------------------------------|
| `FxCard`       | `Card`              | A card with header, subheader, body and footer regions               |
| `FxTile`       | `Tile`              | A titled row with description, optional graphic and an action node   |
| `FxInputGroup` | `InputGroup`        | Glues several inputs together into one visually-joined control group |

`FxCard` and `FxTile` expose `getChildren()` so they participate in the container
hierarchy. Their configurators (`CardConfigurator`, `TileConfigurator`,
`InputGroupConfigurator`) are currently pass-through hooks reserved for future
look-and-feel tweaks, and each container has the matching `@BuilderService`
builder.

---

## Value Translators

Two translators are added for the `Calendar` control (whose view type is
`LocalDate`), registered with `@ValueTranslatorService`:

- **`LocalDateLocalDateTranslator`** — an `IdentityTranslator<LocalDate>` for the
  common case where the model itself is a `LocalDate`.
- **`DateLocalDateTranslator`** — converts between a `java.util.Date`
  (including `java.sql.Date` and `org.tentackle.common.Date`) and the calendar's
  `LocalDate`, returning the concrete `Date` subtype that matches the bound
  component's model type.

All other model types reach `LocalDate` (or `Boolean`) through the built-in
translators of `tentackle-fx`; the delegate picks the right one via
`FxFactory.createValueTranslator(...)`.

---

## Module Integration

- **`org.tentackle.fx.atlanta.service.Hook`** is provided as a
  `org.tentackle.common.ModuleHook`, giving the framework a per-module entry point
  for resource-bundle loading.
- The module is declared `open`, so reflection-based access (FXML loading, TRIP)
  works on the module path.

---

## How It Fits Together

1. Adding `tentackle-fx-atlanta` to an application puts `AtlantaThemeUtilities` on
   the path, which the SPI selects as the active `ThemeUtilities`.
2. At startup the theme applies the chosen AtlantaFX user-agent stylesheet plus
   the Tentackle `tentackle.css` / `pdo-style.css`, so mandatory/error/info states
   and PDO decorations render in the AtlantaFX palette.
3. FXML (or programmatic) views can use the AtlantaFX controls; the builders turn
   them into `FxCalendar`, `FxToggleSwitch`, `FxCustomTextField`,
   `FxMaskTextField`, `FxCard`, `FxTile` and `FxInputGroup`.
4. Configurators wire the new controls for immediate model updates, and the
   delegates plus value translators bind them to the model in the model's own
   type — just like every other Tentackle FX component.

---

## Package Reference

| Package                                                              | Role                                                                                        |
|----------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `org.tentackle.fx.atlanta`                                           | `AtlantaThemeUtilities` — the AtlantaFX `ThemeUtilities` and bundled stylesheets/assets     |
| `org.tentackle.fx.atlanta.component`                                 | Extended components: `FxCalendar`, `FxToggleSwitch`, `FxCustomTextField`, `FxMaskTextField` |
| `org.tentackle.fx.atlanta.component.delegate`                        | Component delegates holding the behaviour                                                   |
| `org.tentackle.fx.atlanta.component.config`                          | Per-component `Configurator`s                                                               |
| `org.tentackle.fx.atlanta.component.build`                           | Component builders used by the FXML builder factory                                         |
| `org.tentackle.fx.atlanta.container`                                 | Extended containers: `FxCard`, `FxTile`, `FxInputGroup`                                     |
| `org.tentackle.fx.atlanta.container.delegate` / `.config` / `.build` | Container delegates, configurators and builders                                             |
| `org.tentackle.fx.atlanta.translate`                                 | `LocalDateLocalDateTranslator`, `DateLocalDateTranslator` for the `Calendar` control        |
| `org.tentackle.fx.atlanta.service`                                   | Module hook (`Hook`) registered as a `ModuleHook`                                           |

See also: [fx.md](../../../../tentackle-fx/src/main/docs/fx.md) for the extended
FX layer this module builds on, and
[rdc.md](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) for the Rich Data
Components it depends on.

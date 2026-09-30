# InteractiveError — From a Validation Result to a Control on Screen

## Overview and Motivation

[Validation](../../../../tentackle-core/src/main/docs/validation.md) is deliberately UI-agnostic. A
`ValidationResult` describes a problem in terms of the **model**: a message, an optional error code,
and a *validation path* such as `invoice.lineList[3].price`. It may have been produced on a server,
travel back through [TRIP](../../../../tentackle-core/src/main/docs/trip.md), and arrive at a client
that never ran the constraint.

A user interface, though, has to answer a different question: **which widget do I mark, and how?**

`InteractiveError` is that link. It pairs a validation result with the concrete `FxControl` it
belongs to, and adds the few facts the presentation needs — is this an error or merely a warning,
what text do we show, and how do we bring the offending control into view. It is the last step of the
chain that carries a failure from a constraint on the model to a red-marked field on the screen.

```
ValidationResult  ──►  ValidationMapper  ──►  Binding  ──►  InteractiveError  ──►  marked FxComponent
 (model path)          (path rewriting)      (component)     (text + control)      (tt-error / popup)
```


## Key Concepts

### The interface

[`InteractiveError`](../java/org/tentackle/fx/InteractiveError.java):

| Member                | Meaning                                                                    |
|-----------------------|----------------------------------------------------------------------------|
| `isWarning()`         | `true` = warning, `false` = error. Decides info vs. error presentation.     |
| `getText()`           | The message shown to the user.                                              |
| `getErrorCode()`      | Optional stable code identifying the error independent of the message text. |
| `getControl()`        | The related `FxControl` — **may be null** if the error maps to no widget.   |
| `showControl()`       | Bring the control to the user's attention. Default: request the focus.      |
| `getValidationResult()` | The originating result, `null` if the error was created explicitly.       |

**Warning or error** is not a separate decision — it is read off the result:
`warning = !validationResult.hasFailed()`. That lines up exactly with the two shipped result types:
`FailedValidationResult` (`hasFailed() == true`) becomes an error, `InfoValidationResult`
(`hasFailed() == false`) becomes a warning.

### The factory

[`InteractiveErrorFactory`](../java/org/tentackle/fx/InteractiveErrorFactory.java) is a singleton
obtained via `InteractiveErrorFactory.getInstance()`, defaulting to
`DefaultInteractiveErrorFactory` (a `@Service`, replaceable by an application). It offers two ways to
create an error:

```java
// 1. from a validation result — the factory finds the control itself
InteractiveError createInteractiveError(NavigableSet<ValidationMapper> mappers,
                                        Binder binder, ValidationResult validationResult);

// 2. explicitly — the caller supplies everything
InteractiveError createInteractiveError(boolean warning, String text, String errorCode,
                                        ValidationResult validationResult, FxControl control);
```

The second form is for errors that never came from the validation machinery — a failed save, a
rejected login — but that you still want to present and attach to a field the same way.

### How the control is found

This is the interesting part of the default factory, and it is where the
[`ValidationMapper`](../../../../tentackle-core/src/main/docs/validation.md) work pays off:

1. `ValidationUtilities.mapValidationPath(mappers, binder, path)` rewrites the **validation path**
   into a **binding path**, possibly handing off to a different (nested) binder along the way.
2. The resulting binder is asked for the binding at that path.
3. If — and only if — that binding is an `FxComponentBinding`, its component becomes the error's
   control.

If any step doesn't resolve, `getControl()` is simply `null`. That is not a failure: the error still
carries its text and still reaches the user through the dialog; it just isn't attached to a widget.
Errors on model members that have no on-screen counterpart behave exactly this way.

### `showControl()` — an extension point, not a callback

`showControl()`'s default implementation requests the focus for the control, and its Javadoc invites
applications to override it to "switch tabs, point to rows in tables, etc."

Worth knowing before you rely on it: **nothing in the framework calls `showControl()` today.** The
standard flow (below) marks the components directly instead. It is a hook available to application
code that wants richer navigation — override it, and call it yourself from your own error handling.
`DefaultInteractiveError` has a copy constructor precisely for this, so an override can wrap an
existing error in an anonymous subclass:

```java
InteractiveError navigating = new DefaultInteractiveError(error) {
  @Override
  public void showControl() {
    tabPane.getSelectionModel().select(detailsTab);   // reveal it first
    super.showControl();                              // then focus
  }
};
```


## How It Fits Together

The standard flow, as implemented by
[`AbstractValidateableFxController.validateForm()`](../java/org/tentackle/fx/AbstractValidateableFxController.java):

1. `prepareValidation()` clears previous errors (`container.clearErrors()`) and saves the view into
   the model — the latter so that errors clear as soon as the user edits the offending field.
2. `ValidationUtilities.validate(this)` runs the constraints and throws `ValidationFailedException`
   carrying the list of results.
3. `FxUtilities.showValidationResults(view, ex, validationMappers, binder)` turns each result into an
   `InteractiveError` (resolving controls as described above) and splits them into warnings and
   errors by `isWarning()`.
4. The texts are concatenated and shown in **one** dialog — an error dialog if there is at least one
   error (with the warnings appended below), an info dialog if there are only warnings.
5. When the user dismisses the dialog, the callback marks every error that resolved to an
   `FxComponent`: `setError(text)` for errors, `setInfo(text)` for warnings. That is what lights up
   the `tt-error` / `tt-info` styles and the component's error popup.
6. `validateForm()` returns `false` if there was at least one real error — warnings alone do not
   block.

`PdoCrud.showValidationResults(ex)` does the same for RDC editors, passing the editor's validation
mappers and binder.

So an application typically never touches `InteractiveError` at all — it falls out of
`validateForm()`. You reach for it when you want to **change** that behavior: replace the factory to
resolve controls differently, or build errors explicitly for problems that validation never saw.


## Package Layout

| Type                            | Role                                                       | Module       |
|---------------------------------|------------------------------------------------------------|--------------|
| `InteractiveError`              | The contract: text, severity, code, control                | tentackle-fx |
| `InteractiveErrorFactory`       | Singleton factory; `@Service`-replaceable                  | tentackle-fx |
| `DefaultInteractiveError`       | Default implementation (incl. the copy constructor)        | tentackle-fx |
| `DefaultInteractiveErrorFactory`| Maps validation path → binding → `FxComponent`             | tentackle-fx |
| `FxUtilities`                   | `createInteractiveError(s)`, `showValidationResults`       | tentackle-fx |
| `ErrorPopupSupported` / `InfoPopupSupported` | `setError` / `setInfo` on the component        | tentackle-fx |


## Related Documentation

- [Validation](../../../../tentackle-core/src/main/docs/validation.md) — where `ValidationResult`s and validation paths come from, and the `ValidationMapper` that rewrites them.
- [Binding](../../../../tentackle-core/src/main/docs/binding.md) — bindings, binding paths, and setting errors directly on a component from a translator.
- [tentackle-fx](fx.md) — the component error/info popups and the `tt-error` / `tt-info` styles.
- [tentackle-fx-atlanta](../../../../tentackle-fx-atlanta/src/main/docs/atlanta.md) — the `-tt-error-color` / `-tt-info-color` theme variables.
- [tentackle-fx-rdc](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) — `PdoCrud` and the editor flow.

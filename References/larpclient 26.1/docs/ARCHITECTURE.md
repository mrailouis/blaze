# Architecture

## Overview

The public mod lives in `larp/` and is split into a small number of layers:

- `bootstrap`: application startup, event registration, keybind registration, and lifecycle wiring
- `module`: base module abstraction, settings, persistence, and module catalog
- `features`: gameplay modules grouped by domain and phase
- `ui`: click GUI, HUD editors, rendering helpers, fonts, and toasts
- `mixin`: Minecraft hooks and accessors used by feature modules
- `util`: shared helpers for logging, text cleanup, and version helpers

## Startup Flow

`LarpClientClient` stays intentionally thin and delegates startup to `ClientBootstrap`.

Startup order:

1. Authenticate and start the heartbeat manager.
2. Bootstrap module, config, and render-dependent services.
3. Register listeners and commands.
4. Register render, lifecycle, and keybinding hooks.

This keeps the entry point easy to audit and change.

## Modules

`ModuleManager` owns:

- the module catalog
- build flavor filtering
- category grouping
- keybind toggling and per-tick dispatch

Each module is still responsible for its own gameplay state, settings, and rendering behavior.

## Logging

Use `LarpLog` for operational messages.

- `info`: important lifecycle events
- `warn`: recoverable issues
- `error`: failures, optionally with stack traces
- `debug`: verbose diagnostics gated behind `-Dlarpclient.debug=true`

This keeps normal logs readable without removing debuggability.

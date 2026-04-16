# Larp

Larp is the public Fabric client-side mod for Minecraft `26.1`.

## Build

From the workspace root:

```bash
./gradlew buildLarp
./gradlew runLarp
./gradlew :larp:test
```

From the exported public repo:

```bash
./gradlew build
./gradlew runClient
./gradlew test
```

Artifacts are written to `build/libs/`.

## Structure

- `src/main/kotlin/me/mrai/larpclient/bootstrap`: startup wiring and lifecycle hooks
- `src/main/kotlin/me/mrai/larpclient/module`: module model, registry, and config
- `src/main/kotlin/me/mrai/larpclient/features`: gameplay and UI features
- `src/main/kotlin/me/mrai/larpclient/ui`: click GUI, HUD, fonts, and toasts
- `src/main/kotlin/me/mrai/larpclient/util`: shared helpers
- `src/main/java/me/mrai/larpclient/mixin`: Fabric mixins and accessors
- `src/test/kotlin`: utility and module tests

More detail is in `docs/ARCHITECTURE.md` in the workspace or exported public repo.

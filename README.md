# blaze

## Workspace layout

This repository is a shared-source multi-version Fabric workspace:

- `src/main/**` contains the shared Blaze code and resources used by every target.
- `versions/1.21.10` contains build settings and optional compatibility overrides for Minecraft 1.21.10.
- `versions/1.21.11` contains build settings and optional compatibility overrides for Minecraft 1.21.11.
- `versions/26.1` contains build settings and optional compatibility overrides for Minecraft 26.1.

Add new features to the root `src/main` tree by default. Only put code under `versions/<mc-version>/src/main` when a version needs a compatibility shim or a mapping-specific implementation.

## Commands

Run these from the repository root:

- `./gradlew build` builds all supported Minecraft versions.
- `./gradlew build12110`, `./gradlew build12111`, `./gradlew build261` build one target.
- `./gradlew runClient12110`, `./gradlew runClient12111`, `./gradlew runClient261` launch the client for one target.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.

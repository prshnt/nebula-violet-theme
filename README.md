# Nebula Violet Theme

A deep dark-purple theme for IntelliJ-based IDEs (2024.1+), with two variants:

| Theme | Use it with |
|---|---|
| **Nebula Violet** | Classic UI, or the New UI without islands |
| **Nebula Violet Islands** | The Islands UI (2025.3+): rounded tool windows and editor on a darker frame |

Both use the bundled **Nebula Violet** editor color scheme.

## Layout

- `src/main/resources/themes/NebulaViolet.theme.json` holds the palette and UI keys (single source of truth)
- `src/main/resources/themes/NebulaViolet.xml` is the editor color scheme
- `src/islands/NebulaVioletIslands.overlay.json` holds only the Islands-specific keys. The build
  (`generateIslandsTheme`) merges it over the classic theme to produce `NebulaVioletIslands.theme.json`.

## Build and try

```bash
./gradlew buildPlugin      # -> build/distributions/nebula-violet-theme-<version>.zip
./gradlew runIde           # sandbox IDE with the theme loaded
./gradlew verifyPlugin     # JetBrains Plugin Verifier against current + latest IDEA
```

Install manually via *Settings | Plugins | ⚙ | Install Plugin from Disk…* and pick the zip.

## Publishing to JetBrains Marketplace

1. Bump `version` in `gradle.properties` and add a `<change-notes>` entry in `plugin.xml`.
2. Create a signing certificate ([docs](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)) and export:
   `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`.
3. First release: `./gradlew signPlugin`, then upload `build/distributions/*-signed.zip` at
   <https://plugins.jetbrains.com/plugin/add>. Choose the *Themes* category, set the license URL, and add screenshots of both variants.
4. Later releases: `PUBLISH_TOKEN=<token> ./gradlew publishPlugin`.

## License

MIT, see [LICENSE](LICENSE).

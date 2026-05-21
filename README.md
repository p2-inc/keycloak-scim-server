# keycloak-scim-server — Phase Two release branch

This branch (`phasetwo-release`) hosts the Phase Two release automation for
[`Metatavu/keycloak-scim-server`](https://github.com/Metatavu/keycloak-scim-server).
It is intentionally isolated from the upstream code so it is never touched by
upstream merges.

## What it does

- Polls `Metatavu/keycloak-scim-server`'s `develop` branch (daily and on
  manual dispatch).
- When upstream HEAD has not been released yet, builds the source from that
  commit, repackages it under `io.phasetwo.keycloak:keycloak-scim-server`, and
  publishes it to Maven Central.
- Versioning: takes upstream's base version from `gradle.properties` (e.g.
  `1.5.0-SNAPSHOT`), strips `-SNAPSHOT`, and appends a 4th segment that
  increments per release. The first release of base `1.5.0` is `1.5.0.1`,
  the next `1.5.0.2`, and so on. If upstream bumps the base (e.g. to
  `1.5.1-SNAPSHOT`), the next release will be `1.5.1.1`.
- Tags each release as `v{version}` on the upstream commit and pushes the
  tag to `p2-inc/keycloak-scim-server`.

## Repository layout

```
.github/
  workflows/
    release-from-upstream.yml   # the scheduled + manual workflow
  release/
    version.sh                  # next-version computation
    phasetwo-release.gradle.kts # Gradle overlay applied to upstream's build
```

## Setup checklist

1. **Default branch.** Make `phasetwo-release` the default branch on
   `p2-inc/keycloak-scim-server`. GitHub only runs scheduled workflows from
   the default branch, so the daily cron will not fire unless this is set.
   The `develop`/`master` branches can stay protected and untouched.
2. **Secrets** (already provided by the user, listed for reference):
   - `GPG_KEY` — ascii-armored secret key for artifact signing.
   - `OSSRH_USERNAME` / `OSSRH_PASSWORD` — Sonatype credentials.
   - `SSH_PRIVATE_KEY` — deploy key with push access for tagging.
3. **Maven Central namespace.** The `io.phasetwo` group must be approved on
   the Sonatype account behind `OSSRH_USERNAME`. (It already is — same
   namespace used by `phasetwo-admin-portal`, `keycloak-orgs`, etc.)

## How the workflow patches upstream's build

The workflow does not modify the upstream repo. For each run it:

1. Checks out `phasetwo-release` (for tooling) and
   `Metatavu/keycloak-scim-server@develop` (for source) into separate
   directories.
2. Copies `phasetwo-release.gradle.kts` into the source tree.
3. Inserts the `io.github.gradle-nexus.publish-plugin` into the upstream
   `build.gradle.kts` `plugins {}` block (anchored on the `org.sonarqube`
   line, which is stable).
4. Appends `apply(from = "phasetwo-release.gradle.kts")` to the upstream
   `build.gradle.kts`. The overlay removes the existing GitHub Packages
   publication, registers a `mavenJava` publication under groupId
   `io.phasetwo.keycloak`, attaches sources + javadoc jars and full POM
   metadata, configures GPG signing via `useGpgCmd()`, and points the
   nexus-publish plugin at `s01.oss.sonatype.org`.
5. Rewrites `gradle.properties` `version=` to the next `X.Y.Z.N` value.
6. Runs
   `./gradlew publishMavenJavaPublicationToSonatypeRepository closeAndReleaseSonatypeStagingRepository`.
7. Tags the upstream HEAD commit with `v{version}` and pushes the tag to
   `p2-inc/keycloak-scim-server`.

## Manual release

Use **Run workflow** in the Actions tab and optionally set `force: true` to
release even if the upstream HEAD already has a tag.

## Java package names are unchanged

Only the published Maven coordinates change. The source-level package
(`fi.metatavu.keycloak.scim.server.*`) is preserved, so consumers only need
to update their dependency declarations — not their imports.

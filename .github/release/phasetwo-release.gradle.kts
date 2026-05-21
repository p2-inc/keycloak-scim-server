// Phase Two release overlay for keycloak-scim-server.
//
// Loaded via `apply(from = "phasetwo-release.gradle.kts")` appended to
// build.gradle.kts by the release workflow. The com.vanniktech.maven.publish
// plugin (added to plugins{} by the workflow) handles:
//   - Publication setup with sources + javadoc jars
//   - Upload + automatic release to the Sonatype Central Portal
//
// This overlay covers the rest:
//   - Wires the signing plugin to use the GPG command-line (vanniktech's
//     signAllPublications() does not call useGpgCmd() for us).
//   - Disables every task tied to the upstream "gpr" publication so it
//     doesn't run as part of the release build. We intentionally do NOT
//     remove the gpr publication from the publishing container, because
//     calling publications.findByName("gpr") forces its lazy realization
//     and downstream re-iteration triggers a duplicate-task-name error.
//     Leaving the publication lazy and disabling its tasks is safe because
//     vanniktech's publishAndReleaseToMavenCentral task only depends on
//     tasks for its own "maven" publication.
//   - Suppresses the "enforced-platform" validation that would otherwise
//     reject the upstream `implementation(enforcedPlatform(...))` dep.
//   - Tells javadoc to be tolerant of missing tags.
//   - Ensures the sources jar (added by vanniktech's JavaLibrary configuration)
//     waits for the OpenAPI model generation.

tasks.named<Javadoc>("javadoc") {
    isFailOnError = false
    (options as? StandardJavadocDocletOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

// Disable every task associated with the upstream "gpr" / GitHubPackages
// publication. configureEach is lazy, so this only fires when a matching
// task actually gets realized.
tasks.configureEach {
    if (name.contains("GprPublication") || name.contains("ToGitHubPackagesRepository")) {
        enabled = false
    }
}

afterEvaluate {
    // Configure the signing plugin (applied by vanniktech's signAllPublications)
    // to use the local GPG command-line tool. Without this, the signing
    // plugin has no signatory and signing fails with
    // "Cannot perform signing task ... because it has no configured signatory".
    // The key to use is selected via the -Psigning.gnupg.keyName=<id> property
    // set by the release workflow.
    extensions.findByType<org.gradle.plugins.signing.SigningExtension>()?.apply {
        useGpgCmd()
    }

    // Upstream uses implementation(enforcedPlatform("org.keycloak.bom:...")).
    // Gradle refuses to embed enforced-platform refs in published module
    // metadata; suppress so the publication metadata generates.
    tasks.withType<org.gradle.api.publish.tasks.GenerateModuleMetadata>().configureEach {
        suppressedValidationErrors.add("enforced-platform")
    }

    // Ensure the sources jar (added by vanniktech's JavaLibrary configuration)
    // waits for the OpenAPI model generation so the included source has
    // every class it references.
    tasks.matching { it.name == "sourcesJar" || it.name == "kotlinSourcesJar" }
        .configureEach { dependsOn("generateModels") }
}

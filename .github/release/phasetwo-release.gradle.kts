// Phase Two release overlay for keycloak-scim-server.
//
// Loaded via `apply(from = "phasetwo-release.gradle.kts")` appended to
// build.gradle.kts by the release workflow. The com.vanniktech.maven.publish
// plugin (added to plugins{} by the workflow) handles:
//   - Publication setup with sources + javadoc jars
//   - Upload + automatic release to the Sonatype Central Portal
//
// This overlay covers the rest:
//   - Drops the upstream "gpr" publication / GitHubPackages repo so it never
//     gets added to vanniktech's signAllPublications() signing list.
//   - Wires the signing plugin to use the GPG command-line (vanniktech's
//     signAllPublications() does not call useGpgCmd() for us).
//   - Suppresses the "enforced-platform" validation that would otherwise
//     reject the upstream `implementation(enforcedPlatform(...))` dep.
//   - Tells javadoc to be tolerant of missing tags.
//   - Ensures the sources jar (added by vanniktech's JavaLibrary configuration)
//     waits for the OpenAPI model generation.

// IMPORTANT: remove the upstream "gpr" publication eagerly — i.e. during
// script execution, not in afterEvaluate. vanniktech's signAllPublications()
// schedules sign(publications) which iterates the publications container at
// the time it runs; if gpr still exists at that point, a signGprPublication
// task gets created and it has no signatory because vanniktech only
// configures one for its own "maven" publication.
extensions.configure<PublishingExtension>("publishing") {
    publications.findByName("gpr")?.let { publications.remove(it) }
    repositories.findByName("GitHubPackages")?.let { repositories.remove(it) }
}

// Defense in depth: even if the publication removal above runs too late for
// some reason, disable the signing task so it doesn't fail the build.
tasks.matching { it.name == "signGprPublication" }.configureEach {
    enabled = false
}

tasks.named<Javadoc>("javadoc") {
    isFailOnError = false
    (options as? StandardJavadocDocletOptions)?.addStringOption("Xdoclint:none", "-quiet")
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

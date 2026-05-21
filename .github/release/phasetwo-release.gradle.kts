// Phase Two release overlay for keycloak-scim-server.
//
// Loaded via `apply(from = "phasetwo-release.gradle.kts")` appended to
// build.gradle.kts by the release workflow. The com.vanniktech.maven.publish
// plugin (added to plugins{} by the workflow) handles:
//   - Publication setup with sources + javadoc jars
//   - GPG signing
//   - Upload + automatic release to the Sonatype Central Portal
//
// This overlay covers the rest:
//   - Drops the upstream "gpr" publication / GitHubPackages repo so `publish`
//     doesn't try to push to Metatavu's GitHub Packages.
//   - Suppresses the "enforced-platform" validation that would otherwise
//     reject the upstream `implementation(enforcedPlatform(...))` dep.
//   - Tells javadoc to be tolerant of missing tags so the build doesn't fail
//     on minor doc issues.

tasks.named<Javadoc>("javadoc") {
    isFailOnError = false
    (options as? StandardJavadocDocletOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

afterEvaluate {
    extensions.configure<PublishingExtension>("publishing") {
        // Drop the existing "gpr" publication and GitHubPackages repository
        publications.findByName("gpr")?.let { publications.remove(it) }
        repositories.findByName("GitHubPackages")?.let { repositories.remove(it) }
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

    // mavenPublishing { ... } configuration block lives in build.gradle.kts
    // itself (appended by the release workflow) so it has access to the
    // com.vanniktech.maven.publish plugin's types via plugins{} classpath.
}

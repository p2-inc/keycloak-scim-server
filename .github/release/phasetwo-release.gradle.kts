// Phase Two release overlay for keycloak-scim-server.
//
// Loaded via `apply(from = "phasetwo-release.gradle.kts")` appended to
// build.gradle.kts by the release workflow. Configures:
//   - groupId "io.phasetwo.keycloak" on the published artifact
//   - sources + javadoc jars
//   - full POM metadata for Maven Central
//   - GPG signing
//   - Sonatype OSSRH publishing repo (the nexus-publish plugin is added to
//     build.gradle.kts by the workflow and configures close+release).
//
// The original "gpr" publication / GitHubPackages repo are removed so a
// plain `gradle publish` doesn't try to push to Metatavu's GitHub Packages.

apply(plugin = "signing")

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
    dependsOn(tasks.named("generateModels"))
}

tasks.named<Javadoc>("javadoc") {
    isFailOnError = false
    (options as? StandardJavadocDocletOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    val javadocTask = tasks.named<Javadoc>("javadoc")
    dependsOn(javadocTask)
    from(javadocTask.map { it.destinationDir!! })
}

afterEvaluate {
    extensions.configure<PublishingExtension>("publishing") {
        // Drop the existing "gpr" publication and GitHubPackages repository so
        // the default `publish` task doesn't try to push to Metatavu's GHP.
        publications.findByName("gpr")?.let { publications.remove(it) }
        repositories.findByName("GitHubPackages")?.let { repositories.remove(it) }

        publications.register<MavenPublication>("mavenJava") {
            groupId = "io.phasetwo.keycloak"
            artifactId = "keycloak-scim-server"
            from(components["java"])
            artifact(sourcesJar.get())
            artifact(javadocJar.get())

            pom {
                name.set("Phase Two Keycloak SCIM Server")
                description.set(
                    "Phase Two-maintained build of the Metatavu Keycloak SCIM 2.0 extension."
                )
                url.set("https://github.com/p2-inc/keycloak-scim-server")
                inceptionYear.set("2025")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("phasetwo")
                        name.set("Phase Two, Inc.")
                        url.set("https://github.com/p2-inc")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:p2-inc/keycloak-scim-server.git")
                    developerConnection.set("scm:git:git@github.com:p2-inc/keycloak-scim-server.git")
                    url.set("https://github.com/p2-inc/keycloak-scim-server")
                }
            }
        }
    }

    extensions.configure<org.gradle.plugins.signing.SigningExtension>("signing") {
        useGpgCmd()
        sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
    }

    // Configure the nexus-publish plugin (added to plugins{} block by the
    // release workflow). Provides staging close+release tasks.
    val nexusExt = extensions.findByName("nexusPublishing")
    if (nexusExt != null) {
        @Suppress("UNCHECKED_CAST")
        val cfg = nexusExt as io.github.gradlenexus.publishplugin.NexusPublishExtension
        cfg.repositories {
            sonatype {
                nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
                snapshotRepositoryUrl.set(
                    uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                )
                username.set(System.getenv("OSSRH_USERNAME"))
                password.set(System.getenv("OSSRH_PASSWORD"))
            }
        }
    }
}

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ArtikelMeister"
include(":app")

// Force Android Studio's "Generate Signed Bundle / APK..." wizard to always use
// app/keystore/release.jks even if a different .jks file is selected in the IDE dialog.
val releaseKeystoreFile = file("app/keystore/release.jks")
if (releaseKeystoreFile.exists()) {
    val updatedProps = HashMap(gradle.startParameter.projectProperties)
    val injectedSigningKeys = listOf(
        "android.injected.signing.store.file",
        "android.injected.signing.store.password",
        "android.injected.signing.key.alias",
        "android.injected.signing.key.password"
    )
    if (injectedSigningKeys.any { updatedProps.containsKey(it) }) {
        updatedProps["android.injected.signing.store.file"] = releaseKeystoreFile.absolutePath
        updatedProps["android.injected.signing.store.password"] = "android"
        updatedProps["android.injected.signing.key.alias"] = "release"
        updatedProps["android.injected.signing.key.password"] = "android"
        gradle.startParameter.projectProperties = updatedProps
    }
}

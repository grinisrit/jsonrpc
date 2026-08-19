import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
}

group = "io.github.mzd00.jsonrpc"

version = libs.versions.jsonrpc.get()

kotlin {
    explicitApi()

    compilerOptions {
        extraWarnings = true
        allWarningsAsErrors = true
        progressiveMode = true
    }

    jvm()
}

dependencies {
    commonMainApi(projects.client)
    commonMainImplementation(libs.kotlinx.coroutines)
    commonMainImplementation(libs.kotlinx.serialization.core)
    commonMainImplementation(libs.kotlinx.serialization.json)
    commonMainImplementation(libs.ktor.client.core)
    commonMainImplementation(libs.ktor.client.cio)
    commonMainImplementation(libs.ktor.client.websockets)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

    pom {
        name = "jsonrpc-ktor-client"
        description = "Ktor transport for the JSON-RPC client"
        url = "https://github.com/grinisrit/jsonrpc"

        licenses {
            license {
                name = "MIT"
                distribution = "repo"
                url =
                    "https://github.com/grinisrit/jsonrpc/blob/main/LICENSE.md"
            }
        }

        developers {
            developer {
                id = "y9san9"
                name = "Alex Sokol"
                email = "y9san9@gmail.com"
            }
        }

        scm {
            connection = "scm:git:https://github.com/grinisrit/jsonrpc.git"
            developerConnection =
                "scm:git:ssh://git@github.com/grinisrit/jsonrpc.git"
            url = "https://github.com/grinisrit/jsonrpc"
        }
    }

    signAllPublications()
}

if (
    providers.gradleProperty("useGpgCmd")
        .map(String::toBoolean)
        .getOrElse(false)
) {
    configure<SigningExtension> {
        useGpgCmd()
    }
}

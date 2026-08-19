import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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

    sourceSets {
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    commonMainImplementation(libs.kotlinx.coroutines)
    commonMainApi(libs.kotlinx.serialization.core)
    commonMainApi(libs.kotlinx.serialization.json)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

    pom {
        name = "jsonrpc-client"
        description = "JSON-RPC implementation in pure Kotlin"
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

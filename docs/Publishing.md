# Publishing

The `client` and `ktor-client` modules are published to Maven Central. Maven
Central releases are immutable, so update and verify the project version before
publishing. Never reuse a version that has already been released.

The published coordinates are:

```text
io.github.mzd00.jsonrpc:client:<version>
io.github.mzd00.jsonrpc:ktor-client:<version>
```

## Requirements

- Access to the Maven Central namespace used by the project.
- A Maven Central user token.
- A GPG key whose public key is available from a keyserver supported by Maven
  Central.

Never commit a Maven Central token, GPG passphrase, private key, exported
secret-key file, or personal Gradle properties.

## Verify the release

Format and build the project before publishing:

```shell
./gradlew ktlintFormat
./gradlew build
```

Inspect the publications locally when changing publishing configuration:

```shell
./gradlew publishToMavenLocal
```

## Local publishing with gpg-agent

Local releases can use the private key already managed by GnuPG. This avoids
exporting the private key or giving its passphrase to Gradle.

Add only non-secret signing configuration to the user-level
`~/.gradle/gradle.properties` file:

```properties
useGpgCmd=true
signing.gnupg.executable=gpg
signing.gnupg.keyName=<PUBLIC_KEY_ID>
```

The `useGpgCmd` property makes Gradle delegate signing to GnuPG. If
`signing.gnupg.keyName` is omitted, GnuPG uses its configured default signing
key. The passphrase is obtained through `gpg-agent`; do not add
`signing.gnupg.passphrase` to Gradle properties.

Read the revocable Maven Central token without placing its value in shell
history, then expose it only to the publishing process:

```zsh
read "CENTRAL_USER?Central token username: "
read -s "CENTRAL_PASSWORD?Central token password: "
print
export ORG_GRADLE_PROJECT_mavenCentralUsername="$CENTRAL_USER"
export ORG_GRADLE_PROJECT_mavenCentralPassword="$CENTRAL_PASSWORD"
export GPG_TTY="$(tty)"
```

Run the applicable Maven Central publication task with a non-persistent Gradle
daemon. List the tasks exposed by the configured publishing-plugin version
when necessary:

```shell
./gradlew tasks --all | rg 'publish.*MavenCentral'
./gradlew --no-daemon publishAllPublicationsToMavenCentralRepository
```

Clear the token after publication:

```shell
unset ORG_GRADLE_PROJECT_mavenCentralUsername
unset ORG_GRADLE_PROJECT_mavenCentralPassword
unset CENTRAL_USER
unset CENTRAL_PASSWORD
```

Environment variables are short-lived, not secret storage. Do not put literal
token values in shell history, scripts, or checked-in files. Prefer an
interactive prompt or a trusted secret manager when setting them.

## CI publishing

CI should not enable `useGpgCmd`. It can use the publishing plugin's in-memory
signing support through protected CI secrets:

```text
ORG_GRADLE_PROJECT_mavenCentralUsername
ORG_GRADLE_PROJECT_mavenCentralPassword
ORG_GRADLE_PROJECT_signingInMemoryKey
ORG_GRADLE_PROJECT_signingInMemoryKeyId
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
```

Configure release publication as a manual or tag-triggered job. Do not publish
on every push to the main branch because a released Maven Central version
cannot be overwritten.

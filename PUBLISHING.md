# Publishing Gatekeeper

Publishing uses the [vanniktech maven-publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/). It produces the AAR, sources jar, javadoc jar and POM.

## JitPack (current)

JitPack builds straight from a Git tag, with no account or secrets:

```bash
# 1. bump VERSION_NAME in gradle.properties and add a CHANGELOG entry

# 2. tests: JVM parity + a device run
./gradlew :gatekeeper:testDebugUnitTest
./gradlew :gatekeeper:connectedDebugAndroidTest          # with a device or emulator attached

# 3. dry run: publish to ~/.m2 and build the sample against that artifact
./gradlew :gatekeeper:publishToMavenLocal
./gradlew :sample:assembleRelease -PuseMavenLocal

# 4. tag and push; then open https://jitpack.io/#rajumark/gatekeeper and press "Get it" on the tag
git tag v1.0.0 && git push origin v1.0.0
```

Consumers then use `implementation("com.github.rajumark:gatekeeper:v1.0.0")`.

## Maven Central (optional, later)

The build is already set up for Maven Central (`io.github.rajumark:gatekeeper`). It needs:

1. A Central Portal account at https://central.sonatype.com that owns the `io.github.rajumark` namespace.
2. A user token and a GPG signing key.
3. These secrets in `~/.gradle/gradle.properties`, **never in the repo**:
   ```properties
   mavenCentralUsername=<token username>
   mavenCentralPassword=<token password>
   signingInMemoryKey=<armored private key, newlines replaced by \n>
   signingInMemoryKeyPassword=<gpg passphrase>
   ```
   In CI, use `ORG_GRADLE_PROJECT_<name>` environment variables instead.

Then run `./gradlew :gatekeeper:publishAndReleaseToMavenCentral`.

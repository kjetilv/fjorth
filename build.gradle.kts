plugins {
    id("java")
    id("application")
    id("org.graalvm.buildtools.native") version "0.11.0"
}

group = "com.github.kjetilv"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "repl"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// Produces a native executable via `./gradlew nativeCompile`
// (output: build/native/nativeCompile/fjorth). Requires a GraalVM 25 toolchain;
// fjorth.fs is embedded explicitly because Bootstrap loads it as a classpath resource.
graalvmNative {
    binaries {
        named("main") {
            imageName = "fjorth"
            mainClass = "repl"
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:IncludeResources=fjorth\\.fs")
        }
        // `./gradlew nativeTest` builds and runs the test suite as a native image.
        // fjorth.fs must be embedded here too: the test fixtures go through Bootstrap.
        named("test") {
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:IncludeResources=fjorth\\.fs")
        }
    }
}

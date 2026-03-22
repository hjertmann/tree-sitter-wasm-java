plugins {
    `java-library`
    `maven-publish`
}

group = "dk.hjertmann"
version = "0.1.0-SNAPSHOT"
// artifactId is derived from rootProject.name = "jtreesitter" in settings.gradle.kts

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
    // Produce bytecode compatible with Java 21 (the minimum supported version)
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.dylibso.chicory:runtime:1.4.0")
    implementation("com.dylibso.chicory:wasi:1.4.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
    // Chicory WASM binary (~25MB optimized) needs significant heap
    jvmArgs("-Xmx2g")
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs integration tests"
    useJUnitPlatform {
        includeTags("integration")
    }
    classpath = tasks.test.get().classpath
    testClassesDirs = tasks.test.get().testClassesDirs
}

tasks.register<Exec>("verifyRustToolchain") {
    group = "build setup"
    description = "Verifies that cargo and wasm32-wasip1 target are available"
    commandLine("sh", "-c", """
        command -v cargo >/dev/null 2>&1 || {
            echo 'ERROR: Rust toolchain required. Install with:'
            echo '  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh'
            echo '  rustup target add wasm32-wasip1'
            exit 1
        }
        cargo build --target wasm32-wasip1 --help >/dev/null 2>&1 || {
            echo 'ERROR: wasm32-wasip1 target not installed. Run:'
            echo '  rustup target add wasm32-wasip1'
            exit 1
        }
    """.trimIndent())
}

tasks.register<Exec>("buildWasm") {
    group = "build"
    description = "Compiles the Rust WASM analysis module (requires WASI SDK)"
    dependsOn("verifyRustToolchain")
    workingDir = file("rust")

    val wasiSdkPath = System.getenv("WASI_SDK_PATH")
        ?: "${projectDir}/rust/wasi-sdk-32.0-arm64-macos"
    val cargoHome = "${System.getProperty("user.home")}/.cargo/bin"

    commandLine("cargo", "build", "--release", "--target", "wasm32-wasip1")
    environment("PATH", System.getenv("PATH") + ":$cargoHome")
    environment("WASI_SDK_PATH", wasiSdkPath)
    environment("CC_wasm32_wasip1", "$wasiSdkPath/bin/clang")
    environment("CFLAGS_wasm32_wasip1", "--sysroot=$wasiSdkPath/share/wasi-sysroot")

    // Only run if WASM is not already up-to-date
    inputs.dir("rust/src")
    inputs.file("rust/Cargo.toml")
    outputs.file("rust/target/wasm32-wasip1/release/tree_sitter_analysis.wasm")
}

tasks.register("optimizeWasm") {
    group = "build"
    description = "Optimizes the WASM binary with wasm-opt (falls back to unoptimized copy if wasm-opt is not available)"

    val input = layout.projectDirectory.file("rust/target/wasm32-wasip1/release/tree_sitter_analysis.wasm")
    // Only depend on buildWasm if the compiled WASM doesn't already exist
    if (!input.asFile.exists()) {
        dependsOn("buildWasm")
    }
    val output = layout.projectDirectory.file("src/main/resources/wasm/tree-sitter-analysis.wasm")

    inputs.file(input)
    outputs.file(output)

    doLast {
        val wasmOptPath = (System.getenv("PATH") ?: "").split(":")
            .map { File(it, "wasm-opt") }
            .firstOrNull { it.canExecute() }

        if (wasmOptPath != null) {
            val result = ProcessBuilder(
                wasmOptPath.absolutePath, "-Oz", "--strip-debug", "--strip-producers",
                input.asFile.absolutePath, "-o", output.asFile.absolutePath
            ).inheritIO().start().waitFor()
            if (result != 0) error("wasm-opt failed with exit code $result")
            val originalSize = input.asFile.length()
            val optimizedSize = output.asFile.length()
            val pct = (100.0 * (originalSize - optimizedSize) / originalSize).toInt()
            logger.lifecycle("wasm-opt: ${originalSize / 1_048_576}MB → ${optimizedSize / 1_048_576}MB (-${pct}%)")
        } else {
            logger.warn("wasm-opt not found — copying unoptimized WASM. Install with: brew install binaryen")
            copy {
                from(input)
                into(output.asFile.parentFile)
                rename { output.asFile.name }
            }
        }
    }
}

tasks.named("processResources") {
    // Only depend on optimizeWasm if the WASM resource doesn't already exist
    val wasmResource = file("src/main/resources/wasm/tree-sitter-analysis.wasm")
    if (!wasmResource.exists()) {
        dependsOn("optimizeWasm")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

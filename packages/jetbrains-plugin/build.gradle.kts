plugins {
    id("java")
    // Version 2.1.0 is fine, but we'll add the metadata bypass in compiler options
    id("org.jetbrains.kotlin.jvm") version "2.1.0" 
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.contextcondenser"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local("/home/alien/pycharm-2026.1")
        
        // 1. Use the specific ID confirmed by your printBundledPlugins
        bundledPlugin("Pythonid")
        
        // 2. We removed com.intellij.java because it doesn't exist in your PyCharm distro
        
        instrumentationTools()
        pluginVerifier()
    }
    implementation("com.google.code.gson:gson:2.10.1")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.contextcondenser"
        name = "Context Condenser — LVM"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "261"
        }
    }
    // Disable searching options to speed up build and avoid internal module conflicts
    buildSearchableOptions = false
}

java {
    toolchain {
        // This will be satisfied by the -Dorg.gradle.java.installations.paths flag
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            // 3. CRITICAL: Bypass the metadata version 2.3.0 vs 2.1.0 crash
            "-Xskip-metadata-version-check"
        )
    }
}

// 4. Force-exclude the problematic modules that were causing "sh.core" and "uml" errors
configurations.all {
    exclude(group = "bundledModule", module = "intellij.sh.core")
    exclude(group = "bundledModule", module = "intellij.diagram")
}

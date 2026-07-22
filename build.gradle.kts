plugins {
    java
    checkstyle
    id("com.diffplug.spotless") version "8.8.0"
    id("org.owasp.dependencycheck") version "12.2.2"
}

group = "com.dungeonhero"
version = "2.0.1"

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "lumine"
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.72-stable")
    compileOnly("io.lumine:Mythic-Dist:5.12.1")
    testCompileOnly("io.papermc.paper:paper-api:26.1.2.build.72-stable")
    testRuntimeOnly("io.papermc.paper:paper-api:26.1.2.build.72-stable")
    testRuntimeOnly("io.lumine:Mythic-Dist:5.12.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.28.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencyCheck {
    failBuildOnCVSS = 7.0F
    formats = listOf("HTML", "SARIF")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}

val dependencyAudit by tasks.registering {
    group = "verification"
    description = "Rejects unpinned or dynamic external dependencies."
    doLast {
        val dynamicDependencies = configurations.flatMap { configuration ->
            configuration.dependencies
                .filterIsInstance<org.gradle.api.artifacts.ExternalModuleDependency>()
                .filter { dependency ->
                    val version = dependency.version.orEmpty()
                    version.isBlank() || version == "latest.release" || version == "latest.integration"
                            || version.contains("+") || version.contains("[") || version.contains("]")
                }
                .map { dependency ->
                    "${configuration.name}:${dependency.group}:${dependency.name}:${dependency.version}"
                }
        }
        check(dynamicDependencies.isEmpty()) {
            "Dynamic or unpinned dependencies are not allowed: $dynamicDependencies"
        }
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck", dependencyAudit)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

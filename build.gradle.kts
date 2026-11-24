// ArtisanLands, the lands plugin on Artisan (successor of MSLands). The package stays
// fr.iban.lands: Warps and MSBoosts use LandsPlugin, Land, Action, SChunk and GuildLand as they are.
// Published to repo.artisanmc.net as net.artisanmc:artisan-lands (snapshots from main, releases from a
// tag v<version>: .github/workflows/release.yml). A release sets the version with -PpluginVersion.
group = "net.artisanmc"
version = (findProperty("pluginVersion") as String?) ?: "2.0.0-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_21

plugins {
    `java-library`
    `maven-publish`
    id("io.github.goooler.shadow") version "8.1.7"
}

val artisanApiVersion: String by project

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        name = "CodeMCQuickShop"
        url = uri("https://repo.codemc.io/repository/ghost-chu/")
    }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://repo.artisanmc.net/releases") }
    maven { url = uri("https://repo.artisanmc.net/snapshots") }
    maven { url = uri("https://repo.codemc.io/repository/maven-public/") }
    maven { url = uri("https://jitpack.io/") }
    maven { url = uri("https://repo.codemc.io/repository/maven-releases/") }
    maven { url = uri("https://repo.tcoded.com/releases") }
    maven { url = uri("https://repo.bluecolored.de/releases") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Artisan is required and provides its API at runtime: never shaded.
    compileOnly("net.artisanmc:artisan-core-api:$artisanApiVersion")
    // ArtisanGuilds (optional at runtime: guild lands), from repo.artisanmc.net.
    compileOnly("net.artisanmc:artisan-guilds:2.0.0-SNAPSHOT")
    // 6.3 API (Locatable shop locations work on 6.2 servers too).
    compileOnly("com.ghostchu:quickshop-hikari:6.3.0.2")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    // BlueMap (optional at runtime: claims drawn on the map).
    compileOnly("de.bluecolored:bluemap-api:2.7.7")

    implementation("com.tcoded:FoliaLib:0.5.1")
    implementation("net.objecthunter:exp4j:0.4.8")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("net.artisanmc:artisan-core-api:$artisanApiVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.yaml:snakeyaml:2.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("de.bluecolored:bluemap-api:2.7.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}

// What Warps and MSBoosts compile against: the plain jar (fr.iban.lands.*), not the shaded one.
publishing {
    publications.create<MavenPublication>("maven") {
        artifactId = "artisan-lands"
        from(components["java"])
        pom {
            name.set("ArtisanLands")
            description.set("Chunk claims for Paper, an Artisan add-on (formerly MSLands).")
            url.set("https://github.com/artisanmc/artisan-lands")
            scm {
                url.set("https://github.com/artisanmc/artisan-lands")
                connection.set("scm:git:https://github.com/artisanmc/artisan-lands.git")
            }
        }
    }
    repositories {
        maven {
            name = "artisanRepo"
            val channel = if (version.toString().endsWith("SNAPSHOT")) "snapshots" else "releases"
            url = uri("https://repo.artisanmc.net/$channel")
            credentials {
                username = providers.gradleProperty("artisanRepoUsername").orNull
                password = providers.gradleProperty("artisanRepoPassword").orNull
            }
        }
    }
}

tasks.shadowJar {
    archiveFileName.set("ArtisanLands-${project.version}.jar")
    relocate("com.tcoded.folialib", "fr.iban.lands.libs.folialib")
    relocate("net.objecthunter.exp4j", "fr.iban.lands.libs.exp4j")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val props = mapOf("project_version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

// The test mocks LandsPlugin: the plugins it hooks (Vault…) must load.
configurations.testImplementation { extendsFrom(configurations.compileOnly.get()) }

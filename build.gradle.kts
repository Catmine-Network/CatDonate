plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.panda-lang.org/releases")
    maven("https://repo.codemc.org/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.catengine.common)
    implementation(libs.catengine.database) {
        exclude(group = "com.mysql", module = "mysql-connector-j")
        exclude(group = "org.flywaydb", module = "flyway-mysql")
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
    implementation(libs.gson)
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation(libs.paper.api)
    testImplementation(libs.sqlite.jdbc)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        javaParameters.set(true)
    }
}

tasks {
    val packagedJarPath = layout.buildDirectory
        .file("libs/${project.name}-${project.version}.jar")
        .get()
        .asFile
        .absolutePath

    shadowJar {
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        exclude("kotlin/**")

        dependencies {
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
            exclude(dependency("org.xerial:sqlite-jdbc"))
        }

        relocate("net.catmine.engine", "net.catmine.studio.catdonate.libs.catengine")
        relocate("com.github.benmanes.caffeine", "net.catmine.studio.catdonate.libs.caffeine")
        relocate("dev.rollczi.litecommands", "net.catmine.studio.catdonate.libs.litecommands")
        relocate("dev.triumphteam.gui", "net.catmine.studio.catdonate.libs.triumphgui")
        relocate("com.zaxxer.hikari", "net.catmine.studio.catdonate.libs.hikari")
        relocate("org.flywaydb", "net.catmine.studio.catdonate.libs.flyway")
        relocate("org.jetbrains.exposed", "net.catmine.studio.catdonate.libs.exposed")
        relocate("com.google.gson", "net.catmine.studio.catdonate.libs.gson")

        mergeServiceFiles()
    }

    val verifyPackagedSqlite = register<JavaExec>("verifyPackagedSqlite") {
        group = "verification"
        description = "Verifies SQLite is externalized and its native driver can open a database."
        dependsOn(shadowJar, testClasses)
        classpath(sourceSets.test.get().runtimeClasspath)
        mainClass.set("net.catmine.studio.catDonate.persistence.PackagedSqliteSmoke")
        args(
            packagedJarPath,
            "org.xerial:sqlite-jdbc:${libs.versions.sqlite.get()}",
        )
    }

    build {
        dependsOn(shadowJar, verifyPackagedSqlite)
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf(
            "version" to version,
            "kotlinVersion" to libs.versions.kotlin.get(),
            "sqliteVersion" to libs.versions.sqlite.get(),
        )
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

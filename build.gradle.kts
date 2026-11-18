plugins { java }

group = "io.github.augmentwild"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }

val paperProbe = sourceSets.create("paperProbe") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
}

tasks.register<Jar>("paperProbeJar") {
    from(paperProbe.output)
    archiveFileName = "augmentwild-paper-probe.jar"
    destinationDirectory = layout.buildDirectory.dir("paper-probe")
}

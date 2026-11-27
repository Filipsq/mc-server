// Setup for plugins we put on customers' servers (mchosting-link): Java 21 and the oldest Paper API
// they may run (1.20.6, the first with Brigadier commands and transfer), so one jar works on all of them.
plugins {
    id("mcserver.java21")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(libs.findLibrary("paper-api-client").get())
    testImplementation(libs.findLibrary("paper-api-client").get())
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

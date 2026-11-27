// Setup for modules that are Paper plugins: Paper API, version in paper-plugin.yml, local test server.
plugins {
    id("mcserver.java")
    id("xyz.jpenilla.run-paper")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(libs.findLibrary("paper-api").get())
    // Tests of plain classes that use libraries Paper ships (Gson, Adventure) need them too.
    testImplementation(libs.findLibrary("paper-api").get())
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.runServer {
    minecraftVersion("26.3")
}

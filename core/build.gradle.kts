// Core plugin: player accounts and other services shared by every server in the network.
plugins {
    id("mcserver.paper-plugin")
}

dependencies {
    api(project(":core-api"))
}

tasks.jar {
    // Ship the API classes inside the core jar so other plugins can use them at runtime.
    from(project(":core-api").sourceSets.main.map { it.output })
}

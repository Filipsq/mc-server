// Minigames plugin: queues, matchmaking and matches of Spleef and TNT Run.
plugins {
    id("mcserver.paper-plugin")
}

dependencies {
    compileOnly(project(":core-api"))
    testImplementation(project(":core-api"))
}

tasks.runServer {
    // Minigames run on the hub server for now, so start the whole set together.
    pluginJars.from(project(":core").tasks.named("jar"))
    pluginJars.from(project(":hub").tasks.named("jar"))
}

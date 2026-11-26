// Hub plugin: everything players see and do in the lobby valley.
plugins {
    id("mcserver.paper-plugin")
}

dependencies {
    compileOnly(project(":core-api"))
}

tasks.runServer {
    // Start the core plugin together with the hub on the local test server.
    pluginJars.from(project(":core").tasks.named("jar"))
}

// Hub plugin: everything players see and do in the lobby valley.
plugins {
    id("mcserver.paper-plugin")
}

dependencies {
    compileOnly(project(":core-api"))
    testImplementation(project(":core-api"))
    // Return tickets from customers' servers (/wroc); shipped inside the hub jar.
    implementation(project(":link-protocol"))
}

tasks.jar {
    from(project(":link-protocol").sourceSets.main.map { it.output })
}

tasks.runServer {
    // Start the core plugin together with the hub on the local test server.
    pluginJars.from(project(":core").tasks.named("jar"))
}

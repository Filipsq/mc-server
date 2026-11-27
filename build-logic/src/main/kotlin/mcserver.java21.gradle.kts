// Like mcserver.java, but the classes run on Java 21: for code that goes to customers' servers,
// which may still run Minecraft 1.20.6 or 1.21 (they need Java 21, not 25).
plugins {
    id("mcserver.java")
}

tasks.withType<JavaCompile>().configureEach {
    // Runs after mcserver.java's setting, so it wins. The compiler stays JDK 25.
    options.release = 21
}

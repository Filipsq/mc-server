// mchosting-link: goes on customers' servers (the panel adds it). /hub sends the player to the
// network's hub with a signed return ticket, so the hub can offer /wroc back to this server.
plugins {
    id("mcserver.client-plugin")
}

base {
    archivesName = "mchosting-link"
}

dependencies {
    implementation(project(":link-protocol"))
}

tasks.jar {
    // The server only loads this one jar, so the ticket classes go inside it.
    from(project(":link-protocol").sourceSets.main.map { it.output })
}

package pl.mchosting.server.hub;

/** A customer's server a player can go back to with /wroc, as the panel describes it. */
record ReturnServer(String name, String host, int port) {

    /** Longest name shown on the item and in chat; the panel allows longer ones. */
    static final int NAME_MAX = 32;

    ReturnServer {
        name = name.strip();
        if (name.length() > NAME_MAX) {
            name = name.substring(0, NAME_MAX - 1) + "…";
        }
        if (name.isEmpty()) {
            name = host;
        }
    }
}

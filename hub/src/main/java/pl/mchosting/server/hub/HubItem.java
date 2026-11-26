package pl.mchosting.server.hub;

/** Items in the hub hotbar, by slot. */
enum HubItem {
    MODE_SELECTOR(0),
    PROFILE(1),
    PLAYER_SERVERS(4),
    HIDE_PLAYERS(8);

    private final int slot;

    HubItem(int slot) {
        this.slot = slot;
    }

    int slot() {
        return slot;
    }
}

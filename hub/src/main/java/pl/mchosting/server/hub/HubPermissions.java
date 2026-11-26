package pl.mchosting.server.hub;

/** Permission nodes used by the hub. */
final class HubPermissions {

    /** Lets staff build, break blocks and move items in the hub. */
    static final String BUILD = "mchosting.hub.build";

    /** Lets staff set points in the valley ({@code /hubadmin}). */
    static final String ADMIN = "mchosting.hub.admin";

    private HubPermissions() {}
}

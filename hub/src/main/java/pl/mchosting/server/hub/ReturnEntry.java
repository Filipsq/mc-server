package pl.mchosting.server.hub;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.security.PublicKey;
import java.util.Optional;
import pl.mchosting.server.link.ticket.LinkKeys;

/** What the panel says about one linked server: where it is and the key its tickets are signed with. */
record ReturnEntry(ReturnServer server, PublicKey key) {

    /**
     * Reads the panel's answer, {@code {"name", "host", "port", "publicKey"}}. Empty for anything
     * missing, malformed, or with an address outside {@code allowed}.
     */
    static Optional<ReturnEntry> parse(String json, AllowedHosts allowed) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject o = root.getAsJsonObject();
            String host = string(o, "host");
            String key = string(o, "publicKey");
            if (host == null || key == null || !allowed.allows(host)) {
                return Optional.empty();
            }
            int port = o.has("port") && o.get("port").isJsonPrimitive()
                    ? o.get("port").getAsInt()
                    : 25565;
            if (port < 1 || port > 65535) {
                return Optional.empty();
            }
            String name = string(o, "name");
            ReturnServer server = new ReturnServer(name == null ? host : name, host, port);
            return LinkKeys.publicKey(key).map(k -> new ReturnEntry(server, k));
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String string(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }
}

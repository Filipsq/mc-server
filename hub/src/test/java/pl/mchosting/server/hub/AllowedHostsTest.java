package pl.mchosting.server.hub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AllowedHostsTest {

    @Test
    void allowsDomainAndItsSubdomainsIgnoringCase() {
        AllowedHosts hosts = new AllowedHosts(List.of("mchost.pl"));

        assertTrue(hosts.allows("ekipa.mchost.pl"));
        assertTrue(hosts.allows("EKIPA.MCHOST.PL"));
        assertTrue(hosts.allows("a.b.mchost.pl"));
        assertTrue(hosts.allows("mchost.pl"));
    }

    @Test
    void rejectsAddressesOutsideAllowedDomains() {
        AllowedHosts hosts = new AllowedHosts(List.of("mchost.pl"));

        for (String host : new String[] {
            "zlymchost.pl",
            "mchost.pl.zly.com",
            "ekipa.mchost.pl:25565",
            "ekipa.mchost.pl/x",
            "ekipa mchost.pl",
            "",
            null,
            "localhost",
            "127.0.0.1"
        }) {
            assertFalse(hosts.allows(host));
        }
    }

    @Test
    void emptyDomainListAllowsNothingAndDomainsAreTrimmed() {
        assertFalse(new AllowedHosts(List.of()).allows("ekipa.mchost.pl"));
        assertTrue(new AllowedHosts(List.of(" MCHOST.pl ")).allows("ekipa.mchost.pl"));
    }
}

package com.habnut.emulator.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Client address")
class ClientAddressTest {

    private static InetSocketAddress from(String ip) {
        return new InetSocketAddress(ip, 41234);
    }

    @Test
    @DisplayName("the web server's header names the player")
    void proxyHeaderIsUsed() {
        assertEquals("81.2.69.160", ClientAddress.resolve(from("172.18.0.5"), "81.2.69.160"));
        assertEquals("2001:db8::1", ClientAddress.resolve(from("127.0.0.1"), "2001:db8::1"));
    }

    @Test
    @DisplayName("a player cannot claim another address")
    void directClientCannotSpoof() {
        assertEquals("81.2.69.160", ClientAddress.resolve(from("81.2.69.160"), "10.0.0.1"));
    }

    @Test
    @DisplayName("always a bare IP, never the socket's /host:port form")
    void bareAddress() {
        assertEquals("172.18.0.5", ClientAddress.resolve(from("172.18.0.5"), null));
    }

    @Test
    @DisplayName("a header that is not an IP literal is ignored, and never looked up")
    void garbageHeaderIgnored() {
        for (String bad : new String[] {"", "abc", "evil.example.com", "1.2.3.4, 5.6.7.8", "999.1.1.1.1", "x".repeat(60)}) {
            assertEquals("172.18.0.5", ClientAddress.resolve(from("172.18.0.5"), bad), bad);
        }
    }

    @Test
    @DisplayName("IPv6 is written the short way, as PHP and Cloudflare write it")
    void shortIpv6() throws Exception {
        String[][] cases = {
            {"2001:41d0:801:2000:0:0:0:3153", "2001:41d0:801:2000::3153"},
            {"0:0:0:0:0:0:0:1", "::1"},
            {"0:0:0:0:0:0:0:0", "::"},
            {"fe80:0:0:0:1:0:0:1", "fe80::1:0:0:1"},
            {"2001:db8:0:1:1:1:1:1", "2001:db8:0:1:1:1:1:1"},
            {"2001:db8:0:0:1:0:0:1", "2001:db8::1:0:0:1"},
            {"1:0:0:0:0:0:0:0", "1::"},
        };
        for (String[] c : cases) {
            assertEquals(c[1], ClientAddress.format(java.net.InetAddress.getByName(c[0])), c[0]);
        }
    }
}

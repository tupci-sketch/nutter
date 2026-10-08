package com.habnut.emulator.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * The address of the player behind a connection.
 *
 * Players reach the hotel through the web server, so the connection itself
 * always comes from the web server's container. The web server passes the
 * player's own address in X-Real-IP (behind Cloudflare it has already taken it
 * from CF-Connecting-IP). Before this, every player was recorded at the web
 * server's address — and as the socket's string form, "/172.18.0.5:41234",
 * port and all — so last_ip, IP bans, machine-ID records and the staff audit
 * log held a value that named nobody and could never match a ban.
 *
 * The header is believed only when the connection comes from a private or
 * loopback address, which is where the web server is. A connection from
 * anywhere else is the player, and anything it claims about itself is ignored.
 */
public final class ClientAddress {

    // Only forms InetAddress parses as a literal, so reading the header can never
    // turn into a DNS lookup of whatever was written in it: four dotted decimal
    // numbers, or anything with a colon, which no hostname can contain.
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*");

    private ClientAddress() {}

    public static String resolve(SocketAddress peer, String realIpHeader) {
        InetAddress peerAddress = peer instanceof InetSocketAddress isa ? isa.getAddress() : null;
        if (peerAddress == null) {
            return "unknown";
        }
        if (isProxy(peerAddress)) {
            InetAddress claimed = parseLiteral(realIpHeader);
            if (claimed != null) {
                return format(claimed);
            }
        }
        return format(peerAddress);
    }

    /**
     * The address as everything else writes it. Java spells IPv6 out in full
     * ("2001:db8:0:0:0:0:0:1") where PHP, nginx and Cloudflare use the short
     * form ("2001:db8::1"); stored both ways, an address banned on one side
     * would never match on the other. This is the RFC 5952 form: the longest
     * run of two or more zero groups becomes "::", the first if runs tie.
     */
    static String format(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return address.getHostAddress();
        }
        byte[] b = address.getAddress();
        int[] g = new int[8];
        for (int i = 0; i < 8; i++) g[i] = ((b[2 * i] & 0xff) << 8) | (b[2 * i + 1] & 0xff);
        int bestStart = -1, bestLen = 1;
        for (int i = 0; i < 8; ) {
            if (g[i] != 0) { i++; continue; }
            int j = i;
            while (j < 8 && g[j] == 0) j++;
            if (j - i > bestLen) { bestStart = i; bestLen = j - i; }
            i = j;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                out.append(i == 0 ? "::" : ":");
                i += bestLen - 1;
                continue;
            }
            out.append(Integer.toHexString(g[i]));
            if (i < 7) out.append(':');
        }
        return out.toString();
    }

    private static boolean isProxy(InetAddress a) {
        return a.isLoopbackAddress() || a.isSiteLocalAddress();
    }

    private static InetAddress parseLiteral(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.length() > 45 || !(IPV4.matcher(v).matches() || IPV6.matcher(v).matches())) return null;
        try {
            return InetAddress.getByName(v);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}

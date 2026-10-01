package com.habnut.emulator.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The names on the wire are decided here, and only here.
 *
 * The game client's packet table is generated from PacketType.java, so a
 * handler that registers or sends a bare string is one the client has no name
 * for — it will never be sent, and never listened to. That is exactly how the
 * client and the server ended up speaking different protocols, so it is worth
 * a test rather than a convention.
 */
class PacketTableTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/habnut/emulator");

    /** `router.register("literal", …)` and `buildPacket("literal", …)`. */
    private static final Pattern LITERAL_PACKET = Pattern.compile(
        "(?:register|buildPacket)\\(\\s*\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"");

    private static Set<String> declaredWireNames() {
        Set<String> names = new HashSet<>();
        for (Field f : PacketType.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                try {
                    names.add((String) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return names;
    }

    @Test
    @DisplayName("no packet name is written as a bare string outside PacketType")
    void noLiteralPacketNames() throws IOException {
        List<String> offences = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("PacketType.java")) continue;

                String body = Files.readString(file);
                Matcher m = LITERAL_PACKET.matcher(body);
                while (m.find()) {
                    offences.add(file.getFileName() + " uses \"" + m.group(1) + "\"");
                }
            }
        }

        assertTrue(offences.isEmpty(),
            "Packet names must be constants in PacketType so the client's table can be "
            + "generated from it. Found:\n  " + String.join("\n  ", offences));
    }

    @Test
    @DisplayName("no two constants share a wire name")
    void noDuplicateWireNames() {
        Map<String, String> seen = new HashMap<>();
        List<String> clashes = new ArrayList<>();

        for (Field f : PacketType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
            String value;
            try {
                value = (String) f.get(null);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
            String previous = seen.put(value, f.getName());
            if (previous != null) {
                clashes.add(previous + " and " + f.getName() + " both mean \"" + value + "\"");
            }
        }

        assertTrue(clashes.isEmpty(),
            "A wire name must mean one thing:\n  " + String.join("\n  ", clashes));
    }

    @Test
    @DisplayName("the generated client table matches this file")
    void clientTableIsUpToDate() throws IOException {
        Path generated = Path.of("../client/src/protocol/packets.ts");
        if (!Files.exists(generated)) return;   // emulator built on its own

        String table = Files.readString(generated);
        List<String> missing = new ArrayList<>();

        for (String wire : declaredWireNames()) {
            if (!table.contains("'" + wire + "'")) missing.add(wire);
        }

        assertTrue(missing.isEmpty(),
            "The client's packet table is out of date; run `pnpm packets:generate` in "
            + "apps/client. Missing:\n  " + String.join("\n  ", missing));
    }
}

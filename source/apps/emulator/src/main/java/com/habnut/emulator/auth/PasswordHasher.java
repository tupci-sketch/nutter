package com.habnut.emulator.auth;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

public final class PasswordHasher {

    private static final Argon2 ARGON2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private static final int ITERATIONS  = 3;
    private static final int MEMORY_KB   = 65536;
    private static final int PARALLELISM = 2;

    private PasswordHasher() {}

    public static String hash(String plaintext) {
        return ARGON2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, plaintext.toCharArray());
    }

    public static boolean verify(String hash, String plaintext) {
        return ARGON2.verify(hash, plaintext.toCharArray());
    }
}

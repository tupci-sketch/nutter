package com.habnut.nutropolis;

/**
 * A player's life in Nutropolis. Its money is the city's own (cash in hand
 * and a bank balance) and never touches the hotel's credits, duckets or
 * diamonds. Changed only while holding the object's lock.
 */
final class Citizen {
    final int userId;
    long cash;
    long bank;
    int health = Nutropolis.MAX_HEALTH;
    int jobId;
    int jobRank;
    int wanted;
    int jailedUntil;
    int shifts;

    Citizen(int userId) {
        this.userId = userId;
    }

    boolean jailed() {
        return this.jailedUntil > Nutropolis.now();
    }
}

package com.habnut.nutropolis;

/**
 * A job in the city. Civil jobs are worked in their workplace; police and
 * medics are on duty anywhere in the city.
 */
record Job(int id, String code, String name, String kind, int roomId, int wage, int shiftMinutes,
           String[] ranks, int vehicleEffect) {

    static final String POLICE = "police";
    static final String MEDIC = "medic";

    boolean mobile() {
        return POLICE.equals(this.kind) || MEDIC.equals(this.kind);
    }

    String rankName(int rank) {
        if (this.ranks.length == 0) return this.name;
        return this.ranks[Math.max(0, Math.min(rank, this.ranks.length - 1))];
    }

    /** Pay rises a fifth for each rank above the first. */
    int wageAt(int rank) {
        return this.wage + this.wage * Math.max(0, rank) / 5;
    }
}

package com.habnut.nutropolis;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The civic side of Nutropolis: the mayor and elections, the treasury and tax,
 * property, player-run businesses, licences and cars, the court, and crafting.
 */
final class Civic {
    static final Set<String> KEYS = Set.of(
            "mayor", "runformayor", "vote", "settax", "announce", "bonus",
            "properties", "buyproperty", "sellproperty",
            "openbusiness", "business", "hire", "fire", "setwage", "pay", "withdrawbusiness",
            "licence", "license", "buycar",
            "appeal", "cases", "verdict",
            "gather", "craft", "use", "items");

    static final int CANDIDATE_FEE = 250;
    static final int ELECTION_DAYS = 3;
    static final int MAX_TAX = 20;
    static final int BUSINESS_FEE = 500;
    static final int APPEAL_FEE = 50;
    static final int CIVILIAN_CAR_EFFECT = 22;
    static final Map<String, Integer> LICENCES = Map.of("driving", 150, "business", 300);
    static final int CAR_PRICE = 800;
    static final String[] MATERIALS = {"wood", "metal", "cloth"};
    static final Map<String, Map<String, Integer>> RECIPES = Map.of(
            "bandage", Map.of("cloth", 2, "herbs", 1),
            "medkit", Map.of("cloth", 2, "herbs", 2, "metal", 1),
            "lockpick", Map.of("metal", 2, "wood", 1));

    private final City city;
    private final Map<Integer, Job> businessJobs = new ConcurrentHashMap<>();

    Civic(City city) {
        this.city = city;
    }

    // ---- the city's numbers -----------------------------------------------------

    static long state(String name, long fallback) {
        String[] r = Db.row("SELECT value FROM habnut_rp_state WHERE name = ?", name);
        try {
            return r == null ? fallback : Long.parseLong(r[0]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static void setState(String name, long value) {
        Db.update("INSERT INTO habnut_rp_state (name, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)",
                name, String.valueOf(value));
    }

    static void addTreasury(long amount) {
        Db.update("INSERT INTO habnut_rp_state (name, value) VALUES ('treasury', ?)"
                + " ON DUPLICATE KEY UPDATE value = CAST(value AS SIGNED) + ?", String.valueOf(amount), amount);
    }

    int taxPercent() {
        return (int) Math.max(0, Math.min(MAX_TAX, state("tax", 5)));
    }

    /** Takes the city's cut of a wage; returns what the worker keeps. */
    int afterTax(int wage) {
        int tax = wage * this.taxPercent() / 100;
        if (tax > 0) addTreasury(tax);
        return wage - tax;
    }

    static String nameOf(long userId) {
        String[] r = Db.row("SELECT username FROM users WHERE id = ?", userId);
        return r == null ? "nobody" : r[0];
    }

    // ---- money helpers --------------------------------------------------------------

    static boolean takeBank(Citizen c, long amount, String reason) {
        long balance;
        synchronized (c) {
            if (c.bank < amount) return false;
            c.bank -= amount;
            balance = c.bank;
            Store.save(c);
        }
        Store.ledger(c.userId, "bank", -amount, balance, reason, 0);
        return true;
    }

    static boolean takeCash(Citizen c, long amount, String reason) {
        long balance;
        synchronized (c) {
            if (c.cash < amount) return false;
            c.cash -= amount;
            balance = c.cash;
            Store.save(c);
        }
        Store.ledger(c.userId, "cash", -amount, balance, reason, 0);
        return true;
    }

    static void giveBank(Citizen c, long amount, String reason) {
        long balance;
        synchronized (c) {
            c.bank += amount;
            balance = c.bank;
            Store.save(c);
        }
        Store.ledger(c.userId, "bank", amount, balance, reason, 0);
    }

    // ---- commands ---------------------------------------------------------------------

    boolean handle(Habbo h, Citizen me, Room room, String verb, String[] p) {
        return switch (verb) {
            case "mayor" -> this.mayor(h);
            case "runformayor" -> this.run(h, me, room);
            case "vote" -> this.vote(h, me, room, p);
            case "settax" -> this.setTax(h, me, p);
            case "announce" -> this.announce(h, me, p);
            case "bonus" -> this.bonus(h, me, p);
            case "properties" -> this.properties(h);
            case "buyproperty" -> this.buyProperty(h, me, room);
            case "sellproperty" -> this.sellProperty(h, me, room);
            case "openbusiness" -> this.openBusiness(h, me, room, p);
            case "business" -> this.business(h, room);
            case "hire" -> this.hire(h, me, room, p);
            case "fire" -> this.fire(h, me, room, p);
            case "setwage" -> this.setWage(h, me, room, p);
            case "pay" -> this.pay(h, me, room, p);
            case "withdrawbusiness" -> this.withdrawBusiness(h, me, room, p);
            case "licence", "license" -> this.licence(h, me, room, p);
            case "buycar" -> this.buyCar(h, me);
            case "appeal" -> this.appeal(h, me);
            case "cases" -> this.cases(h, me);
            case "verdict" -> this.verdict(h, me, room, p);
            case "gather" -> this.gather(h, me, room);
            case "craft" -> this.craft(h, me, p);
            case "use" -> this.use(h, me, room, p);
            case "items" -> this.items(h, me);
            default -> false;
        };
    }

    private boolean atTownHall(Habbo h, Room room) {
        if ("townhall".equals(this.city.kindOf(room))) return true;
        this.city.no(h, "That is done at the Town Hall.");
        return false;
    }

    // ---- government -----------------------------------------------------------------

    private static String[] openElection() {
        return Db.row("SELECT id, closes_at FROM habnut_rp_elections WHERE closed = 0 ORDER BY id DESC LIMIT 1");
    }

    private boolean mayor(Habbo h) {
        long mayorId = state("mayor", 0);
        String[] election = openElection();
        String race = "no election running (:runformayor at the Town Hall starts one)";
        if (election != null) {
            List<String[]> rows = Db.rows("SELECT u.username, (SELECT COUNT(*) FROM habnut_rp_votes v WHERE v.election_id = c.election_id"
                    + " AND v.candidate_id = c.user_id) FROM habnut_rp_candidates c JOIN users u ON u.id = c.user_id WHERE c.election_id = ?",
                    Integer.parseInt(election[0]));
            List<String> names = new ArrayList<>();
            for (String[] r : rows) names.add(r[0] + " (" + r[1] + ")");
            long hours = Math.max(0, (Long.parseLong(election[1]) - Nutropolis.now()) / 3600);
            race = "election closes in " + hours + "h: " + (names.isEmpty() ? "no candidates yet" : String.join(", ", names));
        }
        h.whisper("Mayor: " + (mayorId > 0 ? nameOf(mayorId) : "nobody") + "  ·  tax " + this.taxPercent() + "%  ·  treasury $"
                + state("treasury", 0) + "  ·  " + race, RoomChatMessageBubbles.BLUE);
        return true;
    }

    private boolean run(Habbo h, Citizen me, Room room) {
        if (!this.atTownHall(h, room)) return true;
        if (me.jailed() || me.wanted > 0) return this.city.no(h, "Nobody votes for a fugitive.");
        String[] election = openElection();
        int electionId;
        if (election == null) {
            electionId = Db.insert("INSERT INTO habnut_rp_elections (opened_at, closes_at) VALUES (?, ?)",
                    Nutropolis.now(), Nutropolis.now() + ELECTION_DAYS * 86400);
        } else {
            electionId = Integer.parseInt(election[0]);
        }
        if (Db.number("SELECT COUNT(*) FROM habnut_rp_candidates WHERE election_id = ? AND user_id = ?", electionId, me.userId) > 0) {
            return this.city.no(h, "You are already standing.");
        }
        if (!takeBank(me, CANDIDATE_FEE, "candidacy")) return this.city.no(h, "Standing costs $" + CANDIDATE_FEE + " from your bank.");
        addTreasury(CANDIDATE_FEE);
        Db.update("INSERT IGNORE INTO habnut_rp_candidates (election_id, user_id) VALUES (?, ?)", electionId, me.userId);
        h.shout("*is standing for Mayor of Nutropolis!*", RoomChatMessageBubbles.BLUE);
        return true;
    }

    private boolean vote(Habbo h, Citizen me, Room room, String[] p) {
        if (!this.atTownHall(h, room)) return true;
        if (p.length < 2) return this.city.no(h, "Usage: :vote <candidate>");
        String[] election = openElection();
        if (election == null) return this.city.no(h, "There is no election running.");
        if (me.shifts < 1) return this.city.no(h, "Work at least one shift in the city before you vote.");
        String[] candidate = Db.row("SELECT c.user_id FROM habnut_rp_candidates c JOIN users u ON u.id = c.user_id"
                + " WHERE c.election_id = ? AND u.username = ?", Integer.parseInt(election[0]), p[1]);
        if (candidate == null) return this.city.no(h, p[1] + " is not standing.");
        if (Integer.parseInt(candidate[0]) == me.userId) return this.city.no(h, "You cannot vote for yourself.");
        if (Db.update("INSERT IGNORE INTO habnut_rp_votes (election_id, voter_id, candidate_id) VALUES (?, ?, ?)",
                Integer.parseInt(election[0]), me.userId, Integer.parseInt(candidate[0])) == 0) {
            return this.city.no(h, "You have already voted.");
        }
        h.talk("*casts a vote*", RoomChatMessageBubbles.BLUE);
        return true;
    }

    /** Closes elections that are due; called by the city clock. */
    void tick() {
        for (String[] e : Db.rows("SELECT id FROM habnut_rp_elections WHERE closed = 0 AND closes_at <= ?", Nutropolis.now())) {
            int id = Integer.parseInt(e[0]);
            if (Db.update("UPDATE habnut_rp_elections SET closed = 1 WHERE id = ? AND closed = 0", id) == 0) continue;
            String[] winner = Db.row("SELECT c.user_id, (SELECT COUNT(*) FROM habnut_rp_votes v WHERE v.election_id = c.election_id"
                    + " AND v.candidate_id = c.user_id) votes FROM habnut_rp_candidates c WHERE c.election_id = ? ORDER BY votes DESC LIMIT 1", id);
            if (winner == null) continue;
            int mayor = Integer.parseInt(winner[0]);
            Db.update("UPDATE habnut_rp_elections SET winner_id = ? WHERE id = ?", mayor, id);
            setState("mayor", mayor);
            broadcast(nameOf(mayor) + " has been elected Mayor of Nutropolis with " + winner[1] + " votes!");
        }
    }

    static void broadcast(String message) {
        for (Habbo h : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (h.getClient() != null) h.getClient().sendResponse(new BubbleAlertComposer("admin.transient", message));
        }
    }

    private boolean isMayor(Habbo h, Citizen me) {
        if (state("mayor", 0) == me.userId) return true;
        this.city.no(h, "Only the Mayor can do that.");
        return false;
    }

    private boolean setTax(Habbo h, Citizen me, String[] p) {
        if (!this.isMayor(h, me)) return true;
        long tax = City.amount(p, 1);
        if (tax < 0 || tax > MAX_TAX) return this.city.no(h, "Usage: :settax <0-" + MAX_TAX + ">");
        setState("tax", tax);
        broadcast("The Mayor has set the city's wage tax to " + tax + "%.");
        return true;
    }

    private boolean announce(Habbo h, Citizen me, String[] p) {
        if (!this.isMayor(h, me)) return true;
        if (p.length < 2) return this.city.no(h, "Usage: :announce <message>");
        if (!this.city.cool(me.userId, "announce", 300_000)) return this.city.no(h, "One announcement every five minutes.");
        broadcast("Mayor " + h.getHabboInfo().getUsername() + ": " + City.rest(p, 1));
        return true;
    }

    private boolean bonus(Habbo h, Citizen me, String[] p) {
        if (!this.isMayor(h, me)) return true;
        long amount = City.amount(p, 2);
        if (p.length < 3 || amount <= 0 || amount > 500) return this.city.no(h, "Usage: :bonus <name> <1-500>, from the treasury.");
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(p[1]);
        Citizen them = target == null ? null : this.city.citizen(target);
        if (them == null) return this.city.no(h, p[1] + " is not online.");
        if (Db.update("UPDATE habnut_rp_state SET value = CAST(value AS SIGNED) - ? WHERE name = 'treasury' AND CAST(value AS SIGNED) >= ?",
                amount, amount) == 0) {
            return this.city.no(h, "The treasury cannot afford that.");
        }
        giveBank(them, amount, "mayor bonus");
        target.whisper("The Mayor paid you a $" + amount + " bonus from the treasury.", RoomChatMessageBubbles.GREEN);
        h.whisper("Paid.", RoomChatMessageBubbles.GREEN);
        return true;
    }

    // ---- property ---------------------------------------------------------------------

    private boolean properties(Habbo h) {
        List<String> lines = new ArrayList<>();
        lines.add("<b>Property in Nutropolis</b>");
        lines.add("Go there and type :buyproperty. Paid from your bank.");
        lines.add("");
        for (String[] r : Db.rows("SELECT r.name, p.price, p.owner_id FROM habnut_rp_properties p JOIN rooms r ON r.id = p.room_id ORDER BY p.price")) {
            lines.add(r[0] + ": $" + r[1] + (Integer.parseInt(r[2]) > 0 ? " (owned by " + nameOf(Long.parseLong(r[2])) + ")" : " (for sale)"));
        }
        h.alert(String.join("\r", lines));
        return true;
    }

    private boolean buyProperty(Habbo h, Citizen me, Room room) {
        String[] prop = Db.row("SELECT price, owner_id FROM habnut_rp_properties WHERE room_id = ?", room.getId());
        if (prop == null) return this.city.no(h, "This place is not for sale. Type :properties.");
        if (Integer.parseInt(prop[1]) > 0) return this.city.no(h, "Someone already lives here.");
        int price = Integer.parseInt(prop[0]);
        if (Db.update("UPDATE habnut_rp_properties SET owner_id = ?, bought_at = ? WHERE room_id = ? AND owner_id = 0",
                me.userId, Nutropolis.now(), room.getId()) == 0) {
            return this.city.no(h, "Someone beat you to it.");
        }
        if (!takeBank(me, price, "property")) {
            Db.update("UPDATE habnut_rp_properties SET owner_id = 0 WHERE room_id = ?", room.getId());
            return this.city.no(h, "This costs $" + price + " from your bank.");
        }
        addTreasury(price);
        room.setOwnerId(me.userId);
        room.setOwnerName(h.getHabboInfo().getUsername());
        room.setNeedsUpdate(true);
        room.save();
        room.refreshRightsForHabbo(h);
        h.shout("*gets the keys to " + room.getName() + "!*", RoomChatMessageBubbles.GREEN);
        h.whisper("It is yours: decorate it as you like. Reload the room to see your owner tools.", RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean sellProperty(Habbo h, Citizen me, Room room) {
        String[] prop = Db.row("SELECT price, owner_id FROM habnut_rp_properties WHERE room_id = ?", room.getId());
        if (prop == null || Integer.parseInt(prop[1]) != me.userId) return this.city.no(h, "You do not own this place.");
        String[] biz = Db.row("SELECT id, bank FROM habnut_rp_businesses WHERE room_id = ?", room.getId());
        if (biz != null) {
            giveBank(me, Long.parseLong(biz[1]), "business closed");
            Db.update("DELETE FROM habnut_rp_businesses WHERE id = ?", Integer.parseInt(biz[0]));
            Db.update("UPDATE habnut_rp_citizens SET job_id = 0, job_rank = 0 WHERE job_id = ?", -Integer.parseInt(biz[0]));
            this.businessJobs.remove(Integer.parseInt(biz[0]));
        }
        int refund = Integer.parseInt(prop[0]) / 2;
        Db.update("UPDATE habnut_rp_properties SET owner_id = 0, bought_at = 0 WHERE room_id = ?", room.getId());
        room.ejectUserFurni(me.userId);
        room.setOwnerId(1);
        room.setOwnerName("Habnut");
        room.setNeedsUpdate(true);
        room.save();
        room.refreshRightsForHabbo(h);
        giveBank(me, refund, "property sold");
        h.whisper("Sold back to the city for $" + refund + ". Your furniture is in your inventory.", RoomChatMessageBubbles.GREEN);
        return true;
    }

    // ---- businesses ---------------------------------------------------------------------

    /** A business as a job its staff work, wages paid by the business. */
    Job businessJob(int businessId) {
        return this.businessJobs.computeIfAbsent(businessId, id -> {
            String[] b = Db.row("SELECT name, room_id, wage FROM habnut_rp_businesses WHERE id = ?", id);
            return b == null ? null : new Job(-id, "business" + id, b[0], "business", Integer.parseInt(b[1]),
                    Integer.parseInt(b[2]), 10, new String[] {"Staff"}, 0);
        });
    }

    /** Pays a wage out of the business's bank; false when it cannot. */
    static boolean payFromBusiness(int businessId, int wage) {
        return Db.update("UPDATE habnut_rp_businesses SET bank = bank - ? WHERE id = ? AND bank >= ?", wage, businessId, wage) == 1;
    }

    private String[] businessHere(Room room) {
        return Db.row("SELECT id, owner_id, name, bank, wage FROM habnut_rp_businesses WHERE room_id = ?", room.getId());
    }

    private boolean openBusiness(Habbo h, Citizen me, Room room, String[] p) {
        if (p.length < 2) return this.city.no(h, "Usage: :openbusiness <name>, in a property you own.");
        String[] prop = Db.row("SELECT owner_id FROM habnut_rp_properties WHERE room_id = ?", room.getId());
        if (prop == null || Integer.parseInt(prop[0]) != me.userId) return this.city.no(h, "Open it in a property you own.");
        if (this.businessHere(room) != null) return this.city.no(h, "There is already a business here.");
        if (!this.hasLicence(me, "business")) return this.city.no(h, "You need a business licence from the Town Hall.");
        String name = City.rest(p, 1);
        if (name.length() > 48) name = name.substring(0, 48);
        if (!takeBank(me, BUSINESS_FEE, "business opened")) return this.city.no(h, "Opening a business costs $" + BUSINESS_FEE + " from your bank.");
        addTreasury(BUSINESS_FEE);
        Db.insert("INSERT INTO habnut_rp_businesses (owner_id, name, room_id, created_at) VALUES (?, ?, ?, ?)",
                me.userId, name, room.getId(), Nutropolis.now());
        h.shout("*opens " + name + " for business!*", RoomChatMessageBubbles.GREEN);
        h.whisper("Fund wages with :pay, hire with :hire <name>, set pay with :setwage <amount>.", RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean business(Habbo h, Room room) {
        String[] b = this.businessHere(room);
        if (b == null) return this.city.no(h, "There is no business here.");
        long staff = Db.number("SELECT COUNT(*) FROM habnut_rp_citizens WHERE job_id = ?", -Integer.parseInt(b[0]));
        h.whisper(b[2] + ", owned by " + nameOf(Long.parseLong(b[1])) + "  ·  bank $" + b[3] + "  ·  wage $" + b[4] + "  ·  " + staff + " staff",
                RoomChatMessageBubbles.GREEN);
        return true;
    }

    private String[] ownBusinessHere(Habbo h, Citizen me, Room room) {
        String[] b = this.businessHere(room);
        if (b == null || Integer.parseInt(b[1]) != me.userId) {
            this.city.no(h, "Do that in a business you own.");
            return null;
        }
        return b;
    }

    private boolean hire(Habbo h, Citizen me, Room room, String[] p) {
        String[] b = this.ownBusinessHere(h, me, room);
        if (b == null) return true;
        Habbo target = this.city.near(h, p, 3);
        if (target == null) return true;
        Citizen them = this.city.citizen(target);
        if (them == null) return true;
        this.city.endShift(target, them, "You changed jobs, so your shift has ended.");
        synchronized (them) {
            them.jobId = -Integer.parseInt(b[0]);
            them.jobRank = 0;
            Store.save(them);
        }
        h.talk("*hires " + target.getHabboInfo().getUsername() + "*", RoomChatMessageBubbles.GREEN);
        target.whisper("You work at " + b[2] + " now: $" + b[4] + " a shift. Type :work here to start.", RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean fire(Habbo h, Citizen me, Room room, String[] p) {
        String[] b = this.ownBusinessHere(h, me, room);
        if (b == null) return true;
        if (p.length < 2) return this.city.no(h, "Usage: :fire <name>");
        int changed = Db.update("UPDATE habnut_rp_citizens c JOIN users u ON u.id = c.user_id SET c.job_id = 0, c.job_rank = 0"
                + " WHERE u.username = ? AND c.job_id = ?", p[1], -Integer.parseInt(b[0]));
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(p[1]);
        Citizen them = target == null ? null : this.city.cached(target);
        if (them != null && them.jobId == -Integer.parseInt(b[0])) {
            this.city.endShift(target, them, "You were let go.");
            synchronized (them) {
                them.jobId = 0;
                them.jobRank = 0;
            }
            changed = 1;
        }
        return changed > 0 ? this.city.no(h, p[1] + " no longer works here.") : this.city.no(h, p[1] + " does not work here.");
    }

    private boolean setWage(Habbo h, Citizen me, Room room, String[] p) {
        String[] b = this.ownBusinessHere(h, me, room);
        if (b == null) return true;
        long wage = City.amount(p, 1);
        if (wage < 5 || wage > 200) return this.city.no(h, "Usage: :setwage <5-200>");
        Db.update("UPDATE habnut_rp_businesses SET wage = ? WHERE id = ?", wage, Integer.parseInt(b[0]));
        this.businessJobs.remove(Integer.parseInt(b[0]));
        h.whisper("Staff now earn $" + wage + " a shift.", RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean pay(Habbo h, Citizen me, Room room, String[] p) {
        String[] b = this.businessHere(room);
        if (b == null) return this.city.no(h, "Pay inside a business. To pay a person, use :give.");
        long amount = City.amount(p, 1);
        if (amount <= 0) return this.city.no(h, "Usage: :pay <amount>");
        if (!takeCash(me, amount, "paid business")) return this.city.no(h, "You only have $" + me.cash + " on you.");
        Db.update("UPDATE habnut_rp_businesses SET bank = bank + ? WHERE id = ?", amount, Integer.parseInt(b[0]));
        h.talk("*pays " + b[2] + " $" + amount + "*", RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean withdrawBusiness(Habbo h, Citizen me, Room room, String[] p) {
        String[] b = this.ownBusinessHere(h, me, room);
        if (b == null) return true;
        long amount = City.amount(p, 1);
        if (amount <= 0) return this.city.no(h, "Usage: :withdrawbusiness <amount>");
        if (Db.update("UPDATE habnut_rp_businesses SET bank = bank - ? WHERE id = ? AND bank >= ?", amount, Integer.parseInt(b[0]), amount) == 0) {
            return this.city.no(h, "The business has $" + b[3] + ".");
        }
        giveBank(me, amount, "business takings");
        h.whisper("$" + amount + " moved to your bank.", RoomChatMessageBubbles.GREEN);
        return true;
    }

    // ---- licences and cars ------------------------------------------------------------------

    boolean hasLicence(Citizen c, String licence) {
        return Db.number("SELECT COUNT(*) FROM habnut_rp_licences WHERE user_id = ? AND licence = ?", c.userId, licence) > 0;
    }

    private boolean licence(Habbo h, Citizen me, Room room, String[] p) {
        if (p.length < 2 || !LICENCES.containsKey(p[1].toLowerCase())) {
            return this.city.no(h, "Usage: :licence driving ($" + LICENCES.get("driving") + ") or :licence business ($" + LICENCES.get("business") + ")");
        }
        if (!this.atTownHall(h, room)) return true;
        String kind = p[1].toLowerCase();
        if (this.hasLicence(me, kind)) return this.city.no(h, "You already have one.");
        if (me.jailed() || me.wanted > 0) return this.city.no(h, "Not while the police are after you.");
        int fee = LICENCES.get(kind);
        if (!takeBank(me, fee, kind + " licence")) return this.city.no(h, "A " + kind + " licence costs $" + fee + " from your bank.");
        addTreasury(fee);
        Db.update("INSERT IGNORE INTO habnut_rp_licences (user_id, licence, granted_at) VALUES (?, ?, ?)", me.userId, kind, Nutropolis.now());
        h.talk("*is handed a " + kind + " licence*", RoomChatMessageBubbles.BLUE);
        return true;
    }

    private boolean buyCar(Habbo h, Citizen me) {
        if (!this.hasLicence(me, "driving")) return this.city.no(h, "You need a driving licence from the Town Hall first.");
        if (this.hasLicence(me, "car")) return this.city.no(h, "You already have a car. Type :drive.");
        if (!takeBank(me, CAR_PRICE, "car")) return this.city.no(h, "A car costs $" + CAR_PRICE + " from your bank.");
        Db.update("INSERT IGNORE INTO habnut_rp_licences (user_id, licence, granted_at) VALUES (?, 'car', ?)", me.userId, Nutropolis.now());
        h.shout("*buys a shiny new car!*", RoomChatMessageBubbles.GREEN);
        h.whisper("Type :drive to get in and out.", RoomChatMessageBubbles.GREEN);
        return true;
    }

    // ---- the court -----------------------------------------------------------------------------

    private boolean appeal(Habbo h, Citizen me) {
        String[] record = Db.row("SELECT id, offence FROM habnut_rp_records WHERE user_id = ? AND offence NOT LIKE '[overturned]%'"
                + " ORDER BY id DESC LIMIT 1", me.userId);
        if (record == null) return this.city.no(h, "You have nothing to appeal.");
        if (Db.number("SELECT COUNT(*) FROM habnut_rp_cases WHERE record_id = ?", Integer.parseInt(record[0])) > 0) {
            return this.city.no(h, "That case has already been heard or is waiting for a judge.");
        }
        if (!takeCash(me, APPEAL_FEE, "appeal")) return this.city.no(h, "An appeal costs $" + APPEAL_FEE + " in cash.");
        Db.insert("INSERT INTO habnut_rp_cases (user_id, record_id, created_at) VALUES (?, ?, ?)", me.userId, Integer.parseInt(record[0]), Nutropolis.now());
        h.whisper("Appeal lodged against \"" + record[1] + "\". A judge will hear it.", RoomChatMessageBubbles.BLUE);
        return true;
    }

    private boolean isJudge(Habbo h, Citizen me) {
        Job job = this.city.jobOf(me);
        if (job != null && "judge".equals(job.code()) && this.city.onShift(me)) return true;
        this.city.no(h, "Only a judge on shift can do that.");
        return false;
    }

    private boolean cases(Habbo h, Citizen me) {
        if (!this.isJudge(h, me)) return true;
        List<String> lines = new ArrayList<>();
        for (String[] r : Db.rows("SELECT u.username, rec.offence, rec.minutes FROM habnut_rp_cases c JOIN users u ON u.id = c.user_id"
                + " JOIN habnut_rp_records rec ON rec.id = c.record_id WHERE c.status = 'open' ORDER BY c.id LIMIT 10")) {
            lines.add(r[0] + ": " + r[1] + " (" + r[2] + " min)");
        }
        h.whisper(lines.isEmpty() ? "No cases waiting." : "Cases: " + String.join("; ", lines), RoomChatMessageBubbles.BLUE);
        return true;
    }

    private boolean verdict(Habbo h, Citizen me, Room room, String[] p) {
        if (!this.isJudge(h, me)) return true;
        if (!this.atTownHall(h, room)) return true;
        if (p.length < 3 || !(p[2].equalsIgnoreCase("innocent") || p[2].equalsIgnoreCase("guilty"))) {
            return this.city.no(h, "Usage: :verdict <name> innocent|guilty");
        }
        String[] c = Db.row("SELECT c.id, c.user_id, c.record_id FROM habnut_rp_cases c JOIN users u ON u.id = c.user_id"
                + " WHERE u.username = ? AND c.status = 'open' ORDER BY c.id LIMIT 1", p[1]);
        if (c == null) return this.city.no(h, p[1] + " has no case waiting.");
        boolean innocent = p[2].equalsIgnoreCase("innocent");
        Db.update("UPDATE habnut_rp_cases SET status = ?, judge_id = ? WHERE id = ?", innocent ? "innocent" : "guilty", me.userId, Integer.parseInt(c[0]));
        int userId = Integer.parseInt(c[1]);
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (innocent) {
            Db.update("UPDATE habnut_rp_records SET offence = LEFT(CONCAT('[overturned] ', offence), 128) WHERE id = ?", Integer.parseInt(c[2]));
            Citizen them = target == null ? null : this.city.cached(target);
            if (them != null) {
                synchronized (them) {
                    if (them.jailed()) them.jailedUntil = Nutropolis.now() - 1;
                    them.cash += APPEAL_FEE;
                    Store.save(them);
                }
            } else {
                Db.update("UPDATE habnut_rp_citizens SET cash = cash + ?, jailed_until = LEAST(jailed_until, ?) WHERE user_id = ?",
                        APPEAL_FEE, Nutropolis.now() - 1, userId);
            }
        }
        h.shout("*bangs the gavel: " + p[1] + " is " + (innocent ? "INNOCENT" : "GUILTY") + "!*", RoomChatMessageBubbles.BLUE);
        if (target != null) {
            target.whisper(innocent ? "Your appeal succeeded: conviction overturned and your fee refunded." : "Your appeal failed.",
                    innocent ? RoomChatMessageBubbles.GREEN : RoomChatMessageBubbles.ALERT);
        }
        return true;
    }

    // ---- crafting ------------------------------------------------------------------------------

    static int qty(int userId, String item) {
        return (int) Db.number("SELECT qty FROM habnut_rp_inventory WHERE user_id = ? AND item = ?", userId, item);
    }

    static void addItem(int userId, String item, int qty) {
        Db.update("INSERT INTO habnut_rp_inventory (user_id, item, qty) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE qty = qty + VALUES(qty)",
                userId, item, qty);
    }

    static boolean takeItem(int userId, String item, int qty) {
        return Db.update("UPDATE habnut_rp_inventory SET qty = qty - ? WHERE user_id = ? AND item = ? AND qty >= ?", qty, userId, item, qty) == 1;
    }

    private boolean gather(Habbo h, Citizen me, Room room) {
        String kind = this.city.kindOf(room);
        if (!"street".equals(kind) && !"spawn".equals(kind)) return this.city.no(h, "Scavenge outside: the streets and the plaza.");
        if (!this.city.cool(me.userId, "gather", 60_000)) return this.city.no(h, "You have picked this spot clean. Try again in a minute.");
        String found = ThreadLocalRandom.current().nextInt(10) == 0 ? "herbs" : MATERIALS[ThreadLocalRandom.current().nextInt(MATERIALS.length)];
        addItem(me.userId, found, 1);
        h.talk("*rummages around and finds some " + found + "*", RoomChatMessageBubbles.NORMAL);
        return true;
    }

    private boolean items(Habbo h, Citizen me) {
        List<String> have = new ArrayList<>();
        for (String[] r : Db.rows("SELECT item, qty FROM habnut_rp_inventory WHERE user_id = ? AND qty > 0 ORDER BY item", me.userId)) {
            have.add(r[1] + " " + r[0]);
        }
        h.whisper((have.isEmpty() ? "Your pockets are empty." : "You have: " + String.join(", ", have))
                + "  ·  Recipes: bandage (2 cloth, 1 herbs), medkit (2 cloth, 2 herbs, 1 metal), lockpick (2 metal, 1 wood)",
                RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean craft(Habbo h, Citizen me, String[] p) {
        String what = p.length > 1 ? p[1].toLowerCase() : "";
        Map<String, Integer> recipe = RECIPES.get(what);
        if (recipe == null) return this.city.no(h, "Usage: :craft bandage|medkit|lockpick. Type :items for recipes.");
        for (Map.Entry<String, Integer> need : recipe.entrySet()) {
            if (qty(me.userId, need.getKey()) < need.getValue()) {
                return this.city.no(h, "You need " + need.getValue() + " " + need.getKey() + " for a " + what + ".");
            }
        }
        for (Map.Entry<String, Integer> need : recipe.entrySet()) {
            if (!takeItem(me.userId, need.getKey(), need.getValue())) return this.city.no(h, "You fumbled it. Check :items.");
        }
        addItem(me.userId, what, 1);
        h.talk("*crafts a " + what + "*", RoomChatMessageBubbles.NORMAL);
        return true;
    }

    private boolean use(Habbo h, Citizen me, Room room, String[] p) {
        String what = p.length > 1 ? p[1].toLowerCase() : "";
        switch (what) {
            case "bandage", "medkit" -> {
                if (me.health >= Nutropolis.MAX_HEALTH) return this.city.no(h, "You are already in good health.");
                if (!takeItem(me.userId, what, 1)) return this.city.no(h, "You have no " + what + ".");
                int heal = what.equals("bandage") ? 30 : 70;
                synchronized (me) {
                    me.health = Math.min(Nutropolis.MAX_HEALTH, me.health + heal);
                    Store.save(me);
                }
                h.talk("*patches themself up with a " + what + "*", RoomChatMessageBubbles.GREEN);
                h.whisper("Health " + me.health + "/" + Nutropolis.MAX_HEALTH, RoomChatMessageBubbles.GREEN);
                return true;
            }
            case "lockpick" -> {
                if (!me.jailed()) return this.city.no(h, "There is nothing here to pick.");
                if (!takeItem(me.userId, "lockpick", 1)) return this.city.no(h, "You have no lockpick.");
                boolean free = ThreadLocalRandom.current().nextInt(100) < 35;
                synchronized (me) {
                    if (free) {
                        me.jailedUntil = Nutropolis.now() - 1;
                        me.wanted = Math.min(5, me.wanted + 2);
                    } else {
                        me.jailedUntil += 60;
                    }
                    Store.save(me);
                }
                if (free) {
                    h.shout("*picks the lock and escapes!*", RoomChatMessageBubbles.RED);
                    City.send(h, this.city.roomOf("street"));
                } else {
                    h.talk("*snaps a lockpick in the cell door*", RoomChatMessageBubbles.RED);
                    h.whisper("The guards heard that: a minute added to your sentence.", RoomChatMessageBubbles.ALERT);
                }
                return true;
            }
            default -> {
                return this.city.no(h, "Usage: :use bandage|medkit|lockpick");
            }
        }
    }
}

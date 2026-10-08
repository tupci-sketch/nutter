package com.habnut.nutropolis;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The rules of Nutropolis: who may do what, where, and what it costs. Every
 * command a citizen types arrives in {@link #handle}.
 */
final class City {
    static final String SPAWN = "spawn";
    static final String BANK = "bank";
    static final String JAIL = "jail";
    static final String HOSPITAL = "hospital";
    static final String POLICE = "police";

    private static final int KO_EFFECT = 236;
    private static final int KO_SECONDS = 15;
    private static final int HIT_COOLDOWN_MS = 2500;
    private static final int HEAL_COOLDOWN_MS = 4000;
    private static final int DISPATCH_COOLDOWN_MS = 30000;
    private static final int MAX_WANTED = 5;

    private final Map<Integer, Citizen> citizens = new ConcurrentHashMap<>();
    /** Who is on shift, and since when (milliseconds). */
    private final Map<Integer, Long> shifts = new ConcurrentHashMap<>();
    private final Map<Integer, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<Integer> knockedOut = ConcurrentHashMap.newKeySet();
    final Civic civic = new Civic(this);

    private volatile Map<Integer, Job> jobs = Map.of();
    private volatile Map<String, Job> jobsByCode = Map.of();
    private volatile Map<Integer, String[]> rooms = Map.of();

    void reload() {
        Map<Integer, Job> byId = new HashMap<>();
        Map<String, Job> byCode = new HashMap<>();
        for (Job job : Store.jobs()) {
            byId.put(job.id(), job);
            byCode.put(job.code().toLowerCase(), job);
        }
        this.jobs = byId;
        this.jobsByCode = byCode;
        this.rooms = Store.rooms();
    }

    // ---- places -----------------------------------------------------------

    String kindOf(Room room) {
        String[] r = room == null ? null : this.rooms.get(room.getId());
        return r == null ? null : r[0];
    }

    boolean inCity(Room room) {
        return this.kindOf(room) != null;
    }

    private boolean safe(Room room) {
        String[] r = this.rooms.get(room.getId());
        return r == null || "1".equals(r[1]);
    }

    int roomOf(String kind) {
        return this.rooms.entrySet().stream()
                .filter(e -> kind.equals(e.getValue()[0]))
                .mapToInt(Map.Entry::getKey)
                .min()
                .orElse(0);
    }

    static void send(Habbo habbo, int roomId) {
        if (roomId > 0 && habbo.getClient() != null) habbo.getClient().sendResponse(new ForwardToRoomComposer(roomId));
    }

    /** A forward while a room is still loading is ignored by the client (or drawn over it): wait a moment. */
    static void sendSoon(Habbo habbo, int roomId) {
        Emulator.getThreading().run(() -> send(habbo, roomId), 1500);
    }

    // ---- worlds -------------------------------------------------------------
    // The hotel and Nutropolis are entered separately, from the website; the
    // website records which (habnut_user_world) and the city keeps each player
    // in their own world.

    static final String CITY_WORLD = "city";
    private final Map<Integer, Object[]> worlds = new ConcurrentHashMap<>();

    String worldOf(Habbo habbo) {
        int id = habbo.getHabboInfo().getId();
        Object[] cached = this.worlds.get(id);
        long now = System.currentTimeMillis();
        if (cached != null && (long) cached[1] > now) return (String) cached[0];
        String[] row = Db.row("SELECT world FROM habnut_user_world WHERE user_id = ?", id);
        String world = row == null ? "hotel" : row[0];
        this.worlds.put(id, new Object[] {world, now + 15_000});
        return world;
    }

    boolean inCityWorld(Habbo habbo) {
        return CITY_WORLD.equals(this.worldOf(habbo));
    }

    void forgetWorld(int userId) {
        this.worlds.remove(userId);
    }

    private int hotelHome(Habbo habbo) {
        int home = habbo.getHabboInfo().getHomeRoom();
        if (home > 0 && !this.rooms.containsKey(home)) return home;
        return Emulator.getConfig().getInt("hotel.home.room", 0);
    }

    // ---- citizens ---------------------------------------------------------

    Citizen citizen(Habbo habbo) {
        int id = habbo.getHabboInfo().getId();
        Citizen c = this.citizens.get(id);
        if (c != null) return c;
        Citizen loaded = Store.load(id);
        if (loaded == null) return null;
        Citizen raced = this.citizens.putIfAbsent(id, loaded);
        return raced != null ? raced : loaded;
    }

    /** Known already this session, without touching the database. */
    Citizen cached(Habbo habbo) {
        return this.citizens.get(habbo.getHabboInfo().getId());
    }

    Job jobOf(Citizen c) {
        return c.jobId < 0 ? this.civic.businessJob(-c.jobId) : this.jobs.get(c.jobId);
    }

    boolean onShift(Citizen c) {
        return this.shifts.containsKey(c.userId);
    }

    private boolean onDuty(Citizen c, String kind) {
        Job job = this.jobOf(c);
        return job != null && kind.equals(job.kind()) && this.onShift(c);
    }

    // ---- what happens on its own ---------------------------------------------

    /** Sends a player in the other world's room back to their own; true if it did. */
    boolean keepInWorld(Habbo habbo, Room room) {
        boolean cityWorld = this.inCityWorld(habbo);
        if (cityWorld && !this.inCity(room)) {
            habbo.whisper("You are in Nutropolis. To visit the hotel, use Enter Habnut on the website.", RoomChatMessageBubbles.ALERT);
            Citizen jailed = this.cached(habbo);
            sendSoon(habbo, jailed != null && jailed.jailed() ? this.roomOf(JAIL) : this.roomOf(SPAWN));
            return true;
        }
        if (!cityWorld && this.inCity(room)) {
            habbo.whisper("Nutropolis has its own entrance: use Enter Nutropolis on the website.", RoomChatMessageBubbles.ALERT);
            sendSoon(habbo, this.hotelHome(habbo));
            return true;
        }
        return false;
    }

    void entered(Habbo habbo, Room room) {
        if (this.keepInWorld(habbo, room)) return;
        Citizen c = this.inCity(room) ? this.citizen(habbo) : this.cached(habbo);
        if (c == null) return;

        if (c.jailed() && !JAIL.equals(this.kindOf(room))) {
            habbo.whisper("You are still serving your sentence.", RoomChatMessageBubbles.ALERT);
            sendSoon(habbo, this.roomOf(JAIL));
            return;
        }
        if (!this.inCity(room)) {
            this.endShift(habbo, c, "You left Nutropolis, so your shift has ended.");
            return;
        }
        if (c.shifts == 0 && c.jobId == 0 && SPAWN.equals(this.kindOf(room))) {
            habbo.whisper("Welcome to Nutropolis! You have $" + c.cash + " in your pocket. Type :rphelp to get started.",
                    RoomChatMessageBubbles.GREEN);
        }
    }

    void left(Habbo habbo) {
        Citizen c = this.citizens.remove(habbo.getHabboInfo().getId());
        this.shifts.remove(habbo.getHabboInfo().getId());
        this.knockedOut.remove(habbo.getHabboInfo().getId());
        if (c != null) {
            synchronized (c) {
                Store.save(c);
            }
        }
    }

    /** Every 20 seconds: wages, sentences served, hospital care. */
    void tick() {
        this.civic.tick();
        // A resumed session can come back in the other world's room without entering it.
        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room != null) this.keepInWorld(habbo, room);
        }
        long nowMs = System.currentTimeMillis();
        for (Map.Entry<Integer, Long> shift : this.shifts.entrySet()) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(shift.getKey());
            Citizen c = this.citizens.get(shift.getKey());
            Job job = c == null ? null : this.jobOf(c);
            if (habbo == null || c == null || job == null) {
                this.shifts.remove(shift.getKey());
                continue;
            }
            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (!job.mobile() && (room == null || room.getId() != job.roomId())) {
                this.endShift(habbo, c, "You left your workplace, so your shift has ended.");
                continue;
            }
            if (nowMs - shift.getValue() >= job.shiftMinutes() * 60_000L) {
                int gross = job.wageAt(c.jobRank);
                if ("business".equals(job.kind()) && !Civic.payFromBusiness(-job.id(), gross)) {
                    this.shifts.put(c.userId, nowMs);
                    habbo.whisper(job.name() + " could not afford your wage this time.", RoomChatMessageBubbles.ALERT);
                    continue;
                }
                int pay = this.civic.afterTax(gross);
                long balance;
                synchronized (c) {
                    c.bank += pay;
                    c.shifts++;
                    balance = c.bank;
                    Store.save(c);
                }
                Store.ledger(c.userId, "bank", pay, balance, "wage", 0);
                this.shifts.put(c.userId, nowMs);
                habbo.whisper("Payday! $" + pay + " went into your bank account" + (gross > pay ? " ($" + (gross - pay) + " city tax)" : "") + ".", RoomChatMessageBubbles.GREEN);
            }
        }

        for (Citizen c : this.citizens.values()) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(c.userId);
            if (habbo == null) continue;
            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (c.jailedUntil > 0 && !c.jailed()) {
                synchronized (c) {
                    c.jailedUntil = 0;
                    Store.save(c);
                }
                habbo.whisper("You have served your time. You are free to go.", RoomChatMessageBubbles.GREEN);
                if (JAIL.equals(this.kindOf(room))) send(habbo, this.roomOf(POLICE) > 0 ? this.roomOf(POLICE) : this.roomOf(SPAWN));
            }
            if (HOSPITAL.equals(this.kindOf(room)) && c.health < Nutropolis.MAX_HEALTH && !this.knockedOut.contains(c.userId)) {
                synchronized (c) {
                    c.health = Math.min(Nutropolis.MAX_HEALTH, c.health + 10);
                    Store.save(c);
                }
                if (c.health == Nutropolis.MAX_HEALTH) habbo.whisper("You feel as good as new.", RoomChatMessageBubbles.GREEN);
            }
        }
    }

    void endShift(Habbo habbo, Citizen c, String why) {
        if (this.shifts.remove(c.userId) != null) {
            habbo.whisper(why, RoomChatMessageBubbles.ALERT);
            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room != null) room.giveEffect(habbo, 0, -1);
        }
    }

    private void knockOut(Habbo victim, Citizen c) {
        Room room = victim.getHabboInfo().getCurrentRoom();
        this.knockedOut.add(c.userId);
        this.endShift(victim, c, "You were knocked out, so your shift has ended.");
        victim.getRoomUnit().setCanWalk(false);
        if (room != null) room.giveEffect(victim, KO_EFFECT, KO_SECONDS);
        victim.shout("*is knocked out*", RoomChatMessageBubbles.RED);
        Emulator.getThreading().run(() -> this.wakeInHospital(victim, c), KO_SECONDS * 1000L);
    }

    private void wakeInHospital(Habbo habbo, Citizen c) {
        if (!this.knockedOut.remove(c.userId)) return; // revived, or arrested
        habbo.getRoomUnit().setCanWalk(true);
        synchronized (c) {
            c.health = Math.max(c.health, Nutropolis.MAX_HEALTH / 4);
            Store.save(c);
        }
        if (!c.jailed()) {
            habbo.whisper("You wake up in Nutropolis General.", RoomChatMessageBubbles.ALERT);
            send(habbo, this.roomOf(HOSPITAL));
        }
    }

    // ---- commands --------------------------------------------------------------

    static final String[] KEYS = {
        "city", "nutropolis", "hotel", "classic", "rphelp", "stats", "jobs", "apply", "quitjob", "work", "startwork",
        "stopwork", "balance", "deposit", "withdraw", "give", "hit", "heal", "arrest", "release", "wanted", "dispatch",
        "911", "me", "drive", "setjob", "rpreload",
        "mayor", "runformayor", "vote", "settax", "announce", "bonus", "properties", "buyproperty", "sellproperty",
        "openbusiness", "business", "hire", "fire", "setwage", "pay", "withdrawbusiness", "licence", "license", "buycar",
        "appeal", "cases", "verdict", "gather", "craft", "use", "items", "rpmoney", "goto"
    };

    boolean handle(Habbo habbo, String[] params) {
        String verb = params[0].toLowerCase();
        Room room = habbo.getHabboInfo().getCurrentRoom();

        switch (verb) {
            case "city", "nutropolis", "hotel", "classic" -> {
                habbo.whisper(this.inCityWorld(habbo)
                        ? "You are in Nutropolis. The hotel has its own entrance: Enter Habnut on the website."
                        : "Nutropolis has its own entrance: Enter Nutropolis on the website.", RoomChatMessageBubbles.BLUE);
                return true;
            }
            case "rphelp" -> {
                habbo.alert(String.join("\r",
                        "<b>Nutropolis</b>  enter it from the website: Enter Nutropolis",
                        ":stats  :balance  :items  :me <action>  :911 <message>",
                        "",
                        "<b>Work</b>  :jobs  :apply <job>  :work  :stopwork  :quitjob",
                        "<b>Money</b>  :deposit / :withdraw <amount> (bank)  :give <name> <amount>",
                        "<b>Fights</b>  :hit <name>, outside safe places",
                        "<b>Police & medics</b>  :arrest <name> [offence]  :release  :wanted  :heal <name>  :drive",
                        "",
                        "<b>Government</b>  :mayor  :runformayor  :vote <name> (Town Hall)",
                        "Mayor only: :settax <0-20>  :announce <message>  :bonus <name> <amount>",
                        "<b>Property</b>  :properties  :buyproperty  :sellproperty",
                        "<b>Business</b>  :openbusiness <name>  :business  :hire / :fire <name>  :setwage  :pay <amount>  :withdrawbusiness",
                        "<b>Licences</b>  :licence driving|business (Town Hall)  :buycar  :drive",
                        "<b>Court</b>  :appeal  ·  judges: :cases  :verdict <name> innocent|guilty",
                        "<b>Crafting</b>  :gather (streets)  :craft bandage|medkit|lockpick  :use <item>"));
                return true;
            }
            default -> {}
        }

        boolean staffTool = verb.equals("setjob") || verb.equals("rpreload") || verb.equals("rpmoney") || verb.equals("goto");
        if (!staffTool && (!this.inCityWorld(habbo) || !this.inCity(room))) {
            return this.no(habbo, "That only works in Nutropolis: use Enter Nutropolis on the website.");
        }
        Citizen me = this.citizen(habbo);
        if (me == null) return this.no(habbo, "The city could not find your papers. Try again in a moment.");
        if (this.knockedOut.contains(me.userId) && !verb.equals("stats") && !verb.equals("911")) {
            return this.no(habbo, "You are knocked out.");
        }

        return switch (verb) {
            case "stats" -> this.stats(habbo, me);
            case "jobs" -> this.jobs(habbo);
            case "apply" -> this.apply(habbo, me, room, params);
            case "quitjob" -> this.quit(habbo, me);
            case "work", "startwork" -> this.startWork(habbo, me, room);
            case "stopwork" -> {
                if (!this.onShift(me)) yield this.no(habbo, "You are not on a shift.");
                this.endShift(habbo, me, "Shift over. See you next time.");
                yield true;
            }
            case "balance" -> {
                habbo.whisper("Cash: $" + me.cash + "  ·  Bank: $" + me.bank, RoomChatMessageBubbles.GREEN);
                yield true;
            }
            case "deposit", "withdraw" -> this.bank(habbo, me, room, verb, params);
            case "give" -> this.give(habbo, me, params);
            case "hit" -> this.hit(habbo, me, room, params);
            case "heal" -> this.heal(habbo, me, params);
            case "arrest" -> this.arrest(habbo, me, params);
            case "release" -> this.release(habbo, me, params);
            case "wanted" -> this.wanted(habbo);
            case "dispatch", "911" -> this.dispatch(habbo, me, room, params);
            case "me" -> {
                if (params.length < 2) yield this.no(habbo, "Usage: :me <action>");
                habbo.talk("*" + habbo.getHabboInfo().getUsername() + " " + rest(params, 1) + "*", RoomChatMessageBubbles.NORMAL);
                yield true;
            }
            case "drive" -> this.drive(habbo, me, room);
            case "setjob" -> this.setJob(habbo, params);
            case "goto" -> {
                if (!habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) yield this.no(habbo, "Staff only.");
                long target = amount(params, 1);
                if (target <= 0) yield this.no(habbo, "Usage: :goto <room id>");
                send(habbo, (int) target);
                yield true;
            }
            case "rpmoney" -> {
                if (!habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) yield this.no(habbo, "Staff only.");
                Habbo target = params.length > 2 ? Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]) : null;
                long amount = amount(params, 2);
                Citizen them = target == null ? null : this.citizen(target);
                if (them == null || amount <= 0 || amount > 1_000_000) yield this.no(habbo, "Usage: :rpmoney <online name> <amount>, paid into their bank.");
                Civic.giveBank(them, amount, "staff");
                target.whisper("$" + amount + " was paid into your bank by the hotel.", RoomChatMessageBubbles.GREEN);
                yield this.no(habbo, "Paid $" + amount + " to " + target.getHabboInfo().getUsername() + ".");
            }
            case "rpreload" -> {
                if (!habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) yield this.no(habbo, "Staff only.");
                this.reload();
                habbo.whisper("Nutropolis reloaded: " + this.jobs.size() + " jobs, " + this.rooms.size() + " places.",
                        RoomChatMessageBubbles.GREEN);
                yield true;
            }
            default -> Civic.KEYS.contains(verb) && this.civic.handle(habbo, me, room, verb, params);
        };
    }

    private boolean stats(Habbo habbo, Citizen c) {
        Job job = this.jobOf(c);
        String work = job == null ? "unemployed" : job.rankName(c.jobRank) + " at " + job.name() + (this.onShift(c) ? " (on shift)" : "");
        String law = c.jailed() ? "in jail for " + Math.max(1, (c.jailedUntil - Nutropolis.now()) / 60) + " more min"
                : c.wanted > 0 ? "wanted (" + c.wanted + "/5)" : "clean";
        habbo.whisper("Health " + c.health + "/" + Nutropolis.MAX_HEALTH + "  ·  Cash $" + c.cash + "  ·  Bank $" + c.bank
                + "  ·  " + work + "  ·  " + law, RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean jobs(Habbo habbo) {
        List<String> lines = new ArrayList<>();
        lines.add("<b>Jobs in Nutropolis</b>");
        lines.add("Go to the workplace and type :apply <job>.");
        lines.add("");
        for (Job job : this.jobs.values().stream().sorted((a, b) -> Integer.compare(a.id(), b.id())).toList()) {
            Room place = Emulator.getGameEnvironment().getRoomManager().getRoom(job.roomId());
            lines.add("<b>" + job.code() + "</b> - " + job.name() + " at " + (place == null ? "?" : place.getName())
                    + ": $" + job.wage() + " every " + job.shiftMinutes() + " min");
        }
        habbo.alert(String.join("\r", lines));
        return true;
    }

    private boolean apply(Habbo habbo, Citizen c, Room room, String[] params) {
        if (params.length < 2) return this.no(habbo, "Usage: :apply <job>. Type :jobs to see who is hiring.");
        Job job = this.jobsByCode.get(params[1].toLowerCase());
        if (job == null) return this.no(habbo, "There is no job called " + params[1] + ". Type :jobs.");
        if (c.jobId == job.id()) return this.no(habbo, "You already work there.");
        if (room.getId() != job.roomId()) return this.no(habbo, "Apply in person, at " + job.name() + ".");
        if (c.jailed() || c.wanted > 0) return this.no(habbo, job.name() + " will not hire someone the police are after.");
        if (job.mobile() && Store.recordCount(c.userId) > 0) {
            return this.no(habbo, job.name() + " will not hire someone with a criminal record.");
        }
        this.endShift(habbo, c, "You changed jobs, so your shift has ended.");
        synchronized (c) {
            c.jobId = job.id();
            c.jobRank = 0;
            Store.save(c);
        }
        habbo.talk("*signs a contract with " + job.name() + "*", RoomChatMessageBubbles.NORMAL);
        habbo.whisper("You are now a " + job.rankName(0) + ". Type :work to start a shift.", RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean quit(Habbo habbo, Citizen c) {
        Job job = this.jobOf(c);
        if (job == null) return this.no(habbo, "You do not have a job.");
        this.endShift(habbo, c, "You quit, so your shift has ended.");
        synchronized (c) {
            c.jobId = 0;
            c.jobRank = 0;
            Store.save(c);
        }
        habbo.whisper("You quit " + job.name() + ".", RoomChatMessageBubbles.ALERT);
        return true;
    }

    private boolean startWork(Habbo habbo, Citizen c, Room room) {
        Job job = this.jobOf(c);
        if (job == null) return this.no(habbo, "You need a job first. Type :jobs.");
        if (this.onShift(c)) return this.no(habbo, "You are already on a shift.");
        if (c.jailed()) return this.no(habbo, "Not from a cell.");
        if (room.getId() != job.roomId()) return this.no(habbo, "Clock in at " + job.name() + ".");
        this.shifts.put(c.userId, System.currentTimeMillis());
        habbo.talk("*clocks in at " + job.name() + "*", RoomChatMessageBubbles.NORMAL);
        habbo.whisper("Shift started: $" + job.wageAt(c.jobRank) + " every " + job.shiftMinutes() + " minutes"
                + (job.mobile() ? ", anywhere in the city." : " while you stay here."), RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean bank(Habbo habbo, Citizen c, Room room, String verb, String[] params) {
        if (!BANK.equals(this.kindOf(room))) return this.no(habbo, "You need to be at the bank.");
        long amount = amount(params, 1);
        if (amount <= 0) return this.no(habbo, "Usage: :" + verb + " <amount>");
        long cash, bank;
        synchronized (c) {
            if (verb.equals("deposit")) {
                if (c.cash < amount) return this.no(habbo, "You only have $" + c.cash + " on you.");
                c.cash -= amount;
                c.bank += amount;
            } else {
                if (c.bank < amount) return this.no(habbo, "Your balance is $" + c.bank + ".");
                c.bank -= amount;
                c.cash += amount;
            }
            cash = c.cash;
            bank = c.bank;
            Store.save(c);
        }
        boolean in = verb.equals("deposit");
        Store.ledger(c.userId, "cash", in ? -amount : amount, cash, verb, 0);
        Store.ledger(c.userId, "bank", in ? amount : -amount, bank, verb, 0);
        habbo.whisper((in ? "Deposited $" : "Withdrew $") + amount + ". Cash $" + cash + "  ·  Bank $" + bank,
                RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean give(Habbo habbo, Citizen me, String[] params) {
        Habbo target = this.near(habbo, params, 2);
        if (target == null) return true;
        long amount = amount(params, 2);
        if (amount <= 0) return this.no(habbo, "Usage: :give <name> <amount>");
        Citizen them = this.citizen(target);
        if (them == null) return this.no(habbo, "They are not a citizen.");
        long mine, theirs;
        Citizen first = me.userId < them.userId ? me : them, second = first == me ? them : me;
        synchronized (first) {
            synchronized (second) {
                if (me.cash < amount) return this.no(habbo, "You only have $" + me.cash + " on you.");
                me.cash -= amount;
                them.cash += amount;
                mine = me.cash;
                theirs = them.cash;
                Store.save(me);
                Store.save(them);
            }
        }
        Store.ledger(me.userId, "cash", -amount, mine, "give", them.userId);
        Store.ledger(them.userId, "cash", amount, theirs, "give", me.userId);
        habbo.talk("*hands " + target.getHabboInfo().getUsername() + " $" + amount + "*", RoomChatMessageBubbles.NORMAL);
        return true;
    }

    private boolean hit(Habbo habbo, Citizen me, Room room, String[] params) {
        if (this.safe(room)) return this.no(habbo, "This is a safe place. Take it outside.");
        if (me.jailed()) return this.no(habbo, "Not in here.");
        if (!this.cool(me.userId, "hit", HIT_COOLDOWN_MS)) return true;
        Habbo target = this.near(habbo, params, 1);
        if (target == null) return true;
        Citizen them = this.citizen(target);
        if (them == null || this.knockedOut.contains(them.userId)) return this.no(habbo, "They are already down.");

        int damage = ThreadLocalRandom.current().nextInt(8, 17);
        boolean lawful = this.onDuty(me, Job.POLICE) && them.wanted > 0;
        boolean down;
        synchronized (them) {
            them.health = Math.max(0, them.health - damage);
            down = them.health == 0;
            Store.save(them);
        }
        if (!lawful) {
            synchronized (me) {
                me.wanted = Math.min(MAX_WANTED, me.wanted + 1);
                Store.save(me);
            }
        }
        habbo.talk("*swings at " + target.getHabboInfo().getUsername() + ", hitting them for " + damage + "*",
                RoomChatMessageBubbles.RED);
        target.whisper("Health " + them.health + "/" + Nutropolis.MAX_HEALTH, RoomChatMessageBubbles.RED);
        if (down) this.knockOut(target, them);
        return true;
    }

    private boolean heal(Habbo habbo, Citizen me, String[] params) {
        if (!this.onDuty(me, Job.MEDIC)) return this.no(habbo, "Only a medic on shift can do that.");
        if (!this.cool(me.userId, "heal", HEAL_COOLDOWN_MS)) return true;
        Habbo target = this.near(habbo, params, 1);
        if (target == null) return true;
        Citizen them = this.citizen(target);
        if (them == null) return true;
        boolean revived = this.knockedOut.remove(them.userId);
        synchronized (them) {
            them.health = Math.min(Nutropolis.MAX_HEALTH, Math.max(them.health, 0) + 35);
            Store.save(them);
        }
        if (revived) {
            target.getRoomUnit().setCanWalk(true);
            Room room = target.getHabboInfo().getCurrentRoom();
            if (room != null) room.giveEffect(target, 0, -1);
        }
        habbo.talk("*patches " + target.getHabboInfo().getUsername() + " up*", RoomChatMessageBubbles.GREEN);
        target.whisper("Health " + them.health + "/" + Nutropolis.MAX_HEALTH, RoomChatMessageBubbles.GREEN);
        return true;
    }

    private boolean arrest(Habbo habbo, Citizen me, String[] params) {
        if (!this.onDuty(me, Job.POLICE)) return this.no(habbo, "Only an officer on shift can do that.");
        Habbo target = this.near(habbo, params, 1);
        if (target == null) return true;
        Citizen them = this.citizen(target);
        if (them == null) return true;
        if (them.wanted == 0 && !this.knockedOut.contains(them.userId)) {
            return this.no(habbo, target.getHabboInfo().getUsername() + " is not wanted for anything.");
        }
        int minutes = Math.max(2, Math.min(15, them.wanted * 2));
        String offence = params.length > 2 ? rest(params, 2) : them.wanted > 0 ? "Assault" : "Disorderly conduct";
        synchronized (them) {
            them.wanted = 0;
            them.jailedUntil = Nutropolis.now() + minutes * 60;
            Store.save(them);
        }
        Store.record(them.userId, me.userId, offence, minutes);
        this.knockedOut.remove(them.userId);
        this.endShift(target, them, "You were arrested, so your shift has ended.");
        target.getRoomUnit().setCanWalk(true);
        habbo.talk("*cuffs " + target.getHabboInfo().getUsername() + " and reads them their rights*",
                RoomChatMessageBubbles.BLUE);
        target.whisper("Arrested for " + offence + ": " + minutes + " minutes in jail.", RoomChatMessageBubbles.ALERT);
        send(target, this.roomOf(JAIL));
        return true;
    }

    private boolean release(Habbo habbo, Citizen me, String[] params) {
        if (!this.onDuty(me, Job.POLICE)) return this.no(habbo, "Only an officer on shift can do that.");
        if (params.length < 2) return this.no(habbo, "Usage: :release <name>");
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);
        Citizen them = target == null ? null : this.cached(target);
        if (them == null || !them.jailed()) return this.no(habbo, params[1] + " is not in jail.");
        synchronized (them) {
            them.jailedUntil = Nutropolis.now() - 1;
            Store.save(them);
        }
        habbo.whisper(target.getHabboInfo().getUsername() + " will be let out.", RoomChatMessageBubbles.BLUE);
        return true;
    }

    private boolean wanted(Habbo habbo) {
        List<String> names = new ArrayList<>();
        for (Citizen c : this.citizens.values()) {
            if (c.wanted <= 0) continue;
            Habbo h = Emulator.getGameEnvironment().getHabboManager().getHabbo(c.userId);
            if (h != null) names.add(h.getHabboInfo().getUsername() + " (" + c.wanted + "/5)");
        }
        habbo.whisper(names.isEmpty() ? "Nobody is wanted right now." : "Wanted: " + String.join(", ", names),
                RoomChatMessageBubbles.BLUE);
        return true;
    }

    private boolean dispatch(Habbo habbo, Citizen me, Room room, String[] params) {
        if (params.length < 2) return this.no(habbo, "Usage: :911 <what is happening>");
        if (!this.cool(me.userId, "911", DISPATCH_COOLDOWN_MS)) return true;
        String call = "<b>Dispatch</b>\r" + habbo.getHabboInfo().getUsername() + " at " + room.getName() + ":\r" + rest(params, 1);
        int reached = 0;
        for (Map.Entry<Integer, Long> shift : this.shifts.entrySet()) {
            Citizen c = this.citizens.get(shift.getKey());
            Job job = c == null ? null : this.jobOf(c);
            Habbo responder = Emulator.getGameEnvironment().getHabboManager().getHabbo(shift.getKey());
            if (job != null && job.mobile() && responder != null) {
                responder.alert(call);
                reached++;
            }
        }
        habbo.whisper(reached == 0 ? "Nobody is on duty right now." : "Your call went out to " + reached + " on duty.",
                RoomChatMessageBubbles.BLUE);
        return true;
    }

    private boolean drive(Habbo habbo, Citizen me, Room room) {
        Job job = this.jobOf(me);
        int vehicle = job != null && job.vehicleEffect() > 0 && this.onShift(me) ? job.vehicleEffect()
                : this.civic.hasLicence(me, "car") ? Civic.CIVILIAN_CAR_EFFECT : 0;
        if (vehicle == 0) return this.no(habbo, "You have no vehicle. Get a driving licence at the Town Hall, then :buycar.");
        boolean driving = habbo.getRoomUnit().getEffectId() == vehicle;
        room.giveEffect(habbo, driving ? 0 : vehicle, -1);
        return true;
    }

    private boolean setJob(Habbo habbo, String[] params) {
        if (!habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) return this.no(habbo, "Staff only.");
        if (params.length < 3) return this.no(habbo, "Usage: :setjob <name> <job|none> [rank]");
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);
        if (target == null) return this.no(habbo, params[1] + " is not online.");
        Citizen them = this.citizen(target);
        if (them == null) return true;
        Job job = this.jobsByCode.get(params[2].toLowerCase());
        if (job == null && !params[2].equalsIgnoreCase("none")) return this.no(habbo, "No job called " + params[2] + ".");
        int rank = params.length > 3 ? (int) Math.max(0, amount(params, 3)) : 0;
        this.endShift(target, them, "Your job changed, so your shift has ended.");
        synchronized (them) {
            them.jobId = job == null ? 0 : job.id();
            them.jobRank = rank;
            Store.save(them);
        }
        habbo.whisper(target.getHabboInfo().getUsername() + " is now " + (job == null ? "unemployed" : job.rankName(rank) + " at " + job.name()) + ".",
                RoomChatMessageBubbles.GREEN);
        target.whisper("Your job is now " + (job == null ? "none" : job.rankName(rank) + " at " + job.name()) + ".",
                RoomChatMessageBubbles.GREEN);
        return true;
    }

    // ---- helpers -----------------------------------------------------------------

    /** Someone named in params[1], in the same room and within reach. Whispers why not otherwise. */
    Habbo near(Habbo habbo, String[] params, int reach) {
        if (params.length < 2) {
            this.no(habbo, "Who? Usage: :" + params[0] + " <name>");
            return null;
        }
        Room room = habbo.getHabboInfo().getCurrentRoom();
        Habbo target = room == null ? null : room.getHabbo(params[1]);
        if (target == null || target == habbo) {
            this.no(habbo, params[1] + " is not here.");
            return null;
        }
        int dx = Math.abs(habbo.getRoomUnit().getX() - target.getRoomUnit().getX());
        int dy = Math.abs(habbo.getRoomUnit().getY() - target.getRoomUnit().getY());
        if (Math.max(dx, dy) > reach) {
            this.no(habbo, "Get closer to " + target.getHabboInfo().getUsername() + ".");
            return null;
        }
        return target;
    }

    boolean cool(int userId, String what, int ms) {
        long now = System.currentTimeMillis();
        int key = userId * 31 + what.hashCode();
        Long last = this.cooldowns.get(key);
        if (last != null && now - last < ms) return false;
        this.cooldowns.put(key, now);
        return true;
    }

    boolean no(Habbo habbo, String why) {
        habbo.whisper(why, RoomChatMessageBubbles.ALERT);
        return true;
    }

    static long amount(String[] params, int index) {
        if (params.length <= index) return -1;
        try {
            return Long.parseLong(params[index].replace("$", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String rest(String[] params, int from) {
        return String.join(" ", Arrays.copyOfRange(params, from, params.length)).trim();
    }
}

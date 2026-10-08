package com.habnut.nutropolis;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.navigator.NavigatorSearchResultEvent;
import com.eu.habbo.plugin.events.users.UserDisconnectEvent;
import com.eu.habbo.plugin.events.users.UserLoginEvent;
import com.eu.habbo.plugin.events.users.UserEnterRoomEvent;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nutropolis, Habnut's roleplay city: jobs and wages, a bank, fights and
 * hospitals, police, arrests and jail, all in rooms marked as the city in
 * habnut_rp_rooms. The rest of the hotel is untouched.
 */
public class Nutropolis extends HabboPlugin implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Nutropolis.class);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    static final int MAX_HEALTH = 100;
    static long START_CASH = 100;

    private static final City CITY = new City();
    private static ScheduledFuture<?> clock;

    static int now() {
        return Emulator.getIntUnixTimestamp();
    }

    @Override
    public void onEnable() {
        Emulator.getPluginManager().registerEvents(this, this);
        if (Emulator.isReady) start();
    }

    @Override
    public void onDisable() {
        if (clock != null) clock.cancel(false);
    }

    @Override
    public boolean hasPermission(Habbo habbo, String key) {
        return false;
    }

    @EventHandler
    public static void onEmulatorLoaded(EmulatorLoadedEvent event) {
        start();
    }

    private static void start() {
        if (!STARTED.compareAndSet(false, true)) return;
        START_CASH = Emulator.getConfig().getInt("habnut.rp.start.cash", 100);
        Store.migrate();
        CITY.reload();
        CommandHandler.addCommand(new CityCommand());
        // Each world's navigator shows only its own rooms.
        var filters = Emulator.getGameEnvironment().getNavigatorManager().filters;
        for (var entry : new java.util.ArrayList<>(filters.entrySet())) {
            if (!(entry.getValue() instanceof WorldNavigatorFilter)) {
                filters.put(entry.getKey(), new WorldNavigatorFilter(entry.getValue(), CITY));
            }
        }
        clock = Emulator.getThreading().getService().scheduleAtFixedRate(() -> {
            try {
                CITY.tick();
            } catch (Exception e) {
                LOGGER.error("[Nutropolis] the city clock stumbled", e);
            }
        }, 20, 20, TimeUnit.SECONDS);
        LOGGER.info("[Nutropolis] the city is open");
    }

    @EventHandler
    public static void onEnterRoom(UserEnterRoomEvent event) {
        if (event.habbo == null || event.room == null) return;
        try {
            CITY.entered(event.habbo, event.room);
        } catch (Exception e) {
            LOGGER.error("[Nutropolis] could not welcome {}", event.habbo.getHabboInfo().getUsername(), e);
        }
    }

    /** Each world's navigator searches show only that world's rooms. */
    @EventHandler
    public static void onNavigatorSearch(NavigatorSearchResultEvent event) {
        if (event.habbo == null || event.rooms == null) return;
        boolean cityWorld = CITY.inCityWorld(event.habbo);
        event.rooms.removeIf(room -> room != null && CITY.inCity(room) != cityWorld);
    }

    @EventHandler
    public static void onLogin(UserLoginEvent event) {
        if (event.habbo != null) CITY.forgetWorld(event.habbo.getHabboInfo().getId());
    }

    @EventHandler
    public static void onDisconnect(UserDisconnectEvent event) {
        if (event.habbo != null) CITY.left(event.habbo);
    }

    /** Every Nutropolis verb is one command: the emulator keys commands by class. */
    static final class CityCommand extends Command {
        CityCommand() {
            super(null, City.KEYS);
        }

        @Override
        public boolean handle(GameClient client, String[] params) {
            return client != null && client.getHabbo() != null && CITY.handle(client.getHabbo(), params);
        }
    }
}

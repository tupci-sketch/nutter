package com.habnut.garden;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.PlantConfig;
import com.eu.habbo.habbohotel.items.PlantData;
import com.eu.habbo.habbohotel.items.interactions.InteractionPlant;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Habnut Community Garden. Anyone can plant a flower in a garden room,
 * water it with the watering can (the emulator's own plant mechanics: it
 * grows a stage with each watering and wilts after a day without one),
 * harvest it when it blooms, and clear wilted ones. Every harvest counts
 * towards the hotel's weekly goal; reaching it rewards every gardener.
 * The flowers change with the seasons, and harvests earn badges.
 */
public class Garden extends HabboPlugin implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Garden.class);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    static final int MAX_LIVING_PLANTS = 3;
    static final int HARVEST_DUCKETS = 30;
    static final int COMPOST_DUCKETS = 5;
    static final int GOAL_REWARD_CREDITS = 100;
    static final String GOAL_BADGE = "HNGWK";
    static final int[][] MILESTONES = {{1, 1}, {10, 2}, {50, 3}, {100, 4}, {250, 5}};

    private static volatile Set<Integer> gardenRooms = Set.of();

    @Override
    public void onEnable() {
        Emulator.getPluginManager().registerEvents(this, this);
        if (Emulator.isReady) start();
    }

    @Override
    public void onDisable() {}

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
        GardenStore.migrate();
        reload();
        CommandHandler.addCommand(new GardenCommand());
        LOGGER.info("[Garden] the Community Garden is open in rooms {}", gardenRooms);
    }

    static void reload() {
        Set<Integer> rooms = new HashSet<>();
        for (String id : Emulator.getConfig().getValue("habnut.garden.rooms", "").split("[,;\\s]+")) {
            try {
                if (!id.isBlank()) rooms.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException ignored) {
                // a typo in the setting must not close the garden
            }
        }
        gardenRooms = rooms;
    }

    /** This season's flower: plumeria in spring, primrose in summer, dahlia in autumn, starflower in winter. */
    static String seasonalFlower() {
        int month = LocalDate.now().getMonthValue();
        char species = month >= 3 && month <= 5 ? 'a' : month >= 6 && month <= 8 ? 'b' : month >= 9 && month <= 11 ? 'c' : 'd';
        int colour = ThreadLocalRandom.current().nextInt(1, 4);
        return "jungle_c16_flower" + species + colour;
    }

    static String season() {
        int month = LocalDate.now().getMonthValue();
        return month >= 3 && month <= 5 ? "spring plumerias" : month >= 6 && month <= 8 ? "summer primroses"
                : month >= 9 && month <= 11 ? "autumn dahlias" : "winter starflowers";
    }

    static String week() {
        LocalDate now = LocalDate.now();
        return now.get(IsoFields.WEEK_BASED_YEAR) + "-W" + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    static final class GardenCommand extends Command {
        GardenCommand() {
            super(null, new String[] {"plant", "harvest", "compost", "garden", "gardengoal", "gardenreload"});
        }

        @Override
        public boolean handle(GameClient client, String[] params) {
            if (client == null || client.getHabbo() == null) return false;
            Habbo habbo = client.getHabbo();
            String verb = params[0].toLowerCase();
            try {
                switch (verb) {
                    case "garden" -> status(habbo);
                    case "gardengoal" -> setGoal(habbo, params);
                    case "gardenreload" -> {
                        if (habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) {
                            reload();
                            habbo.whisper("Garden rooms: " + gardenRooms, RoomChatMessageBubbles.GREEN);
                        }
                    }
                    default -> {
                        Room room = habbo.getHabboInfo().getCurrentRoom();
                        if (room == null || !gardenRooms.contains(room.getId())) {
                            habbo.whisper("You can only do that in the Community Garden.", RoomChatMessageBubbles.ALERT);
                            return true;
                        }
                        switch (verb) {
                            case "plant" -> plant(habbo, room);
                            case "harvest" -> harvest(habbo, room);
                            case "compost" -> compost(habbo, room);
                            default -> {}
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[Garden] {} failed", verb, e);
                habbo.whisper("The garden shed is jammed. Try again in a moment.", RoomChatMessageBubbles.ALERT);
            }
            return true;
        }
    }

    // ---- planting -------------------------------------------------------------

    private static void plant(Habbo habbo, Room room) {
        int userId = habbo.getHabboInfo().getId();
        if (GardenStore.livingPlants(userId) >= MAX_LIVING_PLANTS) {
            habbo.whisper("You already have " + MAX_LIVING_PLANTS + " flowers growing. Harvest one first.", RoomChatMessageBubbles.ALERT);
            return;
        }
        RoomTile here = habbo.getRoomUnit().getCurrentLocation();
        RoomTile spot = here == null ? null
                : room.getLayout().getTileInFront(here, habbo.getRoomUnit().getBodyRotation().getValue());
        if (spot == null || !room.getItemsAt(spot).isEmpty() || room.hasHabbosAt(spot.x, spot.y)) {
            habbo.whisper("Face a free patch of ground to plant there.", RoomChatMessageBubbles.ALERT);
            return;
        }
        String name = seasonalFlower();
        Item base = Emulator.getGameEnvironment().getItemManager().getItem(name);
        if (base == null) {
            habbo.whisper("The seed packet is empty.", RoomChatMessageBubbles.ALERT);
            return;
        }
        HabboItem seed = Emulator.getGameEnvironment().getItemManager().createItem(userId, base, 0, 0, "0");
        if (seed == null) return;
        FurnitureMovementError error = room.placeFloorFurniAt(seed, spot, 0, habbo);
        if (error != FurnitureMovementError.NONE) {
            Emulator.getThreading().run(new QueryDeleteHabboItem(seed.getId()));
            habbo.whisper("Nothing will grow there.", RoomChatMessageBubbles.ALERT);
            return;
        }
        GardenStore.planted(seed.getId(), userId, room.getId(), name);
        habbo.talk("*plants some " + season() + "*", RoomChatMessageBubbles.GREEN);
        habbo.whisper("Planted! Fill a watering can at the water and click your flower to water it. It grows with every watering and wilts after a day without one.",
                RoomChatMessageBubbles.GREEN);
    }

    /** The plant in front of the player, if there is one. */
    private static InteractionPlant plantInFront(Habbo habbo, Room room) {
        RoomTile here = habbo.getRoomUnit().getCurrentLocation();
        if (here == null) return null;
        List<RoomTile> look = new ArrayList<>();
        look.add(room.getLayout().getTileInFront(here, habbo.getRoomUnit().getBodyRotation().getValue()));
        look.add(here);
        for (RoomTile tile : look) {
            if (tile == null) continue;
            for (HabboItem item : room.getItemsAt(tile)) {
                if (item instanceof InteractionPlant plant) return plant;
            }
        }
        return null;
    }

    private static void harvest(Habbo habbo, Room room) {
        InteractionPlant plant = plantInFront(habbo, room);
        if (plant == null) {
            habbo.whisper("Face the flower you want to harvest.", RoomChatMessageBubbles.ALERT);
            return;
        }
        int userId = habbo.getHabboInfo().getId();
        if (plant.getUserId() != userId) {
            habbo.whisper("That is someone else's flower.", RoomChatMessageBubbles.ALERT);
            return;
        }
        if (plant.isDead()) {
            habbo.whisper("That one has wilted. Type :compost to clear it.", RoomChatMessageBubbles.ALERT);
            return;
        }
        PlantData data = Emulator.getGameEnvironment().getItemManager().getPlantData(plant.getId());
        PlantConfig config = Emulator.getGameEnvironment().getItemManager().getPlantConfig(plant.getBaseItem().getName());
        int grown = data == null ? 0 : data.getCountState();
        int full = config == null ? Math.max(1, plant.getBaseItem().getStateCount() - 2) : config.getGrowCounts();
        if (grown < full) {
            habbo.whisper("Not ready yet: " + grown + " of " + full + " waterings.", RoomChatMessageBubbles.ALERT);
            return;
        }
        remove(room, plant);
        habbo.givePoints(0, HARVEST_DUCKETS, "garden harvest");
        GardenStore.herbsForNutropolis(userId);
        int total = GardenStore.harvested(userId);
        int[] goal = GardenStore.contribute(week(), userId);
        habbo.talk("*harvests a beautiful flower*", RoomChatMessageBubbles.GREEN);
        habbo.whisper("+" + HARVEST_DUCKETS + " duckets and some herbs for Nutropolis. That is " + total + " harvests. This week the garden has " + goal[0] + " of " + goal[1] + ".",
                RoomChatMessageBubbles.GREEN);
        for (int[] milestone : MILESTONES) {
            if (total == milestone[0]) award(habbo, "HNGD" + milestone[1]);
        }
        if (goal[0] == goal[1]) goalReached(goal[1]);
    }

    private static void compost(Habbo habbo, Room room) {
        InteractionPlant plant = plantInFront(habbo, room);
        if (plant == null || !plant.isDead()) {
            habbo.whisper("Face a wilted flower to compost it.", RoomChatMessageBubbles.ALERT);
            return;
        }
        remove(room, plant);
        habbo.givePoints(0, COMPOST_DUCKETS, "garden compost");
        habbo.talk("*clears a wilted flower onto the compost*", RoomChatMessageBubbles.NORMAL);
        habbo.whisper("+" + COMPOST_DUCKETS + " duckets for keeping the garden tidy.", RoomChatMessageBubbles.GREEN);
    }

    private static void remove(Room room, HabboItem item) {
        room.removeHabboItem(item);
        room.sendComposer(new RemoveFloorItemComposer(item, true).compose());
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        if (tile != null) room.updateTile(tile);
        Emulator.getGameEnvironment().getItemManager().removePlantData(item.getId());
        Emulator.getThreading().run(new QueryDeleteHabboItem(item.getId()));
        GardenStore.removed(item.getId());
    }

    // ---- rewards ----------------------------------------------------------------

    private static void award(Habbo habbo, String code) {
        if (habbo.getInventory().getBadgesComponent().hasBadge(code)) return;
        HabboBadge badge = BadgesComponent.createBadge(code, habbo);
        if (habbo.getClient() != null) habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
        habbo.whisper("New badge! Check your badges.", RoomChatMessageBubbles.GREEN);
    }

    private static void goalReached(int target) {
        String week = week();
        if (!GardenStore.markGoalRewarded(week)) return;
        List<Integer> gardeners = GardenStore.contributors(week);
        String message = "The Community Garden hit this week's goal of " + target + " harvests! "
                + gardeners.size() + " gardeners each get " + GOAL_REWARD_CREDITS + " credits and the Garden Hero badge.";
        for (int userId : gardeners) {
            Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (online != null) {
                online.giveCredits(GOAL_REWARD_CREDITS, "garden goal");
                award(online, GOAL_BADGE);
            } else {
                GardenStore.rewardOffline(userId, GOAL_REWARD_CREDITS, GOAL_BADGE);
            }
        }
        for (Habbo h : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (h.getClient() != null) h.getClient().sendResponse(new BubbleAlertComposer("admin.transient", message));
        }
        LOGGER.info("[Garden] weekly goal {} reached by {} gardeners", target, gardeners.size());
    }

    // ---- information ---------------------------------------------------------------

    private static void status(Habbo habbo) {
        int userId = habbo.getHabboInfo().getId();
        int[] goal = GardenStore.goal(week());
        int[] mine = GardenStore.stats(userId);
        habbo.whisper("Garden: this season's flowers are " + season() + ". You have " + mine[1] + " growing and "
                + mine[0] + " harvests. This week the hotel has harvested " + goal[0] + " of " + goal[1] + ".",
                RoomChatMessageBubbles.GREEN);
    }

    private static void setGoal(Habbo habbo, String[] params) {
        if (!habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) {
            habbo.whisper("Staff only.", RoomChatMessageBubbles.ALERT);
            return;
        }
        try {
            int target = Integer.parseInt(params[1]);
            GardenStore.setTarget(week(), Math.max(1, target));
            habbo.whisper("This week's garden goal is now " + target + " harvests.", RoomChatMessageBubbles.GREEN);
        } catch (RuntimeException e) {
            habbo.whisper("Usage: :gardengoal <harvests>", RoomChatMessageBubbles.ALERT);
        }
    }
}

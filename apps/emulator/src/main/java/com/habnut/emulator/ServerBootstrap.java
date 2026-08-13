package com.habnut.emulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.habnut.emulator.auth.*;
import com.habnut.emulator.config.ServerConfig;
import com.habnut.emulator.db.DatabaseManager;
import com.habnut.emulator.db.FlywayRunner;
import com.habnut.emulator.marketplace.*;
import com.habnut.emulator.metrics.MetricsRegistry;
import com.habnut.emulator.net.*;
import com.habnut.emulator.redis.RedisManager;
import com.habnut.emulator.economy.*;
import com.habnut.emulator.furni.*;
import com.habnut.emulator.room.*;
import com.habnut.emulator.progression.*;
import com.habnut.emulator.social.*;
import com.habnut.emulator.trade.*;
import com.habnut.emulator.wired.*;
import com.habnut.emulator.game.*;
import com.habnut.emulator.pet.*;
import com.habnut.emulator.bot.*;
import com.habnut.emulator.camera.*;
import com.habnut.emulator.sound.*;
import com.habnut.emulator.moderation.*;
import com.habnut.emulator.events.*;
import com.habnut.emulator.garden.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ServerBootstrap.class);

    private final ServerConfig config;
    private DatabaseManager db;
    private RedisManager redis;
    private MetricsRegistry metrics;
    private SessionRegistry sessions;
    private MetricsHttpServer metricsServer;
    private HealthServer healthServer;
    private NettyServer nettyServer;
    private ScheduledExecutorService metricsPoller;
    private RoomManager roomManager;
    private RoomHandler roomHandler;
    private GameEngine  gameEngine;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public ServerBootstrap(ServerConfig config) {
        this.config = config;
    }

    public void start() throws Exception {
        log.info("=== Habnut Emulator starting (worldId={}, devMode={}) ===",
            config.worldId, config.devMode);

        // 1. Database
        db = new DatabaseManager(config);
        FlywayRunner.migrate(db.getDataSource());

        // 2. Redis
        redis = new RedisManager(config);

        // 3. Metrics registry
        metrics = new MetricsRegistry();

        // 4. Session registry
        sessions = new SessionRegistry();

        // 5. Metrics HTTP server
        metricsServer = new MetricsHttpServer(config, metrics);
        metricsServer.start();

        // 6. Health HTTP server
        healthServer = new HealthServer(config, db, redis, sessions);
        healthServer.start();

        // 7. Packet router and WebSocket server
        ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        RateLimiter networkLimiter = new RateLimiter();
        PacketRouter router = buildRouter(mapper, networkLimiter);

        WebSocketHandler handler = new WebSocketHandler(sessions, router, networkLimiter, metrics,
            uid -> { if (roomHandler != null) roomHandler.onSessionDisconnect(uid); });

        nettyServer = new NettyServer(config, () -> handler);
        nettyServer.start();

        // 8. Poll DB pool metrics into gauges every 5 s
        metricsPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-poller");
            t.setDaemon(true);
            return t;
        });
        metricsPoller.scheduleAtFixedRate(this::pollDbMetrics, 5, 5, TimeUnit.SECONDS);

        running.set(true);
        log.info("=== Habnut Emulator ready ===");
    }

    private PacketRouter buildRouter(ObjectMapper mapper, RateLimiter networkLimiter) {
        PacketRouter router = new PacketRouter(mapper);

        // System ping for basic connectivity checks
        router.register("system.ping", (session, payload) ->
            session.send(router.buildPacket("system.pong",
                java.util.Map.of("ts", System.currentTimeMillis()))));

        // Auth domain (Phase 4)
        SessionTicketService ticketService = new SessionTicketService(redis, config);
        UserRepository userRepo = new UserRepository(db);
        BanService banService = new BanService(db);
        MachineIdService machineIdService = new MachineIdService(db);

        new AuthHandler(ticketService, userRepo, banService, machineIdService,
            sessions, router, metrics, redis).register(router);

        // Room domain (Phase 5/6)
        RoomRepository roomRepo     = new RoomRepository(db);
        RoomModelRepository modelRepo = new RoomModelRepository(db);
        modelRepo.preloadAll();
        roomManager = new RoomManager(roomRepo, modelRepo, router, sessions, metrics);
        roomHandler = new RoomHandler(roomManager, roomRepo, modelRepo, userRepo,
            router, metrics, networkLimiter);
        roomHandler.register(router);

        // Economy domain (Phase 8)
        TransactionService txService  = new TransactionService(db);
        InventoryService   invService = new InventoryService(db);
        CatalogueService   catService = new CatalogueService(db, txService, invService);
        new EconomyHandler(txService, invService, catService, router, networkLimiter, metrics)
            .register(router);

        // Furniture domain (Phase 7)
        FurniBaseRepository furniBaseRepo = new FurniBaseRepository(db);
        furniBaseRepo.preloadAll();
        FurniHandler furniHandler = new FurniHandler(roomManager, furniBaseRepo, db, router, networkLimiter);
        furniHandler.register(router);

        // Trade domain (Phase 9)
        TradeService tradeService = new TradeService(db);
        new TradeHandler(tradeService, sessions, router).register(router);

        // Marketplace domain (Phase 9)
        MarketplaceService marketplaceService = new MarketplaceService(db, txService);
        new MarketplaceHandler(marketplaceService, router, networkLimiter).register(router);

        // Social domain (Phase 10)
        FriendService  friendService  = new FriendService(db);
        MessageService messageService = new MessageService(db);
        GroupService   groupService   = new GroupService(db);
        new SocialHandler(friendService, messageService, groupService, sessions, router)
            .register(router);

        // Progression domain (Phase 11)
        BadgeService       badgeService   = new BadgeService(db);
        AchievementService achService     = new AchievementService(db, badgeService);
        QuestService       questService   = new QuestService(db);
        ProfileService     profileService = new ProfileService(db);
        new ProgressionHandler(achService, questService, badgeService, profileService, router)
            .register(router);

        // Wired 2.0 domain (Phase 12)
        WiredEngine wiredEngine = new WiredEngine(db, sessions, router);
        new WiredHandler(wiredEngine, roomManager, router).register(router);

        // Game engine domain (Phase 13)
        gameEngine = new GameEngine(db, sessions, router);
        gameEngine.start();
        TournamentService tournamentService = new TournamentService(db);
        MatchmakingQueue matchmakingQueue   = new MatchmakingQueue(gameEngine, sessions, router);
        new GameHandler(gameEngine, tournamentService, matchmakingQueue, sessions, router)
            .register(router);

        // Pets, Bots, Camera, Sound (Phase 14)
        PetService  petService  = new PetService(db);
        PetAI       petAI       = new PetAI(sessions, router);
        new PetHandler(petService, petAI, roomManager, sessions, router).register(router);

        BotService  botService  = new BotService(db);
        new BotHandler(botService, roomManager, sessions, router).register(router);

        CameraService cameraService  = new CameraService(db);
        new CameraHandler(cameraService, txService, router).register(router);

        SoundService  soundService = new SoundService(db);
        new SoundHandler(soundService, roomManager, sessions, router).register(router);

        // Moderation and Staff (Phase 15)
        AuditService        auditService  = new AuditService(db);
        ChatLogService      chatLogService = new ChatLogService(db);
        ModerationService   modService    = new ModerationService(db, userRepo);
        WordFilter          wordFilter    = new WordFilter(db);
        StaffCommandDispatcher commandDisp = new StaffCommandDispatcher(
            modService, userRepo, roomManager, sessions, auditService, router);
        new ModerationHandler(modService, chatLogService, userRepo, roomManager,
            sessions, auditService, router).register(router);
        new StaffHandler(commandDisp, modService, userRepo, roomManager,
            sessions, auditService, router).register(router);

        // Events, Competitions, Seasons (Phase 16)
        EventService       eventService   = new EventService(db);
        CompetitionService compService    = new CompetitionService(db);
        SeasonService      seasonService  = new SeasonService(db);
        new EventHandler(eventService, compService, seasonService, userRepo,
            sessions, router).register(router);

        // Community Garden (Phase 17)
        GardenService gardenService = new GardenService(db);
        new GardenHandler(gardenService, sessions, router).register(router);

        return router;
    }

    private void pollDbMetrics() {
        try {
            metrics.setDbPoolActive(db.getActiveConnections());
            metrics.setDbPoolIdle(db.getIdleConnections());
        } catch (Exception e) {
            log.warn("Failed to poll DB pool metrics", e);
        }
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("Graceful shutdown initiated");

        if (metricsPoller != null) metricsPoller.shutdownNow();
        if (nettyServer != null) nettyServer.close();
        if (healthServer != null) healthServer.close();
        if (metricsServer != null) metricsServer.close();

        // Disconnect all sessions
        if (sessions != null) {
            sessions.all().forEach(WebSocketSession::close);
        }

        if (gameEngine  != null) gameEngine.stop();
        if (roomManager != null) roomManager.close();
        if (redis != null) redis.close();
        if (db != null) db.close();

        log.info("Habnut Emulator stopped");
        shutdownLatch.countDown();
    }
}

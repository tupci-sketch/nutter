package com.habnut.emulator.rp;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class RpHandler {

    private static final Logger log = LoggerFactory.getLogger(RpHandler.class);

    private final RpCharacterService  charService;
    private final RpFactionService    factionService;
    private final RpJobService        jobService;
    private final RpBankService       bankService;
    private final RpCrimeService      crimeService;
    private final RpCourtService      courtService;
    private final RpDispatchService   dispatchService;
    private final RpMedicalService    medicalService;
    private final RpPropertyService   propertyService;
    private final RpGovernmentService govtService;
    private final RpSceneService      sceneService;
    private final RpCraftingService   craftingService;
    private final RpCombatService     combatService;
    private final RpTurfService       turfService;
    private final SessionRegistry     sessions;
    private final PacketRouter        router;

    public RpHandler(RpCharacterService charService, RpFactionService factionService,
                     RpJobService jobService, RpBankService bankService,
                     RpCrimeService crimeService, RpCourtService courtService,
                     RpDispatchService dispatchService, RpMedicalService medicalService,
                     RpPropertyService propertyService, RpGovernmentService govtService,
                     RpSceneService sceneService, RpCraftingService craftingService,
                     RpCombatService combatService, RpTurfService turfService,
                     SessionRegistry sessions, PacketRouter router) {
        this.charService     = charService;
        this.factionService  = factionService;
        this.jobService      = jobService;
        this.bankService     = bankService;
        this.crimeService    = crimeService;
        this.courtService    = courtService;
        this.dispatchService = dispatchService;
        this.medicalService  = medicalService;
        this.propertyService = propertyService;
        this.govtService     = govtService;
        this.sceneService    = sceneService;
        this.craftingService = craftingService;
        this.combatService   = combatService;
        this.turfService     = turfService;
        this.sessions        = sessions;
        this.router          = router;
    }

    public void register(PacketRouter router) {
        // Characters
        router.register(PacketType.RP_CHARACTER_CREATE,   this::handleCharCreate);
        router.register(PacketType.RP_CHARACTER_INFO,     this::handleCharInfo);
        router.register(PacketType.RP_CHARACTER_UPDATE,   this::handleCharUpdate);
        // Combat
        router.register(PacketType.RP_COMBAT_WEAPONS,     this::handleWeaponList);
        router.register(PacketType.RP_COMBAT_EQUIP,       this::handleWeaponEquip);
        router.register(PacketType.RP_COMBAT_ATTACK,      this::handleAttack);
        router.register(PacketType.RP_COMBAT_REVIVE,      this::handleRevive);
        // Territory
        router.register(PacketType.RP_TURF_LIST,          this::handleTurfList);
        router.register(PacketType.RP_TURF_CAPTURE_BEGIN, this::handleTurfCaptureBegin);
        router.register(PacketType.RP_TURF_CAPTURE_ABANDON, this::handleTurfCaptureAbandon);
        // Factions
        router.register(PacketType.RP_FACTION_LIST,       this::handleFactionList);
        router.register(PacketType.RP_FACTION_JOIN,       this::handleFactionJoin);
        router.register(PacketType.RP_FACTION_LEAVE,      this::handleFactionLeave);
        // Jobs
        router.register(PacketType.RP_JOB_LIST,           this::handleJobList);
        router.register(PacketType.RP_JOB_APPLY,          this::handleJobApply);
        router.register(PacketType.RP_JOB_CLOCKIN,        this::handleJobClockIn);
        router.register(PacketType.RP_JOB_CLOCKOUT,       this::handleJobClockOut);
        // Bank
        router.register(PacketType.RP_BANK_BALANCE,       this::handleBankBalance);
        router.register(PacketType.RP_BANK_DEPOSIT,       this::handleBankDeposit);
        router.register(PacketType.RP_BANK_WITHDRAW,      this::handleBankWithdraw);
        router.register(PacketType.RP_BANK_TRANSFER,      this::handleBankTransfer);
        // Crimes / Arrests
        router.register(PacketType.RP_CRIME_LIST,         this::handleCrimeList);
        router.register(PacketType.RP_ARREST,             this::handleArrest);
        // Court / Prison
        router.register(PacketType.RP_COURT_CASE_LIST,    this::handleCourtCaseList);
        router.register(PacketType.RP_COURT_VERDICT,      this::handleCourtVerdict);
        router.register(PacketType.RP_PRISON_STATUS,      this::handlePrisonStatus);
        router.register(PacketType.RP_RELEASED,           this::handleRelease);
        // Dispatch
        router.register(PacketType.RP_DISPATCH_CREATE,    this::handleDispatchCreate);
        router.register(PacketType.RP_DISPATCH_LIST,      this::handleDispatchList);
        router.register(PacketType.RP_DISPATCH_ACCEPT,    this::handleDispatchAccept);
        router.register(PacketType.RP_DISPATCH_RESOLVE,   this::handleDispatchResolve);
        // Medical
        router.register(PacketType.RP_MEDICAL_RECORDS,    this::handleMedicalRecords);
        router.register(PacketType.RP_MEDICAL_ADMIT,      this::handleMedicalAdmit);
        router.register(PacketType.RP_MEDICAL_TREAT,      this::handleMedicalTreat);
        router.register(PacketType.RP_MEDICAL_DISCHARGE,  this::handleMedicalDischarge);
        // Properties
        router.register(PacketType.RP_PROPERTY_LIST,      this::handlePropertyList);
        router.register(PacketType.RP_PROPERTY_BUY,       this::handlePropertyBuy);
        // Government / Elections
        router.register(PacketType.RP_GOVT_OFFICES,       this::handleGovtOffices);
        router.register(PacketType.RP_ELECTION_LIST,      this::handleElectionList);
        router.register(PacketType.RP_ELECTION_NOMINATE,  this::handleElectionNominate);
        router.register(PacketType.RP_ELECTION_VOTE,      this::handleElectionVote);
        router.register(PacketType.RP_GOVT_DECREE,        this::handleGovtDecree);
        router.register(PacketType.RP_LAW_LIST,           this::handleLawList);
        // Scenes
        router.register(PacketType.RP_SCENE_START,        this::handleSceneStart);
        router.register(PacketType.RP_SCENE_END,          this::handleSceneEnd);
        // Crafting
        router.register(PacketType.RP_RECIPE_LIST,        this::handleRecipeList);
        router.register(PacketType.RP_CRAFT,              this::handleCraft);
    }

    // ─── Characters ───────────────────────────────────────────────────────────

    private void handleCharCreate(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        String name    = p.path("name").asText("").trim();
        String surname = p.path("surname").asText("").trim();
        int    age     = p.path("age").asInt(25);
        String bio     = p.path("biography").asText("");

        if (name.isBlank() || surname.isBlank()) { sendError(session, "invalid_payload"); return; }
        if (age < 18 || age > 80) { sendError(session, "invalid_age"); return; }
        try {
            if (charService.findByUser(userId).isPresent()) {
                sendError(session, "character_exists"); return;
            }
            RpCharacterService.Character ch = charService.create(userId, name, surname, age, bio);
            session.send(router.buildPacket(PacketType.RP_CHARACTER_CREATED, charToMap(ch)));
        } catch (SQLException e) {
            log.error("RP char create error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCharInfo(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            session.send(router.buildPacket(PacketType.RP_CHARACTER_INFO_RESULT, charToMap(ch)));
        } catch (SQLException e) {
            log.error("RP char info error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCharUpdate(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        String bio = p.path("biography").asText(null);
        if (bio == null) { sendError(session, "invalid_payload"); return; }
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            charService.updateBiography(ch.id(), userId, bio);
            session.send(router.buildPacket(PacketType.RP_CHARACTER_UPDATED, Map.of("ok", true)));
        } catch (SQLException e) {
            log.error("RP char update error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Factions ─────────────────────────────────────────────────────────────

    private void handleFactionList(WebSocketSession session, JsonNode p) {
        try {
            List<RpFactionService.Faction> factions = factionService.listAll();
            session.send(router.buildPacket(PacketType.RP_FACTION_LIST_RESULT,
                Map.of("factions", factions.stream().map(this::factionToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP faction list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleFactionJoin(WebSocketSession session, JsonNode p) {
        long userId    = session.getUserId();
        long factionId = p.path("factionId").asLong(-1);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            boolean ok = factionService.join(factionId, ch.id());
            if (!ok) { sendError(session, "join_failed"); return; }
            try (var conn = charService.getConnection()) {
                charService.setFaction(conn, ch.id(), factionId);
            }
            session.send(router.buildPacket(PacketType.RP_FACTION_JOINED,
                Map.of("factionId", factionId)));
        } catch (SQLException e) {
            log.error("RP faction join error", e);
            sendError(session, "server_error");
        }
    }

    private void handleFactionLeave(WebSocketSession session, JsonNode p) {
        long userId    = session.getUserId();
        long factionId = p.path("factionId").asLong(-1);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            factionService.leave(factionId, ch.id());
            try (var conn = charService.getConnection()) {
                charService.setFaction(conn, ch.id(), null);
            }
            session.send(router.buildPacket(PacketType.RP_FACTION_LEFT,
                Map.of("factionId", factionId)));
        } catch (SQLException e) {
            log.error("RP faction leave error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Jobs ─────────────────────────────────────────────────────────────────

    private void handleJobList(WebSocketSession session, JsonNode p) {
        try {
            List<RpJobService.Job> jobs = jobService.listEnabled();
            session.send(router.buildPacket(PacketType.RP_JOB_LIST_RESULT,
                Map.of("jobs", jobs.stream().map(this::jobToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP job list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleJobApply(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        long jobId  = p.path("jobId").asLong(-1);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            jobService.applyForJob(ch.id(), jobId);
            session.send(router.buildPacket(PacketType.RP_JOB_APPLIED, Map.of("jobId", jobId)));
        } catch (SQLException e) {
            log.error("RP job apply error", e);
            sendError(session, "server_error");
        }
    }

    private void handleJobClockIn(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null || ch.jobId() == null) { sendError(session, "no_job"); return; }
            if (jobService.getActiveShift(ch.id()).isPresent()) {
                sendError(session, "already_clocked_in"); return;
            }
            RpJobService.Shift shift = jobService.clockIn(ch.id(), ch.jobId());
            session.send(router.buildPacket(PacketType.RP_JOB_SHIFT_RESULT,
                Map.of("action", "clock_in", "shiftId", shift.id(), "jobId", shift.jobId())));
        } catch (SQLException e) {
            log.error("RP clock in error", e);
            sendError(session, "server_error");
        }
    }

    private void handleJobClockOut(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            Optional<RpJobService.Shift> shift = jobService.clockOut(ch.id());
            if (shift.isEmpty()) { sendError(session, "not_clocked_in"); return; }
            session.send(router.buildPacket(PacketType.RP_JOB_SHIFT_RESULT,
                Map.of("action", "clock_out", "pay", shift.get().payAmount())));
        } catch (SQLException e) {
            log.error("RP clock out error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Bank ─────────────────────────────────────────────────────────────────

    private void handleBankBalance(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            session.send(router.buildPacket(PacketType.RP_BANK_BALANCE_RESULT,
                Map.of("cashBalance", ch.cashBalance(), "bankBalance", ch.bankBalance())));
        } catch (SQLException e) {
            log.error("RP bank balance error", e);
            sendError(session, "server_error");
        }
    }

    private void handleBankDeposit(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        int amount  = p.path("amount").asInt(0);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            RpBankService.TxResult result = bankService.deposit(ch.id(), amount);
            if (!result.ok()) { sendError(session, result.reason()); return; }
            session.send(router.buildPacket(PacketType.RP_BANK_TRANSACTION,
                Map.of("type", "deposit", "amount", amount, "newBankBalance", result.newBalance())));
        } catch (SQLException e) {
            log.error("RP bank deposit error", e);
            sendError(session, "server_error");
        }
    }

    private void handleBankWithdraw(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        int amount  = p.path("amount").asInt(0);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            RpBankService.TxResult result = bankService.withdraw(ch.id(), amount);
            if (!result.ok()) { sendError(session, result.reason()); return; }
            session.send(router.buildPacket(PacketType.RP_BANK_TRANSACTION,
                Map.of("type", "withdraw", "amount", amount, "newBankBalance", result.newBalance())));
        } catch (SQLException e) {
            log.error("RP bank withdraw error", e);
            sendError(session, "server_error");
        }
    }

    private void handleBankTransfer(WebSocketSession session, JsonNode p) {
        long userId       = session.getUserId();
        long toCharId     = p.path("toCharacterId").asLong(-1);
        int  amount       = p.path("amount").asInt(0);
        String desc       = p.path("description").asText("");
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            RpBankService.TxResult result = bankService.transfer(ch.id(), toCharId, amount, desc);
            if (!result.ok()) { sendError(session, result.reason()); return; }
            session.send(router.buildPacket(PacketType.RP_BANK_TRANSACTION,
                Map.of("type", "transfer", "amount", amount,
                       "toCharacterId", toCharId, "newBankBalance", result.newBalance())));
        } catch (SQLException e) {
            log.error("RP bank transfer error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Crimes ───────────────────────────────────────────────────────────────

    private void handleCrimeList(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            List<RpCrimeService.Crime> crimes = crimeService.getCriminalRecord(ch.id());
            session.send(router.buildPacket(PacketType.RP_CRIME_LIST_RESULT,
                Map.of("crimes", crimes.stream().map(this::crimeToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP crime list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleArrest(WebSocketSession session, JsonNode p) {
        long officerUserId  = session.getUserId();
        long suspectCharId  = p.path("suspectCharacterId").asLong(-1);
        String crimeDesc    = p.path("crimeDescription").asText("");
        String crimeType    = p.path("crimeType").asText("unspecified");
        int sentenceHours   = p.path("sentenceHours").asInt(1);

        if (crimeDesc.isBlank()) { sendError(session, "invalid_payload"); return; }
        try {
            RpCharacterService.Character officer = charService.findByUser(officerUserId).orElse(null);
            if (officer == null) { sendError(session, "no_character"); return; }
            RpCharacterService.Character suspect = charService.findById(suspectCharId).orElse(null);
            if (suspect == null) { sendError(session, "suspect_not_found"); return; }

            long crimeId = crimeService.recordCrime(suspectCharId, crimeType, crimeDesc,
                officer.id());
            crimeService.arrest(suspectCharId, officer.id(), crimeDesc);

            try (var conn = charService.getConnection()) {
                courtService.imprison(conn, suspectCharId, crimeId, sentenceHours);
            }

            session.send(router.buildPacket(PacketType.RP_ARRESTED,
                Map.of("suspectCharacterId", suspectCharId, "crimeId", crimeId,
                       "sentenceHours", sentenceHours)));

            sessions.byUserId(suspect.userId()).ifPresent(s ->
                s.send(router.buildPacket(PacketType.RP_SENTENCED,
                    Map.of("crimeType", crimeType, "sentenceHours", sentenceHours))));
        } catch (SQLException e) {
            log.error("RP arrest error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Court / Prison ───────────────────────────────────────────────────────

    private void handleCourtCaseList(WebSocketSession session, JsonNode p) {
        try {
            List<RpCourtService.CourtCase> cases = courtService.listPending();
            session.send(router.buildPacket(PacketType.RP_COURT_CASE_LIST_RESULT,
                Map.of("cases", cases.stream().map(this::caseToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP court list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCourtVerdict(WebSocketSession session, JsonNode p) {
        long userId       = session.getUserId();
        long caseId       = p.path("caseId").asLong(-1);
        String verdict    = p.path("verdict").asText("");
        String sentType   = p.path("sentenceType").asText(null);
        Integer sentHours = p.has("sentenceHours") ? p.path("sentenceHours").asInt() : null;
        Integer fine      = p.has("fineAmount") ? p.path("fineAmount").asInt() : null;
        try {
            RpCharacterService.Character judge = charService.findByUser(userId).orElse(null);
            if (judge == null) { sendError(session, "no_character"); return; }
            boolean ok = courtService.renderVerdict(caseId, judge.id(), verdict,
                sentType, sentHours, fine);
            if (!ok) { sendError(session, "verdict_failed"); return; }
            session.send(router.buildPacket(PacketType.RP_SENTENCED,
                Map.of("caseId", caseId, "verdict", verdict)));
        } catch (SQLException e) {
            log.error("RP court verdict error", e);
            sendError(session, "server_error");
        }
    }

    private void handlePrisonStatus(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            Optional<RpCourtService.PrisonRecord> rec = courtService.getActivePrison(ch.id());
            if (rec.isEmpty()) {
                session.send(router.buildPacket(PacketType.RP_PRISON_STATUS_RESULT,
                    Map.of("imprisoned", false)));
            } else {
                RpCourtService.PrisonRecord pr = rec.get();
                session.send(router.buildPacket(PacketType.RP_PRISON_STATUS_RESULT,
                    Map.of("imprisoned", true, "sentenceEnd", pr.sentenceEnd())));
            }
        } catch (SQLException e) {
            log.error("RP prison status error", e);
            sendError(session, "server_error");
        }
    }

    private void handleRelease(WebSocketSession session, JsonNode p) {
        long userId   = session.getUserId();
        long targetCh = p.path("characterId").asLong(-1);
        String reason = p.path("reason").asText("released");
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            boolean ok = courtService.release(targetCh, reason);
            if (!ok) { sendError(session, "not_imprisoned"); return; }
            session.send(router.buildPacket(PacketType.RP_RELEASED,
                Map.of("characterId", targetCh)));
        } catch (SQLException e) {
            log.error("RP release error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Dispatch ─────────────────────────────────────────────────────────────

    private void handleDispatchCreate(WebSocketSession session, JsonNode p) {
        long userId   = session.getUserId();
        String type   = p.path("callType").asText("");
        String loc    = p.path("location").asText("");
        String desc   = p.path("description").asText("");
        int priority  = p.path("priority").asInt(2);
        if (type.isBlank() || loc.isBlank()) { sendError(session, "invalid_payload"); return; }
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            long callId = dispatchService.create(ch.id(), type, loc, desc, priority);

            String push = router.buildPacket(PacketType.RP_DISPATCH_CALL_RECEIVED,
                Map.of("callId", callId, "callType", type, "location", loc,
                       "description", desc, "priority", priority));
            sessions.all().forEach(s -> s.send(push));

            session.send(router.buildPacket(PacketType.RP_DISPATCH_CALL_RECEIVED,
                Map.of("callId", callId)));
        } catch (SQLException e) {
            log.error("RP dispatch create error", e);
            sendError(session, "server_error");
        }
    }

    private void handleDispatchList(WebSocketSession session, JsonNode p) {
        String type = p.has("callType") ? p.path("callType").asText() : null;
        try {
            List<RpDispatchService.DispatchCall> calls = dispatchService.listPending(type);
            session.send(router.buildPacket(PacketType.RP_DISPATCH_LIST_RESULT,
                Map.of("calls", calls.stream().map(this::callToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP dispatch list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleDispatchAccept(WebSocketSession session, JsonNode p) {
        long userId  = session.getUserId();
        long callId  = p.path("callId").asLong(-1);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            boolean ok = dispatchService.accept(callId, ch.id());
            if (!ok) sendError(session, "accept_failed");
        } catch (SQLException e) {
            log.error("RP dispatch accept error", e);
            sendError(session, "server_error");
        }
    }

    private void handleDispatchResolve(WebSocketSession session, JsonNode p) {
        long callId = p.path("callId").asLong(-1);
        try {
            boolean ok = dispatchService.resolve(callId);
            if (!ok) sendError(session, "resolve_failed");
        } catch (SQLException e) {
            log.error("RP dispatch resolve error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Medical ──────────────────────────────────────────────────────────────

    private void handleMedicalRecords(WebSocketSession session, JsonNode p) {
        long userId = session.getUserId();
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            List<RpMedicalService.MedicalRecord> recs = medicalService.getRecords(ch.id());
            session.send(router.buildPacket(PacketType.RP_MEDICAL_RECORDS_RESULT,
                Map.of("records", recs.stream().map(this::medRecToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP medical records error", e);
            sendError(session, "server_error");
        }
    }

    private void handleMedicalAdmit(WebSocketSession session, JsonNode p) {
        long userId   = session.getUserId();
        long targetCh = p.path("characterId").asLong(-1);
        String cond   = p.path("conditionDescription").asText("").trim();
        if (cond.isBlank()) { sendError(session, "invalid_payload"); return; }
        try {
            long recId = medicalService.admit(targetCh, cond);
            session.send(router.buildPacket(PacketType.RP_MEDICAL_ADMIT,
                Map.of("recordId", recId, "characterId", targetCh)));
        } catch (SQLException e) {
            log.error("RP medical admit error", e);
            sendError(session, "server_error");
        }
    }

    private void handleMedicalTreat(WebSocketSession session, JsonNode p) {
        long userId       = session.getUserId();
        long recordId     = p.path("recordId").asLong(-1);
        String treatment  = p.path("treatment").asText("").trim();
        int healthRestore = p.path("healthRestore").asInt(10);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            boolean ok = medicalService.treat(recordId, ch.id(), treatment, healthRestore);
            if (!ok) sendError(session, "treat_failed");
        } catch (SQLException e) {
            log.error("RP medical treat error", e);
            sendError(session, "server_error");
        }
    }

    private void handleMedicalDischarge(WebSocketSession session, JsonNode p) {
        long recordId = p.path("recordId").asLong(-1);
        try {
            boolean ok = medicalService.discharge(recordId);
            if (!ok) sendError(session, "discharge_failed");
        } catch (SQLException e) {
            log.error("RP medical discharge error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Properties ───────────────────────────────────────────────────────────

    private void handlePropertyList(WebSocketSession session, JsonNode p) {
        try {
            List<RpPropertyService.Property> props = propertyService.listForSale();
            session.send(router.buildPacket(PacketType.RP_PROPERTY_LIST_RESULT,
                Map.of("properties", props.stream().map(this::propToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP property list error", e);
            sendError(session, "server_error");
        }
    }

    private void handlePropertyBuy(WebSocketSession session, JsonNode p) {
        long userId  = session.getUserId();
        long propId  = p.path("propertyId").asLong(-1);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            String err = propertyService.buy(propId, ch.id());
            if (err != null) { sendError(session, err); return; }
            session.send(router.buildPacket(PacketType.RP_PROPERTY_BOUGHT,
                Map.of("propertyId", propId)));
        } catch (SQLException e) {
            log.error("RP property buy error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Government / Elections ───────────────────────────────────────────────

    private void handleGovtOffices(WebSocketSession session, JsonNode p) {
        try {
            List<RpGovernmentService.GovtOffice> offices = govtService.listOffices();
            session.send(router.buildPacket(PacketType.RP_GOVT_OFFICES_RESULT,
                Map.of("offices", offices.stream().map(this::officeToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP govt offices error", e);
            sendError(session, "server_error");
        }
    }

    private void handleElectionList(WebSocketSession session, JsonNode p) {
        String status = p.has("status") ? p.path("status").asText() : null;
        try {
            List<RpGovernmentService.Election> elections = govtService.listElections(status);
            session.send(router.buildPacket(PacketType.RP_ELECTION_LIST_RESULT,
                Map.of("elections", elections.stream().map(this::electionToMap)
                    .collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP election list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleElectionNominate(WebSocketSession session, JsonNode p) {
        long userId     = session.getUserId();
        long electionId = p.path("electionId").asLong(-1);
        String platform = p.path("platform").asText("");
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            boolean ok = govtService.nominate(electionId, ch.id(), platform);
            if (!ok) sendError(session, "nominate_failed");
        } catch (SQLException e) {
            log.error("RP election nominate error", e);
            sendError(session, "server_error");
        }
    }

    private void handleElectionVote(WebSocketSession session, JsonNode p) {
        long userId       = session.getUserId();
        long electionId   = p.path("electionId").asLong(-1);
        long candidateId  = p.path("candidateCharacterId").asLong(-1);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            boolean ok = govtService.vote(electionId, ch.id(), candidateId);
            if (!ok) { sendError(session, "vote_failed"); return; }
            session.send(router.buildPacket(PacketType.RP_ELECTION_VOTED,
                Map.of("electionId", electionId)));
        } catch (SQLException e) {
            log.error("RP election vote error", e);
            sendError(session, "server_error");
        }
    }

    private void handleGovtDecree(WebSocketSession session, JsonNode p) {
        long userId    = session.getUserId();
        String title   = p.path("title").asText("").trim();
        String body    = p.path("body").asText("").trim();
        String expires = p.path("expiresAt").asText(null);
        if (title.isBlank() || body.isBlank()) { sendError(session, "invalid_payload"); return; }
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            long id = govtService.issueDecree(ch.id(), title, body, expires);
            String push = router.buildPacket(PacketType.RP_GOVT_DECREED,
                Map.of("decreeId", id, "title", title, "issuedByCharId", ch.id()));
            sessions.all().forEach(s -> s.send(push));
        } catch (SQLException e) {
            log.error("RP decree error", e);
            sendError(session, "server_error");
        }
    }

    private void handleLawList(WebSocketSession session, JsonNode p) {
        try {
            List<RpGovernmentService.Law> laws = govtService.listActiveLaws();
            session.send(router.buildPacket(PacketType.RP_LAW_LIST_RESULT,
                Map.of("laws", laws.stream().map(this::lawToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP law list error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Scenes ───────────────────────────────────────────────────────────────

    private void handleSceneStart(WebSocketSession session, JsonNode p) {
        long userId  = session.getUserId();
        long roomId  = p.path("roomId").asLong(-1);
        String title = p.path("title").asText("").trim();
        String desc  = p.path("description").asText("");
        if (title.isBlank()) { sendError(session, "invalid_payload"); return; }
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            long sceneId = sceneService.start(roomId, ch.id(), title, desc);
            session.send(router.buildPacket(PacketType.RP_SCENE_STARTED,
                Map.of("sceneId", sceneId, "roomId", roomId, "title", title)));
        } catch (SQLException e) {
            log.error("RP scene start error", e);
            sendError(session, "server_error");
        }
    }

    private void handleSceneEnd(WebSocketSession session, JsonNode p) {
        long userId  = session.getUserId();
        long sceneId = p.path("sceneId").asLong(-1);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            boolean ok = sceneService.end(sceneId, ch.id());
            if (!ok) { sendError(session, "scene_end_failed"); return; }
            session.send(router.buildPacket(PacketType.RP_SCENE_ENDED,
                Map.of("sceneId", sceneId)));
        } catch (SQLException e) {
            log.error("RP scene end error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Crafting ─────────────────────────────────────────────────────────────

    private void handleRecipeList(WebSocketSession session, JsonNode p) {
        try {
            List<RpCraftingService.Recipe> recipes = craftingService.listRecipes();
            session.send(router.buildPacket(PacketType.RP_RECIPE_LIST_RESULT,
                Map.of("recipes", recipes.stream().map(this::recipeToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP recipe list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleCraft(WebSocketSession session, JsonNode p) {
        long userId   = session.getUserId();
        long recipeId = p.path("recipeId").asLong(-1);
        try {
            RpCharacterService.Character ch = charService.findByUser(userId).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            RpCraftingService.Recipe recipe = craftingService.findById(recipeId).orElse(null);
            if (recipe == null) { sendError(session, "recipe_not_found"); return; }
            long craftedId = craftingService.craft(ch.id(), recipeId);
            session.send(router.buildPacket(PacketType.RP_CRAFTED,
                Map.of("craftedId", craftedId, "recipeId", recipeId, "recipeName", recipe.name())));
        } catch (SQLException e) {
            log.error("RP craft error", e);
            sendError(session, "server_error");
        }
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private Map<String, Object> charToMap(RpCharacterService.Character ch) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",           ch.id());
        m.put("name",         ch.name());
        m.put("surname",      ch.surname());
        m.put("age",          ch.age());
        m.put("biography",    ch.biography());
        m.put("factionId",    ch.factionId());
        m.put("jobId",        ch.jobId());
        m.put("health",       ch.health());
        m.put("cashBalance",  ch.cashBalance());
        m.put("bankBalance",  ch.bankBalance());
        m.put("prisonExpiry", ch.prisonExpiry());
        return m;
    }

    private Map<String, Object> factionToMap(RpFactionService.Faction f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",          f.id());
        m.put("name",        f.name());
        m.put("tag",         f.tag());
        m.put("description", f.description());
        m.put("maxMembers",  f.maxMembers());
        m.put("recruiting",  f.isRecruiting());
        m.put("badgeId",     f.badgeId());
        m.put("leaderId",    f.leaderId());
        return m;
    }

    private Map<String, Object> jobToMap(RpJobService.Job j) {
        return Map.of("id", j.id(), "title", j.title(), "description", j.description(),
            "salary", j.salary(), "salaryInterval", j.salaryInterval(),
            "maxOpenings", j.maxOpenings());
    }

    private Map<String, Object> crimeToMap(RpCrimeService.Crime c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",          c.id());
        m.put("type",        c.type());
        m.put("description", c.description());
        m.put("recordedAt",  c.recordedAt());
        return m;
    }

    private Map<String, Object> caseToMap(RpCourtService.CourtCase cc) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",           cc.id());
        m.put("defendantId",  cc.defendantId());
        m.put("verdict",      cc.verdict());
        m.put("sentenceType", cc.sentenceType());
        m.put("sentenceHours",cc.sentenceHours());
        m.put("fineAmount",   cc.fineAmount());
        return m;
    }

    private Map<String, Object> callToMap(RpDispatchService.DispatchCall c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",          c.id());
        m.put("callType",    c.callType());
        m.put("location",    c.location());
        m.put("description", c.description());
        m.put("priority",    c.priority());
        m.put("status",      c.status());
        m.put("createdAt",   c.createdAt());
        return m;
    }

    private Map<String, Object> medRecToMap(RpMedicalService.MedicalRecord r) {
        return Map.of("id", r.id(), "conditionDesc", r.conditionDesc(),
            "treatment", r.treatment() != null ? r.treatment() : "",
            "admittedAt", r.admittedAt(),
            "dischargedAt", r.dischargedAt() != null ? r.dischargedAt() : "");
    }

    private Map<String, Object> propToMap(RpPropertyService.Property prop) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",      prop.id());
        m.put("name",    prop.name());
        m.put("type",    prop.type());
        m.put("price",   prop.price());
        m.put("address", prop.address());
        return m;
    }

    private Map<String, Object> officeToMap(RpGovernmentService.GovtOffice o) {
        return Map.of("id", o.id(), "title", o.title(),
            "holderCharId", o.holderCharId() != null ? o.holderCharId() : 0,
            "termEndsAt", o.termEndsAt() != null ? o.termEndsAt() : "");
    }

    private Map<String, Object> electionToMap(RpGovernmentService.Election e) {
        return Map.of("id", e.id(), "officeId", e.officeId(), "status", e.status(),
            "nominationOpenAt", e.nominationOpenAt(),
            "votingOpenAt", e.votingOpenAt(),
            "votingCloseAt", e.votingCloseAt());
    }

    private Map<String, Object> lawToMap(RpGovernmentService.Law l) {
        return Map.of("id", l.id(), "title", l.title(), "body", l.body(),
            "enactedAt", l.enactedAt());
    }

    private Map<String, Object> recipeToMap(RpCraftingService.Recipe r) {
        return Map.of("id", r.id(), "name", r.name(),
            "levelRequired", r.levelRequired(),
            "factionRequired", r.factionRequired() != null ? r.factionRequired() : "");
    }


    // ─── combat ─────────────────────────────────────────────────────────────

    private void handleWeaponList(WebSocketSession session, JsonNode p) {
        try {
            RpCharacterService.Character ch = charService.findByUser(session.getUserId()).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            session.send(router.buildPacket(PacketType.RP_COMBAT_WEAPONS_RESULT, Map.of(
                "carried",   combatService.inventory(ch.id()).stream()
                                 .map(this::weaponToMap).collect(Collectors.toList()),
                "catalogue", combatService.catalogue().stream()
                                 .map(this::weaponToMap).collect(Collectors.toList()))));
        } catch (SQLException e) {
            log.error("RP weapon list error", e);
            sendError(session, "server_error");
        }
    }

    private void handleWeaponEquip(WebSocketSession session, JsonNode p) {
        try {
            RpCharacterService.Character ch = charService.findByUser(session.getUserId()).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            int weaponId = p.path("weaponId").asInt(-1);
            if (!combatService.equip(ch.id(), weaponId)) { sendError(session, "not_carried"); return; }
            session.send(router.buildPacket(PacketType.RP_COMBAT_EQUIPPED,
                Map.of("weaponId", weaponId)));
        } catch (SQLException e) {
            log.error("RP weapon equip error", e);
            sendError(session, "server_error");
        }
    }

    private void handleAttack(WebSocketSession session, JsonNode p) {
        try {
            RpCharacterService.Character attacker =
                charService.findByUser(session.getUserId()).orElse(null);
            if (attacker == null) { sendError(session, "no_character"); return; }

            long victimId = p.path("targetCharacterId").asLong(-1);
            if (victimId < 1) { sendError(session, "no_target"); return; }

            // Distance and room come from the client only as a hint; the
            // service re-checks range against the weapon it finds equipped.
            int distance = Math.max(0, p.path("distance").asInt(1));
            Long roomId  = p.has("roomId") ? p.path("roomId").asLong() : null;

            RpCombatService.AttackResult result =
                combatService.attack(attacker.id(), victimId, distance, roomId);

            if (!result.landed()) { sendError(session, result.rejection()); return; }

            Map<String, Object> outcome = Map.of(
                "targetCharacterId", victimId,
                "damage",            result.damage(),
                "targetHealth",      result.victimHealthAfter(),
                "fatal",             result.fatal());
            session.send(router.buildPacket(PacketType.RP_COMBAT_ATTACK_RESULT, outcome));

            // The victim learns of it wherever they are.
            charService.findById(victimId).ifPresent(victim ->
                sessions.byUserId(victim.userId()).ifPresent(s -> s.send(
                    router.buildPacket(result.fatal()
                        ? PacketType.RP_COMBAT_DOWNED
                        : PacketType.RP_COMBAT_ATTACK_RESULT, outcome))));

        } catch (SQLException e) {
            log.error("RP attack error", e);
            sendError(session, "server_error");
        }
    }

    private void handleRevive(WebSocketSession session, JsonNode p) {
        try {
            RpCharacterService.Character ch = charService.findByUser(session.getUserId()).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }

            long targetId = p.path("targetCharacterId").asLong(ch.id());
            // Reviving yourself is only allowed once the down timer has run out;
            // medics may revive someone else at any point.
            if (targetId == ch.id() && !combatService.canSelfRevive(ch.id())) {
                sendError(session, "still_down");
                return;
            }
            if (!combatService.revive(targetId, 25)) { sendError(session, "not_downed"); return; }

            session.send(router.buildPacket(PacketType.RP_COMBAT_REVIVED,
                Map.of("characterId", targetId, "health", 25)));
        } catch (SQLException e) {
            log.error("RP revive error", e);
            sendError(session, "server_error");
        }
    }

    // ─── territory ──────────────────────────────────────────────────────────

    private void handleTurfList(WebSocketSession session, JsonNode p) {
        session.send(router.buildPacket(PacketType.RP_TURF_LIST_RESULT, Map.of(
            "turfs", turfService.list().stream().map(this::turfToMap).collect(Collectors.toList()))));
    }

    private void handleTurfCaptureBegin(WebSocketSession session, JsonNode p) {
        try {
            RpCharacterService.Character ch = charService.findByUser(session.getUserId()).orElse(null);
            if (ch == null) { sendError(session, "no_character"); return; }
            if (ch.factionId() == null) { sendError(session, "no_faction"); return; }

            int turfId = p.path("turfId").asInt(-1);
            String rejection = turfService.beginCapture(turfId, ch.factionId().intValue(), ch.id());
            if (rejection != null) { sendError(session, rejection); return; }

            session.send(router.buildPacket(PacketType.RP_TURF_CAPTURE_STARTED,
                Map.of("turfId", turfId, "factionId", ch.factionId())));
        } catch (SQLException e) {
            log.error("RP turf capture error", e);
            sendError(session, "server_error");
        }
    }

    private void handleTurfCaptureAbandon(WebSocketSession session, JsonNode p) {
        try {
            RpCharacterService.Character ch = charService.findByUser(session.getUserId()).orElse(null);
            if (ch == null || ch.factionId() == null) { sendError(session, "no_character"); return; }
            int turfId = p.path("turfId").asInt(-1);
            turfService.abandonCapture(turfId, ch.factionId().intValue());
        } catch (SQLException e) {
            log.error("RP turf abandon error", e);
            sendError(session, "server_error");
        }
    }

    private Map<String, Object> weaponToMap(RpCombatService.Weapon w) {
        return Map.of("id", w.id(), "code", w.code(), "name", w.name(),
            "category", w.category(), "damage", w.damage(),
            "range", w.rangeTiles(), "cooldownMs", w.cooldownMs(),
            "licenceRequired", w.licenceRequired(), "price", w.price());
    }

    private Map<String, Object> turfToMap(RpTurfService.Turf t) {
        return Map.of("id", t.id(), "code", t.code(), "name", t.name(),
            "description", t.description(),
            "incomePerHour", t.incomePerHour(),
            "captureSeconds", t.captureSeconds(),
            "ownerFactionId", t.ownerFactionId() != null ? t.ownerFactionId() : 0,
            "ownerFactionName", t.ownerFactionName() != null ? t.ownerFactionName() : "");
    }

    private void sendError(WebSocketSession session, String reason) {
        session.send(router.buildPacket("rp.error", Map.of("reason", reason)));
    }
}

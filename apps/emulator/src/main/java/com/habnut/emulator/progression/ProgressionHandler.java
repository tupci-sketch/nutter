package com.habnut.emulator.progression;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.ErrorCode;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class ProgressionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProgressionHandler.class);

    private final AchievementService achievements;
    private final QuestService quests;
    private final BadgeService badges;
    private final ProfileService profiles;
    private final PacketRouter router;

    public ProgressionHandler(AchievementService achievements, QuestService quests,
                               BadgeService badges, ProfileService profiles,
                               PacketRouter router) {
        this.achievements = achievements;
        this.quests       = quests;
        this.badges       = badges;
        this.profiles     = profiles;
        this.router       = router;
    }

    public void register(PacketRouter router) {
        router.register(PacketType.ACH_LIST,              this::handleAchList);
        router.register(PacketType.QUEST_LIST,            this::handleQuestList);
        router.register(PacketType.QUEST_ACCEPT,          this::handleQuestAccept);
        router.register(PacketType.QUEST_ABANDON,         this::handleQuestAbandon);
        router.register(PacketType.PROFILE_VIEW,          this::handleProfileView);
        router.register(PacketType.PROFILE_UPDATE_MOTTO,  this::handleUpdateMotto);
        router.register(PacketType.PROFILE_UPDATE_FIGURE, this::handleUpdateFigure);
        router.register(PacketType.INVENTORY_BADGE_EQUIP,   this::handleBadgeEquip);
        router.register(PacketType.INVENTORY_BADGE_UNEQUIP, this::handleBadgeUnequip);
        router.register(PacketType.INVENTORY_BADGES_LIST,   this::handleBadgeList);
    }

    private void handleAchList(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        session.send(router.buildPacket(PacketType.ACH_LIST_RESULT,
            Map.of("achievements", achievements.getForUser(session.getUserId()))));
    }

    private void handleQuestList(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        session.send(router.buildPacket(PacketType.QUEST_LIST_RESULT,
            Map.of("quests", quests.getActive(session.getUserId()))));
    }

    private void handleQuestAccept(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long questId = payload.path("questId").asLong(-1);
        if (questId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid quest"); return; }
        boolean accepted = quests.accept(session.getUserId(), questId);
        if (!accepted) sendError(session, ErrorCode.GENERIC_NOT_FOUND, "Quest not found or already accepted");
    }

    private void handleQuestAbandon(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long questId = payload.path("questId").asLong(-1);
        if (questId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid quest"); return; }
        quests.abandon(session.getUserId(), questId);
    }

    private void handleProfileView(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long targetId = payload.path("userId").asLong(session.getUserId());
        profiles.getProfile(targetId, badges).ifPresentOrElse(
            p -> session.send(router.buildPacket(PacketType.PROFILE_VIEW_RESULT, Map.of("profile", p))),
            () -> sendError(session, ErrorCode.GENERIC_NOT_FOUND, "Profile not found")
        );
    }

    private void handleUpdateMotto(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        String motto = payload.path("motto").asText("");
        boolean updated = profiles.updateMotto(session.getUserId(), motto);
        if (updated) {
            session.send(router.buildPacket(PacketType.PROFILE_MOTTO_UPDATED,
                Map.of("motto", motto.trim())));
        }
    }

    private void handleUpdateFigure(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        String figure = payload.path("figure").asText("");
        if (figure.isBlank()) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid figure"); return; }
        boolean updated = profiles.updateFigure(session.getUserId(), figure);
        if (updated) {
            session.send(router.buildPacket(PacketType.PROFILE_FIGURE_UPDATED,
                Map.of("figure", figure.trim())));
        }
    }

    private void handleBadgeEquip(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        String code     = payload.path("code").asText("");
        int    slotIndex = payload.path("slot").asInt(-1);
        if (code.isBlank() || slotIndex < 1) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid badge or slot"); return;
        }
        boolean ok = badges.equip(session.getUserId(), code, slotIndex);
        if (!ok) sendError(session, ErrorCode.GENERIC_NOT_FOUND, "Badge not found");
        else session.send(router.buildPacket(PacketType.INVENTORY_BADGE_EQUIPPED,
            Map.of("code", code, "slot", slotIndex)));
    }

    private void handleBadgeUnequip(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        String code = payload.path("code").asText("");
        if (code.isBlank()) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid badge"); return; }
        badges.unequip(session.getUserId(), code);
    }

    private void handleBadgeList(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long targetId = payload.path("userId").asLong(session.getUserId());
        session.send(router.buildPacket(PacketType.INVENTORY_BADGES_LIST_RESULT,
            Map.of("badges", badges.getBadges(targetId))));
    }

    private void sendError(WebSocketSession session, String code, String msg) {
        session.send(router.buildPacket("progression.error", Map.of("code", code, "message", msg)));
    }
}

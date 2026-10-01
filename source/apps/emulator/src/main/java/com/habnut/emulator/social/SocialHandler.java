package com.habnut.emulator.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.habnut.emulator.net.PacketRouter;
import com.habnut.emulator.net.SessionRegistry;
import com.habnut.emulator.net.WebSocketSession;
import com.habnut.emulator.protocol.ErrorCode;
import com.habnut.emulator.protocol.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class SocialHandler {

    private static final Logger log = LoggerFactory.getLogger(SocialHandler.class);
    private static final int MAX_MSG_LIST = 50;

    private final FriendService friendService;
    private final MessageService messageService;
    private final GroupService groupService;
    private final SessionRegistry sessions;
    private final PacketRouter router;
    private final com.habnut.emulator.auth.UserRepository users;

    public SocialHandler(FriendService friendService, MessageService messageService,
                         GroupService groupService, SessionRegistry sessions,
                         PacketRouter router, com.habnut.emulator.auth.UserRepository users) {
        this.friendService  = friendService;
        this.messageService = messageService;
        this.groupService   = groupService;
        this.sessions       = sessions;
        this.router         = router;
        this.users          = users;
    }

    public void register(PacketRouter router) {
        // Friends
        router.register(PacketType.SOCIAL_FRIEND_LIST,            this::handleFriendList);
        router.register(PacketType.SOCIAL_FRIEND_REQUEST_SEND,    this::handleRequestSend);
        router.register(PacketType.SOCIAL_FRIEND_REQUEST_ACCEPT,  this::handleRequestAccept);
        router.register(PacketType.SOCIAL_FRIEND_REQUEST_DECLINE, this::handleRequestDecline);
        router.register(PacketType.SOCIAL_FRIEND_REMOVE,          this::handleFriendRemove);
        router.register(PacketType.SOCIAL_BLOCK,                  this::handleBlock);
        // Messages
        router.register(PacketType.SOCIAL_MSG_SEND,               this::handleMsgSend);
        router.register(PacketType.SOCIAL_MSG_LIST,               this::handleMsgList);
        // Groups
        router.register(PacketType.GROUP_CREATE,                  this::handleGroupCreate);
        router.register(PacketType.GROUP_JOIN,                    this::handleGroupJoin);
        router.register(PacketType.GROUP_LEAVE,                   this::handleGroupLeave);
        router.register(PacketType.GROUP_INFO,                    this::handleGroupInfo);
        router.register(PacketType.GROUP_SEARCH,                  this::handleGroupSearch);
        router.register(PacketType.GROUP_FORUM_THREAD_CREATE,     this::handleForumThreadCreate);
        router.register(PacketType.GROUP_FORUM_POST_CREATE,       this::handleForumPostCreate);
    }

    // --- Friends ---

    private void handleFriendList(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long userId = session.getUserId();
        List<FriendService.Friend> friends = friendService.getFriends(userId);
        List<FriendService.FriendRequest> pending = friendService.getPendingRequests(userId);
        session.send(router.buildPacket(PacketType.SOCIAL_FRIEND_LIST_RESULT,
            Map.of("friends", friends, "pendingRequests", pending)));
    }

    private void handleRequestSend(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;

        // A player adds a friend by typing their name; an id is accepted too,
        // for the places in the client that already have one to hand.
        long toUserId = payload.path("toUserId").asLong(-1);
        if (toUserId < 1) {
            String username = payload.path("username").asText("").trim();
            if (username.isEmpty()) {
                sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid user");
                return;
            }
            com.habnut.emulator.auth.UserRepository.UserRow target = users.findByUsername(username);
            if (target == null) {
                sendError(session, ErrorCode.GENERIC_NOT_FOUND, "No one here goes by that name");
                return;
            }
            toUserId = target.id();
        }

        FriendService.SendRequestResult result = friendService.sendRequest(session.getUserId(), toUserId);
        switch (result) {
            case SENT -> {
                // Notify target if online
                long fromId = session.getUserId();
                sessions.byUserId(toUserId).ifPresent(s ->
                    s.send(router.buildPacket(PacketType.SOCIAL_FRIEND_REQUEST_RECEIVED,
                        describeUser("from", fromId))));
                session.send(router.buildPacket(PacketType.SOCIAL_FRIEND_REQUEST_SENT,
                    Map.of("toUserId", toUserId)));
            }
            case ALREADY_FRIENDS    -> sendError(session, ErrorCode.TRADE_INVALID_PARTNER, "Already friends");
            case ALREADY_PENDING    -> sendError(session, ErrorCode.TRADE_INVALID_PARTNER, "Request already pending");
            case BLOCKED            -> sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "Blocked");
            case SELF               -> sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Cannot add self");
        }
    }

    private void handleRequestAccept(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long requestId = payload.path("requestId").asLong(-1);
        if (requestId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid request"); return; }
        boolean ok = friendService.acceptRequest(requestId, session.getUserId());
        if (!ok) { sendError(session, ErrorCode.GENERIC_NOT_FOUND, "Request not found"); return; }

        friendService.getFriend(session.getUserId(), payload.path("fromUserId").asLong(-1))
            .ifPresent(f -> {
                session.send(router.buildPacket(PacketType.SOCIAL_FRIEND_ADDED,
                    Map.of("friend", f)));
                sessions.byUserId(f.userId()).ifPresent(s ->
                    s.send(router.buildPacket(PacketType.SOCIAL_FRIEND_ADDED,
                        Map.of("friend", buildFriendForOther(session.getUserId())))));
            });
    }

    private void handleRequestDecline(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long requestId = payload.path("requestId").asLong(-1);
        if (requestId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid request"); return; }
        friendService.declineRequest(requestId, session.getUserId());
    }

    private void handleFriendRemove(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long friendId = payload.path("friendId").asLong(-1);
        if (friendId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid user"); return; }
        boolean removed = friendService.removeFriend(session.getUserId(), friendId);
        if (removed) {
            session.send(router.buildPacket(PacketType.SOCIAL_FRIEND_REMOVED,
                Map.of("friendId", friendId)));
            sessions.byUserId(friendId).ifPresent(s ->
                s.send(router.buildPacket(PacketType.SOCIAL_FRIEND_REMOVED,
                    Map.of("friendId", session.getUserId()))));
        }
    }

    private void handleBlock(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long blockedId = payload.path("userId").asLong(-1);
        if (blockedId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid user"); return; }
        friendService.block(session.getUserId(), blockedId);
    }

    // --- Messages ---

    private void handleMsgSend(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long toUserId = payload.path("toUserId").asLong(-1);
        String body = payload.path("body").asText("");
        if (toUserId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid user"); return; }

        MessageService.SendResult result = messageService.send(session.getUserId(), toUserId, body, friendService);
        switch (result) {
            case SENT -> {
                session.send(router.buildPacket(PacketType.SOCIAL_MSG_SENT, Map.of("toUserId", toUserId)));
                // Deliver immediately if target is online
                Map<String, Object> delivery = new java.util.HashMap<>(describeUser("from", session.getUserId()));
                delivery.put("body", body);
                delivery.put("sentAt", java.time.Instant.now().toString());
                sessions.byUserId(toUserId).ifPresent(s ->
                    s.send(router.buildPacket(PacketType.SOCIAL_MSG_RECEIVED, delivery)));
            }
            case NOT_FRIENDS -> sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "Not friends");
            case BLOCKED     -> sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "Cannot send message");
            case TOO_LONG    -> sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Message too long");
        }
    }

    private void handleMsgList(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        int limit  = Math.min(payload.path("limit").asInt(20), MAX_MSG_LIST);
        int offset = Math.max(payload.path("offset").asInt(0), 0);
        boolean sent = payload.path("sent").asBoolean(false);
        List<MessageService.Message> messages = sent
            ? messageService.getSent(session.getUserId(), limit, offset)
            : messageService.getInbox(session.getUserId(), limit, offset);
        session.send(router.buildPacket(PacketType.SOCIAL_MSG_LIST_RESULT,
            Map.of("messages", messages, "sent", sent)));
    }

    // --- Groups ---

    private void handleGroupCreate(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        String name        = payload.path("name").asText("").trim();
        String description = payload.path("description").asText("").trim();
        String badge       = payload.path("badge").asText("").trim();
        String type        = payload.path("type").asText("public");
        long homeRoomId    = payload.path("homeRoomId").asLong(-1);

        if (name.isEmpty() || name.length() > 64) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid group name"); return;
        }
        if (!List.of("public", "private").contains(type)) type = "public";
        try {
            long groupId = groupService.createGroup(session.getUserId(), homeRoomId,
                name, description, badge, type);
            groupService.findById(groupId).ifPresent(g ->
                session.send(router.buildPacket(PacketType.GROUP_CREATED, Map.of("group", g))));
        } catch (SQLException e) {
            log.error("Group create failed for user {}", session.getUserId(), e);
            sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Failed to create group");
        }
    }

    private void handleGroupJoin(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long groupId = payload.path("groupId").asLong(-1);
        if (groupId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid group"); return; }

        GroupService.JoinResult result = groupService.join(session.getUserId(), groupId);
        switch (result) {
            case JOINED         -> session.send(router.buildPacket(PacketType.GROUP_JOINED,
                Map.of("groupId", groupId)));
            case ALREADY_MEMBER -> sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Already a member");
            case INVITE_ONLY    -> sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "Group is private");
            case NOT_FOUND      -> sendError(session, ErrorCode.GENERIC_NOT_FOUND, "Group not found");
        }
    }

    private void handleGroupLeave(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long groupId = payload.path("groupId").asLong(-1);
        if (groupId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid group"); return; }
        boolean left = groupService.leave(session.getUserId(), groupId);
        if (left) {
            session.send(router.buildPacket(PacketType.GROUP_LEFT, Map.of("groupId", groupId)));
        } else {
            sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "Cannot leave (owner or not a member)");
        }
    }

    private void handleGroupInfo(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long groupId = payload.path("groupId").asLong(-1);
        if (groupId < 1) { sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid group"); return; }
        groupService.findById(groupId).ifPresentOrElse(
            g -> {
                List<GroupService.GroupMember> members = groupService.getMembers(groupId, 50, 0);
                List<GroupService.ForumThread> threads = groupService.getThreads(groupId, 20, 0);
                session.send(router.buildPacket(PacketType.GROUP_INFO_RESULT,
                    Map.of("group", g, "members", members, "threads", threads)));
            },
            () -> sendError(session, ErrorCode.GENERIC_NOT_FOUND, "Group not found")
        );
    }

    private void handleGroupSearch(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        String query = payload.path("query").asText("").trim();
        int limit  = Math.min(payload.path("limit").asInt(20), 50);
        int offset = Math.max(payload.path("offset").asInt(0), 0);
        List<GroupService.Group> results = groupService.search(query, limit, offset);
        session.send(router.buildPacket(PacketType.GROUP_SEARCH_RESULT,
            Map.of("query", query, "groups", results)));
    }

    private void handleForumThreadCreate(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long groupId = payload.path("groupId").asLong(-1);
        String title = payload.path("title").asText("").trim();
        if (groupId < 1 || title.isEmpty() || title.length() > 128) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid thread parameters"); return;
        }
        String rank = groupService.getMemberRank(groupId, session.getUserId());
        if (rank == null) {
            sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "Not a member"); return;
        }
        try {
            long threadId = groupService.createThread(groupId, session.getUserId(), title);
            session.send(router.buildPacket(PacketType.GROUP_FORUM_THREAD_CREATED,
                Map.of("threadId", threadId, "groupId", groupId)));
        } catch (SQLException e) {
            log.error("Forum thread create failed: group={}", groupId, e);
            sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Failed to create thread");
        }
    }

    private void handleForumPostCreate(WebSocketSession session, JsonNode payload) {
        if (!session.isAuthenticated()) return;
        long threadId = payload.path("threadId").asLong(-1);
        long groupId  = payload.path("groupId").asLong(-1);
        String body   = payload.path("body").asText("").trim();
        if (threadId < 1 || groupId < 1 || body.isEmpty() || body.length() > 2000) {
            sendError(session, ErrorCode.GENERIC_INVALID_PAYLOAD, "Invalid post parameters"); return;
        }
        String rank = groupService.getMemberRank(groupId, session.getUserId());
        if (rank == null) {
            sendError(session, ErrorCode.GENERIC_PERMISSION_DENIED, "Not a member"); return;
        }
        try {
            long postId = groupService.createPost(threadId, session.getUserId(), body);
            session.send(router.buildPacket(PacketType.GROUP_FORUM_POST_CREATED,
                Map.of("postId", postId, "threadId", threadId)));
        } catch (SQLException e) {
            log.error("Forum post create failed: thread={}", threadId, e);
            sendError(session, ErrorCode.GENERIC_INTERNAL_ERROR, "Failed to create post");
        }
    }

    /**
     * Enough about a user to show them: id, name and figure.
     *
     * These notices used to carry only a numeric id, which no part of the
     * client can draw or address somebody by, so a friend request arrived from
     * nobody in particular.
     */
    private Map<String, Object> describeUser(String prefix, long userId) {
        com.habnut.emulator.auth.UserRepository.UserRow row = users.findById(userId);
        Map<String, Object> m = new java.util.HashMap<>();
        m.put(prefix + "UserId", userId);
        m.put(prefix + "Username", row != null ? row.username() : "");
        m.put(prefix + "Figure", row != null ? row.figureString() : "");
        return m;
    }

    private Map<String, Object> buildFriendForOther(long userId) {
        com.habnut.emulator.auth.UserRepository.UserRow row = users.findById(userId);
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("userId", userId);
        m.put("username", row != null ? row.username() : "");
        m.put("figure", row != null ? row.figureString() : "");
        m.put("online", true);
        return m;
    }

    private void sendError(WebSocketSession session, String code, String msg) {
        session.send(router.buildPacket(PacketType.SOCIAL_ERROR, Map.of("code", code, "message", msg)));
    }
}

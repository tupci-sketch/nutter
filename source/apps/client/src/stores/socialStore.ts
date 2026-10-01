import { create } from 'zustand';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';

export interface Friend {
  userId: number;
  username: string;
  figure: string;
  motto: string;
  online: boolean;
  lastSeen: string | null;
}

export interface FriendRequest {
  requestId: number;
  fromUserId: number;
  fromUsername: string;
  fromFigure: string;
  sentAt: string;
}

export interface Message {
  id: number;
  fromUserId: number;
  fromUsername: string;
  toUserId: number;
  body: string;
  read: boolean;
  sentAt: string;
}

interface SocialStore {
  friends: Friend[];
  requests: FriendRequest[];
  inbox: Message[];
  unreadCount: number;
  notice: string | null;

  loadFriends: () => void;
  sendFriendRequest: (username: string) => void;
  acceptRequest: (requestId: number, fromUserId: number) => void;
  declineRequest: (requestId: number) => void;
  removeFriend: (friendId: number) => void;
  block: (userId: number) => void;
  loadInbox: () => void;
  sendMessage: (toUserId: number, body: string) => void;
  clearNotice: () => void;
}

export const useSocialStore = create<SocialStore>(set => ({
  friends: [],
  requests: [],
  inbox: [],
  unreadCount: 0,
  notice: null,

  loadFriends() {
    getWsClient().send(Packet.SOCIAL_FRIEND_LIST, {});
  },

  sendFriendRequest(username) {
    getWsClient().send(Packet.SOCIAL_FRIEND_REQUEST_SEND, { username });
  },

  // The hotel wants to know who the request came from as well as which one it
  // is, so it can tell both sides they are now friends.
  acceptRequest(requestId, fromUserId) {
    getWsClient().send(Packet.SOCIAL_FRIEND_REQUEST_ACCEPT, { requestId, fromUserId });
  },

  declineRequest(requestId) {
    getWsClient().send(Packet.SOCIAL_FRIEND_REQUEST_DECLINE, { requestId });
    set(s => ({ requests: s.requests.filter(r => r.requestId !== requestId) }));
  },

  removeFriend(friendId) {
    getWsClient().send(Packet.SOCIAL_FRIEND_REMOVE, { friendId });
  },

  block(userId) {
    getWsClient().send(Packet.SOCIAL_BLOCK, { userId });
  },

  loadInbox() {
    getWsClient().send(Packet.SOCIAL_MSG_LIST, { limit: 50, offset: 0 });
  },

  sendMessage(toUserId, body) {
    getWsClient().send(Packet.SOCIAL_MSG_SEND, { toUserId, body });
  },

  clearNotice() {
    set({ notice: null });
  },
}));

export function initSocialListeners(): void {
  const ws = getWsClient();

  ws.on(Packet.SOCIAL_FRIEND_LIST_RESULT, raw => {
    const p = raw as { friends: Friend[]; pendingRequests: FriendRequest[] };
    useSocialStore.setState({
      friends: p.friends ?? [],
      requests: p.pendingRequests ?? [],
    });
  });

  ws.on(Packet.SOCIAL_FRIEND_ADDED, raw => {
    const p = raw as { friend: Friend };
    if (!p.friend) return;
    useSocialStore.setState(s =>
      s.friends.some(f => f.userId === p.friend.userId)
        ? {}
        : {
            friends: [...s.friends, p.friend],
            requests: s.requests.filter(r => r.fromUserId !== p.friend.userId),
          },
    );
  });

  ws.on(Packet.SOCIAL_FRIEND_REMOVED, raw => {
    const p = raw as { friendId: number };
    useSocialStore.setState(s => ({
      friends: s.friends.filter(f => f.userId !== p.friendId),
    }));
  });

  ws.on(Packet.SOCIAL_FRIEND_REQUEST_RECEIVED, raw => {
    const p = raw as { fromUserId: number; fromUsername: string; fromFigure: string };
    useSocialStore.setState(s =>
      s.requests.some(r => r.fromUserId === p.fromUserId)
        ? {}
        : {
            requests: [
              ...s.requests,
              {
                requestId: 0,
                fromUserId: p.fromUserId,
                fromUsername: p.fromUsername,
                fromFigure: p.fromFigure,
                sentAt: new Date().toISOString(),
              },
            ],
            notice: `${p.fromUsername} wants to be friends.`,
          },
    );
    // The request id is only known from the list, so refresh it.
    useSocialStore.getState().loadFriends();
  });

  ws.on(Packet.SOCIAL_FRIEND_REQUEST_SENT, () => {
    useSocialStore.setState({ notice: 'Friend request sent.' });
  });

  // Someone coming online or going offline is a change to one row, not a
  // reason to ask for the whole list again.
  ws.on(Packet.SOCIAL_FRIEND_ONLINE, raw => {
    const p = raw as { userId: number };
    setFriendPresence(p.userId, true);
  });

  ws.on(Packet.SOCIAL_FRIEND_OFFLINE, raw => {
    const p = raw as { userId: number };
    setFriendPresence(p.userId, false);
  });

  ws.on(Packet.SOCIAL_MSG_LIST_RESULT, raw => {
    const p = raw as { messages: Message[] };
    const messages = p.messages ?? [];
    useSocialStore.setState({
      inbox: messages,
      unreadCount: messages.filter(m => !m.read).length,
    });
  });

  ws.on(Packet.SOCIAL_MSG_RECEIVED, raw => {
    const p = raw as { fromUserId: number; fromUsername: string; body: string; sentAt: string };
    useSocialStore.setState(s => ({
      inbox: [
        {
          id: 0,
          fromUserId: p.fromUserId,
          fromUsername: p.fromUsername,
          toUserId: 0,
          body: p.body,
          read: false,
          sentAt: p.sentAt ?? new Date().toISOString(),
        },
        ...s.inbox,
      ],
      unreadCount: s.unreadCount + 1,
      notice: `New message from ${p.fromUsername}.`,
    }));
  });

  ws.on(Packet.SOCIAL_ERROR, raw => {
    const p = raw as { message?: string };
    if (p.message) useSocialStore.setState({ notice: p.message });
  });
}

function setFriendPresence(userId: number, online: boolean): void {
  useSocialStore.setState(s => {
    if (!s.friends.some(f => f.userId === userId)) return {};
    return {
      friends: s.friends.map(f => (f.userId === userId ? { ...f, online } : f)),
    };
  });
}

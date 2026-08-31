import { create } from 'zustand';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';

export interface Friend {
  userId: number;
  username: string;
  online: boolean;
  figure: string;
}

export interface Message {
  id: number;
  fromUserId: number;
  fromUsername: string;
  body: string;
  sentAt: string;
  read: boolean;
}

interface SocialStore {
  friends: Friend[];
  inbox: Message[];
  unreadCount: number;

  loadFriends: () => void;
  sendFriendRequest: (username: string) => void;
  removeFriend: (userId: number) => void;
  loadInbox: () => void;
  sendMessage: (toUserId: number, body: string) => void;
}

export const useSocialStore = create<SocialStore>((_set) => ({
  friends: [],
  inbox: [],
  unreadCount: 0,

  loadFriends() {
    getWsClient().send(Packet.FRIEND_LIST, {});
  },

  sendFriendRequest(username) {
    getWsClient().send(Packet.FRIEND_REQUEST, { username });
  },

  removeFriend(userId) {
    getWsClient().send(Packet.FRIEND_REMOVE, { userId });
  },

  loadInbox() {
    getWsClient().send(Packet.MSG_INBOX, {});
  },

  sendMessage(toUserId, body) {
    getWsClient().send(Packet.MSG_SEND, { toUserId, body });
  },
}));

export function initSocialListeners(): void {
  const ws = getWsClient();

  ws.on(Packet.FRIEND_LIST_RESULT, (raw) => {
    const p = raw as { friends: Friend[] };
    useSocialStore.setState({ friends: p.friends });
  });

  ws.on(Packet.MSG_INBOX_RESULT, (raw) => {
    const p = raw as { messages: Message[] };
    const unread = p.messages.filter(m => !m.read).length;
    useSocialStore.setState({ inbox: p.messages, unreadCount: unread });
  });

  ws.on(Packet.MSG_RECEIVE, (raw) => {
    const msg = raw as Message;
    useSocialStore.setState((s) => ({
      inbox: [msg, ...s.inbox],
      unreadCount: s.unreadCount + 1,
    }));
  });
}

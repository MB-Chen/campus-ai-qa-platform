import { defineStore } from 'pinia';
import { store } from '@/store';

export interface IMessageState {
  unreadCount: number;
  notification: number;
  announcement: number;
  private: number;
}

export const useMessageStore = defineStore({
  id: 'app-message',
  state: (): IMessageState => ({
    unreadCount: 0,
    notification: 0,
    announcement: 0,
    private: 0,
  }),
  getters: {
    getUnreadCount(): number {
      return this.unreadCount;
    },
    getNotificationCount(): number {
      return this.notification;
    },
    getAnnouncementCount(): number {
      return this.announcement;
    },
    getPrivateCount(): number {
      return this.private;
    },
  },
  actions: {
    setUnreadCount(count: number) {
      this.unreadCount = count;
    },
    setNotificationCount(count: number) {
      this.notification = count;
    },
    setAnnouncementCount(count: number) {
      this.announcement = count;
    },
    setPrivateCount(count: number) {
      this.private = count;
    },
    updateCounts(data: { notification: number; announcement: number; private: number }) {
      this.notification = data.notification || 0;
      this.announcement = data.announcement || 0;
      this.private = data.private || 0;
      this.unreadCount = this.notification + this.announcement + this.private;
    },
  },
});

// Need to be used outside the setup
export function useMessage() {
  return useMessageStore(store);
}

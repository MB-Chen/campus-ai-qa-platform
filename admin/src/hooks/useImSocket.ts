import { ref, onBeforeUnmount } from 'vue';
import { useGlobSetting } from '@/hooks/setting';
import { useUser } from '@/store/modules/user';

/**
 * 客服端 IM WebSocket 连接管理
 * - 自动从 apiUrl 推导 ws 地址
 * - 心跳保活 + 断线重连
 * - 统一收帧回调
 */
export interface ImFrame {
  type: string;
  [key: string]: any;
}

export function useImSocket() {
  const { apiUrl } = useGlobSetting();
  const userStore = useUser();
  const token = userStore.getToken;

  // http(s)://host → ws(s)://host
  const wsBase = (apiUrl || '').replace(/^http/, 'ws').replace(/\/$/, '');
  const wsUrl = `${wsBase}/ws/im?role=cs&token=${encodeURIComponent(token)}`;

  const connected = ref(false);
  let ws: WebSocket | null = null;
  let heartbeatTimer: any = null;
  let reconnectTimer: any = null;
  let manualClose = false;

  /** 帧回调（外部注册） */
  let frameHandler: ((frame: ImFrame) => void) | null = null;

  function onFrame(cb: (frame: ImFrame) => void) {
    frameHandler = cb;
  }

  function connect() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      return;
    }
    manualClose = false;
    try {
      ws = new WebSocket(wsUrl);
    } catch (e) {
      scheduleReconnect();
      return;
    }
    ws.onopen = () => {
      connected.value = true;
      startHeartbeat();
    };
    ws.onclose = () => {
      connected.value = false;
      stopHeartbeat();
      if (!manualClose) {
        scheduleReconnect();
      }
    };
    ws.onerror = () => {
      connected.value = false;
    };
    ws.onmessage = (ev) => {
      try {
        const frame = JSON.parse(ev.data) as ImFrame;
        frameHandler?.(frame);
      } catch (e) {
        // 忽略非法帧
      }
    };
  }

  function send(frame: any) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(frame));
      return true;
    }
    return false;
  }

  function startHeartbeat() {
    stopHeartbeat();
    heartbeatTimer = setInterval(() => {
      send({ type: 'ping' });
    }, 25000);
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer) return;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, 3000);
  }

  function close() {
    manualClose = true;
    stopHeartbeat();
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    if (ws) {
      ws.close();
      ws = null;
    }
    connected.value = false;
  }

  onBeforeUnmount(() => {
    close();
  });

  return { connected, connect, send, onFrame, close };
}

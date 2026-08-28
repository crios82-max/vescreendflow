import { io, Socket } from 'socket.io-client';
import { getApiUrl } from './storage';

let socket: Socket | null = null;
let socketUrl = '';

export async function getMobileSocket(): Promise<Socket> {
  const url = await getApiUrl();
  if (!socket || socketUrl !== url) {
    socket?.disconnect();
    socketUrl = url;
    socket = io(url, { autoConnect: true, transports: ['websocket', 'polling'] });
  }
  return socket;
}

export async function reconnectSocket(): Promise<void> {
  if (socket) {
    socket.disconnect();
    socket = null;
  }
  await getMobileSocket();
}

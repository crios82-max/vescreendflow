import { io } from 'socket.io-client';

const SOCKET_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:4001';

export const mobileSocket = io(SOCKET_URL, { autoConnect: true });

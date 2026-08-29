import { useEffect, useState } from 'react';
import { api } from './api';
import { getSocket } from './socket';
import { useI18n } from './I18nProvider';

interface Props {
  rideId: string;
}

export function ChatPanel({ rideId }: Props) {
  const { t } = useI18n();
  const [messages, setMessages] = useState<Array<{ id: string; senderName?: string; message: string }>>([]);
  const [text, setText] = useState('');

  const load = () => api.getChatMessages(rideId).then((r) => setMessages(r.messages)).catch(() => {});

  useEffect(() => {
    load();
    const socket = getSocket();
    socket.emit('join:ride', rideId);
    const onMessage = (msg: { id: string; senderName?: string; message: string }) => {
      setMessages((prev) => (prev.some((m) => m.id === msg.id) ? prev : [...prev, msg]));
    };
    socket.on('chat:message', onMessage);
    const timer = setInterval(load, 15000);
    return () => {
      socket.off('chat:message', onMessage);
      clearInterval(timer);
    };
  }, [rideId]);

  return (
    <div className="chat-panel">
      <div className="chat-messages">
        {messages.length === 0 ? (
          <p className="muted-text">{t('common.emptyChat')}</p>
        ) : messages.map((m) => (
          <div key={m.id} className="chat-msg"><strong>{m.senderName ?? t('common.user')}:</strong> {m.message}</div>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <input className="place-input" value={text} onChange={(e) => setText(e.target.value)} placeholder={t('common.message')} />
        <button className="btn-secondary" type="button" onClick={async () => {
          if (!text.trim()) return;
          await api.sendChatMessage(rideId, text.trim());
          setText('');
          load();
        }}>{t('common.send')}</button>
      </div>
    </div>
  );
}

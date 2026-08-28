import { useEffect, useState } from 'react';
import { api } from './api';

interface Props {
  rideId: string;
}

export function ChatPanel({ rideId }: Props) {
  const [messages, setMessages] = useState<Array<{ id: string; senderName?: string; message: string }>>([]);
  const [text, setText] = useState('');

  const load = () => api.getChatMessages(rideId).then((r) => setMessages(r.messages)).catch(() => {});

  useEffect(() => {
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, [rideId]);

  return (
    <div className="chat-panel">
      <div className="chat-messages">
        {messages.map((m) => (
          <div key={m.id} className="chat-msg"><strong>{m.senderName ?? 'Usuario'}:</strong> {m.message}</div>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <input className="place-input" value={text} onChange={(e) => setText(e.target.value)} placeholder="Mensaje..." />
        <button className="btn-secondary" type="button" onClick={async () => {
          if (!text.trim()) return;
          await api.sendChatMessage(rideId, text.trim());
          setText('');
          load();
        }}>Enviar</button>
      </div>
    </div>
  );
}

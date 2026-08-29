import { useEffect, useState } from 'react';
import { api } from './api';
import { getSocket } from './socket';
import { useI18n } from './I18nProvider';

interface Props {
  rideId: string;
}

export function ChatPanel({ rideId }: Props) {
  const { t, te } = useI18n();
  const [messages, setMessages] = useState<Array<{ id: string; senderName?: string; message: string }>>([]);
  const [text, setText] = useState('');
  const [error, setError] = useState('');
  const [loadFailed, setLoadFailed] = useState(false);
  const [sending, setSending] = useState(false);

  const load = () =>
    api.getChatMessages(rideId)
      .then((r) => {
        setMessages(r.messages);
        setLoadFailed(false);
      })
      .catch(() => setLoadFailed(true));

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
        {loadFailed ? (
          <p className="error-text">
            {t('common.loadFailed')}{' '}
            <button type="button" className="link-btn" onClick={() => load()}>{t('common.retry')}</button>
          </p>
        ) : messages.length === 0 ? (
          <p className="muted-text">{t('common.emptyChat')}</p>
        ) : messages.map((m) => (
          <div key={m.id} className="chat-msg"><strong>{m.senderName ?? t('common.user')}:</strong> {m.message}</div>
        ))}
      </div>
      {error && <p className="error-text">{error}</p>}
      <div style={{ display: 'flex', gap: 8 }}>
        <input className="place-input" value={text} onChange={(e) => setText(e.target.value)} placeholder={t('common.message')} aria-label={t('common.message')} />
        <button className="btn-secondary" type="button" disabled={sending} aria-label={t('common.send')} onClick={async () => {
          if (!text.trim() || sending) return;
          setError('');
          setSending(true);
          try {
            await api.sendChatMessage(rideId, text.trim());
            setText('');
            load();
          } catch (err) {
            setError(te(err instanceof Error ? err.message : t('common.error')));
          } finally {
            setSending(false);
          }
        }}>{sending ? t('common.sending') : t('common.send')}</button>
      </div>
    </div>
  );
}

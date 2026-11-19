import React, { useState, useRef, useEffect, useId } from 'react';
import { X, ArrowUp, FileText, RefreshCw } from 'lucide-react';
import { Playbook } from '../../types';
import { storeService, RagCitation } from '../../services/storeService';
import { useDialogAccessibility } from '../../hooks/useDialogAccessibility';
import thinkingRobot from '../../assets/robot-pensando.png';
import '../marketplace/editorial-overlays.css';

interface ChatMessage {
  id: string;
  sender: 'user' | 'ia';
  text: string;
  timestamp: string;
  fuentes?: RagCitation[];
  tokensUsados?: number;
  latenciaMs?: number;
}

type ExecutiveChatDrawerProps = {
  activePlaybook?: Playbook | null;
  onClearContext?: () => void;
} & (
  | { integrado: true; isOpen?: never; onClose?: never }
  | { integrado?: false; isOpen: boolean; onClose: () => void }
);

function ChatRobot({ state }: { state: 'idle' | 'reading' | 'answered' }) {
  return (
    <div className="lx-chat-robot" data-state={state} aria-hidden="true">
      <div className="lx-chat-robot-head">
        <div className="lx-chat-robot-visor"><span /><span /></div>
      </div>
      <div className="lx-chat-robot-light" />
    </div>
  );
}

export const ExecutiveChatDrawer: React.FC<ExecutiveChatDrawerProps> = ({
  integrado = false,
  isOpen = true,
  onClose,
  activePlaybook = null,
  onClearContext,
}) => {
  const [sesionId, setSesionId] = useState<string>(() =>
    typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `chat-session-${Date.now()}`
  );
  const [sessionToken, setSessionToken] = useState<string | null>(null);
  const [lastFailedQuestion, setLastFailedQuestion] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      sender: 'ia',
      text: 'Hola. ¿Qué te gustaría saber de nuestros cursos?',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    },
  ]);
  const [inputQuestion, setInputQuestion] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleNewConversation = () => {
    const newSession = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `chat-session-${Date.now()}`;
    setSesionId(newSession);
    setSessionToken(null);
    setLastFailedQuestion(null);
    setInputQuestion('');
    setMessages([
      {
        id: 'welcome',
        sender: 'ia',
        text: 'Hola. ¿Qué te gustaría saber de nuestros cursos?',
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      },
    ]);
  };
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const panelMensajes = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const dialogRef = useDialogAccessibility<HTMLDivElement>(isOpen && !integrado, () => onClose?.());

  // Reiniciar la conversación de forma segura si cambia el usuario o el estado de autenticación
  const currentAuthToken = typeof window !== 'undefined' ? localStorage.getItem('lysandri_token') : null;
  const lastAuthTokenRef = useRef<string | null>(currentAuthToken);
  useEffect(() => {
    if (currentAuthToken !== lastAuthTokenRef.current) {
      lastAuthTokenRef.current = currentAuthToken;
      handleNewConversation();
    }
  }, [currentAuthToken]);

  useEffect(() => {
    if (activePlaybook) {
      setMessages((prev) => [
        ...prev,
        {
          id: `context-${Date.now()}`,
          sender: 'ia',
          text: `Estamos hablando de "${activePlaybook.title}". ¿Qué te gustaría saber sobre este programa?`,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    }
  }, [activePlaybook]);

  useEffect(() => {
    const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (integrado) {
      const panel = panelMensajes.current;
      if (panel) panel.scrollTop = panel.scrollHeight;
    } else {
      messagesEndRef.current?.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth' });
    }
  }, [messages, isLoading, integrado]);

  if (!isOpen) return null;

  const handleSend = async (questionText?: string) => {
    const q = (questionText || inputQuestion).trim();
    if (!q || isLoading) return;

    setLastFailedQuestion(null);
    const userMsg: ChatMessage = {
      id: `user-${Date.now()}`,
      sender: 'user',
      text: q,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputQuestion('');
    setIsLoading(true);

    try {
      const response = await storeService.consultarAsistenteRag(
        q,
        activePlaybook ? activePlaybook.programId : undefined,
        sesionId,
        sessionToken || undefined
      );

      if (response.sesionId) {
        setSesionId(response.sesionId);
      }
      if (response.sessionToken) {
        setSessionToken(response.sessionToken);
      }

      const iaMsg: ChatMessage = {
        id: `ia-${Date.now()}`,
        sender: 'ia',
        text: response.respuesta,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        fuentes: response.fuentes && response.fuentes.length > 0 ? response.fuentes : undefined,
        tokensUsados: response.tokensUsados,
        latenciaMs: response.latenciaMs,
      };

      setMessages((prev) => [...prev, iaMsg]);
    } catch (err: any) {
      setLastFailedQuestion(q);
      const errorMsg: ChatMessage = {
        id: `error-${Date.now()}`,
        sender: 'ia',
        text: err?.message || 'No pude responder en este momento. Por favor comprueba tu conexión e inténtalo de nuevo.',
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setIsLoading(false);
    }
  };


  const samplePrompts = activePlaybook
    ? [
        { label: 'Precio', question: '¿Cuánto cuesta este curso?' },
        { label: 'Duración', question: '¿Cuánto dura?' },
        { label: 'Contenido', question: '¿Qué voy a aprender?' },
      ]
    : [
        { label: 'Cursos', question: '¿Qué cursos tienen?' },
        { label: 'Precios', question: '¿Cuánto cuestan los cursos?' },
        { label: 'Duración', question: '¿Cuánto duran los cursos?' },
      ];
  const lastMessage = messages[messages.length - 1];
  const robotState = isLoading ? 'reading' : lastMessage?.id.startsWith('ia-') ? 'answered' : 'idle';

  const panel = (
    <div
      ref={integrado ? undefined : dialogRef}
      role={integrado ? undefined : 'dialog'}
      aria-modal={integrado ? undefined : true}
      aria-labelledby={integrado ? undefined : titleId}
      tabIndex={integrado ? undefined : -1}
      className={integrado ? 'lx-chat lx-chat-integrado' : 'lx-drawer lx-chat flex w-screen max-w-lg flex-col'}
    >
      {/* Header */}
      <div className="lx-overlay-header lx-chat-header">
        <div className="lx-chat-identity">
          <ChatRobot state={robotState} />
          <div>
            <h2 id={titleId}>Asistente Lysandri</h2>
            <p>Consulta sobre programas</p>
          </div>
        </div>

        <div className="lx-chat-header-actions">
          <button
            type="button"
            onClick={handleNewConversation}
            className="lx-chat-reset"
            disabled={isLoading}
            aria-label="Nueva conversación"
            title="Iniciar una nueva conversación independiente"
          >
            <RefreshCw className="h-3 w-3" />
            <span>Nueva conversación</span>
          </button>
          {!integrado && <button
            type="button"
            onClick={onClose}
            data-dialog-close
            aria-label="Cerrar asistente"
            className="lx-overlay-close flex h-11 w-11 items-center justify-center"
          >
            <X className="h-5 w-5" />
          </button>}
        </div>
      </div>

      {/* Active Context Banner */}
      {activePlaybook && (
        <div className="lx-chat-context flex items-center justify-between border-b px-6 py-2.5 text-xs">
          <div className="flex items-center gap-2 text-cyan-300 truncate">
            <FileText className="h-3.5 w-3.5 shrink-0" />
            <span className="truncate">Contexto: {activePlaybook.title}</span>
          </div>
          {onClearContext && (
            <button
              type="button"
              onClick={onClearContext}
              className="text-[10px] text-slate-400 hover:text-white underline ml-2 shrink-0"
            >
              Quitar
            </button>
          )}
        </div>
      )}

      {/* Messages Area */}
      <div ref={panelMensajes} role="log" aria-label="Mensajes del asistente" aria-live="polite" aria-relevant="additions" aria-busy={isLoading} className="lx-chat-messages">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`lx-chat-message lx-chat-message-${msg.sender}`}
          >
            <div
              className="lx-chat-bubble"
            >
              <p className="whitespace-pre-wrap">{msg.text}</p>
              {msg.fuentes?.some(fuente => !fuente.url && fuente.seccion !== 'Web') && (
                <details className="lx-chat-sources mt-3 border-t pt-2">
                  <summary className="lx-chat-sources-toggle cursor-pointer text-xs font-semibold">
                    Fuentes consultadas
                  </summary>
                  <div className="mt-2 space-y-1.5 pl-2 border-l border-cyan-800/60">
                    {msg.fuentes.filter(fuente => !fuente.url && fuente.seccion !== 'Web').map((fuente, indice) => (
                      <div key={indice} className="text-[10px] text-slate-400">
                        <span className="font-mono text-cyan-300 font-bold">
                          {fuente.titulo || `Documento ${fuente.idDocumento}`}
                          {fuente.seccion ? ` · ${fuente.seccion}` : ''}
                          {fuente.version ? ` · Versión ${fuente.version}` : ''}
                        </span>
                        <p>{fuente.extracto}</p>
                      </div>
                    ))}
                  </div>
                </details>
              )}

              {msg.id.startsWith('error-') && lastFailedQuestion && (
                <div className="mt-2 pt-2 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={() => handleSend(lastFailedQuestion)}
                    className="inline-flex items-center gap-1 rounded-lg bg-rose-500/20 px-2.5 py-1 text-[10px] font-bold text-rose-300 hover:bg-rose-500/30 transition"
                  >
                    <RefreshCw className="h-3 w-3" />
                    <span>Reintentar</span>
                  </button>
                </div>
              )}
              <time className="lx-chat-time">{msg.timestamp}</time>
            </div>
          </div>
        ))}

        {isLoading && (
          <div className="lx-chat-loading">
            <span className="lx-thinking-robot" aria-hidden="true">
              <img src={thinkingRobot} alt="" width={36} height={36} />
            </span>
            <span>Pensando…</span>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Quick Prompts */}
      <div className="lx-chat-quick" role="group" aria-label="Consultas sugeridas">
        {samplePrompts.map(({ label, question }) => (
          <button key={label} type="button" disabled={isLoading} onClick={() => handleSend(question)}>
            {label}
          </button>
        ))}
      </div>

      {/* Input Box */}
      <div className="lx-chat-composer">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSend();
          }}
          className="flex items-center gap-2"
        >
          <input
            type="text"
            value={inputQuestion}
            onChange={(e) => setInputQuestion(e.target.value)}
            aria-label="Consulta para el asistente"
            placeholder="Escribe tu consulta…"
          />
          <button
            type="submit"
            disabled={!inputQuestion.trim() || isLoading}
            aria-label="Enviar consulta"
            className="lx-chat-send"
          >
            <ArrowUp className="h-4 w-4" aria-hidden="true" />
          </button>
        </form>
      </div>
    </div>
  );

  if (integrado) return panel;

  return (
    <div className="lx-overlay lx-chat-overlay fixed inset-0 z-50 overflow-hidden">
      <div className="lx-overlay-backdrop absolute inset-0" onClick={onClose} aria-hidden="true" />
      <div className="fixed inset-y-0 right-0 flex max-w-full pl-10">
        {panel}
      </div>
    </div>
  );
};

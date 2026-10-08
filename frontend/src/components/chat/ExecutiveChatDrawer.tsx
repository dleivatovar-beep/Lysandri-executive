import React, { useState, useRef, useEffect, useId } from 'react';
import { X, Send, FileText, LoaderCircle, ChevronDown, ChevronUp } from 'lucide-react';
import { Playbook } from '../../types';
import { storeService, RagCitation } from '../../services/storeService';
import { useDialogAccessibility } from '../../hooks/useDialogAccessibility';
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

interface ExecutiveChatDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  activePlaybook?: Playbook | null;
  onClearContext?: () => void;
}

export const ExecutiveChatDrawer: React.FC<ExecutiveChatDrawerProps> = ({
  isOpen,
  onClose,
  activePlaybook = null,
  onClearContext,
}) => {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      sender: 'ia',
      text: 'Hola. Puedo ayudarte a consultar la información disponible de los programas. ¿Qué te gustaría saber?',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    },
  ]);
  const [inputQuestion, setInputQuestion] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [expandedSources, setExpandedSources] = useState<Record<string, boolean>>({});
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const dialogRef = useDialogAccessibility<HTMLDivElement>(isOpen, onClose);

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
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  if (!isOpen) return null;

  const handleSend = async (questionText?: string) => {
    const q = (questionText || inputQuestion).trim();
    if (!q || isLoading) return;

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
        activePlaybook ? activePlaybook.programId : undefined
      );

      const iaMsg: ChatMessage = {
        id: `ia-${Date.now()}`,
        sender: 'ia',
        text: response.respuesta,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        fuentes: response.fuentes,
        tokensUsados: response.tokensUsados,
        latenciaMs: response.latenciaMs,
      };

      setMessages((prev) => [...prev, iaMsg]);
    } catch (err: any) {
      const errorMsg: ChatMessage = {
        id: `error-${Date.now()}`,
        sender: 'ia',
        text: 'No pude responder ahora. Comprueba tu conexión e inténtalo de nuevo en unos momentos.',
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setIsLoading(false);
    }
  };

  const toggleSources = (msgId: string) => {
    setExpandedSources((prev) => ({
      ...prev,
      [msgId]: !prev[msgId],
    }));
  };

  const samplePrompts = [
    '¿Cuál es la duración y carga horaria semanal?',
    '¿Qué prerrequisitos técnicos se solicitan?',
    '¿Cómo se evalúa y qué certificación otorga?',
  ];

  return (
    <div className="lx-overlay lx-chat-overlay fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        className="lx-overlay-backdrop absolute inset-0"
        onClick={onClose}
        aria-hidden="true"
      />

      <div className="fixed inset-y-0 right-0 flex max-w-full pl-10">
        <div
          ref={dialogRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          tabIndex={-1}
          className="lx-drawer lx-chat flex w-screen max-w-lg flex-col"
        >
          {/* Header */}
          <div className="lx-overlay-header flex items-center justify-between px-6 py-4">
            <div className="flex items-center gap-3">
              <div>
                <h2 id={titleId}>Consulta sobre programas</h2>
                <p>Asistente de Lysandri Executive</p>
              </div>
            </div>

            <button
              type="button"
              onClick={onClose}
              data-dialog-close
              aria-label="Cerrar asistente"
              className="lx-overlay-close flex h-11 w-11 items-center justify-center"
            >
              <X className="h-5 w-5" />
            </button>
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
          <div role="log" aria-label="Mensajes del asistente" aria-live="polite" aria-relevant="additions" aria-busy={isLoading} className="flex-1 overflow-y-auto p-6 space-y-4">
            {messages.map((msg) => (
              <div
                key={msg.id}
                className={`flex flex-col ${msg.sender === 'user' ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`max-w-[85%] rounded-2xl p-4 text-xs leading-relaxed ${
                    msg.sender === 'user'
                      ? 'bg-gradient-to-r from-cyan-600 to-indigo-600 text-white rounded-tr-none'
                      : 'border border-slate-800 bg-slate-950/80 text-slate-200 rounded-tl-none'
                  }`}
                >
                  <p className="whitespace-pre-wrap">{msg.text}</p>

                  {/* Sources Grounding */}
                  {msg.fuentes && msg.fuentes.length > 0 && (
                    <div className="mt-3 border-t border-slate-800 pt-2">
                      <button
                        type="button"
                        onClick={() => toggleSources(msg.id)}
                        className="flex items-center gap-1.5 text-[10px] font-semibold text-cyan-400 hover:text-cyan-300"
                      >
                        <FileText className="h-3 w-3" />
                        <span>
                          {msg.fuentes.length} fragmento{msg.fuentes.length !== 1 ? 's' : ''} citado{msg.fuentes.length !== 1 ? 's' : ''} del Sílabo
                        </span>
                        {expandedSources[msg.id] ? (
                          <ChevronUp className="h-3 w-3" />
                        ) : (
                          <ChevronDown className="h-3 w-3" />
                        )}
                      </button>

                      {expandedSources[msg.id] && (
                        <div className="mt-2 space-y-1.5 pl-2 border-l border-cyan-800/60">
                          {msg.fuentes.map((f, fIdx) => (
                            <div key={fIdx} className="text-[10px] text-slate-400">
                              <span className="font-mono text-cyan-300 font-bold">
                                [Pág. {f.pagina || 'S/N'}]:
                              </span>{' '}
                              "{f.extracto}"
                              <span className="text-slate-500 ml-1">
                                ({(f.similitud * 100).toFixed(0)}% relevancia)
                              </span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  <div className="mt-1 flex items-center justify-end gap-2 text-[9px] text-slate-500">
                    {msg.latenciaMs && <span>{msg.latenciaMs}ms</span>}
                    <span>{msg.timestamp}</span>
                  </div>
                </div>
              </div>
            ))}

            {isLoading && (
              <div className="flex items-center gap-2 rounded-2xl border border-slate-800 bg-slate-950/80 p-3 text-xs text-cyan-400 w-fit">
                <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                <span>Analizando sílabos con pgvector...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Prompts */}
          <div className="px-6 py-2 border-t border-slate-800 bg-slate-950/40">
            <div className="flex flex-wrap gap-1.5">
              {samplePrompts.map((prompt, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => handleSend(prompt)}
                  className="min-h-11 rounded-lg border border-slate-800 bg-slate-900 px-2.5 py-1 text-xs text-slate-400 hover:border-cyan-500/40 hover:text-cyan-300 transition-colors"
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>

          {/* Input Box */}
          <div className="border-t border-slate-800 bg-slate-950/90 p-4">
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
                placeholder="Escribe tu consulta sobre los programas o sílabos..."
                className="min-w-0 flex-1 rounded-xl border border-slate-800 bg-slate-900 px-4 py-2.5 text-sm text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
              />
              <button
                type="submit"
                disabled={!inputQuestion.trim() || isLoading}
                aria-label="Enviar consulta"
                className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-500 text-slate-950 hover:bg-cyan-400 disabled:opacity-40 transition-colors"
              >
                <Send className="h-4 w-4" />
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
};

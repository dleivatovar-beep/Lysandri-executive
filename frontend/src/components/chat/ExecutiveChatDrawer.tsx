import React, { useState, useRef, useEffect } from 'react';
import { X, Send, Bot, FileText, LoaderCircle, ChevronDown, ChevronUp } from 'lucide-react';
import { Playbook } from '../../types';
import { storeService, RagCitation } from '../../services/storeService';

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
      text: 'Estimado ejecutivo, soy el Asistente Inteligente de Lysandri Executive. ¿En qué puedo orientarle hoy sobre nuestras mallas curriculares, requisitos directivos o acreditación en Moodle LMS?',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    },
  ]);
  const [inputQuestion, setInputQuestion] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [expandedSources, setExpandedSources] = useState<Record<string, boolean>>({});
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (activePlaybook) {
      setMessages((prev) => [
        ...prev,
        {
          id: `context-${Date.now()}`,
          sender: 'ia',
          text: `He fijado el contexto en el programa ejecutivo: "${activePlaybook.title}". ¿Tiene alguna duda específica sobre el contenido de su sílabo o perfil de egreso?`,
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
        text: 'Lo sentimos, el motor RAG está procesando alta demanda en este momento. Por favor, reintente en unos segundos.',
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
    <div className="fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-slate-950/70 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />

      <div className="fixed inset-y-0 right-0 flex max-w-full pl-10">
        <div className="w-screen max-w-lg border-l border-cyan-500/20 bg-slate-900 text-slate-100 shadow-2xl flex flex-col">
          {/* Header */}
          <div className="flex items-center justify-between border-b border-slate-800 bg-slate-950/80 px-6 py-4">
            <div className="flex items-center gap-3">
              <div className="rounded-xl bg-gradient-to-tr from-cyan-500 to-indigo-600 p-2.5 text-white shadow-md shadow-cyan-500/20">
                <Bot className="h-5 w-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-base font-bold text-white">Asistente Ejecutivo RAG</h2>
                  <span className="flex h-2 w-2 relative">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-cyan-500"></span>
                  </span>
                </div>
                <p className="text-[11px] text-slate-400">pgvector + Embeddings 1536d</p>
              </div>
            </div>

            <button
              type="button"
              onClick={onClose}
              className="rounded-lg p-2 text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* Active Context Banner */}
          {activePlaybook && (
            <div className="flex items-center justify-between border-b border-slate-800/80 bg-cyan-950/40 px-6 py-2.5 text-xs">
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
          <div className="flex-1 overflow-y-auto p-6 space-y-4">
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
                  className="rounded-lg border border-slate-800 bg-slate-900 px-2.5 py-1 text-[10px] text-slate-400 hover:border-cyan-500/40 hover:text-cyan-300 transition-colors"
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
                placeholder="Escribe tu consulta sobre los programas o sílabos..."
                className="flex-1 rounded-xl border border-slate-800 bg-slate-900 px-4 py-2.5 text-xs text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
              />
              <button
                type="submit"
                disabled={!inputQuestion.trim() || isLoading}
                className="rounded-xl bg-cyan-500 p-2.5 text-slate-950 hover:bg-cyan-400 disabled:opacity-40 transition-colors"
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

// src/components/chat/ChatContainer.tsx
import React, { useEffect, useRef } from 'react';
import { RefreshCw, Sparkles, Database, Trash2, Cpu } from 'lucide-react';
import { ChatMessage } from '../../types';
import { MessageBubble } from './MessageBubble';
import { ChatInput } from './ChatInput';

interface ChatContainerProps {
  messages: ChatMessage[];
  onSendMessage: (message: string) => void;
  isLoading: boolean;
  onClearHistory: () => void;
}

export const ChatContainer: React.FC<ChatContainerProps> = ({
  messages,
  onSendMessage,
  isLoading,
  onClearHistory,
}) => {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isLoading]);

  return (
    <div className="flex flex-col h-[calc(100vh-5rem)] w-full max-w-5xl mx-auto glass-panel rounded-2xl border border-slate-800/90 shadow-executive overflow-hidden">
      {/* Console Header Bar */}
      <div className="flex items-center justify-between px-5 py-3 bg-slate-950/90 border-b border-slate-800">
        <div className="flex items-center space-x-3">
          <div className="p-2 rounded-lg bg-slate-900 border border-cyan-500/30 text-cyan-400">
            <Cpu className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-xs font-bold text-slate-100 flex items-center space-x-2">
              <span>Consola IA RAG Ejecutiva</span>
              <span className="text-[9px] font-mono px-2 py-0.5 rounded bg-cyan-500/10 text-cyan-300 border border-cyan-500/20 uppercase">
                Lysandri-Enterprise-RAG
              </span>
            </h2>
            <p className="text-[10px] text-slate-400 flex items-center space-x-2 font-mono">
              <Database className="w-3 h-3 text-indigo-400" />
              <span>Conectado a Vector Store & Archivos de Arquitectura C-Suite</span>
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={onClearHistory}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-slate-400 hover:text-rose-400 hover:border-rose-500/30 text-xs font-mono transition-all"
            title="Reiniciar Sesión RAG"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Limpiar Sesión</span>
          </button>
        </div>
      </div>

      {/* Messages Scrollable Container */}
      <div className="flex-1 overflow-y-auto p-4 md:p-6 space-y-4">
        {messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-center p-8 space-y-4">
            <div className="w-14 h-14 rounded-2xl bg-slate-900 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shadow-glow-cyan">
              <Sparkles className="w-7 h-7" />
            </div>
            <div className="max-w-md space-y-1.5">
              <h3 className="text-sm font-bold text-slate-200">Asistente IA para la Alta Directiva</h3>
              <p className="text-xs text-slate-400">
                Consulte sobre decisiones arquitectónicas, análisis de retorno de inversión (ROI) en FinOps, o evalúe playbooks de seguridad Zero-Trust.
              </p>
            </div>
          </div>
        ) : (
          messages.map((msg) => (
            <MessageBubble key={msg.id} message={msg} />
          ))
        )}

        {/* Loading Indicator */}
        {isLoading && (
          <div className="flex items-center space-x-3 p-3.5 glass-panel rounded-xl w-fit border border-cyan-500/30 text-cyan-300 text-xs font-mono">
            <RefreshCw className="w-3.5 h-3.5 animate-spin text-cyan-400" />
            <span>Sintetizando respuesta RAG con contexto documental...</span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Console Input Footer */}
      <div className="p-3.5 bg-slate-950/95 border-t border-slate-800">
        <ChatInput onSendMessage={onSendMessage} isLoading={isLoading} />
      </div>
    </div>
  );
};

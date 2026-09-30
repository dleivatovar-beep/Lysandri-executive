// src/components/chat/MessageBubble.tsx
import React, { useState } from 'react';
import { 
  Bot, 
  User, 
  FileText, 
  Copy, 
  Check, 
  ShieldCheck 
} from 'lucide-react';
import { ChatMessage } from '../../types';
import { FormattedText } from './FormattedText';

interface MessageBubbleProps {
  message: ChatMessage;
  userName?: string;
}

export const MessageBubble: React.FC<MessageBubbleProps> = ({ message, userName }) => {
  const [copied, setCopied] = useState<boolean>(false);

  const isUser = message.sender === 'USER';
  const fuentesList = message.fuentes && message.fuentes.length > 0 
    ? message.fuentes 
    : message.sources || [];

  const handleCopy = () => {
    navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={`flex w-full space-x-3.5 md:space-x-4 py-2 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {/* Assistant Avatar */}
      {!isUser && (
        <div className={`flex-shrink-0 w-8 h-8 rounded-lg bg-slate-900 border ${
          message.isError ? 'border-rose-500/40 text-rose-400' : 'border-cyan-500/30 text-cyan-400'
        } flex items-center justify-center shadow-glow-cyan mt-1`}>
          <Bot className="w-4 h-4" />
        </div>
      )}

      {/* Message Content Container */}
      <div className={`relative max-w-2xl flex flex-col space-y-1.5 ${isUser ? 'items-end' : 'items-start'}`}>
        {/* Header Metadata */}
        <div className="flex items-center space-x-2 text-[10px] text-slate-400 font-mono px-1">
          <span className="font-semibold text-slate-300">
            {isUser ? (userName || 'Tú (Ejecutivo)') : 'Lysandri Executive'}
          </span>
          <span>•</span>
          <span>{message.timestamp}</span>
        </div>

        {/* Message Bubble Box */}
        <div 
          className={`p-4 rounded-xl text-xs md:text-sm leading-relaxed ${
            isUser
              ? 'bg-gradient-to-r from-cyan-600/20 via-indigo-600/20 to-slate-900 text-slate-100 border border-cyan-500/30 shadow-md rounded-tr-none'
              : message.isError
                ? 'bg-rose-950/20 text-rose-200 border border-rose-500/40 shadow-executive rounded-tl-none'
                : 'bg-slate-900/90 text-slate-200 border border-slate-800/90 shadow-executive rounded-tl-none'
          }`}
        >
          {isUser ? (
            <div className="text-slate-100 font-medium whitespace-pre-wrap">
              {message.content}
            </div>
          ) : (
            <FormattedText content={message.content} />
          )}

          {/* Badges de Fuentes y Referencias Técnicas al pie de la burbuja */}
          {!isUser && fuentesList.length > 0 && (
            <div className="mt-3 pt-2.5 border-t border-slate-800/80 flex flex-col space-y-1.5">
              <span className="text-[10px] font-mono text-slate-400 flex items-center gap-1.5">
                <FileText className="w-3 h-3 text-cyan-400" />
                <span>Fuentes y referencias técnicas:</span>
              </span>
              <div className="flex flex-wrap gap-1.5 pt-0.5">
                {fuentesList.map((fuente, idx) => (
                  <span
                    key={idx}
                    className="text-xs bg-slate-800 text-slate-300 rounded px-2 py-0.5 border border-slate-700/60 inline-flex items-center gap-1.5 shadow-sm hover:border-cyan-500/40 transition-colors"
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-cyan-400"></span>
                    {fuente}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Footer Status & Copy Bar for Assistant */}
          {!isUser && (
            <div className="mt-3 pt-2.5 border-t border-slate-800/80 flex items-center justify-between text-[10px] text-slate-500 font-mono">
              <span className="flex items-center space-x-1.5 text-emerald-400/90">
                <ShieldCheck className="w-3 h-3" />
                <span>Respuesta Verificada</span>
              </span>

              <button
                onClick={handleCopy}
                className="flex items-center space-x-1 px-2 py-0.5 rounded bg-slate-850 hover:bg-slate-800 text-slate-400 hover:text-cyan-300 border border-slate-800 transition-colors"
                title="Copiar respuesta"
              >
                {copied ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                <span>{copied ? 'Copiado' : 'Copiar'}</span>
              </button>
            </div>
          )}
        </div>
      </div>

      {/* User Avatar */}
      {isUser && (
        <div className="flex-shrink-0 w-8 h-8 rounded-lg bg-slate-800 border border-slate-700 text-slate-300 flex items-center justify-center mt-1">
          <User className="w-4 h-4" />
        </div>
      )}
    </div>
  );
};

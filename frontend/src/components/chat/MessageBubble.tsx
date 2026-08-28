// src/components/chat/MessageBubble.tsx
import React, { useState } from 'react';
import { 
  Bot, 
  User, 
  FileText, 
  ChevronDown, 
  ChevronUp, 
  Copy, 
  Check,
  ShieldCheck,
  ExternalLink,
  Sparkles
} from 'lucide-react';
import { ChatMessage } from '../../types';
import { FormattedText } from './FormattedText';

interface MessageBubbleProps {
  message: ChatMessage;
}

export const MessageBubble: React.FC<MessageBubbleProps> = ({ message }) => {
  const [sourcesOpen, setSourcesOpen] = useState<boolean>(true);
  const [copied, setCopied] = useState<boolean>(false);

  const isUser = message.sender === 'USER';

  const handleCopy = () => {
    navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={`flex w-full space-x-3.5 md:space-x-4 py-2 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {/* Assistant Avatar */}
      {!isUser && (
        <div className="flex-shrink-0 w-8 h-8 rounded-lg bg-slate-900 border border-cyan-500/30 text-cyan-400 flex items-center justify-center shadow-glow-cyan mt-1">
          <Bot className="w-4 h-4" />
        </div>
      )}

      {/* Message Content Container */}
      <div className={`relative max-w-2xl flex flex-col space-y-1.5 ${isUser ? 'items-end' : 'items-start'}`}>
        {/* Header Metadata */}
        <div className="flex items-center space-x-2 text-[10px] text-slate-400 font-mono px-1">
          <span className="font-semibold text-slate-300">
            {isUser ? 'Alexander Vance (CTO)' : 'Lysandri RAG Assistant'}
          </span>
          <span>•</span>
          <span>{message.timestamp}</span>
        </div>

        {/* Message Bubble Box */}
        <div 
          className={`p-4 rounded-xl text-xs md:text-sm leading-relaxed ${
            isUser
              ? 'bg-gradient-to-r from-cyan-600/20 via-indigo-600/20 to-slate-900 text-slate-100 border border-cyan-500/30 shadow-md rounded-tr-none'
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

          {/* Footer Status & Copy Bar for Assistant */}
          {!isUser && (
            <div className="mt-3 pt-2.5 border-t border-slate-800/80 flex items-center justify-between text-[10px] text-slate-500 font-mono">
              <span className="flex items-center space-x-1.5 text-emerald-400/90">
                <ShieldCheck className="w-3 h-3" />
                <span>RAG Verified Grounding</span>
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

        {/* RAG Sources Section */}
        {!isUser && message.sources && message.sources.length > 0 && (
          <div className="w-full mt-1 bg-slate-900/95 border border-slate-800 rounded-xl overflow-hidden text-xs shadow-sm">
            <button
              onClick={() => setSourcesOpen(!sourcesOpen)}
              className="w-full px-3 py-2 bg-slate-950/80 flex items-center justify-between text-[11px] font-mono text-slate-300 hover:text-cyan-300 transition-colors"
            >
              <div className="flex items-center space-x-2">
                <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
                <span className="font-semibold">{message.sources.length} Fuentes RAG Indexadas</span>
              </div>
              {sourcesOpen ? <ChevronUp className="w-3.5 h-3.5 text-slate-400" /> : <ChevronDown className="w-3.5 h-3.5 text-slate-400" />}
            </button>

            {sourcesOpen && (
              <div className="p-2 space-y-1 bg-slate-950/40 border-t border-slate-800/80">
                {message.sources.map((src, index) => (
                  <div 
                    key={index}
                    className="flex items-center justify-between px-2.5 py-1.5 rounded-lg bg-slate-900/90 border border-slate-800/70 text-slate-300 text-[11px] font-mono hover:border-cyan-500/40 transition-colors group"
                  >
                    <div className="flex items-center space-x-2 truncate">
                      <FileText className="w-3.5 h-3.5 text-cyan-400 flex-shrink-0" />
                      <span className="truncate group-hover:text-cyan-200">{src}</span>
                    </div>
                    <div className="flex items-center space-x-1.5 flex-shrink-0 ml-2">
                      <span className="text-[9px] text-slate-400 bg-slate-800 px-1.5 py-0.5 rounded border border-slate-700/50">
                        Document
                      </span>
                      <ExternalLink className="w-3 h-3 text-slate-500 group-hover:text-cyan-400" />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
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

// src/components/chat/ChatInput.tsx
import React, { useState, useRef, KeyboardEvent } from 'react';
import { Send, Sparkles, CornerDownLeft, Paperclip } from 'lucide-react';

interface ChatInputProps {
  onSendMessage: (message: string) => void;
  isLoading: boolean;
}

const PROMPT_SUGGESTIONS = [
  "¿Cuál es el ROI de migrar a arquitectura orientada a eventos con Kafka?",
  "Revisa nuestro modelo de costos FinOps para AWS EKS",
  "¿Qué guardrails de seguridad OPA aplican a nuestro gateway LLM?"
];

export const ChatInput: React.FC<ChatInputProps> = ({ onSendMessage, isLoading }) => {
  const [text, setText] = useState<string>('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = () => {
    if (!text.trim() || isLoading) return;
    onSendMessage(text.trim());
    setText('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setText(e.target.value);
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 160)}px`;
    }
  };

  return (
    <div className="w-full space-y-2.5">
      {/* Quick Prompt Suggestions Chips */}
      <div className="hidden sm:flex items-center space-x-2 overflow-x-auto pb-1 scrollbar-none">
        <span className="text-[10px] font-mono text-slate-500 flex items-center space-x-1 whitespace-nowrap">
          <Sparkles className="w-3 h-3 text-cyan-400" />
          <span>Consultas Frecuentes:</span>
        </span>
        {PROMPT_SUGGESTIONS.map((prompt, idx) => (
          <button
            key={idx}
            onClick={() => {
              setText(prompt);
              if (textareaRef.current) textareaRef.current.focus();
            }}
            className="text-[10px] font-medium px-2.5 py-1 rounded-lg bg-slate-900 border border-slate-800 text-slate-400 hover:text-cyan-300 hover:border-cyan-500/30 whitespace-nowrap transition-all"
          >
            {prompt}
          </button>
        ))}
      </div>

      {/* Input Box */}
      <div className="relative flex items-end w-full p-2 rounded-xl bg-slate-900 border border-slate-800 focus-within:border-cyan-500/50 focus-within:ring-1 focus-within:ring-cyan-500/20 transition-all">
        {/* Attachment icon */}
        <button 
          className="p-2 rounded-lg text-slate-400 hover:text-cyan-400 hover:bg-slate-800 transition-colors"
          title="Adjuntar documento o arquitectura (.pdf, .json, .tf)"
        >
          <Paperclip className="w-4 h-4" />
        </button>

        {/* Textarea */}
        <textarea
          ref={textareaRef}
          value={text}
          onChange={handleInput}
          onKeyDown={handleKeyDown}
          placeholder="Consulte al Asistente IA RAG sobre arquitectura, costos FinOps o activos tecnológicos..."
          rows={1}
          disabled={isLoading}
          className="w-full mx-2 py-2 bg-transparent text-slate-100 placeholder-slate-500 text-xs md:text-sm focus:outline-none resize-none max-h-36 font-sans leading-relaxed"
        />

        {/* Send Button & Keyboard Hint */}
        <div className="flex items-center space-x-2">
          <span className="hidden md:flex items-center space-x-1 text-[9px] font-mono text-slate-500 mr-1">
            <span>Enter</span>
            <CornerDownLeft className="w-2.5 h-2.5" />
          </span>

          <button
            onClick={handleSend}
            disabled={!text.trim() || isLoading}
            className={`p-2.5 rounded-lg transition-all duration-200 flex items-center justify-center ${
              text.trim() && !isLoading
                ? 'bg-gradient-to-r from-cyan-500 to-indigo-600 text-slate-950 shadow-glow-cyan font-bold active:scale-95'
                : 'bg-slate-800 text-slate-600 cursor-not-allowed'
            }`}
            title="Enviar mensaje"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};

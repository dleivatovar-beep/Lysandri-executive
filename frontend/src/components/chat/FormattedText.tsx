// src/components/chat/FormattedText.tsx
import React from 'react';

interface FormattedTextProps {
  content: string;
}

export const FormattedText: React.FC<FormattedTextProps> = ({ content }) => {
  // Simple, elegant parser for executive markdown rendering
  const lines = content.split('\n');

  return (
    <div className="space-y-3 font-sans text-xs md:text-sm text-slate-200 leading-relaxed">
      {lines.map((line, idx) => {
        const trimmed = line.trim();

        if (!trimmed) {
          return <div key={idx} className="h-1.5" />;
        }

        // Headings (### or ##)
        if (trimmed.startsWith('### ') || trimmed.startsWith('## ')) {
          const headingText = trimmed.replace(/^#+\s*/, '');
          return (
            <h4 
              key={idx} 
              className="text-xs font-mono font-bold tracking-wider uppercase text-cyan-400 pt-2 pb-1 border-b border-slate-800/80 flex items-center space-x-2"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400" />
              <span>{renderInlineFormatting(headingText)}</span>
            </h4>
          );
        }

        // Bullet lists (* or -)
        if (trimmed.startsWith('* ') || trimmed.startsWith('- ')) {
          const listText = trimmed.replace(/^[*|-]\s*/, '');
          return (
            <div key={idx} className="flex items-start space-x-2.5 pl-2 my-1">
              <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 mt-1.5 flex-shrink-0" />
              <div className="text-slate-300">
                {renderInlineFormatting(listText)}
              </div>
            </div>
          );
        }

        // Regular paragraph
        return (
          <p key={idx} className="text-slate-300">
            {renderInlineFormatting(trimmed)}
          </p>
        );
      })}
    </div>
  );
};

// Inline formatter for **bold**, `code`, and metric highlights
function renderInlineFormatting(text: string): React.ReactNode {
  // Regex splitting by bold (**text**) and code (`code`)
  const parts = text.split(/(\*\*.*?\*\*|`.*?`)/g);

  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      const boldText = part.slice(2, -2);
      return (
        <strong key={i} className="font-semibold text-slate-100 bg-slate-800/40 px-1 rounded text-cyan-300 border border-slate-700/30">
          {boldText}
        </strong>
      );
    }

    if (part.startsWith('`') && part.endsWith('`')) {
      const codeText = part.slice(1, -1);
      return (
        <code key={i} className="font-mono text-[11px] bg-slate-950 text-indigo-300 px-1.5 py-0.5 rounded border border-indigo-500/20">
          {codeText}
        </code>
      );
    }

    return part;
  });
}

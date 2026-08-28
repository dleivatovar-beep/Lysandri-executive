// src/pages/ChatView.tsx
import React from 'react';
import { ChatContainer } from '../components/chat/ChatContainer';
import { ChatMessage } from '../types';

interface ChatViewProps {
  messages: ChatMessage[];
  onSendMessage: (message: string) => void;
  isLoading: boolean;
  onClearHistory: () => void;
}

export const ChatView: React.FC<ChatViewProps> = ({
  messages,
  onSendMessage,
  isLoading,
  onClearHistory,
}) => {
  return (
    <div className="w-full flex flex-col justify-center">
      <ChatContainer
        messages={messages}
        onSendMessage={onSendMessage}
        isLoading={isLoading}
        onClearHistory={onClearHistory}
      />
    </div>
  );
};

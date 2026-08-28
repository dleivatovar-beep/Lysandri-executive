// src/App.tsx
import React, { useState, useEffect } from 'react';
import { ActiveView, Playbook, Category, ChatMessage } from './types';
import { INITIAL_USER } from './services/mockData';
import { PlaybookService, ChatService } from './services/api';
import { Navbar } from './components/layout/Navbar';
import { Sidebar } from './components/layout/Sidebar';
import { MarketplaceView } from './pages/MarketplaceView';
import { ChatView } from './pages/ChatView';
import { CheckCircle2, X } from 'lucide-react';

export const App: React.FC = () => {
  const [activeView, setActiveView] = useState<ActiveView>('MARKETPLACE');
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState<boolean>(false);
  
  // Data state
  const [playbooks, setPlaybooks] = useState<Playbook[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isChatLoading, setIsChatLoading] = useState<boolean>(false);

  // Notification state
  const [purchasedPlaybook, setPurchasedPlaybook] = useState<Playbook | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      const pbs = await PlaybookService.getAll();
      const cats = await PlaybookService.getCategories();
      const msgs = await ChatService.getMessages('session-executive-01');
      
      setPlaybooks(pbs);
      setCategories(cats);
      setMessages(msgs);
    };

    fetchData();
  }, []);

  const handleSendMessage = async (content: string) => {
    const userMsg: ChatMessage = {
      id: `msg-user-${Date.now()}`,
      sessionId: 'session-executive-01',
      sender: 'USER',
      content,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    setMessages((prev) => [...prev, userMsg]);
    setIsChatLoading(true);

    try {
      const aiResponse = await ChatService.sendMessage(content, 'session-executive-01');
      setMessages((prev) => [...prev, aiResponse]);
    } catch (error) {
      console.error('Error sending chat message:', error);
    } finally {
      setIsChatLoading(false);
    }
  };

  const handleClearChatHistory = () => {
    setMessages([]);
  };

  const handleSelectPlaybook = (playbook: Playbook) => {
    setPurchasedPlaybook(playbook);
    setTimeout(() => {
      setPurchasedPlaybook(null);
    }, 4000);
  };

  return (
    <div className="min-h-screen bg-[#07090e] text-slate-100 flex flex-col font-sans">
      {/* Top Navbar */}
      <Navbar 
        user={INITIAL_USER} 
        onOpenSearch={() => setActiveView('MARKETPLACE')}
      />

      {/* Main Workspace Layout */}
      <div className="flex flex-1 overflow-hidden">
        {/* Collapsible Sidebar */}
        <Sidebar
          activeView={activeView}
          setActiveView={setActiveView}
          isCollapsed={isSidebarCollapsed}
          setIsCollapsed={setIsSidebarCollapsed}
        />

        {/* Content Area */}
        <main className="flex-1 overflow-y-auto p-4 md:p-6 lg:p-8">
          {activeView === 'MARKETPLACE' ? (
            <MarketplaceView
              playbooks={playbooks}
              categories={categories}
              onSelectPlaybook={handleSelectPlaybook}
            />
          ) : (
            <ChatView
              messages={messages}
              onSendMessage={handleSendMessage}
              isLoading={isChatLoading}
              onClearHistory={handleClearChatHistory}
            />
          )}
        </main>
      </div>

      {/* Acquisition Confirmation Toast */}
      {purchasedPlaybook && (
        <div className="fixed bottom-6 right-6 z-50 flex items-center space-x-3 p-4 rounded-xl glass-panel border border-cyan-500/40 shadow-glow-cyan text-xs font-mono text-slate-100 animate-bounce">
          <CheckCircle2 className="w-5 h-5 text-cyan-400" />
          <div>
            <span className="font-bold text-cyan-300 block">¡Activo Tecnológico Adquirido!</span>
            <span className="text-slate-400">{purchasedPlaybook.title}</span>
          </div>
          <button 
            onClick={() => setPurchasedPlaybook(null)}
            className="p-1 rounded text-slate-400 hover:text-slate-200"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}
    </div>
  );
};

export default App;

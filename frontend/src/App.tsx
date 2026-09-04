// src/App.tsx
import React, { useEffect, useState } from 'react';
import { ActiveView, Playbook, Category, ChatMessage } from './types';
import { INITIAL_USER } from './services/mockData';
import { PlaybookService, ChatService } from './services/api';
import { Navbar } from './components/layout/Navbar';
import { Sidebar } from './components/layout/Sidebar';
import { Footer } from './components/layout/Footer';
import { MarketplaceView } from './pages/MarketplaceView';
import { ChatView } from './pages/ChatView';
import { LoginView } from './components/auth/LoginView';
import { CheckCircle2, X, Bot, BookOpen, ClipboardList } from 'lucide-react';

export const App: React.FC = () => {
  const [activeView, setActiveView] =
    useState<ActiveView>('MARKETPLACE');
  const [isSidebarCollapsed, setIsSidebarCollapsed] =
    useState<boolean>(false);
  const [theme, setTheme] = useState<'dark' | 'light'>('dark');
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(false);

  const [playbooks, setPlaybooks] = useState<Playbook[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isChatLoading, setIsChatLoading] = useState<boolean>(false);
  const [selectedPlaybook, setSelectedPlaybook] =
    useState<Playbook | null>(null);

  useEffect(() => {
    if (theme === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }, [theme]);

  useEffect(() => {
    const fetchData = async () => {
      const playbooksData = await PlaybookService.getAll();
      const categoriesData = await PlaybookService.getCategories();
      const messagesData = await ChatService.getMessages(
        'session-executive-01'
      );

      setPlaybooks(playbooksData);
      setCategories(categoriesData);
      setMessages(messagesData);
    };

    fetchData();
  }, []);

  const handleSendMessage = async (content: string) => {
    const userMessage: ChatMessage = {
      id: `msg-user-${Date.now()}`,
      sessionId: 'session-executive-01',
      sender: 'USER',
      content,
      timestamp: new Date().toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
      }),
    };

    setMessages((previous) => [...previous, userMessage]);
    setIsChatLoading(true);

    try {
      const aiResponse = await ChatService.sendMessage(
        content,
        'session-executive-01'
      );

      setMessages((previous) => [...previous, aiResponse]);
    } catch (error) {
      console.error('Error:', error);
    } finally {
      setIsChatLoading(false);
    }
  };

  const handleSelectCourse = (playbook: Playbook) => {
    if (!isLoggedIn) {
      setActiveView('LOGIN');
      return;
    }

    setSelectedPlaybook(playbook);

    setTimeout(() => {
      setSelectedPlaybook(null);
    }, 4000);
  };

  const handleLoginSuccess = () => {
    setIsLoggedIn(true);
    setActiveView('MARKETPLACE');
  };

  const handleLogout = () => {
    setIsLoggedIn(false);
    setActiveView('MARKETPLACE');
  };

  if (activeView === 'LOGIN') {
    return (
      <div
        className={`min-h-screen w-full font-sans transition-colors duration-500 ${
          theme === 'dark' ? 'dark' : ''
        }`}
      >
        <LoginView
          onLoginSuccess={handleLoginSuccess}
          onBack={() => setActiveView('MARKETPLACE')}
        />
      </div>
    );
  }

  return (
    <div className="relative flex min-h-screen flex-col overflow-hidden bg-slate-50 font-sans text-slate-900 transition-colors duration-500 dark:bg-[#07090e] dark:text-slate-100">
      <Navbar
        user={INITIAL_USER}
        theme={theme}
        onToggleTheme={() =>
          setTheme(theme === 'dark' ? 'light' : 'dark')
        }
        isLoggedIn={isLoggedIn}
        onLoginClick={() => setActiveView('LOGIN')}
        onLogoutClick={handleLogout}
      />

      <div className="flex flex-1 overflow-hidden">
        {isLoggedIn && (
          <Sidebar
            activeView={activeView}
            setActiveView={setActiveView}
            isCollapsed={isSidebarCollapsed}
            setIsCollapsed={setIsSidebarCollapsed}
          />
        )}

        <main className="relative flex-1 overflow-y-auto p-4 md:p-6 lg:p-8">
          {activeView === 'MARKETPLACE' && (
            <MarketplaceView
              playbooks={playbooks}
              categories={categories}
              onSelectPlaybook={handleSelectCourse}
            />
          )}

          {activeView === 'CHAT' && (
            <ChatView
              messages={messages}
              onSendMessage={handleSendMessage}
              isLoading={isChatLoading}
              onClearHistory={() => setMessages([])}
            />
          )}

          {activeView === 'LIBROS' && (
            <div className="flex min-h-[60vh] h-full flex-col items-center justify-center space-y-4">
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800/80 dark:bg-slate-900/50 dark:shadow-inner">
                <BookOpen className="h-12 w-12 text-slate-400 dark:text-cyan-500/40" />
              </div>

              <h2 className="text-xl font-bold text-slate-800 dark:text-slate-200">
                Sección de Libros
              </h2>

              <p className="max-w-md text-center text-sm text-slate-500">
                Esta sección se encuentra en desarrollo. Los libros y manuales
                se agregarán próximamente.
              </p>
            </div>
          )}

          {activeView === 'TAREAS' && (
            <div className="flex min-h-[60vh] h-full flex-col items-center justify-center space-y-4">
              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800/80 dark:bg-slate-900/50 dark:shadow-inner">
                <ClipboardList className="h-12 w-12 text-slate-400 dark:text-cyan-500/40" />
              </div>

              <h2 className="text-xl font-bold text-slate-800 dark:text-slate-200">
                Sección de Tareas
              </h2>

              <p className="max-w-md text-center text-sm text-slate-500">
                Esta sección se encuentra en desarrollo. El gestor de tareas se
                activará en la próxima actualización.
              </p>
            </div>
          )}

          <Footer />
        </main>
      </div>

      <button
        onClick={() => setActiveView('CHAT')}
        title="Consultar Asistente IA"
        className="group fixed bottom-6 right-6 z-40 flex items-center justify-center rounded-full bg-gradient-to-r from-indigo-600 to-indigo-500 p-4 text-white shadow-lg shadow-indigo-500/30 transition-all duration-300 hover:scale-110 hover:from-indigo-500 hover:to-indigo-400 active:scale-90"
      >
        <Bot className="h-6 w-6 transition-transform duration-300 group-hover:scale-110" />
      </button>

      {selectedPlaybook && (
        <div className="fixed bottom-24 right-6 z-50 flex items-center space-x-3 rounded-xl border border-cyan-400 bg-white p-4 text-xs font-mono text-slate-800 shadow-lg dark:border-cyan-500/40 dark:bg-[#0f141f] dark:text-slate-100 dark:shadow-glow-cyan">
          <CheckCircle2 className="h-5 w-5 text-cyan-500 dark:text-cyan-400" />

          <div>
            <span className="block font-bold text-cyan-600 dark:text-cyan-300">
              ¡Curso seleccionado!
            </span>

            <span className="text-slate-500 dark:text-slate-400">
              {selectedPlaybook.title}
            </span>
          </div>

          <button
            onClick={() => setSelectedPlaybook(null)}
            className="rounded p-1 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}
    </div>
  );
};

export default App;
// src/types/index.ts

export type TierLevel = 'ESSENTIAL' | 'ADVANCED' | 'ENTERPRISE';

export interface Playbook {
  id: string;
  title: string;
  slug: string;
  description: string;
  category: string;
  price: number;
  tier: TierLevel;
  rating: number;
  downloadsCount: number;
  tags: string[];
}

export type SenderType = 'USER' | 'ASSISTANT' | 'SYSTEM';

export interface ChatMessage {
  id: string;
  sessionId: string;
  sender: SenderType;
  content: string;
  timestamp: string;
  sources?: string[];
}

export interface Category {
  id: string;
  name: string;
  iconName: string;
  count: number;
}

export type ActiveView = 'MARKETPLACE' | 'CHAT';

export interface UserProfile {
  name: string;
  role: string;
  company: string;
  avatarUrl: string;
}

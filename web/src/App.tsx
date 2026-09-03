import { useState, useEffect, useCallback } from 'react';
import { Home as HomeIcon, Edit3, BarChart3, Settings as SettingsIcon } from 'lucide-react';
import type { HomeResponse } from './types';
import { fetchHome } from './api';
import HomeTab from './tabs/HomeTab';
import InputTab from './tabs/InputTab';
import LogTab from './tabs/LogTab';
import SettingsTab from './tabs/SettingsTab';

export default function App(): JSX.Element {
  const [currentTab, setCurrentTab] = useState<'home' | 'input' | 'log' | 'settings'>('home');
  const [home, setHome] = useState<HomeResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadHome = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchHome();
      setHome(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'ホームデータの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHome();
  }, [loadHome]);

  const handleSubmitted = useCallback(() => {
    // きろくタブから送信成功後、ホーム／じぶんログを再取得する
    // タブ遷移は行わず、InputTabに留まって気づきカードとフィードバックを操作可能にする
    loadHome();
  }, [loadHome]);

  const handleGoToLog = useCallback(() => {
    setCurrentTab('log');
  }, []);

  return (
    <div className="flex-1 flex flex-col h-full justify-between relative">
      {/* 画面スクロールエリア */}
      <div className="flex-1 overflow-y-auto p-4 pb-20">
        {currentTab === 'home' && (
          <HomeTab home={home} loading={loading} error={error} onGoToLog={handleGoToLog} />
        )}
        {currentTab === 'input' && <InputTab onSubmitted={handleSubmitted} />}
        {currentTab === 'log' && <LogTab home={home} loading={loading} error={error} />}
        {currentTab === 'settings' && <SettingsTab />}
      </div>

      {/* ボトムナビゲーションバー */}
      <div className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[440px] bg-surface/95 backdrop-blur-md border-t border-borderSubtle h-16 flex items-center justify-around px-3 z-40">
        {[
          { id: 'home', label: 'ホーム', icon: HomeIcon },
          { id: 'input', label: 'きろく', icon: Edit3 },
          { id: 'log', label: 'じぶんログ', icon: BarChart3 },
          { id: 'settings', label: '設定', icon: SettingsIcon },
        ].map((item) => {
          const Icon = item.icon;
          const isActive = currentTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setCurrentTab(item.id as 'home' | 'input' | 'log' | 'settings')}
              className={`flex flex-col items-center justify-center flex-1 py-1 transition ${
                isActive ? 'text-accent font-black scale-105' : 'text-textTertiary font-medium'
              }`}
            >
              <Icon size={20} strokeWidth={isActive ? 2.5 : 1.8} />
              <span className="text-[10px] mt-1">{item.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

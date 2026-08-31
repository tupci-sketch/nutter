import { useAuthStore } from '@/stores/authStore';
import { useUiStore } from '@/stores/uiStore';
import { LoginPage } from '@/pages/LoginPage';
import { RoomCanvas } from '@/components/room/RoomCanvas';
import { ChatBox } from '@/components/room/ChatBox';
import { Toolbar } from '@/components/layout/Toolbar';
import { NavigatorPanel } from '@/components/panels/NavigatorPanel';
import { CataloguePanel } from '@/components/panels/CataloguePanel';
import { InventoryPanel } from '@/components/panels/InventoryPanel';
import { FriendsPanel } from '@/components/panels/FriendsPanel';
import { ProfilePanel } from '@/components/panels/ProfilePanel';
import { AchievementsPanel } from '@/components/panels/AchievementsPanel';
import { GardenPanel } from '@/components/panels/GardenPanel';
import { Panel } from '@/components/panels/Panel';
import { AccessibilityPanel } from '@/components/ui/AccessibilityPanel';
import { EraSwitch } from '@/components/ui/EraSwitch';

const PANEL_MAP = {
  navigator:    NavigatorPanel,
  catalogue:    CataloguePanel,
  inventory:    InventoryPanel,
  friends:      FriendsPanel,
  profile:      ProfilePanel,
  achievements: AchievementsPanel,
  garden:       GardenPanel,
  settings:     SettingsPanel,
} as const;

/** Everything about how the hotel presents itself, in one place. */
function SettingsPanel() {
  return (
    <Panel title="Settings" width={340}>
      <EraSwitch />
      <h3 style={{ margin: '16px 0 8px', color: '#f0a040', fontSize: 13 }}>Accessibility</h3>
      <AccessibilityPanel />
    </Panel>
  );
}

export function App() {
  const authenticated = useAuthStore((s) => s.authenticated);
  const activePanel = useUiStore((s) => s.activePanel);

  if (!authenticated) return <LoginPage />;

  const PanelComponent = activePanel ? (PANEL_MAP as Record<string, React.ComponentType>)[activePanel] : null;

  return (
    <div style={styles.root}>
      <RoomCanvas />
      <ChatBox />
      {PanelComponent && <PanelComponent />}
      <Toolbar />
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  root: {
    width: '100%',
    height: '100%',
    position: 'relative',
    overflow: 'hidden',
    background: '#1a1a2e',
  },
};

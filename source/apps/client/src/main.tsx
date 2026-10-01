import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { getWsClient } from './ws/WsClient';
import { initAuthListeners, startHandoff } from './stores/authStore';
import { initMuteListeners } from './stores/muteStore';
import { initRoomListeners } from './stores/roomStore';
import { initInventoryListeners } from './stores/inventoryStore';
import { initSocialListeners } from './stores/socialStore';

const ws = getWsClient();
ws.connect();

initAuthListeners();
initRoomListeners();
initMuteListeners();
initInventoryListeners();
initSocialListeners();

// A player who signed in on the website arrives with a ticket in the address;
// spend it before anything is drawn so they never see a login box.
startHandoff();

const root = document.getElementById('root');
if (!root) throw new Error('Root element not found');

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

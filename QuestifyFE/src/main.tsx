
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './tw.css';
import { AuthProvider } from './contexts/AuthContext';

import icon192 from './assets/questify_192.png';
import apple180 from './assets/questify_192.png';
import faviconIco from './assets/favicon.ico';

function applyInitialTheme() {
  const stored = localStorage.getItem('theme'); // 'light' | 'dark'
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const theme = stored ?? (prefersDark ? 'dark' : 'light');
  const root = document.documentElement;
  if (theme === 'dark') root.classList.add('dark');
  else root.classList.remove('dark');
}
applyInitialTheme();

function setFavicons() {
  const head = document.head;

  head.querySelectorAll<HTMLLinkElement>('link[rel="icon"]').forEach((l) => l.remove());

  if (faviconIco) {
    const ico = document.createElement('link');
    ico.rel = 'icon';
    ico.href = faviconIco;
    head.appendChild(ico);
  }

  const links: Array<{ rel: string; sizes?: string; type?: string; href: string }> = [
    { rel: 'icon', type: 'image/png', sizes: '192x192', href: icon192 },
    { rel: 'apple-touch-icon', sizes: '180x180', href: apple180 },
  ];

  for (const cfg of links) {
    const l = document.createElement('link');
    l.rel = cfg.rel;
    if (cfg.type) l.type = cfg.type;
    if (cfg.sizes) l.sizes = cfg.sizes;
    l.href = cfg.href;
    head.appendChild(l);
  }
}
setFavicons();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  //<React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  //</React.StrictMode>,
);

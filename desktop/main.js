// IPTV для Linux, Windows и macOS: окно Electron с общей веб-основой из папки web/.
const { app, BrowserWindow, Menu, shell } = require('electron');
const path = require('path');

// Канал должен включаться сам при запуске, без щелчка мышью.
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 720,
    minWidth: 640,
    minHeight: 360,
    useContentSize: true,
    backgroundColor: '#000000',
    autoHideMenuBar: true,
    title: 'IPTV',
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: {
      // Плейлисты и потоки лежат на чужих серверах без заголовков CORS.
      // Окно показывает только встроенные страницы приложения, поэтому проверку можно отключить.
      webSecurity: false,
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  if (process.platform !== 'darwin') Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'app', 'index.html'));

  // F11 или двойной щелчок — на весь экран. Полный экран включает сама страница: тогда интерфейс
  // переходит из режима окна в ТВ-вид, а Esc возвращает окно.
  win.webContents.on('before-input-event', (event, input) => {
    if (input.type === 'keyDown' && input.key === 'F11') {
      win.webContents.executeJavaScript('document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen()', true).catch(() => win.setFullScreen(!win.isFullScreen()));
      event.preventDefault();
    }
  });
  // Наружу открываем только страницу загрузки на GitHub — в обычном браузере
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\/github\.com\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());

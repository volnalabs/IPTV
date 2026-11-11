/*
 * Загрузчик: решает, какую версию приложения запустить.
 *
 * В установленном пакете лежит «базовая» версия (base.js). Когда выходит новая, приложение само скачивает
 * её из релиза на GitHub и кладёт в хранилище — заново ставить пакет не нужно, а каналы, избранное и
 * настройки остаются на месте. При запуске берём то, что новее: сохранённое обновление или базовую версию.
 *
 * Если обновлённая версия не смогла запуститься, в следующий раз возвращаемся к базовой.
 * Код на ES5 — он должен работать и на телевизорах 2016–2017 годов.
 */
(function () {
  'use strict';

  // Номер оболочки: пакет с файлами, которые обновление заменить не может (плеер hls.js, окно Electron,
  // описание приложения для телевизора). Увеличивается вместе с NEEDS_SHELL в app.js, когда новой версии
  // нужно что-то из этого. Тогда старый пакет честно скажет, что его надо поставить заново.
  var SHELL = 1;
  var KEY = 'iptv_bundle', MARK = 'iptv_bundleBoot';
  var base = window.__iptvBase, stored = null, pick = base, failed = null;

  function get(key) { try { return window.localStorage.getItem(key); } catch (e) { return null; } }
  function set(key, value) { try { window.localStorage.setItem(key, value); } catch (e) { /* хранилище недоступно */ } }
  function drop(key) { try { window.localStorage.removeItem(key); } catch (e) { /* хранилище недоступно */ } }
  function newer(a, b) {
    var x = String(a).replace(/^v/, '').split('.'), y = String(b).replace(/^v/, '').split('.');
    for (var i = 0; i < Math.max(x.length, y.length); i++) {
      var p = parseInt(x[i], 10) || 0, q = parseInt(y[i], 10) || 0;
      if (p !== q) return p > q;
    }
    return false;
  }
  function usable(b) {
    return !!b && typeof b.version === 'string' && typeof b.js === 'string' && typeof b.html === 'string' && typeof b.css === 'string' &&
      !(b.shell > SHELL) && newer(b.version, base.version);
  }

  try { stored = JSON.parse(get(KEY) || 'null'); } catch (e) { stored = null; }
  failed = get(MARK);
  if (usable(stored) && failed !== stored.version) {
    pick = stored;
    // Отметка «запускаем обновлённую версию». Приложение снимает её, когда запустилось целиком.
    set(MARK, stored.version);
  } else if (get(KEY) !== null) {
    // Обновление старше установленного пакета (пакет поставили заново) или в прошлый раз не запустилось
    drop(KEY);
    drop(MARK);
  }

  window.__iptvShell = SHELL;
  window.__iptvBoot = { version: pick.version, base: base.version, updated: pick !== base, failed: pick === base && !!failed };

  function run(bundle) {
    var style = document.createElement('style');
    style.appendChild(document.createTextNode(bundle.css));
    document.head.appendChild(style);
    document.body.innerHTML = bundle.html;
    (new Function(bundle.js + '\n//# sourceURL=iptv-' + bundle.version + '.js'))();
  }

  try {
    run(pick);
  } catch (e) {
    if (pick === base) throw e;
    // Обновление сломано: убираем его и запускаем то, что лежит в пакете
    drop(KEY);
    window.location.reload();
  }
})();

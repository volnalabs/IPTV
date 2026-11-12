/*
 * Среда выполнения плагинов IPTV. Один и тот же файл работает во всех версиях приложения:
 * на Android его загружает скрытая веб-страница, на компьютерах, Samsung и LG — само приложение.
 * Описание для авторов плагинов: docs/PLUGINS.md
 */
(function (root) {
  'use strict';

  var defs = [];   // зарегистрированные плагины
  var source = -1; // номер файла, который сейчас выполняется (чтобы знать, какой плагин из какого файла)
  var host = {};   // функции приложения: toast, log, error, version, platform

  function log(text) {
    try { if (host.log) host.log(String(text)); } catch (e) { /* журнал недоступен */ }
  }

  var api = {
    apiVersion: 1,
    version: '',
    platform: 'web',
    toast: function (text) { try { if (host.toast) host.toast(String(text)); } catch (e) { /* нет экрана */ } },
    log: log,
    storage: {
      get: function (key, fallback) {
        try {
          var v = root.localStorage.getItem('plugin_' + key);
          return v === null || v === undefined ? fallback : JSON.parse(v);
        } catch (e) { return fallback; }
      },
      set: function (key, value) {
        try { root.localStorage.setItem('plugin_' + key, JSON.stringify(value)); } catch (e) { /* хранилище недоступно */ }
      }
    }
  };

  function safe(def, name, fn) {
    try { return fn(); } catch (e) {
      log('Плагин «' + def.id + '», ' + name + ': ' + e);
      // Ошибку отдаём приложению: оно покажет её в карточке плагина. Старые версии приложения её просто не слушают.
      try { if (typeof host.error === 'function') host.error(String(def.id), name, String(e), def.__source); } catch (e2) { /* приложение не слушает */ }
      return undefined;
    }
  }

  root.IPTV = {
    apiVersion: 1,
    plugin: function (def) {
      if (!def || typeof def !== 'object' || !def.id) { log('IPTV.plugin: у плагина нет поля id'); return; }
      def.__source = source;
      defs.push(def);
    }
  };

  root.__iptvPlugins = {
    setHost: function (h) {
      host = h || {};
      api.version = String(host.version || '');
      api.platform = String(host.platform || 'web');
    },
    setSource: function (index) { source = index; },

    /** Сведения о плагинах для экрана «Плагины». */
    list: function () {
      return defs.map(function (d) {
        return {
          source: d.__source,
          id: String(d.id),
          name: String(d.name || d.id),
          version: String(d.version || ''),
          description: String(d.description || ''),
          author: String(d.author || ''),
          hasPlaylist: typeof d.playlist === 'function',
          hasStream: typeof d.stream === 'function'
        };
      });
    },

    start: function () {
      defs.forEach(function (d) {
        if (typeof d.start === 'function') safe(d, 'start', function () { d.start(api); });
      });
    },

    hasPlaylist: function () { return defs.some(function (d) { return typeof d.playlist === 'function'; }); },
    hasStream: function () { return defs.some(function (d) { return typeof d.stream === 'function'; }); },

    /** Пропускает список каналов через все плагины по очереди. */
    playlist: function (channels) {
      defs.forEach(function (d) {
        if (typeof d.playlist !== 'function') return;
        var result = safe(d, 'playlist', function () { return d.playlist(channels.slice(), api); });
        if (result && typeof result.length === 'number') {
          // Оставляем только пригодные записи: без названия или адреса канал не включить
          var clean = [];
          for (var i = 0; i < result.length; i++) {
            var c = result[i];
            if (c && typeof c === 'object' && c.url && c.name) clean.push(c);
          }
          channels = clean;
        }
      });
      return channels;
    },

    /** Спрашивает у плагинов адрес потока перед включением канала. */
    stream: function (channel) {
      var url = channel.url;
      defs.forEach(function (d) {
        if (typeof d.stream !== 'function') return;
        var result = safe(d, 'stream', function () { return d.stream(channel, api); });
        if (typeof result === 'string' && result) { url = result; channel.url = result; }
      });
      return url;
    }
  };
})(typeof window !== 'undefined' ? window : this);

/*
 * IPTV — общая веб-основа для компьютеров (Electron), телевизоров Samsung (Tizen) и LG (webOS).
 * Интерфейс «Тихий эфир»: плеер — корневой экран, всё остальное — слои поверх него (гид, мини-гид,
 * сетка, поиск, настройки), «Назад» всегда снимает ровно один слой. Спецификация: docs/design/handoff/SPEC.md
 * Код на ES5: он должен запускаться и на телевизорах 2016–2017 годов. Возможности, которых там нет
 * (например, потоковая распаковка телепрограммы), проверяются перед использованием.
 */
(function () {
  'use strict';

  var VERSION = '1.0.0';
  // Какая оболочка нужна этой версии (см. SHELL в boot.js). Увеличивается, когда коду понадобилось то,
  // что лежит в пакете и само не обновляется: тогда приложение попросит поставить пакет заново.
  var NEEDS_SHELL = 1;
  var REPO = 'volnalabs/IPTV';
  var KEY = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACK: 10009, BACK_LG: 461, ESC: 27, DEL: 46,
    CH_UP: 427, CH_DOWN: 428, PAGE_UP: 33, PAGE_DOWN: 34, IME_DONE: 65376, IME_CANCEL: 65385, BKSP: 8,
    RED: 403, GREEN: 404, YELLOW: 405, BLUE: 406, INFO: 457, GUIDE: 458, CH_LIST: 10073, PRE_CH: 10190,
    PLAY: 415, PAUSE: 19, PLAY_PAUSE: 10252, REW: 412, FF: 417, SPACE: 32, TAB: 9, F10: 121, MENU: 93,
    VOL_UP: 447, VOL_DOWN: 448, MUTE: 449 };
  var TIZEN = !!window.tizen;
  var WEBOS = !!(window.PalmSystem || window.webOS);
  var DESKTOP = /Electron/.test(navigator.userAgent);
  var PLATFORM = TIZEN ? 'samsung' : WEBOS ? 'lg' : DESKTOP ? 'desktop' : 'web';
  var PLATFORM_NAME = { samsung: 'Samsung', lg: 'LG', desktop: 'компьютера', web: 'браузера' }[PLATFORM];
  var DAY = 86400000, MIN = 60000;
  // Телевизоры 2016–2018 годов: без анимаций и градиентов (SPEC 10.4)
  var UA = navigator.userAgent;
  var LITE = /Tizen [12]\.|Tizen 3\.0/.test(UA) || /Web0S|webOS/i.test(UA) && /Chrom(e|ium)\/(3\d|4\d)\./.test(UA);
  if (LITE) document.body.className += ' lite';
  var ICON = {
    search: 'M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0M16 16l4.5 4.5',
    settings: 'M4 7h5.5M14.5 7H20M9.5 7a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M4 17h3.5M12.5 17H20M7.5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0',
    star: 'M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1.05 5.9L12 16.9l-5.25 2.8 1.05-5.9-4.3-4.1 5.9-.8z',
    tv: 'M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM8.5 20.5h7',
    history: 'M5.2 8.8A7.5 7.5 0 1 1 4.5 12M4.6 4.9v4h4M12 8.5V12l2.5 1.5',
    play: 'M8.5 6L18 12l-9.5 6z',
    plug: 'M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0zM12 17v4',
    plus: 'M12 5v14M5 12h14',
    list: 'M8 7h12M8 12h12M8 17h12M4 7h.01M4 12h.01M4 17h.01',
    'chevron-up': 'M6 14.5l6-6 6 6',
    'chevron-down': 'M6 9.5l6 6 6-6',
    fullscreen: 'M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5',
    guide: 'M4 5h16v14H4zM4 10h16M9.5 10v9',
    refresh: 'M19.5 12A7.5 7.5 0 1 1 17.3 6.7M19.4 4.6v4h-4',
    phone: 'M8.5 3h7A1.5 1.5 0 0 1 17 4.5v15a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 7 19.5v-15A1.5 1.5 0 0 1 8.5 3zM11 18h2',
    volume: 'M4 10h3l4.5-3.5v11L7 14H4zM15 9.5a3.5 3.5 0 0 1 0 5M17.5 7a7 7 0 0 1 0 10',
    'star-filled': 'M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1.05 5.9L12 16.9l-5.25 2.8 1.05-5.9-4.3-4.1 5.9-.8z',
    'play-filled': 'M8.5 6L18 12l-9.5 6z',
    playing: 'M6 19v-7M12 19V5M18 19V9',
    pause: 'M9 6v12M15 6v12',
    rewind: 'M11 7l-5 5 5 5M18 7l-5 5 5 5',
    forward: 'M13 7l5 5-5 5M6 7l5 5-5 5',
    'chevron-left': 'M14.5 6l-6 6 6 6',
    'chevron-right': 'M9.5 6l6 6-6 6',
    close: 'M6 6l12 12M18 6L6 18',
    check: 'M5 12.5l4.5 4.5L19 7.5',
    more: 'M6 12h.01M12 12h.01M18 12h.01',
    previous: 'M9 6.5L5 10.5l4 4M5 10.5h9.5a4.5 4.5 0 0 1 0 9H12',
    frame: 'M4 8V5h3M17 5h3v3M20 16v3h-3M7 19H4v-3M8.5 9.5h7v5h-7z',
    info: 'M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0M12 11v5M12 8h.01',
    alert: 'M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0M12 7.5v5M12 16h.01',
    warning: 'M12 4.5L20.5 19h-17zM12 10v4M12 16.5h.01',
    offline: 'M5 10a10.5 10.5 0 0 1 14 0M8 13.5a6 6 0 0 1 8 0M12 17.5h.01M4 4l16 16',
    trash: 'M5 7h14M10 7V4.5h4V7M7 7l1 12.5h8L17 7',
    external: 'M14 4h6v6M20 4l-8 8M18 14v4.5A1.5 1.5 0 0 1 16.5 20h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10',
    back: 'M19 12H5M11 6l-6 6 6 6',
    mute: 'M4 10h3l4.5-3.5v11L7 14H4zM15.5 9.5l5 5M20.5 9.5l-5 5',
    backspace: 'M9 6h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9l-5-6zM12 9.5l5 5M17 9.5l-5 5',
    link: 'M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1',
    shield: 'M12 3.5l7 2.5v5.5c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z',
    grid: 'M4 5h16v14H4zM4 10h16M4 14.5h16M9 5v14',
    clock: 'M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0M12 7.5V12l3 2',
    download: 'M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19.5h14',
    edit: 'M5 19l1-4 9.5-9.5 3 3L9 18zM13.5 7.5l3 3',
    menu: 'M4 7h16M4 12h16M4 17h16',
    keyboard: 'M3.5 6.5h17v11h-17zM7 10h.01M10.5 10h.01M14 10h.01M17.5 10h.01M8 14h8',
    'exit-fs': 'M9 4v5H4M20 9h-5V4M15 20v-5h5M4 15h5v5',
    folder: 'M4 9.5h16V19H4zM6 6.5h12M8 3.5h8'
  };
  // Подписи кнопок: на компьютере — клавиши, на телевизоре — кнопки пульта
  var PC = DESKTOP || PLATFORM === 'web';
  var KEYS = PC ? { ok: 'Enter', back: 'Esc' } : { ok: 'OK', back: 'Назад' };
  var MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
  var WDAYS = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
  var WD = ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'];

  // ---------- мелкие помощники ----------
  function load(key, def) {
    try { var v = window.localStorage.getItem('iptv_' + key); return v === null ? def : JSON.parse(v); } catch (e) { return def; }
  }
  function save(key, value) {
    try { window.localStorage.setItem('iptv_' + key, JSON.stringify(value)); } catch (e) { /* хранилище переполнено — не страшно */ }
  }
  function $(id) { return document.getElementById(id); }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function trim(s) { return String(s).replace(/^\s+|\s+$/g, ''); }
  function cls(el, name, on) {
    var list = el.className ? el.className.split(/\s+/) : [], i = list.indexOf(name);
    if (on && i < 0) list.push(name);
    if (!on && i >= 0) list.splice(i, 1);
    el.className = list.join(' ');
  }
  function has(el, name) { return (' ' + el.className + ' ').indexOf(' ' + name + ' ') >= 0; }
  function show(id, on) { cls($(id), 'on', !!on); }
  function shown(id) { return has($(id), 'on'); }
  function word(n, one, few, many) {
    var a = n % 10, b = n % 100;
    return a === 1 && b !== 11 ? one : a >= 2 && a <= 4 && (b < 12 || b > 14) ? few : many;
  }
  function plural(n, one, few, many) { return n + ' ' + word(n, one, few, many); }
  function chCount(n) { return plural(n, 'канал', 'канала', 'каналов'); }
  function norm(s) { return trim(String(s).toLowerCase().replace(/ё/g, 'е')); }
  function key2(s) { return String(s).toLowerCase().replace(/[^0-9a-zа-яё]/g, ''); }
  function two(n) { return ('0' + n).slice(-2); }
  function clock(t) { var d = new Date(t); return two(d.getHours()) + ':' + two(d.getMinutes()); }
  function dayStart(t) { var d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); }
  function dayDiff(t, now) { return Math.round((dayStart(t) - dayStart(now)) / DAY); }
  function dateText(t) { var d = new Date(t); return d.getDate() + ' ' + MONTHS[d.getMonth()]; }
  /** «Вчера, 10 ноября», «Сегодня, 11 ноября», дальше «Пт, 13 ноября». */
  function dayTitle(t, now) {
    var diff = dayDiff(t, now), d = new Date(t);
    return (diff === 0 ? 'Сегодня' : diff === -1 ? 'Вчера' : diff === 1 ? 'Завтра' : WD[d.getDay()]) + ', ' + dateText(t);
  }
  /** Короткая подпись дня для вкладок: «Вчера», «Сегодня», «Завтра», «Пт 13». */
  function dayTab(t, now) {
    var diff = dayDiff(t, now), d = new Date(t);
    return diff === 0 ? 'Сегодня' : diff === -1 ? 'Вчера' : diff === 1 ? 'Завтра' : WD[d.getDay()] + ' ' + d.getDate();
  }
  /** «сегодня, 18:00», «вчера, 21:10», «9 ноября, 12:00» — для подписей времени. */
  function whenText(t, now) {
    var diff = dayDiff(t, now);
    return (diff === 0 ? 'сегодня' : diff === -1 ? 'вчера' : diff === 1 ? 'завтра' : dateText(t)) + ', ' + clock(t);
  }
  function headDate(now) { var d = new Date(now); return WD[d.getDay()] + ', ' + dateText(now) + ' · ' + clock(now); }
  function duration(sec) {
    sec = Math.max(0, Math.floor(sec));
    var h = Math.floor(sec / 3600), m = Math.floor(sec % 3600 / 60), s = sec % 60;
    return h + ':' + two(m) + ':' + two(s);
  }
  function newer(remote, local) {
    var r = String(remote).replace(/^v/, '').split('.'), l = String(local).replace(/^v/, '').split('.');
    for (var i = 0; i < Math.max(r.length, l.length); i++) {
      var a = parseInt(r[i], 10) || 0, b = parseInt(l[i], 10) || 0;
      if (a !== b) return a > b;
    }
    return false;
  }
  /** Ссылку на страницу файла GitHub превращаем в ссылку на сам файл. */
  function rawUrl(url) {
    var m = /^https?:\/\/github\.com\/([^\/]+)\/([^\/]+)\/blob\/(.+)$/i.exec(trim(url));
    return m ? 'https://raw.githubusercontent.com/' + m[1] + '/' + m[2] + '/' + m[3] : trim(url);
  }
  function hostOf(url) { var m = /^https?:\/\/([^\/:?#]+)/i.exec(url); return m ? m[1] : ''; }
  /** Длинная ссылка обрезается по середине: начало и имя файла остаются видны. */
  function midCut(s, max) {
    if (s.length <= max) return s;
    var tail = Math.floor(max * 0.4), headLen = max - tail - 1;
    return s.substr(0, headLen) + '…' + s.substr(s.length - tail);
  }
  var raf = window.requestAnimationFrame ? function (fn) { window.requestAnimationFrame(fn); } : function (fn) { setTimeout(fn, 16); };

  // ---------- состояние ----------
  var S = {
    url: load('url', ''),
    epgUrl: load('epgurl', ''),
    favs: load('favs', {}),
    auto: load('auto', true),
    plugins: load('plugins', []),
    playlists: load('playlists', null), activePl: load('activePl', ''),
    recent: load('recent', []), seen: load('seen', {}),
    vol: load('vol', 100), muted: load('muted', false),
    channels: [], epgUrls: [],
    menu: [], menuId: 'all', list: [],
    gi: 0, ci: 0, pi: 0,
    cur: null, sel: null, arch: null, playList: [],
    epg: null, epgCh: null, prog: [], day: 0, days: [],
    prev: null, update: null, pluginsDirty: false, epgStatus: '', epgError: false
  };
  window.__iptvState = S; // для отладки и автотестов
  // Текущий слой записываем и в <body data-z>: по нему CSS решает, что показывать поверх эфира
  var zoneName = 'play';
  Object.defineProperty(S, 'zone', {
    enumerable: true,
    get: function () { return zoneName; },
    set: function (z) { zoneName = z; document.body.setAttribute('data-z', z); }
  });
  S.zone = 'play';

  var PL = window.__iptvPlugins;

  // ---------- метки «новое» ----------
  function isNew(key) { return !S.seen[key]; }
  function markSeen(key) { if (!S.seen[key]) { S.seen[key] = 1; save('seen', S.seen); } }
  function newTag(key, text) { return isNew(key) ? '<span class="new">' + (text || 'новое') + '</span>' : ''; }

  // ---------- плейлисты ----------
  // Плейлистов может быть несколько. Старые ключи url/epgurl/m3u держатся равными активному плейлисту:
  // тогда откат на прежнюю версию (boot.js) ничего не ломает.
  function migratePlaylists() {
    if (S.playlists && S.playlists.length) { if (!activePlaylist()) S.activePl = S.playlists[0].id; syncActive(); return; }
    S.playlists = [];
    if (S.url) {
      S.playlists.push({ id: 'p1', name: 'Мой плейлист', url: S.url, epgUrl: S.epgUrl, updated: 0, count: 0, lastError: null });
      S.activePl = 'p1';
      savePlaylists();
    }
  }
  function savePlaylists() { save('playlists', S.playlists); save('activePl', S.activePl); }
  function playlistById(id) {
    for (var i = 0; i < S.playlists.length; i++) if (S.playlists[i].id === id) return S.playlists[i];
    return null;
  }
  function activePlaylist() { return playlistById(S.activePl); }
  function syncActive() {
    var a = activePlaylist();
    if (!a) return;
    if (S.url !== a.url) { S.url = a.url; save('url', a.url); }
    if (S.epgUrl !== (a.epgUrl || '')) { S.epgUrl = a.epgUrl || ''; save('epgurl', S.epgUrl); }
  }
  function newPlaylistId() {
    var n = 1;
    while (playlistById('p' + n)) n++;
    return 'p' + n;
  }

  // ---------- разбор плейлиста ----------
  function parseM3U(text) {
    var lines = text.replace(/﻿/g, '').split(/\r?\n/), out = [], epgUrls = [], info = null, grp = null;
    var head = { catchup: '', days: 0, source: '' };
    function attrsOf(line) {
      var a = {}, re = /([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"/g, m;
      while ((m = re.exec(line))) a[m[1].toLowerCase()] = trim(m[2]);
      return a;
    }
    for (var i = 0; i < lines.length; i++) {
      var line = trim(lines[i]);
      if (!line) continue;
      if (/^#EXTM3U/i.test(line)) {
        var h = attrsOf(line), tvg = h['url-tvg'] || h['x-tvg-url'] || h['tvg-url'] || '';
        tvg.split(',').forEach(function (u) { if (trim(u)) epgUrls.push(trim(u)); });
        head.catchup = h.catchup || head.catchup;
        head.days = parseInt(h['catchup-days'] || h.timeshift, 10) || head.days;
        head.source = h['catchup-source'] || head.source;
      } else if (/^#EXTINF/i.test(line)) {
        info = { attrs: attrsOf(line), name: '' };
        grp = null;
        var quoted = false;
        for (var k = 0; k < line.length; k++) {
          if (line.charAt(k) === '"') quoted = !quoted;
          else if (line.charAt(k) === ',' && !quoted) { info.name = trim(line.substring(k + 1)); break; }
        }
      } else if (/^#EXTGRP:/i.test(line)) {
        grp = trim(line.substring(8));
      } else if (line.charAt(0) !== '#') {
        var a = info ? info.attrs : {};
        var type = (a.catchup || a['catchup-type'] || head.catchup || '').toLowerCase();
        var days = parseInt(a['catchup-days'] || a.timeshift || a['tvg-rec'], 10) || head.days;
        if (type && !days) days = 1;
        out.push({
          number: out.length + 1,
          name: (info && info.name) || a['tvg-name'] || ('Канал ' + (out.length + 1)),
          url: line,
          group: a['group-title'] || grp || 'Без группы',
          logo: a['tvg-logo'] || '',
          tvgId: a['tvg-id'] || '',
          tvgName: a['tvg-name'] || '',
          catchup: days > 0 ? (type || 'shift') : '',
          catchupDays: days,
          catchupSource: a['catchup-source'] || head.source || ''
        });
        info = null; grp = null;
      }
    }
    return { channels: out, epgUrls: epgUrls };
  }

  /** Ссылка на запись передачи по правилам плейлиста (то же, что Catchup.kt в приложении для Android). */
  function catchupUrl(ch, start, stop) {
    if (!ch.catchup || !ch.catchupDays) return '';
    var now = Date.now(), s = Math.floor(start / 1000), e = Math.floor(stop / 1000), n = Math.floor(now / 1000);
    var src = ch.catchupSource, type = ch.catchup, sep = ch.url.indexOf('?') >= 0 ? '&' : '?';
    function fill(t) {
      var d = new Date(start);
      var map = { utc: s, start: s, utcend: e, end: e, lutc: n, now: n, timestamp: n, duration: e - s, offset: Math.max(0, n - s),
        Y: d.getUTCFullYear(), m: two(d.getUTCMonth() + 1), d: two(d.getUTCDate()), H: two(d.getUTCHours()), M: two(d.getUTCMinutes()), S: two(d.getUTCSeconds()) };
      return t.replace(/\$?\{([A-Za-z]+)\}/g, function (all, name) { return map.hasOwnProperty(name) ? map[name] : all; });
    }
    if (type === 'append' && src) return ch.url + fill(src);
    if (src && (type === 'default' || type === 'flussonic' || type === 'fs' || src.indexOf('{') >= 0)) return /^http/i.test(src) ? fill(src) : ch.url + fill(src);
    if (type === 'flussonic' || type === 'fs') {
      var dur = Math.max(60, Math.floor((Math.min(stop, now) - start) / 1000));
      var re = /\/(index|video|mono|tracks-[^\/]+)\.m3u8/;
      if (re.test(ch.url)) return ch.url.replace(re, function (all, name) { return '/' + name + '-' + s + '-' + dur + '.m3u8'; });
    }
    return ch.url + sep + 'utc=' + s + '&lutc=' + n;
  }

  /** Загрузка текста. fail(причина, кодОтвета): 0 — нет связи, -1 — таймаут, -2 — неверная ссылка. Возвращает запрос — его можно прервать. */
  function fetchText(url, ok, fail) {
    var x = new XMLHttpRequest(), done = false;
    function finish(fn, a, b) { if (!done) { done = true; fn(a, b); } }
    try { x.open('GET', url, true); } catch (e) { fail('неверная ссылка', -2); return null; }
    x.timeout = 30000;
    x.onload = function () { if (x.status >= 200 && x.status < 300) finish(ok, x.responseText); else finish(fail, 'сервер ответил ' + x.status, x.status); };
    x.onerror = function () { finish(fail, 'нет связи с сервером', 0); };
    x.ontimeout = function () { finish(fail, 'сервер не отвечает', -1); };
    x.onabort = function () { done = true; };
    try { x.send(); } catch (e2) { finish(fail, 'неверная ссылка', -2); }
    return x;
  }
  /** Текст причины для формы плейлиста (SPEC 5.3). */
  function linkError(reason, status) {
    if (status === 404) return 'Сервер ответил 404 — по этой ссылке плейлиста нет.';
    if (status === 403 || status === 401) return 'Сервер не пускает по этой ссылке (ошибка ' + status + '). Проверьте ссылку у провайдера.';
    if (status === -1) return 'Сервер не отвечает.';
    if (status === 0) return 'Нет связи с сервером.';
    if (status === -3) return 'Ссылка открылась, но в файле нет ни одного канала.';
    return reason.charAt(0).toUpperCase() + reason.substr(1) + '.';
  }

  function applyPlaylist(parsed, fresh) {
    var channels = parsed.channels;
    if (PL.hasPlaylist()) channels = PL.playlist(channels);
    channels.forEach(function (c, i) { c.number = i + 1; c.group = c.group || 'Без группы'; });
    S.channels = channels;
    S.epgUrls = parsed.epgUrls;
    buildMenu();
    if (!channels.length) return;
    var keep = !fresh && S.cur ? findByUrl(S.cur.url) : null;
    if (keep) {
      S.cur = keep; S.sel = null;
      S.playList = listOf(load('group', 'all')) || channels;
      refocus();
      return;
    }
    var ch = findByUrl(load('last', '')), group = load('group', 'all'), list = listOf(group);
    if (!list || !ch || list.indexOf(ch) < 0) list = channels;
    S.playList = list;
    S.prev = null;
    play(ch || channels[0], null);
    refocus();
  }

  function findByUrl(url) {
    for (var i = 0; i < S.channels.length; i++) if (S.channels[i].url === url) return S.channels[i];
    return null;
  }

  /** Список обновился под фокусом: ищем тот же канал по адресу, иначе тот же номер строки (SPEC 8.3 п. 7). */
  function refocus() {
    if (!inGuide()) return;
    var old = S.list[S.ci], url = old ? old.url : '';
    S.list = listOf(S.menuId) || S.channels;
    if (!listOf(S.menuId)) S.menuId = 'all';
    S.gi = menuIndex(S.menuId);
    var at = -1;
    for (var i = 0; i < S.list.length; i++) if (S.list[i].url === url) { at = i; break; }
    S.ci = at >= 0 ? at : Math.max(0, Math.min(S.ci, S.list.length - 1));
    renderGuide();
  }

  var refreshBusy = false;
  /** Обновляет активный плейлист. manual — нажали «Обновить каналы»: результат сообщаем тостом. */
  function refresh(manual) {
    var a = activePlaylist();
    if (!a || refreshBusy) return;
    refreshBusy = true;
    $('splashCap').textContent = S.channels.length ? '' : 'Загружаю каналы…';
    if (S.zone === 'pane') renderSetup();
    fetchText(a.url, function (text) {
      var parsed = parseM3U(text);
      refreshBusy = false;
      if (!parsed.channels.length) { loadFailed('в плейлисте нет каналов', -3, manual); return; }
      save('m3u', text);
      $('splashCap').textContent = '';
      a.updated = Date.now(); a.count = parsed.channels.length; a.lastError = null;
      savePlaylists();
      applyPlaylist(parsed, false);
      if (manual) { S.epg = null; toast('Список обновлён · ' + chCount(S.channels.length), 'ok'); }
      loadEpg(manual);
      if (S.zone === 'onb') closeOnboarding();
      if (S.zone === 'setup' || S.zone === 'pane') renderSetup();
    }, function (reason, status) { refreshBusy = false; loadFailed(reason, status, manual); });
  }

  function loadFailed(reason, status, manual) {
    var a = activePlaylist();
    if (a) { a.lastError = { time: Date.now(), message: linkError(reason, status).replace(/\.$/, '') }; savePlaylists(); }
    hideSplash();
    if (S.zone === 'onb') { onbFailed(linkError(reason, status)); return; }
    // Ошибка не прячет работающие данные: сохранённый список остаётся на экране
    if (manual || !S.channels.length) toast('Не удалось обновить список: ' + reason + '.' + (S.channels.length ? ' Работает прежний список.' : ''), 'warn');
    if (S.zone === 'setup' || S.zone === 'pane') renderSetup();
    if (inGuide()) renderGuide();
  }

  /** Делает плейлист активным: список грузится по его ссылке и подменяется только после успеха (SPEC 5.2). */
  var switching = null;
  function activatePlaylist(pl, done) {
    if (switching || pl.id === S.activePl) { if (done) done(pl.id === S.activePl); return; }
    switching = pl.id;
    toast('Загружаем «' + pl.name + '»…', 'wait');
    fetchText(pl.url, function (text) {
      var parsed = parseM3U(text);
      switching = null;
      if (!parsed.channels.length) { switchFailed(pl, 'в файле нет ни одного канала', done); return; }
      pl.updated = Date.now(); pl.count = parsed.channels.length; pl.lastError = null;
      S.activePl = pl.id;
      savePlaylists();
      syncActive();
      save('m3u', text);
      save('group', 'all');
      S.epg = null; epgSeq++; epgBusy = false;
      S.menuId = 'all'; S.arch = null;
      applyPlaylist(parsed, true);
      toast('Плейлист «' + pl.name + '» · ' + chCount(S.channels.length), 'ok');
      loadEpg(true);
      if (done) done(true);
    }, function (reason) { switching = null; switchFailed(pl, reason, done); });
  }
  function switchFailed(pl, reason, done) {
    pl.lastError = { time: Date.now(), message: reason.charAt(0).toUpperCase() + reason.substr(1) };
    savePlaylists();
    toast('«' + pl.name + '» не загрузился: ' + reason, 'warn');
    if (done) done(false);
  }

  // ---------- телепрограмма ----------
  var epgBusy = false, epgSeq = 0;

  function setEpgStatus(text, isError) {
    S.epgStatus = text; S.epgError = !!isError;
    if (shown('guide') && !S.prog.length) renderDetails();
  }
  function epgLoaded() {
    progKey = '';
    renderZone();
    if (shown('osd')) renderOsd();
    if (S.zone === 'search' || S.zone === 'results') { runSearch(); renderSearch(); }
  }

  function xmlTime(s) {
    if (!s || s.length < 14) return 0;
    var t = Date.UTC(+s.substr(0, 4), +s.substr(4, 2) - 1, +s.substr(6, 2), +s.substr(8, 2), +s.substr(10, 2), +s.substr(12, 2));
    var tz = /([+-])(\d\d)(\d\d)\s*$/.exec(s);
    if (tz) t -= (tz[1] === '-' ? -1 : 1) * (+tz[2] * 60 + +tz[3]) * 60000;
    return t;
  }
  function detach(s) { return s.length < 13 ? s : JSON.parse(JSON.stringify(s)); }
  function unxml(s) {
    return s.indexOf('&') < 0 ? s : s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'")
      .replace(/&#(\d+);/g, function (a, n) { return String.fromCharCode(+n); }).replace(/&amp;/g, '&');
  }

  /**
   * Телепрограмма бывает на сотни мегабайт, поэтому читаем её потоком: распаковываем на лету,
   * берём только каналы из плейлиста и только нужные дни. На старых телевизорах потокового
   * чтения нет — там приложение работает без программы.
   */
  function loadEpg(force) {
    var url = S.epgUrl || S.epgUrls[0];
    if (!url || !S.channels.length || epgBusy) { if (!url) setEpgStatus('', false); return; }
    if (!force && S.epg && Date.now() - S.epg.updated < 12 * 3600000) return;
    if (!window.TextDecoder || !window.Uint8Array) { setEpgStatus('Телепрограмма на этой модели не поддерживается', true); return; }

    var wantId = {}, wantName = {}, maxDays = 1;
    S.channels.forEach(function (c) {
      if (c.tvgId) wantId[c.tvgId] = 1;
      wantName[key2(c.tvgName || c.name)] = 1;
      wantName[key2(c.name)] = 1;
      if (c.catchupDays > maxDays) maxDays = c.catchupDays;
    });
    var seq = ++epgSeq;
    var now = Date.now(), from = now - (Math.min(maxDays, 14) + 1) * DAY, to = now + 2 * DAY;
    // Описания храним только для «сегодня ± 1 день» и не длиннее 400 символов — иначе на телевизоре растёт память
    var descFrom = dayStart(now) - DAY, descTo = dayStart(now) + 2 * DAY;
    var byId = {}, nameToId = {}, keepId = {}, count = 0, buf = '';
    var reId = /channel="([^"]*)"/, reStart = /start="([^"]*)"/, reStop = /stop="([^"]*)"/, reTitle = /<title[^>]*>([^<]*)<\/title>/, reDesc = /<desc[^>]*>([^<]*)<\/desc>/;

    function channelsIn(text) {
      var re = /<channel\s+id="([^"]*)"[^>]*>([\s\S]*?)<\/channel>/g, m;
      while ((m = re.exec(text))) {
        var id = unxml(m[1]), names = m[2].match(/<display-name[^>]*>[^<]*/g) || [];
        for (var i = 0; i < names.length; i++) {
          var n = key2(unxml(names[i].replace(/<display-name[^>]*>/, '')));
          if (wantName[n]) { keepId[id] = 1; if (!nameToId[n]) nameToId[n] = id; }
        }
      }
    }
    function eat(final) {
      if (seq !== epgSeq) { buf = ''; return; }
      var parts = buf.split('</programme>');
      buf = final ? '' : parts.pop();
      for (var i = 0; i < parts.length; i++) {
        var part = parts[i], at = part.lastIndexOf('<programme');
        if (at < 0) { if (part.indexOf('<channel') >= 0) channelsIn(part); continue; }
        if (at > 0 && part.lastIndexOf('<channel', at) >= 0) channelsIn(part.substring(0, at));
        var tagEnd = part.indexOf('>', at), tag = part.substring(at, tagEnd);
        var m = reId.exec(tag);
        if (!m) continue;
        var id = unxml(m[1]);
        if (!wantId[id] && !keepId[id]) continue;
        var s = xmlTime((reStart.exec(tag) || [])[1]), e = xmlTime((reStop.exec(tag) || [])[1]);
        if (!s || e <= s || e <= from || s >= to) continue;
        var t = reTitle.exec(part.substring(tagEnd));
        var rec = byId[id] || (byId[id] = { s: [], e: [], t: [], d: [] });
        // Название копируем: иначе короткая строка держит в памяти весь прочитанный кусок файла
        rec.s.push(s); rec.e.push(e); rec.t.push(t ? detach(unxml(trim(t[1]))) : '');
        var ds = s < descTo && e > descFrom ? reDesc.exec(part.substring(tagEnd)) : null;
        rec.d.push(ds ? detach(unxml(trim(ds[1])).substr(0, 400)) : '');
        count++;
      }
    }
    function finish() {
      eat(true);
      if (seq !== epgSeq) return;
      var ids = 0;
      for (var id in byId) if (byId.hasOwnProperty(id)) {
        ids++;
        var r = byId[id], sorted = true;
        for (var i = 1; i < r.s.length; i++) if (r.s[i] < r.s[i - 1]) { sorted = false; break; }
        if (!sorted) {
          var order = r.s.map(function (v, k) { return k; }).sort(function (a, b) { return r.s[a] - r.s[b]; });
          byId[id] = { s: order.map(function (k) { return r.s[k]; }), e: order.map(function (k) { return r.e[k]; }), t: order.map(function (k) { return r.t[k]; }), d: order.map(function (k) { return r.d[k]; }) };
        }
      }
      epgBusy = false;
      if (!ids) { setEpgStatus('В телепрограмме нет передач для этих каналов', true); epgLoaded(); return; }
      S.epg = { byId: byId, nameToId: nameToId, updated: Date.now() };
      var d = new Date();
      setEpgStatus('Программа: ' + two(d.getDate()) + '.' + two(d.getMonth() + 1) + '.' + String(d.getFullYear()).slice(-2) + ', ' + clock(d.getTime()), false);
      epgLoaded();
    }
    function failed(err) {
      if (seq !== epgSeq) return;
      epgBusy = false;
      setEpgStatus('Не удалось загрузить телепрограмму' + (err && err.message ? ': ' + err.message : ''), true);
    }

    /** Телевизоры до 2022 года не умеют распаковывать .gz сами — распаковываем встроенной библиотекой fflate. */
    function unpackOldTv(reader, first) {
      return new Promise(function (resolve, reject) {
        loadScript('fflate.min.js', function (ok) {
          if (!ok || !window.fflate) { reject(new Error('эта модель не умеет распаковывать .gz')); return; }
          var decoder = new window.TextDecoder('utf-8'), shown = 0;
          var unzip = new window.fflate.Gunzip(function (data, last) {
            buf += decoder.decode(data, { stream: !last });
            eat(false);
          });
          function step(chunk) {
            if (chunk.done) { unzip.push(new Uint8Array(0), true); finish(); resolve(); return undefined; }
            unzip.push(chunk.value, false);
            if (count - shown >= 20000) { shown = count; setEpgStatus('Загружаю программу… ' + count, false); }
            return reader.read().then(step);
          }
          Promise.resolve(first).then(step)['catch'](reject);
        });
      });
    }

    /**
     * Запасной путь для телевизоров, где потоковое чтение недоступно или запрещено для чужих адресов:
     * скачиваем файл целиком (сжатая программа весит немного) и разбираем его небольшими порциями.
     */
    function viaXhr() {
      var x = new XMLHttpRequest();
      buf = ''; count = 0;
      x.open('GET', url, true);
      x.responseType = 'arraybuffer';
      x.timeout = 180000;
      x.onerror = function () { failed(new Error('нет связи с сервером')); };
      x.ontimeout = function () { failed(new Error('сервер не отвечает')); };
      x.onload = function () {
        if (x.status < 200 || x.status >= 300 || !x.response) { failed(new Error('сервер ответил ' + x.status)); return; }
        var bytes = new Uint8Array(x.response), decoder = new window.TextDecoder('utf-8'), pos = 0, STEP = 262144;
        function text(data, last) { buf += decoder.decode(data, { stream: !last }); eat(false); }
        function run(unzip) {
          (function next() {
            try {
              var end = Math.min(bytes.length, pos + STEP), last = end === bytes.length, part = bytes.subarray(pos, end);
              pos = end;
              if (unzip) unzip.push(part, last); else text(part, last);
              if (last) { bytes = null; finish(); } else { setEpgStatus('Загружаю программу… ' + count, false); setTimeout(next, 0); }
            } catch (e) { failed(e); }
          })();
        }
        if (bytes[0] !== 0x1f || bytes[1] !== 0x8b) { run(null); return; }
        loadScript('fflate.min.js', function () {
          if (!window.fflate) { failed(new Error('не удалось распаковать .gz')); return; }
          run(new window.fflate.Gunzip(text));
        });
      };
      try { x.send(); } catch (e) { failed(e); }
    }

    epgBusy = true;
    setEpgStatus('Загружаю программу…', false);
    if (!window.fetch || !window.ReadableStream || !window.Promise) { viaXhr(); return; }
    window.fetch(url).then(function (res) {
      if (!res.ok) throw new Error('сервер ответил ' + res.status);
      var reader = res.body.getReader();
      return reader.read().then(function (first) {
        var gz = first.value && first.value[0] === 0x1f && first.value[1] === 0x8b;
        if (gz && !window.DecompressionStream) return unpackOldTv(reader, first);
        if (gz) {
          var pending = first, raw = reader;
          reader = new window.ReadableStream({
            pull: function (c) {
              if (pending) { var p = pending; pending = null; if (p.done) c.close(); else c.enqueue(p.value); return undefined; }
              return raw.read().then(function (x) { if (x.done) c.close(); else c.enqueue(x.value); });
            }
          }).pipeThrough(new window.DecompressionStream('gzip')).getReader();
          first = null;
        }
        var decoder = new window.TextDecoder('utf-8'), shown = 0;
        function step(chunk) {
          if (chunk.done) { buf += decoder.decode(); return finish(); }
          buf += decoder.decode(chunk.value, { stream: true });
          eat(false);
          if (count - shown >= 20000) { shown = count; setEpgStatus('Загружаю программу… ' + count, false); }
          return reader.read().then(step);
        }
        return first ? step(first) : reader.read().then(step);
      });
    })['catch'](function (err) { if (count === 0) viaXhr(); else failed(err); });
  }

  function epgFor(ch) {
    if (!S.epg || !ch) return null;
    return S.epg.byId[ch.tvgId] || S.epg.byId[S.epg.nameToId[key2(ch.tvgName || ch.name)]] || S.epg.byId[S.epg.nameToId[key2(ch.name)]] || null;
  }
  /** Номер передачи, идущей в момент t, или -1. */
  function progAt(e, t) {
    var lo = 0, hi = e.s.length - 1, found = -1;
    while (lo <= hi) { var mid = (lo + hi) >> 1; if (e.s[mid] <= t) { found = mid; lo = mid + 1; } else hi = mid - 1; }
    return found >= 0 && e.e[found] > t ? found : -1;
  }

  /** Описание текущей передачи из <desc> — если оно есть в загруженной программе. */
  function descAt(e, i) { return e && e.d && e.d[i] ? e.d[i] : ''; }
  function hasEpgSource() { return !!(S.epgUrl || S.epgUrls[0]); }

  // ---------- меню и списки ----------
  function favList() { return S.channels.filter(function (c) { return S.favs[c.url]; }); }
  function recentList() {
    var out = [], now = Date.now(), kept = [];
    for (var i = 0; i < S.recent.length; i++) {
      var r = S.recent[i];
      if (!r || now - r.t > 7 * DAY) continue;
      kept.push(r);
      var ch = findByUrl(r.url);
      if (ch) out.push(ch);
    }
    if (kept.length !== S.recent.length) { S.recent = kept; save('recent', kept); }
    return out;
  }
  function addRecent(ch) {
    var out = [{ url: ch.url, t: Date.now() }];
    for (var i = 0; i < S.recent.length && out.length < 20; i++) if (S.recent[i].url !== ch.url) out.push(S.recent[i]);
    S.recent = out;
    save('recent', out);
  }
  function listOf(id) {
    if (id === 'all') return S.channels;
    if (id === 'fav') return favList();
    if (id === 'recent') return recentList();
    for (var i = 0; i < S.menu.length; i++) if (S.menu[i].id === id) return S.menu[i].list || null;
    return null;
  }

  /** Меню гида: Поиск, Избранное, Недавние, Все каналы, группы плейлиста в порядке файла, Настройки. */
  var TOP = 4; // сколько пунктов закреплено сверху
  function buildMenu() {
    var menu = [
      { id: 'search', title: 'Поиск', icon: 'search', action: true },
      { id: 'fav', title: 'Избранное', icon: 'star', list: favList() },
      { id: 'recent', title: 'Недавние', icon: 'history', list: recentList(), isNew: 'recent' },
      { id: 'all', title: 'Все каналы', icon: 'tv', list: S.channels }
    ];
    var seen = {}, order = [];
    S.channels.forEach(function (c) {
      if (!seen[c.group]) { seen[c.group] = { id: 'g:' + c.group, title: c.group, icon: 'folder', list: [] }; order.push(seen[c.group]); }
      seen[c.group].list.push(c);
    });
    S.menu = menu.concat(order, [{ id: 'settings', title: 'Настройки', icon: 'settings', action: true }]);
    if (!listOf(S.menuId)) S.menuId = 'all';
  }
  function menuIndex(id) {
    for (var i = 0; i < S.menu.length; i++) if (S.menu[i].id === id) return i;
    return 0;
  }
  function menuTitle(id) { var m = S.menu[menuIndex(id)]; return m ? m.title : 'Все каналы'; }
  function inGuide() { return S.zone === 'groups' || S.zone === 'channels' || S.zone === 'epg' || S.zone === 'date' || S.zone === 'head'; }

  /**
   * В списках показываем только строки вокруг выбранной — тысячу узлов телевизор не потянет.
   * Пока выбор двигают пультом, он держится на заданном месте (lead); колесо мыши прокручивает список, не трогая выбор.
   */
  var tops = {}, seenSel = {};
  function winTop(name, sel, total, visible, stamp, lead) {
    var key = sel + '|' + stamp;
    if (seenSel[name] !== key) {
      seenSel[name] = key;
      var top = tops[name] || 0;
      if (lead === undefined) { if (sel < top) top = sel; else if (sel >= top + visible) top = sel - visible + 1; }
      else top = sel - lead;
      tops[name] = top;
    }
    tops[name] = Math.max(0, Math.min(tops[name] || 0, total - visible));
    return tops[name];
  }
  function svg(name, cl) {
    return '<svg class="ic' + (cl ? ' ' + cl : '') + (name === 'star-filled' || name === 'play-filled' ? ' fill' : '') + (name === 'playing' ? ' thick' : '') +
      '" viewBox="0 0 24 24"><path d="' + ICON[name] + '"/></svg>';
  }
  /** Буквы вместо логотипа: первые буквы слов названия («Россия К» → «РК», «5 канал» → «5К»), не больше трёх. */
  function initials(name) {
    var words = String(name).split(/[^0-9A-Za-zА-Яа-яЁё]+/), out = '';
    for (var i = 0; i < words.length && out.length < 3; i++) if (words[i]) out += /^\d+$/.test(words[i]) ? words[i].substr(0, 3 - out.length) : words[i].charAt(0);
    if (out.length === 1 && words[0] && words[0].length > 1 && !/^\d/.test(words[0])) out = words[0].substr(0, 2);
    return (out || '•').toUpperCase();
  }
  /** Логотип: буквы на нейтральной плитке, картинка появляется поверх после загрузки. Цветных плашек нет. */
  function logoHtml(ch, cl) {
    return '<span class="logo' + (cl ? ' ' + cl : '') + '"><b>' + esc(initials(ch.name)) + '</b>' +
      (ch.logo ? '<img src="' + esc(ch.logo) + '" alt="" style="opacity:0" onload="this.style.opacity=1;this.parentNode.className+=\' has\'" onerror="this.style.display=\'none\'">' : '') + '</span>';
  }
  function bar(from, to, now, cl) {
    return '<span class="bar' + (cl ? ' ' + cl : '') + '"><i style="width:' + Math.max(0, Math.min(100, Math.round((now - from) * 100 / Math.max(1, to - from)))) + '%"></i></span>';
  }
  function numText(n) {
    var len = S.channels.length > 999 ? 4 : 3, s = String(n);
    while (s.length < len) s = '0' + s;
    return s;
  }

  // ---------- подсказки кнопок ----------
  var ARROW = { l: 'chevron-left', r: 'chevron-right', u: 'chevron-up', d: 'chevron-down' };
  var PCKEY = { l: '←', r: '→', u: '↑', d: '↓', lr: '← →', ud: '↑ ↓', ok: 'Enter', back: 'Esc', hold: 'ПКМ' };
  /** Кнопка пульта (круглая OK и стрелки, овальные пары и текст) или клавиша клавиатуры на компьютере. */
  function cap(k) {
    if (PC && PCKEY[k]) return '<b class="kbd">' + PCKEY[k] + '</b>';
    if (k === 'ok') return '<b class="cap round">OK</b>';
    if (k === 'hold') return '<i class="hold">удерж.</i><b class="cap round ring">OK</b>';
    if (k === 'back') return '<b class="cap">Назад</b>';
    if (ARROW[k]) return '<b class="cap round">' + svg(ARROW[k]) + '</b>';
    if (k === 'lr') return '<b class="cap pair">' + svg('chevron-left') + svg('chevron-right') + '</b>';
    if (k === 'ud') return '<b class="cap pair">' + svg('chevron-up') + svg('chevron-down') + '</b>';
    return '<b class="' + (PC ? 'kbd' : 'cap') + '">' + k + '</b>';
  }
  /** Подсказка «кнопка — что она делает». С действием её можно нажать мышью или указкой. */
  function hint(k, label, action) {
    return '<span class="h"' + (action ? ' data-a="' + action + '"' : '') + '>' + cap(k) + label + '</span>';
  }

  // ---------- тосты ----------
  var T = { timer: null, action: null };
  var TOAST_ICON = { ok: 'check', err: 'alert', warn: 'warning', fav: 'star-filled' };
  /** Уведомление на 3 с; с действием («Вернуть») или предупреждение — 6 с. Новое заменяет старое. */
  function toast(text, kind, action, ms) {
    var el = $('toast');
    T.action = action || null;
    el.className = 'on' + (kind ? ' ' + kind : '');
    el.innerHTML = (kind === 'wait' ? '<span class="spin"></span>' : TOAST_ICON[kind] ? svg(TOAST_ICON[kind]) : '') + '<span class="tx">' + esc(text) +
      (action && !PC ? ' · OK — ' + esc(action.label.toLowerCase()) : '') + '</span>' +
      (action && PC ? '<span class="btn" data-a="toast">' + esc(action.label) + '</span>' : '');
    clearTimeout(T.timer);
    T.timer = setTimeout(hideToast, ms || (action ? 6000 : 3000));
  }
  function hideToast() {
    clearTimeout(T.timer);
    var a = T.action;
    T.action = null;
    cls($('toast'), 'on', false);
    if (a && a.expire) a.expire();
  }
  function toastAct() {
    var a = T.action;
    if (!a) return false;
    T.action = null;
    clearTimeout(T.timer);
    cls($('toast'), 'on', false);
    a.fn();
    return true;
  }

  // ---------- диалоги ----------
  var D = { buttons: [], i: 0, back: 'play', onBack: null, busy: false };
  /** Диалог: заголовок, текст и кнопки вида { t: надпись, a: функция, danger, primary }. Фокус заперт на кнопках. */
  function openDialog(o) {
    if (S.zone !== 'dialog') D.back = S.zone;
    D.buttons = o.buttons; D.i = o.focus || 0; D.onBack = o.onBack || null; D.busy = false;
    S.zone = 'dialog';
    $('dialog').innerHTML = '<div class="dbox" role="dialog" aria-modal="true" aria-labelledby="dTitle"><div id="dTitle" class="dt">' + esc(o.title) + '</div>' +
      '<div class="dx">' + esc(o.text) + '</div>' + (o.progress ? '<div class="dbar"><i id="dFill"></i></div>' : '') + '<div class="btns" id="dButtons"></div></div>';
    show('dialog', true);
    renderDialog();
  }
  function renderDialog() {
    var html = '';
    for (var i = 0; i < D.buttons.length; i++) {
      var b = D.buttons[i];
      html += '<div class="btn' + (b.danger ? ' danger' : '') + (b.primary ? ' primary' : '') + (i === D.i ? ' sel' : '') + '" data-z="dialog" data-i="' + i + '">' +
        (b.icon ? svg(b.icon) : '') + esc(b.t) + '</div>';
    }
    $('dButtons').innerHTML = html;
  }
  function closeDialog() {
    show('dialog', false);
    S.zone = D.back;
    if (S.zone === 'play') showInfo();
    renderZone();
  }
  function keyDialog(code) {
    if (D.busy) return;
    if (code === KEY.LEFT || code === KEY.UP) D.i = Math.max(0, D.i - 1);
    else if (code === KEY.RIGHT || code === KEY.DOWN || code === KEY.TAB) D.i = code === KEY.TAB ? (D.i + 1) % Math.max(1, D.buttons.length) : Math.min(D.buttons.length - 1, D.i + 1);
    else if (code === KEY.ENTER) { var b = D.buttons[D.i]; if (b) b.a(); return; }
    renderDialog();
  }
  function confirmDialog(title, text, onYes) {
    openDialog({ title: title, text: text, focus: 1, buttons: [
      { t: 'Удалить', danger: true, icon: 'trash', a: function () { closeDialog(); onYes(); } },
      { t: 'Отмена', a: closeDialog }] });
  }

  // ---------- всплывающее меню: плейлисты (G1), действия с каналом (G3), удержание OK в настройках ----------
  var M = { items: [], i: 0, back: 'play', title: '' };
  function openPop(zone, title, items, pos, focus) {
    if (S.zone !== 'ctx' && S.zone !== 'plm') M.back = S.zone;
    M.items = items; M.title = title;
    M.i = focus || 0;
    while (M.items[M.i] && (M.items[M.i].sep || M.items[M.i].off) && M.i < M.items.length - 1) M.i++;
    S.zone = zone;
    var el = $('pop');
    el.style.left = pos.x + 'px'; el.style.top = pos.y + 'px';
    cls(el, 'at', !!pos.cursor);
    show('pop', true);
    renderPop();
    renderHints();
    if (pos.cursor) {
      // У курсора: не даём меню уйти за край окна
      var w = el.offsetWidth, h = el.offsetHeight, W = stageW(), H = stageH();
      el.style.left = Math.max(8, Math.min(pos.x, W - w - 8)) + 'px';
      el.style.top = Math.max(8, Math.min(pos.y, H - h - 8)) + 'px';
    }
  }
  function renderPop() {
    var html = M.title ? '<div class="ph">' + esc(M.title) + '</div>' : '';
    for (var i = 0; i < M.items.length; i++) {
      var it = M.items[i];
      if (it.sep) { html += '<div class="psep"></div>'; continue; }
      html += '<div class="pi' + (i === M.i ? ' sel' : '') + (it.danger ? ' danger' : '') + (it.off ? ' off' : '') + '" data-z="' + S.zone + '" data-i="' + i + '">' +
        (it.icon ? svg(it.icon) : '<span class="ic"></span>') + '<span class="t">' + esc(it.t) + (it.sub ? '<small>' + esc(it.sub) + '</small>' : '') + '</span>' +
        (it.tog !== undefined ? '<span class="sw' + (it.tog ? ' on' : '') + '"><i></i></span>' : '') +
        (it.key && !PC ? '<span class="pk">' + it.key + '</span>' : '') + '</div>';
    }
    $('pop').innerHTML = html;
  }
  function closePop(silent) {
    show('pop', false);
    S.zone = M.back;
    if (!silent) renderZone();
  }
  function keyPop(code) {
    var i = M.i, step = code === KEY.UP ? -1 : code === KEY.DOWN ? 1 : 0;
    if (step) {
      // без круга; разделители и недоступные пункты пропускаются
      for (i += step; i >= 0 && i < M.items.length; i += step) if (!M.items[i].sep && !M.items[i].off) { M.i = i; break; }
      renderPop();
    } else if (code === KEY.ENTER) popChoose();
  }
  function popChoose() {
    var it = M.items[M.i];
    if (!it || it.sep || it.off) return;
    if (it.keep) { it.a(); renderPop(); return; }
    closePop(true);
    it.a();
    if (!shown('pop')) renderZone();
  }

  function openPlaylistMenu(pos) {
    var items = S.playlists.map(function (p) {
      var bad = !!p.lastError && p.id !== S.activePl;
      return { t: p.name, sub: bad ? 'не загрузился' : p.count ? chCount(p.count) : '', icon: p.id === S.activePl ? 'check' : '', off: bad,
        a: function () { if (p.id !== S.activePl) activatePlaylist(p, function (ok) { if (ok && inGuide()) openGuide(); }); } };
    });
    var cur = 0;
    for (var i = 0; i < S.playlists.length; i++) if (S.playlists[i].id === S.activePl) cur = i;
    items.push({ sep: true });
    items.push({ t: 'Управлять плейлистами', icon: 'settings', a: function () { openSetup('playlists', true); } });
    markSeenLater('playlists');
    openPop('plm', '', items, pos || { x: 96, y: 136 }, cur);
  }

  /** Меню действий с каналом. from — откуда открыто: guide | play | results | grid. */
  function openChannelMenu(ch, pos, from) {
    if (!ch) return;
    var fav = !!S.favs[ch.url], items = [
      { t: 'Смотреть', icon: 'play', key: 'OK', a: function () { tuneTo(ch, from === 'guide' ? S.list : null, from === 'guide' ? S.menuId : 'all'); } },
      { t: fav ? 'Убрать из избранного' : 'Добавить в избранное', icon: fav ? 'star-filled' : 'star', key: 'зелёная', a: function () { toggleFav(ch); } }];
    if (epgFor(ch)) items.push({ t: 'Программа канала', icon: 'guide', key: '▶', a: function () { showProgramme(ch); } });
    if (S.epg) items.push({ t: 'Найти в сетке телепрограммы', icon: 'grid', a: function () { openGrid(ch); } });
    openPop('ctx', ch.name + ' · ' + numText(ch.number), items, pos || { x: 1080, y: 380 }, 0);
  }

  // ---------- гид: меню · каналы · подробности ----------
  var GROUP_ROWS = 5, PITCH = 264;
  var progKey = '';

  function menuRow(i) {
    var m = S.menu[i], n = m.list ? m.list.length : 0, here = S.cur && m.id === 'g:' + S.cur.group;
    return '<div role="option" aria-selected="' + (m.id === S.menuId) + '" data-z="groups" data-i="' + i + '" class="mi' + (i === S.gi && S.zone === 'groups' ? ' sel' : '') +
      (m.id === S.menuId ? ' act' : '') + '" title="' + esc(m.title) + '">' + svg(m.icon) + '<span class="t">' + esc(m.title) + (m.isNew ? newTag(m.isNew) : '') + '</span>' +
      (here ? '<span class="dot"></span>' : '') + (m.action || (!n && (m.id === 'fav' || m.id === 'recent')) ? '' : '<span class="n">' + n + '</span>') + '</div>';
  }

  function renderMenu() {
    var last = S.menu.length - 1, n = last - TOP, html = '', i;
    for (i = 0; i < TOP; i++) html += menuRow(i);
    if (n > 0) {
      var rows = deskMode() ? Math.max(3, Math.floor(($('gmenu').clientHeight - TOP * 36 - 32 - 21 - 80) / 36)) : GROUP_ROWS;
      var sel = S.gi >= TOP && S.gi < last ? S.gi - TOP : Math.max(0, menuIndex(S.menuId) - TOP);
      var start = winTop('g', Math.min(sel, n - 1), n, Math.min(rows, n), n + ':' + S.gi);
      html += '<div class="sect">Группы</div>';
      for (i = start; i < Math.min(n, start + rows); i++) html += menuRow(TOP + i);
    }
    html += '<div class="sep"></div>' + menuRow(last);
    if (deskMode()) html += '<div class="mi' + (S.zone === 'groups' && S.gi === last + 1 ? ' sel' : '') + '" data-z="groups" data-i="' + (last + 1) + '" title="Плагины">' + svg('plug') + '<span class="t">Плагины</span></div>';
    $('gmenu').innerHTML = html;
  }

  function emptyState(icon, title, text, buttons) {
    var b = '';
    if (buttons) for (var i = 0; i < buttons.length; i++) b += '<span class="btn' + (buttons[i].sel ? ' sel' : '') + '" data-a="' + buttons[i].a + '">' + buttons[i].t + '</span>';
    return '<div class="state">' + svg(icon, 'big') + '<div class="st">' + title + '</div><div class="sx">' + text + '</div>' + (b ? '<div class="btns">' + b + '</div>' : '') + '</div>';
  }
  function emptyChannels() {
    if (S.menuId === 'fav') return emptyState('star', 'В избранном пока пусто', PC ? 'Нажмите звезду в строке канала, чтобы добавить его сюда.' : 'Удержите OK на канале или нажмите зелёную кнопку, чтобы добавить его сюда.');
    if (S.menuId === 'recent') return emptyState('history', 'Здесь появятся каналы, которые вы смотрели', 'Последние 7 дней, до 20 каналов.');
    return emptyState('alert', 'В плейлисте нет каналов', 'Ссылка открылась, но в файле нет ни одного канала. Проверьте ссылку у провайдера.',
      [{ t: 'Изменить ссылку', a: 'editlink' }, { t: 'Обновить', a: 'refresh' }]);
  }

  /** Плитки каналов: фокус стоит на второй позиции, список двигается под ним. В DOM — не больше пяти плиток. */
  function renderTiles() {
    var n = S.list.length, html = '', focus = S.zone === 'channels';
    if (!n) { $('gchs').innerHTML = emptyChannels(); return; }
    var from = Math.max(0, S.ci - 1), to = Math.min(n - 1, S.ci + (S.ci === 0 ? 3 : 2));
    for (var i = from; i <= to; i++) {
      var c = S.list[i], cur = S.cur && c.url === S.cur.url, fav = !!S.favs[c.url];
      var y = S.ci === 0 ? 18 + i * PITCH : 152 + (i - S.ci) * PITCH;
      html += '<div role="option" aria-selected="' + (i === S.ci) + '" data-z="channels" data-i="' + i + '" class="trow' + (i === S.ci ? ' cur' : '') + (i === S.ci && focus ? ' sel' : '') + (cur ? ' play' : '') +
        '" style="top:' + y + 'px"><span class="no">' + numText(c.number) + '</span><span class="tile">' + logoHtml(c, 'tl') +
        '<span class="nm">' + esc(c.name) + '</span><span class="bd">' + (cur ? svg('playing', 'pl') : '') + (fav ? svg('star-filled') : '') + '</span></span></div>';
    }
    $('gchs').innerHTML = html + '<div class="fade t"></div><div class="fade b"></div>';
  }

  /** Строки каналов (компьютер): обычный список с окном по высоте колонки. */
  function chRow(c, i, zone, sel, opt) {
    var now = Date.now(), e = epgFor(c), p = e ? progAt(e, now) : -1, cur = S.cur && c.url === S.cur.url, fav = !!S.favs[c.url];
    opt = opt || {};
    return '<div role="option" aria-selected="' + !!sel + '" data-z="' + zone + '" data-i="' + i + '" class="ch' + (sel ? ' sel' : '') + (opt.act ? ' act' : '') + (cur ? ' play' : '') + '">' +
      '<span class="no">' + (opt.no || c.number) + '</span>' + logoHtml(c) +
      '<span class="tx"><span class="nm">' + (opt.name || esc(c.name)) + '</span><span class="sub' + (p < 0 && !opt.sub ? ' none' : '') + '">' +
      (opt.sub || (p >= 0 ? esc(e.t[p]) : S.epg ? 'Нет телепрограммы' : esc(c.group))) + '</span>' + (p >= 0 && !opt.nobar ? bar(e.s[p], e.e[p], now, cur ? 'live' : '') : '') + '</span>' +
      '<span class="tail">' + (cur ? svg('playing', 'pl') : '') + (opt.star ? '<span class="fv' + (fav ? ' on' : '') + '" data-a="fav" title="' + (fav ? 'Убрать из избранного' : 'В избранное') + '">' + svg(fav ? 'star-filled' : 'star') + '</span>' : fav ? svg('star-filled') : '') + '</span></div>';
  }
  function renderRows() {
    var n = S.list.length, html = '', focus = S.zone === 'channels';
    $('gcap').innerHTML = '<b>' + esc(menuTitle(S.menuId)) + '</b><span>' + n + '</span>';
    if (!n) { $('gchs').innerHTML = emptyChannels(); return; }
    var visible = Math.max(1, Math.floor($('gchs').clientHeight / 56));
    var start = winTop('c', S.ci, n, Math.min(visible, n), S.menuId + ':' + n);
    for (var i = start; i < Math.min(n, start + visible + 1); i++) html += chRow(S.list[i], i, 'channels', i === S.ci && focus, { act: i === S.ci && !focus, star: true });
    $('gchs').innerHTML = html;
  }

  /** Дни, за которые у канала есть программа. */
  function daysOf(e) {
    var out = [], last = -1;
    for (var i = 0; i < e.s.length; i++) {
      var d = dayStart(e.s[i]);
      if (d !== last) { if (out.indexOf(d) < 0) out.push(d); last = d; }
    }
    return out.sort(function (a, b) { return a - b; });
  }

  /** Передачи канала за выбранный день. Возвращает false, если программы нет. */
  function loadProg(ch, day) {
    var e = ch ? epgFor(ch) : null, now = Date.now();
    S.epgCh = ch || null;
    S.prog = []; S.pi = 0; S.days = [];
    if (!e) return false;
    S.days = daysOf(e);
    if (!S.days.length) return false;
    if (day === undefined || S.days.indexOf(day) < 0) day = S.days.indexOf(dayStart(now)) >= 0 ? dayStart(now) : S.days[S.days.length - 1];
    S.day = day;
    var from = ch.catchupDays ? now - ch.catchupDays * DAY : Infinity;
    for (var i = 0; i < e.s.length; i++) {
      if (dayStart(e.s[i]) !== day) continue;
      if (e.s[i] <= now && e.e[i] > now) S.pi = S.prog.length;
      if (S.arch && S.cur === ch && S.arch.s === e.s[i]) S.pi = S.prog.length;
      S.prog.push({ s: e.s[i], e: e.e[i], t: e.t[i] || 'Без названия', d: descAt(e, i), arch: !!ch.catchupDays && e.s[i] >= from && e.e[i] <= now });
    }
    return S.prog.length > 0;
  }

  /** Подробности следуют за выбранным каналом — программу видно, ещё не заходя в неё. */
  function syncEpg() {
    if (S.zone === 'epg' || S.zone === 'date') return;
    if ((S.zone === 'ctx' || S.zone === 'plm' || S.zone === 'dialog') && S.epgCh) return;
    var ch = deskMode() ? (S.epgPin || S.cur) : (S.list[S.ci] || S.cur);
    var key = !ch ? '' : ch.url + '|' + (S.epg ? S.epg.updated : 0) + '|' + (S.arch && S.cur === ch ? S.arch.s : 0) + '|' + Math.floor(Date.now() / MIN);
    if (key === progKey) return;
    progKey = key;
    loadProg(ch);
  }

  function progRow(i, focus) {
    var p = S.prog[i], now = Date.now(), isNow = p.s <= now && p.e > now, past = p.e <= now;
    var playing = S.arch && S.cur && S.epgCh && S.cur.url === S.epgCh.url && S.arch.s === p.s;
    return '<div role="option" aria-selected="' + (i === S.pi && focus) + '" data-z="epg" data-i="' + i + '" class="pr' + (i === S.pi && focus ? ' sel' : '') + (isNow ? ' now' : '') + (past ? ' past' : '') + (playing ? ' rec' : '') + '">' +
      '<span class="tm">' + clock(p.s) + '</span><span class="ico">' + (isNow || playing ? svg('play-filled') : past && p.arch ? svg('history') : '') + '</span>' +
      '<span class="tt"><span class="t1">' + esc(p.t) + '</span>' + (isNow ? bar(p.s, p.e, now, 'live') : '') +
      (isNow && p.d ? '<span class="ds">' + esc(p.d) + '</span>' : '') + '</span>' +
      (deskMode() && isNow ? '<span class="live"><i></i>Эфир</span>' : '') + '</div>';
  }

  function renderDetails() {
    var ch = S.epgCh, n = S.prog.length, html = '', now = Date.now(), inEpg = S.zone === 'epg';
    if (!ch) { $('gdet').innerHTML = ''; return; }
    var e = epgFor(ch), p = e ? progAt(e, now) : -1;
    if (!deskMode()) {
      html = '<div class="now">' + (ch.catchupDays ? '<span class="badge">Архив · ' + ch.catchupDays + ' дн</span>' : '') +
        '<span class="t">' + esc(p >= 0 ? e.t[p] : ch.group) + '</span></div><div class="name">' + esc(ch.name) + '</div>';
    }
    if (!n) {
      html += epgBusy ? '<div class="status"><span class="spin"></span>' + esc(S.epgStatus || 'Загружаю программу…') + '</div>'
        : S.epg || hasEpgSource() ? emptyState('guide', 'Для этого канала телепрограммы нет', 'Провайдер не передаёт расписание. Канал можно смотреть.')
        : emptyState('guide', 'Телепрограммы нет', 'В плейлисте нет ссылки на телепрограмму. Её можно указать в настройках плейлиста.');
      $('gdet').innerHTML = (deskMode() ? deskProgHead(ch) : '') + html;
      return;
    }
    var di = S.days.indexOf(S.day), dsel = S.zone === 'date';
    if (deskMode()) {
      html += deskProgHead(ch) + '<div class="tabs">';
      for (var k = 0; k < S.days.length; k++) html += '<span class="tab' + (k === di ? ' act' : '') + '" data-a="day" data-v="' + k + '">' + dayTab(S.days[k], now) + '</span>';
      html += '</div>';
    } else {
      html += '<div class="date' + (dsel ? ' sel' : '') + '" data-z="date" data-i="0">' + (dsel && di > 0 ? svg('chevron-left') : '') +
        '<span>' + dayTitle(S.day, now) + '</span>' + (dsel && di < S.days.length - 1 ? svg('chevron-right') : '') + '</div>';
      var cur = S.prog[S.pi];
      if (isNew('desc') && cur && cur.d) html += '<div class="newline">' + newTag('desc', 'описание — новое') + '</div>';
    }
    var visible = deskMode() ? Math.max(3, Math.floor(($('gdet').clientHeight - 96) / 44)) : 5;
    // Со стороны видно «одна прошедшая, текущая, следующие»; внутри фокус держится второй строкой
    var start = winTop('p', S.pi, n, Math.min(visible, n), ch.url + ':' + S.day + ':' + inEpg, deskMode() && inEpg ? undefined : 1);
    html += '<div class="prs" role="listbox">';
    for (var i = start; i < Math.min(n, start + visible); i++) html += progRow(i, inEpg);
    $('gdet').innerHTML = html + '</div><div class="fade b"></div>';
    if (S.prog[S.pi] && S.prog[S.pi].d) markSeenLater('desc');
  }
  var seenTimers = {};
  /** Метка «новое» пропадает после первого использования — но не в тот же миг, а при следующем открытии. */
  function markSeenLater(key) {
    if (S.seen[key] || seenTimers[key]) return;
    seenTimers[key] = setTimeout(function () { markSeen(key); }, 4000);
  }

  function renderHead() {
    var a = activePlaylist(), many = S.playlists.length > 1, now = Date.now(), d = new Date(now);
    $('gtitle').className = (S.zone === 'head' ? 'sel' : '') + (many ? ' many' : '');
    $('gtitle').innerHTML = svg('list') + '<span class="t">' + esc(a ? a.name : 'IPTV') + '</span>' + (deskMode() && a ? '<span class="n"> · ' + S.channels.length + '</span>' : '') +
      (many ? svg('chevron-down', 'chev') : '') + newTag('playlists');
    $('gclock').innerHTML = '<b>' + clock(now) + '</b><span>' + dateText(now) + ' ' + d.getFullYear() + '<br>' + WDAYS[d.getDay()] + '</span>';
  }

  function renderHints() {
    var h = '', z = S.zone, ch = S.list[S.ci];
    if (z === 'channels') {
      h = (ch ? hint('ok', 'Смотреть', 'ok') + hint('hold', 'Действия', 'ctx') : '') + (ch && S.prog.length ? hint('r', 'Программа', 'right') : '') +
        hint('l', 'Меню', 'left') + hint('0–9', 'Номер') + hint('back', 'Закрыть', 'back');
    } else if (z === 'groups') {
      h = hint('ud', 'Раздел') + hint('ok', 'Открыть', 'ok') + (S.playlists.length > 1 ? hint('u', 'сверху Плейлист') : '') + hint('back', 'Закрыть', 'back');
    } else if (z === 'epg') {
      h = hint('ud', 'Передачи') + hint('ok', 'Смотреть', 'ok') + hint('u', 'сверху День') + hint('l', 'К каналам', 'left') + hint('back', 'Закрыть', 'back');
    } else if (z === 'date') {
      h = hint('lr', 'День') + hint('d', 'К передачам', 'down') + hint('back', 'К передачам', 'back');
    } else if (z === 'head') {
      h = hint('ok', 'Сменить плейлист', 'ok') + hint('d', 'К меню', 'down') + hint('back', 'К меню', 'back');
    } else if (z === 'ctx' || z === 'plm') {
      h = hint('ud', 'Выбор') + hint('ok', 'Выбрать', 'ok') + hint('back', 'Закрыть', 'back');
    }
    $('ghints').innerHTML = h;
  }

  function renderGuide() {
    if (!shown('guide')) return;
    var g = $('guide');
    cls(g, 'zg', S.zone === 'groups' || S.zone === 'head');
    cls(g, 'zc', S.zone === 'channels');
    cls(g, 'ze', S.zone === 'epg' || S.zone === 'date');
    syncEpg();
    renderHead();
    renderMenu();
    if (deskMode()) renderRows(); else renderTiles();
    renderDetails();
    renderHints();
  }
  var guideQueued = false;
  /** При удержании ▲▼ перерисовываем не чаще одного кадра. */
  function renderGuideSoon() {
    if (guideQueued) return;
    guideQueued = true;
    raf(function () { guideQueued = false; renderGuide(); });
  }

  function selectMenu(id) {
    S.menuId = id;
    S.gi = menuIndex(id);
    S.list = listOf(id) || [];
    var at = S.cur ? S.list.indexOf(S.cur) : -1;
    S.ci = at < 0 ? 0 : at;
    if (id === 'recent') markSeen('recent');
  }

  /** Открывает гид: фокус на играющем канале в его разделе. */
  function openGuide(zone) {
    if (!S.channels.length && !activePlaylist()) { openOnboarding(); return; }
    closeLayers();
    var g = load('group', 'all'), gl = listOf(g);
    selectMenu(gl && gl.indexOf(S.cur) >= 0 ? g : 'all');
    S.zone = zone || (S.list.length ? 'channels' : 'groups');
    progKey = '';
    show('guide', true);
    renderGuide();
  }

  function closeGuide() {
    if (deskMode()) { S.zone = 'play'; renderGuide(); return; }
    S.zone = 'play';
    show('guide', false);
  }

  /** Программа канала в гиде: фокус на текущей передаче (жёлтая, GUIDE, ▶ в плеере, «Программа канала»). */
  function showProgramme(ch, at) {
    if (!shown('guide') || deskMode() && S.zone === 'play') openGuide('channels');
    var i = S.list.indexOf(ch);
    if (i < 0) { selectMenu('all'); i = S.list.indexOf(ch); }
    S.ci = Math.max(0, i);
    S.zone = 'channels';
    S.epgPin = ch;
    progKey = '';
    if (!loadProg(ch)) { renderGuide(); toast(epgBusy ? 'Телепрограмма ещё загружается…' : 'Для этого канала телепрограммы нет'); return false; }
    if (at) {
      loadProg(ch, dayStart(at));
      for (var k = 0; k < S.prog.length; k++) if (S.prog[k].s === at) S.pi = k;
    }
    S.zone = 'epg';
    renderGuide();
    return true;
  }

  /** Включает канал из любого списка: запоминает раздел, из которого он включён. */
  function tuneTo(ch, list, group) {
    S.playList = list && list.length ? list : S.channels;
    save('group', group || 'all');
    var same = S.cur && S.cur.url === ch.url && !S.arch && !P.state;
    closeLayers();
    if (same) showInfo(); else play(ch, null);
    if (deskMode()) renderGuide();
  }

  function chooseChannel() {
    var ch = S.list[S.ci];
    if (ch) tuneTo(ch, S.list, S.menuId);
  }

  function chooseProgramme() {
    var p = S.prog[S.pi], ch = S.epgCh, now = Date.now();
    if (!p || !ch) return;
    if (p.s <= now && p.e > now) tuneTo(ch, S.list.indexOf(ch) >= 0 ? S.list : null, S.list.indexOf(ch) >= 0 ? S.menuId : 'all');
    else if (p.e <= now && p.arch) { closeLayers(); play(ch, p); if (deskMode()) renderGuide(); }
    else if (p.e <= now) toast('У этой передачи нет записи');
    else toast('Передача начнётся в ' + clock(p.s));
  }

  function toggleFav(ch) {
    if (S.favs[ch.url]) delete S.favs[ch.url]; else S.favs[ch.url] = 1;
    save('favs', S.favs);
    toast('«' + ch.name + '» ' + (S.favs[ch.url] ? 'добавлен в избранное' : 'убран из избранного'), 'fav');
    buildMenu();
    if (shown('guide')) {
      if (S.menuId === 'fav') { S.list = listOf('fav'); S.ci = Math.max(0, Math.min(S.ci, S.list.length - 1)); if (!S.list.length && S.zone === 'channels') S.zone = 'groups'; }
      else S.list = listOf(S.menuId) || S.list;
      S.gi = menuIndex(S.menuId);
      renderGuide();
    }
    if (shown('ctl')) renderCtl();
    if (S.zone === 'results') renderSearch();
  }

  function shiftDay(dir) {
    var i = S.days.indexOf(S.day) + dir;
    if (i < 0 || i >= S.days.length) return;
    loadProg(S.epgCh, S.days[i]);
    renderGuide();
  }

  function keyGroups(code) {
    var last = S.menu.length - 1 + (deskMode() ? 1 : 0);
    if (code === KEY.UP) {
      if (S.gi > 0) S.gi--;
      else if (S.playlists.length > 1) { S.zone = 'head'; renderGuide(); return; }
      else return;
    } else if (code === KEY.DOWN) { if (S.gi < last) S.gi++; else return; }
    else if (code === KEY.CH_UP) S.gi = Math.max(0, S.gi - GROUP_ROWS);
    else if (code === KEY.CH_DOWN) S.gi = Math.min(last, S.gi + GROUP_ROWS);
    else if (code === KEY.ENTER || code === KEY.RIGHT) {
      var m = S.menu[S.gi];
      if (!m) { if (code === KEY.ENTER) openSetup('plugins'); return; }
      if (m.id === 'settings') { if (code === KEY.ENTER) openSetup(); return; }
      if (m.id === 'search') { openSearch(); return; }
      selectMenu(m.id);
      if (!S.list.length) { renderGuide(); return; } // пустой раздел — фокус остаётся в меню
      S.zone = 'channels';
      renderGuide();
      return;
    } else return;
    // При движении по разделам сразу показываем их каналы
    var cur = S.menu[S.gi];
    if (cur && !cur.action) { S.menuId = cur.id; S.list = cur.list; S.ci = Math.max(0, S.list.indexOf(S.cur)); if (cur.id === 'recent') markSeen('recent'); }
    renderGuideSoon();
  }

  function keyChannels(code) {
    var n = S.list.length;
    if (code === KEY.UP) { if (S.ci > 0) S.ci--; else return; }
    else if (code === KEY.DOWN) { if (S.ci < n - 1) S.ci++; else return; }
    else if (code === KEY.CH_UP) S.ci = Math.max(0, S.ci - 7);
    else if (code === KEY.CH_DOWN) S.ci = Math.min(n - 1, S.ci + 7);
    else if (code === KEY.LEFT) { S.zone = 'groups'; S.gi = menuIndex(S.menuId); renderGuide(); return; }
    else if (code === KEY.RIGHT) {
      if (deskMode()) { S.epgPin = S.list[S.ci]; progKey = ''; syncEpg(); }
      if (S.list[S.ci] && S.prog.length && S.epgCh === S.list[S.ci]) { S.zone = 'epg'; renderGuide(); }
      return;
    } else return;
    renderGuideSoon();
  }

  function keyEpg(code) {
    var n = S.prog.length;
    if (code === KEY.UP) { if (S.pi > 0) S.pi--; else { S.zone = 'date'; renderGuide(); return; } }
    else if (code === KEY.DOWN) { if (S.pi < n - 1) S.pi++; else return; }
    else if (code === KEY.CH_UP) S.pi = Math.max(0, S.pi - 5);
    else if (code === KEY.CH_DOWN) S.pi = Math.min(n - 1, S.pi + 5);
    else if (code === KEY.LEFT) { S.zone = 'channels'; progKey = ''; renderGuide(); return; }
    else if (code === KEY.ENTER) { chooseProgramme(); return; }
    else return;
    renderGuideSoon();
  }

  function keyGuide(code) {
    var z = S.zone;
    if (z === 'head') {
      if (code === KEY.DOWN) { S.zone = 'groups'; S.gi = 0; renderGuide(); }
      else if (code === KEY.ENTER) openPlaylistMenu();
    } else if (z === 'groups') keyGroups(code);
    else if (z === 'channels') keyChannels(code);
    else if (z === 'epg') keyEpg(code);
    else if (z === 'date') {
      if (code === KEY.LEFT) shiftDay(-1);
      else if (code === KEY.RIGHT) shiftDay(1);
      else if (code === KEY.DOWN) { S.zone = 'epg'; S.pi = 0; renderGuide(); }
    }
  }

  // ---------- плеер ----------
  // Samsung — собственный плеер AVPlay; LG и Safari умеют HLS сами; в остальных браузерах помогает hls.js.
  var video = $('video');
  var av = null, hls = null, paused = false;
  var retries = 0, retryTimer = null;
  var nativeHls = !!video.canPlayType && video.canPlayType('application/vnd.apple.mpegurl') !== '';
  // Состояние слоёв плеера: state — блок загрузки/ошибки, started — пошёл ли первый кадр
  var P = { state: null, code: 0, started: false, bufs: [], lastWarn: 0, trouble: false, zapTimer: null, ctlTimer: null, infoTimer: null,
    volTimer: null, imgTimer: null, btns: [], bi: 0 };

  function loadScript(src, done) {
    var s = document.createElement('script');
    s.src = src;
    s.onload = function () { done(true); };
    s.onerror = function () { done(false); };
    document.head.appendChild(s);
  }

  function preparePlayer(done) {
    if (TIZEN) {
      loadScript('$WEBAPIS/webapis/webapis.js', function () {
        if (window.webapis && window.webapis.avplay) {
          var o = document.createElement('object');
          o.id = 'av';
          o.type = 'application/avplayer';
          document.body.insertBefore(o, document.body.firstChild);
          av = window.webapis.avplay;
          cls(document.body, 'av', true);
        }
        done();
      });
    } else if (!nativeHls) {
      loadScript('hls.min.js', function () { done(); });
    } else done();
  }

  function stopPlayer() {
    clearTimeout(retryTimer);
    clearTimeout(P.imgTimer);
    paused = false;
    if (av) { try { av.stop(); } catch (e) {} try { av.close(); } catch (e2) {} }
    else {
      if (hls) { try { hls.destroy(); } catch (e4) {} hls = null; }
      try { video.pause(); video.removeAttribute('src'); video.load(); } catch (e3) {}
    }
  }

  function streamUrl() {
    var ch = S.cur, url = S.arch ? (catchupUrl(ch, S.arch.s, S.arch.e) || ch.url) : ch.url;
    if (PL.hasStream()) {
      var copy = {};
      for (var k in ch) if (ch.hasOwnProperty(k)) copy[k] = ch[k];
      copy.url = url;
      copy.archive = !!S.arch;
      url = PL.stream(copy) || url;
    }
    return url;
  }

  function start() {
    var ch = S.cur, url = streamUrl();
    stopPlayer();
    P.started = false;
    setState('loading');
    if (av) {
      try {
        av.open(url);
        av.setDisplayRect(0, 0, 1920, 1080);
        av.setListener({
          onbufferingstart: function () { buffering(); },
          onbufferingcomplete: function () { if (P.state === 'buffer') setState(null); },
          onstreamcompleted: function () { ended(ch); },
          onerror: function () { playFailed(ch, 0); }
        });
        av.prepareAsync(function () { try { av.play(); } catch (e) {} playing(); }, function () { playFailed(ch, 0); });
      } catch (e) { playFailed(ch, 0); }
    } else {
      var isHls = /\.m3u8?($|\?)|\/hls\//i.test(url);
      if (!nativeHls && isHls && window.Hls && window.Hls.isSupported()) {
        hls = new window.Hls({ enableWorker: true });
        hls.on(window.Hls.Events.ERROR, function (ev, data) {
          // Код ответа сервера есть только у hls.js: по нему отличаем «канал недоступен» (403/404)
          if (data && data.fatal) playFailed(ch, data.response && data.response.code ? data.response.code : 0);
        });
        hls.loadSource(url);
        hls.attachMedia(video);
      } else {
        video.src = url;
      }
      applyVolume();
      var p = video.play();
      if (p && p['catch']) p['catch'](function () {});
    }
  }

  function playing() {
    retries = 0;
    P.started = true;
    setState(null);
    hideSplash();
    if (P.trouble) { P.trouble = false; toast('Связь восстановлена', 'ok'); }
    // Звук идёт, кадров нет дольше 5 с — говорим об этом (только там, где видео — обычный <video>)
    clearTimeout(P.imgTimer);
    if (!av) P.imgTimer = setTimeout(function () {
      if (P.started && !P.state && !video.paused && video.videoWidth === 0 && video.currentTime > 0) setState('noimage');
    }, 5000);
  }

  function buffering() {
    if (!P.started) return;
    var now = Date.now();
    setState('buffer');
    P.bufs.push(now);
    while (P.bufs.length && now - P.bufs[0] > 60000) P.bufs.shift();
    if (P.bufs.length >= 3 && now - P.lastWarn > 5 * 60000) {
      P.lastWarn = now; P.bufs = []; P.trouble = true;
      toast('Поток часто останавливается. Проверьте интернет или выберите другой канал', 'warn', null, 6000);
    }
  }

  function ended(ch) {
    if (!(S.arch && S.cur === ch)) { playFailed(ch, 0); return; }
    // Запись доиграла: следующая передача в записи, если она уже прошла; иначе — эфир
    var e = epgFor(ch), i = e ? e.s.indexOf(S.arch.s) : -1, now = Date.now();
    if (i >= 0 && i + 1 < e.s.length && e.e[i + 1] <= now) play(ch, { s: e.s[i + 1], e: e.e[i + 1], t: e.t[i + 1] || 'Без названия' });
    else { play(ch, null); toast('Прямой эфир'); }
  }

  function playFailed(ch, code) {
    if (!S.cur || S.cur.url !== ch.url) return;
    P.trouble = true;
    if (code === 403 || code === 404) { P.code = code; hideSplash(); setState('denied'); return; } // автоповторов нет
    if (retries < 2) {
      retries++;
      clearTimeout(retryTimer);
      retryTimer = setTimeout(function () { if (S.cur && S.cur.url === ch.url) start(); }, 2000 * retries);
    } else {
      hideSplash();
      setState('error');
    }
  }

  video.addEventListener('playing', function () { if (S.cur) playing(); });
  video.addEventListener('waiting', buffering);
  video.addEventListener('ended', function () { if (S.cur) ended(S.cur); });
  video.addEventListener('error', function () { if (S.cur && !hls && video.getAttribute('src')) playFailed(S.cur, 0); });

  /** Единая точка включения канала: запоминает последний и предыдущий канал, «Недавние», показывает инфо. */
  function play(ch, programme) {
    clearTimeout(P.zapTimer);
    S.sel = null;
    show('zap', false);
    if (S.cur && S.cur.url !== ch.url) S.prev = S.cur; // для кнопки «предыдущий канал»
    S.cur = ch;
    S.arch = programme || null;
    retries = 0;
    P.bufs = [];
    if (!programme) save('last', ch.url);
    addRecent(ch);
    S.menu[2] && (S.menu[2].list = recentList());
    cls(document.body, 'arch', !!S.arch);
    renderArchTag();
    start();
    showInfo();
  }
  function retryPlay() { if (S.cur) { retries = 0; start(); } }
  function goLive() { if (S.cur) { play(S.cur, null); toast('Прямой эфир'); } }
  function playPrev() { if (S.prev) { var ch = findByUrl(S.prev.url) || S.prev; closeCtl(); play(ch, null); if (deskMode()) renderGuide(); } }

  function position() {
    try { return av ? av.getCurrentTime() / 1000 : video.currentTime || 0; } catch (e) { return 0; }
  }
  function archLength() { return S.arch ? (Math.min(S.arch.e, Date.now()) - S.arch.s) / 1000 : 0; }
  function seek(sec) {
    if (!S.arch) return;
    try {
      if (av) { if (sec > 0) av.jumpForward(sec * 1000); else av.jumpBackward(-sec * 1000); }
      else video.currentTime = Math.max(0, position() + sec);
    } catch (e) { /* поток не перематывается */ }
    renderTimeline();
  }
  function seekTo(sec) {
    if (!S.arch || av) return;
    try { video.currentTime = Math.max(0, Math.min(archLength(), sec)); } catch (e) { /* поток не перематывается */ }
    renderTimeline();
  }
  function togglePause() {
    if (!S.arch) return;
    paused = !paused;
    try {
      if (av) { if (paused) av.pause(); else av.play(); }
      else if (paused) video.pause(); else video.play();
    } catch (e) { /* ничего */ }
    renderArchTag();
    if (shown('ctl')) { renderCtl(); armCtl(); }
    showInfo();
  }

  /** ▲▼ листают плашку канала; поток включается через 400 мс после последнего нажатия. */
  function zap(dir) {
    var list = S.playList.length ? S.playList : S.channels;
    if (!list.length) return;
    var i = list.indexOf(S.sel || S.cur);
    S.sel = list[i < 0 ? 0 : (i + dir + list.length) % list.length];
    $('zap').innerHTML = svg('chevron-up') + svg('chevron-down') + '<span>включится через 0,4 с после последнего нажатия</span>';
    show('zap', true);
    showInfo();
    clearTimeout(P.zapTimer);
    P.zapTimer = setTimeout(commitZap, 400);
  }
  function commitZap() {
    var ch = S.sel;
    S.sel = null;
    show('zap', false);
    if (!ch) return;
    if (ch === S.cur && !S.arch && !P.state) showInfo(); else play(ch, null);
    if (deskMode()) renderGuide();
  }

  // ---------- состояния плеера: загрузка, буфер, ошибки (SPEC 4.4) ----------
  function setState(name) {
    var el = $('state'), ch = S.cur;
    P.state = name;
    if (!name || !ch) { el.className = ''; el.innerHTML = ''; return; }
    var row = '<div class="srow">' + logoHtml(ch) + '<span class="no">' + ch.number + '</span><span class="nm">' + esc(ch.name) + '</span></div>';
    var html = '';
    if (name === 'loading' || name === 'buffer') {
      html = '<div class="mid"><span class="spin big"></span><div class="scap">Подгружаем поток…</div></div>' + (name === 'loading' ? row : '');
    } else {
      var title, text, icon = 'alert', buttons;
      if (name === 'error') {
        icon = 'warning'; title = 'Канал не включился';
        text = 'Нет связи с сервером провайдера. ' + (PC ? 'Enter — повторить, ↑↓ — другой канал.' : 'OK — повторить, ▲▼ — другой канал, ◀ — список.');
        buttons = [['retry', 'Повторить'], ['guide', 'Список каналов']];
      } else if (name === 'denied') {
        title = 'Канал недоступен';
        text = 'Провайдер не отдаёт этот поток (ошибка ' + P.code + '). Обычно это ограничение подписки.';
        buttons = [['guide', 'Другой канал']];
      } else {
        title = 'Есть звук, нет изображения';
        text = 'Канал передаёт только звук или изображение ещё не пришло.';
        buttons = [['retry', 'Повторить']];
      }
      html = '<div class="box">' + row + svg(icon, 'big ' + (name === 'error' ? 'warn' : 'err')) + '<div class="st">' + title + '</div><div class="sx">' + text + '</div><div class="btns">';
      for (var i = 0; i < buttons.length; i++) html += '<span class="btn' + (i === 0 ? ' sel' : '') + '" data-a="' + buttons[i][0] + '">' + buttons[i][1] + '</span>';
      html += '</div></div>';
    }
    el.className = 'on ' + name;
    el.innerHTML = html;
  }
  /** OK в плеере, когда показана ошибка: нажимает первую кнопку блока. */
  function stateAction() {
    if (P.state === 'error' || P.state === 'noimage') { retryPlay(); return true; }
    if (P.state === 'denied') { openGuide(); return true; }
    return false;
  }

  // ---------- инфо о канале (OSD), верхняя строка ----------
  function archText() { return S.arch ? 'Запись · ' + whenText(S.arch.s, Date.now()) : ''; }
  function renderArchTag() {
    $('archTag').innerHTML = S.arch ? '<span class="tag">' + svg('history') + archText() + '</span>' + (paused ? '<span class="pz">Пауза</span>' : '') : '';
  }
  function renderOsd() {
    var ch = S.sel || S.cur;
    if (!ch) return;
    var now = Date.now(), arch = !S.sel && S.arch, e = epgFor(ch), p = e ? progAt(e, now) : -1, line;
    if (arch) {
      var len = Math.max(1, archLength());
      line = '<span class="tm">' + clock(arch.s) + ' – ' + clock(arch.e) + '</span><span class="tt">' + esc(arch.t) + '</span>' +
        '<span class="bar rec"><i style="width:' + Math.min(100, Math.round(position() * 100 / len)) + '%"></i></span>';
    } else if (p >= 0) {
      line = '<span class="tm">' + clock(e.s[p]) + ' – ' + clock(e.e[p]) + '</span><span class="tt">' + esc(e.t[p] || 'Без названия') + '</span>' + bar(e.s[p], e.e[p], now, 'live') +
        (p + 1 < e.s.length ? '<span class="vsep"></span><span class="tm">' + clock(e.s[p + 1]) + '</span><span class="nx">' + esc(e.t[p + 1]) + '</span>' : '');
    } else line = '<span class="tm">' + (epgBusy ? 'Телепрограмма загружается…' : 'Для этого канала нет телепрограммы') + '</span>';
    $('osd').innerHTML = logoHtml(ch, 'big') +
      '<div class="num">' + svg('chevron-up') + '<b>' + numText(ch.number) + '</b>' + svg('chevron-down') + '</div>' +
      '<div class="tx"><div class="grp">' + esc(ch.group) + (arch ? '<span class="tag">' + svg('history') + archText() + '</span>' : '<span class="live"><i></i>Эфир</span>') + '</div>' +
      '<div class="nm">' + esc(ch.name) + '</div><div class="line">' + line + '</div></div>';
  }
  function renderTopRight() {
    var el = $('topRight');
    if (shown('ctl')) el.innerHTML = '<div class="hints">' + hint('lr', 'Выбор') + hint('ok', 'Нажать', 'ok') + hint('back', 'Скрыть', 'back') + '</div>';
    else el.innerHTML = '<span class="clk">' + clock(Date.now()) + '</span>';
    cls(el, 'on', shown('ctl') || shown('osd') && !shown('vol'));
  }
  function showInfo() {
    if (!S.cur || !playerZone()) return;
    renderOsd();
    show('osd', true); show('shadeB', true);
    renderTopRight();
    clearTimeout(P.infoTimer);
    if (!(S.arch && paused)) P.infoTimer = setTimeout(hideInfo, 5000);
  }
  function hideInfo() {
    clearTimeout(P.infoTimer);
    show('osd', false);
    if (!shown('ctl')) show('shadeB', false);
    renderTopRight();
  }
  function playerZone() { return S.zone === 'play' || S.zone === 'ctl' || deskMode() && S.zone !== 'dialog'; }
  setInterval(function () {
    if (shown('osd')) { renderOsd(); renderTopRight(); }
    if (shown('ctl') && S.arch) renderTimeline();
    if (deskMode()) deskTick();
  }, 1000);

  // ---------- панель управления (SPEC 4.2) ----------
  function ctlButtons() {
    var ch = S.cur, fav = ch && S.favs[ch.url], b;
    if (S.arch) {
      b = [
        { id: 'rew', icon: 'rewind', t: '−30 с', off: position() < 1 },
        { id: 'pause', icon: paused ? 'play' : 'pause', t: paused ? 'Продолжить' : 'Пауза' },
        { id: 'fwd', icon: 'forward', t: '+30 с', off: archLength() - position() < 30, end: true },
        { id: 'live', icon: 'back', t: 'Вернуться в эфир', text: 'Вернуться в эфир', end: true },
        { id: 'info', icon: 'info', t: 'О передаче' },
        { id: 'grid', icon: 'guide', t: 'Телепрограмма — сетка каналов', off: !S.epg, isNew: 'grid' }];
    } else {
      b = [
        { id: 'list', icon: 'list', t: 'Каналы — список' },
        { id: 'up', icon: 'chevron-up', t: 'Канал выше' },
        { id: 'down', icon: 'chevron-down', t: 'Канал ниже' },
        { id: 'prev', icon: 'previous', t: 'Предыдущий канал', off: !S.prev, end: true },
        { id: 'info', icon: 'info', t: 'О передаче' },
        { id: 'grid', icon: 'guide', t: 'Телепрограмма — сетка каналов', off: !S.epg, isNew: 'grid' },
        { id: 'rec', icon: 'history', t: 'Записи этого канала', soft: !(ch && ch.catchup), end: true }];
      if (VOL.mute) b.push({ id: 'mute', icon: S.muted ? 'mute' : 'volume', t: S.muted ? 'Включить звук' : 'Без звука', on: S.muted, end: true, isNew: 'volume' });
      b.push({ id: 'fav', icon: fav ? 'star-filled' : 'star', t: fav ? 'Убрать из избранного' : 'В избранное', on: fav });
      b.push({ id: 'more', icon: 'more', t: 'Ещё' });
    }
    return b;
  }
  function renderCtl() {
    var b = P.btns = ctlButtons(), html = '';
    if (P.bi >= b.length) P.bi = b.length - 1;
    while (b[P.bi] && b[P.bi].off && P.bi > 0) P.bi--;
    for (var i = 0; i < b.length; i++) {
      html += '<span role="button" aria-label="' + b[i].t + '" title="' + b[i].t + '" class="cb' + (i === P.bi && S.zone === 'ctl' ? ' sel' : '') + (b[i].off || b[i].soft ? ' off' : '') + (b[i].on ? ' active' : '') + (b[i].text ? ' wide' : '') +
        (b[i].end && i < b.length - 1 ? ' end' : '') + '" data-z="ctl" data-i="' + i + '">' + svg(b[i].icon) + (b[i].text || '') + '</span>';
    }
    $('ctlCap').innerHTML = b[P.bi] && S.zone === 'ctl' ? esc(b[P.bi].t) + (b[P.bi].isNew ? newTag(b[P.bi].isNew) : '') : '';
    $('ctlRow').innerHTML = html + (deskMode() ? deskCtlExtra() : '');
    renderTimeline();
  }
  function renderTimeline() {
    var el = $('timeline');
    if (!S.arch || !shown('ctl')) { cls(el, 'on', false); return; }
    var len = Math.max(1, archLength()), pos = Math.min(len, position()), buf = 0;
    try { if (!av && video.buffered.length) buf = video.buffered.end(video.buffered.length - 1); } catch (e) { buf = 0; }
    el.innerHTML = '<span class="t">' + duration(pos) + '</span><span class="tr" data-a="seek"><i class="bf" style="width:' + Math.min(100, buf * 100 / len) + '%"></i>' +
      '<i class="fl" style="width:' + pos * 100 / len + '%"></i><i class="th" style="left:' + pos * 100 / len + '%"></i></span><span class="t">−' + duration(len - pos) + '</span>';
    cls(el, 'on', true);
  }
  function armCtl() {
    clearTimeout(P.ctlTimer);
    if (!(S.arch && paused)) P.ctlTimer = setTimeout(function () { if (!overCtl) closeCtl(); else armCtl(); }, 4000);
  }
  function openCtl(focusId) {
    if (!S.cur) return;
    if (S.zone !== 'play' && S.zone !== 'ctl') return;
    S.zone = 'ctl';
    var b = ctlButtons();
    P.bi = 0;
    for (var i = 0; i < b.length; i++) if (b[i].id === (focusId || (S.arch ? 'pause' : 'list'))) P.bi = i;
    cls(document.body, 'ctl', true);
    show('ctl', true); show('shadeB', true); show('shadeT', true);
    renderCtl();
    showInfo();
    armCtl();
  }
  function closeCtl() {
    clearTimeout(P.ctlTimer);
    if (!shown('ctl')) return;
    show('ctl', false); show('shadeT', false); show('timeline', false);
    cls(document.body, 'ctl', false);
    if (S.zone === 'ctl') S.zone = 'play';
    if (!shown('osd')) show('shadeB', false);
    renderTopRight();
  }
  function ctlPress(i) {
    var b = P.btns[i], id = b && b.id;
    if (!b || b.off) return;
    armCtl();
    if (b.soft) { toast('Провайдер не хранит записи этого канала'); return; }
    if (id === 'list') openGuide();
    else if (id === 'up') zap(1);
    else if (id === 'down') zap(-1);
    else if (id === 'prev') playPrev();
    else if (id === 'info') { closeCtl(); showInfo(); }
    else if (id === 'grid') openGrid(S.cur);
    else if (id === 'rec') showProgramme(S.cur);
    else if (id === 'mute') { toggleMute(); renderCtl(); }
    else if (id === 'fav') toggleFav(S.cur);
    else if (id === 'more') openMore();
    else if (id === 'rew') { seek(-30); renderCtl(); }
    else if (id === 'fwd') { seek(30); renderCtl(); }
    else if (id === 'pause') togglePause();
    else if (id === 'live') { closeCtl(); goLive(); }
  }
  function openMore() {
    closeCtl();
    openPop('ctx', '', [
      { t: 'Настройки', icon: 'settings', a: function () { openSetup(); } },
      { t: 'Плагины', icon: 'plug', a: function () { openSetup('plugins'); } }], deskMode() ? { x: lastMouse.x, y: lastMouse.y - 120, cursor: true } : { x: 918, y: 760 }, 0);
  }
  function keyCtl(code) {
    var b = P.btns, i = P.bi;
    armCtl();
    if (code === KEY.LEFT) { for (i--; i >= 0; i--) if (!b[i].off) { P.bi = i; break; } renderCtl(); }
    else if (code === KEY.RIGHT) { for (i++; i < b.length; i++) if (!b[i].off) { P.bi = i; break; } renderCtl(); }
    else if (code === KEY.ENTER) ctlPress(P.bi);
    else if (code === KEY.UP || code === KEY.DOWN) closeCtl();
    else return false;
    return true;
  }

  // ---------- громкость и «Без звука» ----------
  // level — мы сами меняем уровень и показываем шкалу; mute — умеем выключить звук.
  // На Samsung и LG уровнем управляет телевизор и показывает свою шкалу (SPEC 7.1, 10.3 п. 3).
  var tvAudio = null;
  try { tvAudio = TIZEN && window.tizen.tvaudiocontrol ? window.tizen.tvaudiocontrol : null; } catch (e0) { tvAudio = null; }
  var VOL = { level: !TIZEN && !WEBOS, mute: !TIZEN || !!tvAudio };
  function applyVolume() {
    try {
      if (tvAudio) { tvAudio.setMute(!!S.muted); return; }
      if (VOL.level) video.volume = Math.max(0, Math.min(1, S.vol / 100));
      video.muted = !!S.muted;
    } catch (e) { /* плеер не дал поменять звук */ }
  }
  function showVol() {
    var el = $('vol');
    markSeen('volume');
    if (VOL.level) {
      el.className = 'on' + (S.muted ? ' muted' : '');
      el.innerHTML = svg(S.muted ? 'mute' : 'volume') + '<span class="t">' + (S.muted ? 'Без звука' : 'Громкость') + '</span><span class="bar"><i style="width:' + S.vol + '%"></i></span><span class="n">' + S.vol + '</span>';
    } else muteTag();
    renderTopRight();
    clearTimeout(P.volTimer);
    P.volTimer = setTimeout(muteTag, 2000);
  }
  /** Значок «Без звука» остаётся, пока звук выключен. */
  function muteTag() {
    var el = $('vol');
    el.className = S.muted ? 'on tag muted' : '';
    el.innerHTML = S.muted ? svg('mute') + '<span class="t">Без звука</span>' : '';
    renderTopRight();
  }
  function setVolume(v) {
    if (!VOL.level) return;
    S.vol = Math.max(0, Math.min(100, Math.round(v)));
    if (S.muted && S.vol > 0) S.muted = false;
    save('vol', S.vol); save('muted', S.muted);
    applyVolume();
    showVol();
    if (shown('ctl')) renderCtl();
  }
  function toggleMute() {
    if (!VOL.mute) return;
    S.muted = !S.muted;
    save('muted', S.muted);
    applyVolume();
    showVol();
    if (shown('ctl')) renderCtl();
  }

  // ---------- номер канала с подсказками ----------
  var N = { on: false, digits: '', items: [], sel: 0, timer: null };

  /** Каналы, чей номер начинается с набранных цифр: сам номер, потом 10–19, потом 100–199 и так далее. */
  function numMatches(digits, max) {
    var n = parseInt(digits, 10), total = S.channels.length, out = [];
    if (!(n > 0)) return out;
    if (n <= total) out.push(S.channels[n - 1]);
    for (var mult = 10; n * mult <= total && out.length < max; mult *= 10)
      for (var k = n * mult; k < (n + 1) * mult && k <= total && out.length < max; k++) out.push(S.channels[k - 1]);
    return out;
  }

  function typeDigit(d) {
    if (!S.channels.length) return;
    if (N.digits.length >= String(S.channels.length).length) N.digits = ''; // номер набран целиком — начинаем новый
    if (!N.digits && d === 0) { if (N.on) numClose(); return; } // номера начинаются с единицы
    N.digits += d;
    numUpdate();
  }

  function numUpdate() {
    var n = parseInt(N.digits, 10);
    N.items = numMatches(N.digits, 4);
    N.sel = 0;
    if (!N.on) { hideInfo(); closeCtl(); }
    N.on = true;
    renderNum();
    // Продолжения у номера нет — переключаем почти сразу; есть из чего выбрать — даём время прочитать
    numArm(!N.items.length ? 2000 : n * 10 > S.channels.length ? 1000 : 3000);
  }

  function renderNum() {
    var html = '', n = parseInt(N.digits, 10), len = String(n).length;
    for (var i = 0; i < N.items.length; i++) {
      var c = N.items[i], num = String(c.number);
      html += chRow(c, i, 'num', i === N.sel, { no: '<em>' + num.substr(0, len) + '</em>' + num.substr(len), nobar: true });
    }
    $('numpad').innerHTML = '<div class="nv">' + esc(N.digits) + '<i></i></div>' +
      (N.items.length ? '<div class="nl" role="listbox">' + html + '</div>' : '<div class="nn">Канала с номером ' + n + ' нет</div>') +
      '<div class="nt"><i id="numBar"></i></div>';
    $('numHints').innerHTML = (N.items.length > 1 ? hint('ud', 'Выбрать') : '') + (N.items.length ? hint('ok', 'Включить', 'ok') : '') + hint('l', 'Стереть', 'left') + hint('back', 'Отмена', 'back');
    show('numpad', true); show('numHints', true);
  }

  /** Отсчёт до переключения: полоска под списком убывает, по окончании включается выбранный канал. */
  function numArm(ms) {
    var b = $('numBar');
    clearTimeout(N.timer);
    N.timer = setTimeout(numCommit, ms);
    // Убывание полоски — единственная анимация дольше 240 мс: это таймер, а не украшение. На старых ТВ её нет.
    b.style.webkitTransition = b.style.transition = 'none';
    b.style.webkitTransform = b.style.transform = 'scaleX(1)';
    if (LITE) return;
    void b.offsetWidth;
    b.style.webkitTransition = '-webkit-transform ' + ms + 'ms linear';
    b.style.transition = 'transform ' + ms + 'ms linear';
    b.style.webkitTransform = b.style.transform = 'scaleX(0)';
  }

  function numClose() {
    clearTimeout(N.timer);
    N.on = false;
    N.digits = '';
    show('numpad', false); show('numHints', false);
  }

  function numCommit() {
    var ch = N.items[N.sel];
    numClose();
    if (!ch) return;
    tuneTo(ch, S.channels, 'all');
  }

  function numKey(code, e) {
    var d = digitOf(code, e);
    if (d >= 0) typeDigit(d);
    else if (code === KEY.UP || code === KEY.DOWN) {
      if (N.items.length > 1) { N.sel = Math.max(0, Math.min(N.items.length - 1, N.sel + (code === KEY.DOWN ? 1 : -1))); renderNum(); numArm(3000); }
    } else if (code === KEY.ENTER) { swallowEnter = true; numCommit(); }
    else if (code === KEY.LEFT || code === KEY.BKSP || code === KEY.DEL) {
      N.digits = N.digits.slice(0, -1);
      if (N.digits) numUpdate(); else numClose();
    } else if (code !== KEY.RIGHT) { numClose(); return false; }
    return true;
  }

  // ---------- мини-гид поверх видео (SPEC 3.3) ----------
  var MG = { list: [], i: 0, group: 'all' };
  function openMini() {
    if (!S.channels.length) return;
    closeCtl(); hideInfo();
    var g = load('group', 'all'), list = g.indexOf('g:') === 0 ? listOf(g) : null;
    // Канал включён из «Избранного» или «Недавних» — показываем «Все каналы»
    if (!list || list.indexOf(S.cur) < 0) { g = 'all'; list = S.channels; }
    MG.list = list; MG.group = g;
    MG.i = Math.max(0, list.indexOf(S.cur));
    S.zone = 'mini';
    show('mini', true);
    renderMini();
  }
  function renderMini() {
    var n = MG.list.length, html = '';
    var start = winTop('m', MG.i, n, Math.min(7, n), MG.group + ':' + n, 3);
    for (var i = start; i < Math.min(n, start + 7); i++) html += chRow(MG.list[i], i, 'mini', i === MG.i);
    $('mini').innerHTML = '<div class="mh"><b>' + esc(menuTitle(MG.group)) + '</b><span>' + chCount(n) + ' · ' + (PC ? '←' : '◀') + ' — все группы</span></div>' +
      '<div class="ml" role="listbox">' + html + '</div><div class="hints">' + hint('ok', 'Включить', 'ok') + (epgFor(MG.list[MG.i]) ? hint('r', 'Программа', 'right') : '') + hint('l', 'Группы', 'left') + hint('back', 'Закрыть', 'back') + '</div>';
  }
  var miniQueued = false;
  function renderMiniSoon() { if (!miniQueued) { miniQueued = true; raf(function () { miniQueued = false; if (S.zone === 'mini') renderMini(); }); } }
  function closeMini() { show('mini', false); if (S.zone === 'mini') S.zone = 'play'; }
  function keyMini(code) {
    var n = MG.list.length, ch = MG.list[MG.i];
    if (code === KEY.UP) { if (MG.i > 0) { MG.i--; renderMiniSoon(); } }
    else if (code === KEY.DOWN) { if (MG.i < n - 1) { MG.i++; renderMiniSoon(); } }
    else if (code === KEY.CH_UP) { MG.i = Math.max(0, MG.i - 7); renderMiniSoon(); }
    else if (code === KEY.CH_DOWN) { MG.i = Math.min(n - 1, MG.i + 7); renderMiniSoon(); }
    else if (code === KEY.ENTER) { if (ch) tuneTo(ch, MG.list, MG.group); }
    else if (code === KEY.LEFT) { openGuide('groups'); }
    else if (code === KEY.RIGHT) { if (ch && epgFor(ch)) { closeMini(); showProgramme(ch); } }
  }

  /** Закрывает все слои с фокусом поверх плеера: два фокусируемых слоя одновременно не открыты. */
  function closeLayers() {
    if (N.on) numClose();
    closeCtl();
    show('mini', false); show('grid', false); show('searchScr', false); show('pop', false);
    if (!deskMode()) show('guide', false);
    hideInfo();
    S.zone = 'play';
  }
  /** Перерисовывает слой, в котором сейчас фокус. */
  function renderZone() {
    var z = S.zone;
    if (inGuide() || deskMode() && z === 'play') renderGuide();
    else if (z === 'mini') renderMini();
    else if (z === 'grid') renderGrid();
    else if (z === 'search' || z === 'results') renderSearch();
    else if (z === 'setup' || z === 'pane') renderSetup();
    else if (z === 'ctl') renderCtl();
    else if (z === 'ctx' || z === 'plm') { renderPop(); renderHints(); }
  }

  // ---------- сетка «каналы × время» (SPEC 3.4) ----------
  // В DOM только видимые ячейки (не больше 40), координаты абсолютные. Фокус помнит «колонку времени» (ft):
  // при переходе между каналами он встаёт на передачу, которая идёт в то же время.
  var G = { list: [], group: 'all', row: 0, t0: 0, ft: 0, back: 'play', head: -1, rows: 6, span: 150, colw: 380, w: 1348 };
  function half(t) { return Math.floor(t / (30 * MIN)) * 30 * MIN; }
  function gridDays(now) { var d = dayStart(now); return [d - DAY, d, d + DAY, d + 2 * DAY]; }

  function openGrid(ch) {
    if (!S.channels.length) return;
    if (!S.epg) { toast(epgBusy ? 'Телепрограмма ещё загружается…' : 'Телепрограмма не загружена'); return; }
    var back = S.zone === 'ctl' || S.zone === 'ctx' ? (shown('guide') && !deskMode() ? 'channels' : 'play') : S.zone;
    var g = load('group', 'all'), list = listOf(g), now = Date.now();
    ch = ch || S.cur;
    if (!list || list.indexOf(ch) < 0) { g = 'all'; list = S.channels; }
    closeCtl(); hideInfo(); show('pop', false); show('mini', false);
    G.back = back === 'grid' ? G.back : back;
    G.list = list; G.group = g;
    G.row = Math.max(0, list.indexOf(ch));
    G.t0 = half(now) - 30 * MIN; G.ft = now; G.head = -1;
    S.zone = 'grid';
    markSeenLater('grid');
    show('grid', true);
    renderGrid();
  }
  function closeGrid() {
    show('grid', false);
    S.zone = G.back === 'grid' ? 'play' : G.back;
    if (S.zone === 'play') showInfo();
    renderZone();
  }
  /** Номер передачи канала, которая стоит в «колонке времени»; -1 — программы нет. */
  function gridProg(ch) {
    var e = epgFor(ch);
    if (!e || !e.s.length) return -1;
    var lo = 0, hi = e.s.length - 1, found = 0;
    while (lo <= hi) { var mid = (lo + hi) >> 1; if (e.s[mid] <= G.ft) { found = mid; lo = mid + 1; } else hi = mid - 1; }
    // Между передачами дыра или программа ещё не началась — берём ближайшую в окне
    if (e.e[found] <= G.t0 && found + 1 < e.s.length) found++;
    return e.e[found] > G.t0 && e.s[found] < G.t0 + G.span * MIN ? found : -1;
  }
  function gridStatus(s, e, arch, now) {
    if (s <= now && e > now) return 'идёт сейчас, до ' + clock(e);
    if (s > now) {
      var m = Math.round((s - now) / MIN);
      return m <= 180 ? 'начнётся через ' + m + ' мин' : dayTitle(s, now).toLowerCase();
    }
    return dayDiff(s, now) === 0 ? (arch ? 'запись есть' : 'сегодня') : (dayDiff(s, now) === -1 ? 'вчера' : dateText(s)) + (arch ? ' · запись есть' : '');
  }

  function renderGrid() {
    var now = Date.now(), n = G.list.length, t1 = G.t0 + G.span * MIN, html = '', cells = 0, i, k;
    if (deskMode()) { G.rows = 9; G.span = 180; G.colw = 240; G.w = Math.max(300, $('grid').clientWidth - 48 - G.colw); }
    else { G.rows = 6; G.span = 150; G.colw = 380; G.w = 1348; }
    t1 = G.t0 + G.span * MIN;
    var scale = G.w / G.span / MIN, rowH = deskMode() ? 56 : 103;
    var days = gridDays(now), curDay = dayStart(G.ft);
    var head = '<div class="grh"><b>Телепрограмма</b>' + newTag('grid') + '<div class="tabs">';
    for (k = 0; k < days.length; k++) head += '<span class="tab' + (days[k] === curDay ? ' act' : '') + (G.head === k ? ' sel' : '') + '" data-z="gridh" data-i="' + k + '">' + dayTab(days[k], now) + '</span>';
    head += '<span class="tab grp' + (G.head === days.length ? ' sel' : '') + '" data-z="gridh" data-i="' + days.length + '">Группа<b>' + esc(menuTitle(G.group)) + '</b>' + svg('chevron-down') + '</span></div>' +
      '<span class="clk">' + clock(now) + '</span></div>';

    var fch = G.list[G.row], fe = fch ? epgFor(fch) : null, fp = fch ? gridProg(fch) : -1;
    var line = fch ? fch.number + ' · ' + esc(fch.name) + (fp >= 0 ? ' · ' + clock(fe.s[fp]) + '–' + clock(fe.e[fp]) + ' · ' + gridStatus(fe.s[fp], fe.e[fp], !!fch.catchupDays && fe.s[fp] >= now - fch.catchupDays * DAY, now) : '') : '';
    head += '<div class="grf"><div class="l1">' + line + '</div><div class="l2">' + esc(fp >= 0 ? fe.t[fp] || 'Без названия' : fch ? 'Нет телепрограммы' : '') + '</div></div>';

    var scaleRow = '<div class="grs" style="margin-left:' + G.colw + 'px">';
    for (var t = G.t0; t < t1; t += 30 * MIN) scaleRow += '<span style="left:' + Math.round((t - G.t0) * scale) + 'px">' + clock(t) + '</span>';
    scaleRow += '</div>';

    var top = winTop('gr', G.row, n, Math.min(G.rows, n), G.group + ':' + n);
    for (i = top; i < Math.min(n, top + G.rows); i++) {
      var c = G.list[i], e = epgFor(c), cur = S.cur && c.url === S.cur.url, row = '';
      if (e) {
        var lo = 0, hi = e.s.length - 1, first = e.s.length;
        while (lo <= hi) { var mid = (lo + hi) >> 1; if (e.e[mid] > G.t0) { first = mid; hi = mid - 1; } else lo = mid + 1; }
        var sel = i === G.row && G.head < 0 ? gridProg(c) : -1, cnt = 0;
        for (k = first; k < e.s.length && e.s[k] < t1 && cnt < 7 && cells < 40; k++, cnt++, cells++) {
          var s = Math.max(e.s[k], G.t0), en = Math.min(e.e[k], t1), w = Math.round((en - s) * scale) - 8;
          if (w < 4) continue;
          var past = e.e[k] <= now, isNow = e.s[k] <= now && e.e[k] > now, arch = past && !!c.catchupDays && e.s[k] >= now - c.catchupDays * DAY;
          var wide = w >= (deskMode() ? 150 : 230), mid2 = w >= (deskMode() ? 100 : 160);
          row += '<div data-z="grid" data-i="' + i + '" data-v="' + k + '" class="cell' + (k === sel ? ' sel' : '') + (past ? ' past' : '') + (isNow ? ' now' : '') + (e.s[k] < G.t0 ? ' cut' : '') +
            '" style="left:' + Math.round((s - G.t0) * scale) + 'px;width:' + w + 'px">' +
            (wide ? '<span class="t1">' + (arch ? svg('history') : '') + (e.s[k] < G.t0 ? '← ' : '') + esc(e.t[k] || 'Без названия') + '</span><span class="t2">' + clock(e.s[k]) + '–' + clock(e.e[k]) + '</span>'
              : mid2 ? '<span class="t2 only">' + (arch ? svg('history') : '') + 'до ' + clock(e.e[k]) + '</span>' : arch && w >= 44 ? '<span class="t2 only">' + svg('history') + '</span>' : '') +
            (isNow ? '<i class="pg" style="width:' + Math.max(0, Math.min(100, (now - s) * 100 / Math.max(1, en - s))) + '%"></i>' : '') + '</div>';
        }
      }
      if (!row) {
        cells++;
        row = '<div data-z="grid" data-i="' + i + '" data-v="-1" class="cell none' + (i === G.row && G.head < 0 ? ' sel' : '') + '" style="left:0;width:' + (G.w - 8) + 'px"><span class="t1">Нет телепрограммы — канал можно смотреть, расписания нет</span></div>';
      }
      html += '<div class="grr' + (cur ? ' play' : '') + '"><div class="grc" style="width:' + G.colw + 'px" data-z="grid" data-i="' + i + '" data-v="ch"><span class="no">' + c.number + '</span>' + logoHtml(c) +
        '<span class="nm">' + esc(c.name) + '</span></div><div class="grl" style="width:' + G.w + 'px;height:' + rowH + 'px">' + row + '</div></div>';
    }
    var nowLine = now >= G.t0 && now < t1 ? '<div class="grn" style="left:' + (G.colw + Math.round((now - G.t0) * scale)) + 'px"><i></i></div>' : '';
    $('grid').innerHTML = head + '<div class="grb">' + scaleRow + html + nowLine + '</div>' +
      '<div class="hints">' + hint('ud', 'Каналы') + hint('lr', 'Время') + hint('ok', 'Включить канал', 'ok') + hint('hold', 'Действия', 'ctx') + (PC ? '' : hint('CH±', 'Страница')) + hint('back', 'Закрыть', 'back') + '</div>';
  }
  var gridQueued = false;
  function renderGridSoon() { if (!gridQueued) { gridQueued = true; raf(function () { gridQueued = false; if (S.zone === 'grid') renderGrid(); }); } }

  function gridMove(dir) {
    var ch = G.list[G.row], e = epgFor(ch), p = gridProg(ch), step = 30 * MIN, now = Date.now();
    var minT = dayStart(now) - 14 * DAY, maxT = dayStart(now) + 3 * DAY;
    if (!e || p < 0) {
      // у канала нет программы — двигаем само окно
      if (G.t0 + dir * step < minT || G.t0 + dir * step > maxT) return;
      G.t0 += dir * step; G.ft += dir * step;
      return;
    }
    var q = p + dir;
    if (q < 0 || q >= e.s.length) return;
    if (dir > 0) {
      while (e.s[q] >= G.t0 + (G.span - 30) * MIN) G.t0 += 60 * MIN;
      G.ft = Math.max(e.s[q], G.t0) + MIN;
    } else {
      if (e.s[q] < G.t0) G.t0 = half(Math.max(e.s[q], e.e[q] - 90 * MIN));
      G.ft = Math.max(e.s[q], G.t0) + MIN;
    }
  }
  function gridChoose() {
    var ch = G.list[G.row], e = epgFor(ch), p = ch ? gridProg(ch) : -1, now = Date.now();
    if (!ch) return;
    if (p >= 0 && e.e[p] <= now && ch.catchupDays && e.s[p] >= now - ch.catchupDays * DAY) {
      closeLayers();
      play(ch, { s: e.s[p], e: e.e[p], t: e.t[p] || 'Без названия' });
      if (deskMode()) renderGuide();
    } else tuneTo(ch, G.list, G.group);
  }
  function gridGroupMenu() {
    var items = [{ t: 'Все каналы', icon: G.group === 'all' ? 'check' : '', a: function () { gridGroup('all'); } }];
    S.menu.forEach(function (m) {
      if (m.id.indexOf('g:') === 0) items.push({ t: m.title, sub: String(m.list.length), icon: G.group === m.id ? 'check' : '', a: function () { gridGroup(m.id); } });
    });
    if (items.length > 9) items = items.slice(0, 9); // меню целиком помещается на экран
    openPop('ctx', 'Группа', items, deskMode() ? { x: lastMouse.x, y: lastMouse.y, cursor: true } : { x: 1160, y: 126 }, 0);
  }
  function gridGroup(id) {
    var keep = G.list[G.row];
    G.group = id; G.list = listOf(id) || S.channels;
    G.row = Math.max(0, G.list.indexOf(keep));
    G.head = -1;
  }
  function gridDay(k) {
    var now = Date.now(), days = gridDays(now), d = days[k], tod = G.ft - dayStart(G.ft);
    if (d === undefined) return;
    G.ft = d === dayStart(now) ? now : d + tod;
    G.t0 = half(G.ft) - 30 * MIN;
  }
  function keyGrid(code) {
    var n = G.list.length, tabs = gridDays(Date.now()).length;
    if (G.head >= 0) {
      if (code === KEY.LEFT) G.head = Math.max(0, G.head - 1);
      else if (code === KEY.RIGHT) G.head = Math.min(tabs, G.head + 1);
      else if (code === KEY.DOWN) G.head = -1;
      else if (code === KEY.ENTER) { if (G.head === tabs) { gridGroupMenu(); return; } gridDay(G.head); G.head = -1; }
      else return;
      renderGrid();
      return;
    }
    if (code === KEY.UP) { if (G.row > 0) G.row--; else { G.head = Math.max(0, gridDays(Date.now()).indexOf(dayStart(G.ft))); renderGrid(); return; } }
    else if (code === KEY.DOWN) { if (G.row < n - 1) G.row++; else return; }
    else if (code === KEY.CH_UP) G.row = Math.max(0, G.row - G.rows);
    else if (code === KEY.CH_DOWN) G.row = Math.min(n - 1, G.row + G.rows);
    else if (code === KEY.LEFT) gridMove(-1);
    else if (code === KEY.RIGHT) gridMove(1);
    else return;
    renderGridSoon();
  }

  // ---------- поиск (SPEC 3.5) ----------
  var Q = { q: '', en: false, kr: 0, kc: 0, items: [], i: 0, back: 'play', timer: null, sections: [] };
  var RU = 'АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ', EN = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  /** Раскладка экранной клавиатуры: ряды клавиш { t — надпись, w — ширина в клавишах, a — действие }. */
  function kbRows() {
    var letters = Q.en ? EN : RU, rows = [], r, i;
    for (i = 0; i < letters.length; i += 8) {
      r = [];
      for (var k = i; k < Math.min(letters.length, i + 8); k++) r.push({ t: letters.charAt(k), w: 1 });
      rows.push(r);
    }
    r = [];
    for (i = 1; i <= 8; i++) r.push({ t: String(i), w: 1 });
    rows.push(r);
    rows.push([{ t: '9', w: 1 }, { t: '0', w: 1 }, { t: 'Пробел', w: 3, a: 'space', sv: true }, { t: '', w: 2, a: 'bksp', icon: 'backspace', sv: true, label: 'Стереть' }]);
    rows.push([{ t: Q.en ? 'РУ' : 'EN', w: 2, a: 'lang', sv: true }, { t: 'Очистить', w: 3, a: 'clear', sv: true }]);
    return rows;
  }
  function openSearch(text) {
    if (!S.channels.length) return;
    if (deskMode()) { deskSearch(text); return; }
    var back = inGuide() ? 'groups' : 'play';
    closeLayers();
    Q.back = back;
    Q.q = text || ''; Q.en = false; Q.kr = 0; Q.kc = 0; Q.i = 0;
    S.zone = 'search';
    show('guide', false);
    show('searchScr', true);
    runSearch();
    renderSearch();
  }
  function closeSearch() {
    show('searchScr', false);
    Q.q = ''; Q.items = []; // истории запросов нет
    if (Q.back === 'groups') { S.zone = 'play'; openGuide('groups'); S.gi = 0; renderGuide(); }
    else { S.zone = 'play'; showInfo(); }
  }
  function searchRe(q) {
    var pat = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/[её]/gi, '[её]');
    try { return new RegExp(pat, 'i'); } catch (e) { return null; }
  }
  /** Подсветка совпадения: жирно и с подчёркиванием (border-bottom — на старых ТВ нет text-underline-offset). */
  function hl(text, re) {
    var m = re ? re.exec(text) : null;
    if (!m || !m[0]) return esc(text);
    return esc(text.substr(0, m.index)) + '<span class="hl">' + esc(m[0]) + '</span>' + esc(text.substr(m.index + m[0].length));
  }
  function runSearch() {
    var q = trim(Q.q), re = q ? searchRe(q) : null, num = /^\d+$/.test(q), now = Date.now();
    var chs = [], progs = [], recs = [], groups = [], pls = [], i;
    Q.sections = []; Q.items = [];
    if (!q || !re) { Q.i = 0; return; }
    for (i = 0; i < S.channels.length && chs.length < 5; i++) if (num && String(S.channels[i].number).indexOf(q) === 0) chs.push(S.channels[i]);
    for (i = 0; i < S.channels.length && chs.length < 5; i++) if (!(num && String(S.channels[i].number).indexOf(q) === 0) && re.test(S.channels[i].name)) chs.push(S.channels[i]);
    if (S.epg && !num) {
      var done = {};
      for (i = 0; i < S.channels.length && (progs.length < 60 || recs.length < 60); i++) {
        var c = S.channels[i], e = epgFor(c);
        if (!e) continue;
        var id = c.tvgId || key2(c.name);
        if (done[id]) continue;
        done[id] = 1;
        var lo = 0, hi = e.s.length - 1, first = e.s.length, k;
        while (lo <= hi) { var mid = (lo + hi) >> 1; if (e.e[mid] > now) { first = mid; hi = mid - 1; } else lo = mid + 1; }
        for (k = first; k < e.s.length && progs.length < 60; k++) if (re.test(e.t[k])) progs.push({ ch: c, s: e.s[k], e: e.e[k], t: e.t[k] });
        if (c.catchupDays) {
          var from = now - c.catchupDays * DAY;
          for (k = first - 1; k >= 0 && e.s[k] >= from && recs.length < 60; k--) if (re.test(e.t[k])) recs.push({ ch: c, s: e.s[k], e: e.e[k], t: e.t[k], rec: true });
        }
      }
      progs.sort(function (a, b) { return a.s - b.s; });
      recs.sort(function (a, b) { return b.s - a.s; });
    }
    S.menu.forEach(function (m) { if (m.id.indexOf('g:') === 0 && groups.length < 5 && re.test(m.title)) groups.push(m); });
    S.playlists.forEach(function (p) { if (pls.length < 5 && re.test(p.name)) pls.push(p); });
    function add(name, note, list, type, isNewKey, total) {
      var sec = { name: name, note: note, from: Q.items.length, n: Math.min(5, list.length), total: total === undefined ? list.length : total, isNew: isNewKey, type: type };
      for (var j = 0; j < sec.n; j++) Q.items.push({ type: type, v: list[j] });
      Q.sections.push(sec);
    }
    add('Каналы', '', chs, 'ch');
    if (S.epg) { if (!num) { add('Передачи', 'сейчас и позже', progs, 'prog', 'progsearch'); add('Записи', '', recs, 'prog', 'progsearch'); } }
    else if (epgBusy) Q.sections.push({ name: 'Передачи', status: 'Телепрограмма загружается — передачи появятся через минуту', n: 0, from: Q.items.length });
    add('Группы', '', groups, 'group');
    if (S.playlists.length > 1) add('Плейлисты', '', pls, 'pl', 'playlists');
    Q.i = Math.max(0, Math.min(Q.i, Q.items.length - 1));
    Q.re = re;
  }
  function searchSoon() {
    clearTimeout(Q.timer);
    // На больших списках и при поиске по программе считаем после паузы в наборе
    if (S.channels.length > 2000 || S.epg) Q.timer = setTimeout(function () { runSearch(); renderSearch(); }, 150);
    else runSearch();
    renderSearch();
  }
  function resultRow(it, i, sel) {
    var now = Date.now(), v = it.v;
    if (it.type === 'ch') {
      var num = /^\d+$/.test(trim(Q.q)) && String(v.number).indexOf(trim(Q.q)) === 0;
      return chRow(v, i, 'results', sel, { name: num ? esc(v.name) : hl(v.name, Q.re), no: num ? '<em>' + trim(Q.q) + '</em>' + String(v.number).substr(trim(Q.q).length) : '', nobar: true, star: deskMode() });
    }
    if (it.type === 'prog') {
      var live = v.s <= now && v.e > now;
      var when = live ? 'идёт сейчас, до ' + clock(v.e) : v.rec ? (dayDiff(v.s, now) === 0 ? 'сегодня' : dayDiff(v.s, now) === -1 ? 'вчера' : dateText(v.s)) + ' · запись есть'
        : dayDiff(v.s, now) === 0 ? 'сегодня' : dayDiff(v.s, now) === 1 ? 'завтра' : dateText(v.s);
      return '<div role="option" aria-selected="' + !!sel + '" data-z="results" data-i="' + i + '" class="rp' + (sel ? ' sel' : '') + '"><span class="tm">' + clock(v.s) + '</span><span class="tx"><span class="t1">' + hl(v.t, Q.re) + '</span>' +
        '<span class="sub">' + esc(v.ch.name) + ' · ' + when + '</span></span>' + (live ? '<span class="live"><i></i>Эфир</span>' : v.rec ? svg('history') : '') + '</div>';
    }
    var title = it.type === 'group' ? v.title : v.name, sub = it.type === 'group' ? chCount(v.list.length) : v.id === S.activePl ? 'Активный' : v.count ? chCount(v.count) : '';
    return '<div role="option" aria-selected="' + !!sel + '" data-z="results" data-i="' + i + '" class="rp' + (sel ? ' sel' : '') + '"><span class="tm">' + svg(it.type === 'group' ? 'folder' : 'list') + '</span><span class="tx"><span class="t1">' + hl(title, Q.re) + '</span>' +
      '<span class="sub">' + sub + '</span></span></div>';
  }
  function resultsHtml(focus) {
    var html = '', q = trim(Q.q);
    if (!q) return '';
    if (!Q.items.length && !(Q.sections.length && Q.sections.some(function (s) { return s.status; })))
      return emptyState('search', 'Ничего не нашлось', 'Попробуйте короче или наберите номер канала цифрами.');
    for (var k = 0; k < Q.sections.length; k++) {
      var s = Q.sections[k];
      html += '<div class="sect"><b>' + s.name + '</b><span>' + (s.status ? '' : (s.note ? s.note + (s.n ? ' · ' : '') : '') + (s.n ? s.n : s.note ? ' · не найдено' : 'не найдено')) + '</span>' + (s.isNew && s.n ? newTag(s.isNew) : '') + '</div>';
      if (s.status) html += '<div class="status"><span class="spin"></span>' + s.status + '</div>';
      for (var i = s.from; i < s.from + s.n; i++) html += resultRow(Q.items[i], i, focus && i === Q.i);
    }
    return html;
  }
  function renderSearch() {
    if (deskMode()) { deskRenderSearch(); return; }
    var rows = kbRows(), kb = '', focus = S.zone === 'search', it = Q.items[Q.i];
    for (var r = 0; r < rows.length; r++) {
      kb += '<div class="kr">';
      for (var c = 0; c < rows[r].length; c++) {
        var k = rows[r][c];
        kb += '<span class="k' + (k.sv ? ' sv' : '') + (focus && r === Q.kr && c === Q.kc ? ' sel' : '') + '" style="width:' + (k.w * 80 - 8) + 'px" data-z="search" data-i="' + (r * 10 + c) + '"' +
          (k.label ? ' aria-label="' + k.label + '"' : '') + '>' + (k.icon ? svg(k.icon) : k.t) + '</span>';
      }
      kb += '</div>';
    }
    var h;
    if (focus) h = hint('ok', 'Ввести букву', 'ok') + (Q.items.length ? hint('r', 'К результатам', 'right') : '') + hint('0–9', 'Цифры') + hint('back', 'Закрыть', 'back');
    else h = hint('ok', it && it.type === 'prog' && it.v.rec ? 'Смотреть запись' : 'Смотреть', 'ok') + (it && (it.type === 'ch' || it.type === 'prog') ? hint('hold', 'Действия', 'ctx') : '') + hint('l', 'К клавиатуре', 'left') + hint('back', 'К клавиатуре', 'back');
    $('searchScr').innerHTML = '<div class="top"><b>Поиск</b><span>' + headDate(Date.now()) + '</span></div>' +
      '<div class="sl"><div class="qbox">' + svg('search') + '<span class="q">' + esc(Q.q) + '</span><i class="caret"></i></div><div class="kb">' + kb + '</div>' +
      '<div class="kh">Цифры — поиск по номеру канала. Пульт с цифрами тоже работает.</div></div>' +
      '<div class="sr" role="listbox">' + resultsHtml(!focus) + '</div><div class="hints">' + h + '</div>';
    // Результатов больше, чем помещается: подвигаем список так, чтобы строка в фокусе была видна
    var sr = document.querySelector('#searchScr .sr'), se = sr.querySelector('.sel');
    if (se && se.offsetTop + se.offsetHeight > sr.clientHeight) sr.scrollTop = se.offsetTop + se.offsetHeight - sr.clientHeight + 8;
  }
  function kbPress() {
    var k = kbRows()[Q.kr][Q.kc];
    if (!k) return;
    if (k.a === 'space') { if (Q.q && Q.q.charAt(Q.q.length - 1) !== ' ') Q.q += ' '; }
    else if (k.a === 'bksp') Q.q = Q.q.slice(0, -1);
    else if (k.a === 'clear') Q.q = '';
    else if (k.a === 'lang') { Q.en = !Q.en; Q.kr = kbRows().length - 1; Q.kc = 0; renderSearch(); return; }
    else if (Q.q.length < 40) Q.q += Q.q ? k.t.toLowerCase() : k.t;
    Q.i = 0;
    searchSoon();
  }
  function kbX(row, col) { var x = 0; for (var i = 0; i < col; i++) x += row[i].w; return x; }
  function keySearch(code, e) {
    var rows = kbRows(), row = rows[Q.kr], d = digitOf(code, e), ch = e && e.key && e.key.length === 1 && !e.ctrlKey && !e.altKey && !e.metaKey ? e.key : '';
    if (d >= 0) { if (Q.q.length < 40) Q.q += d; Q.i = 0; searchSoon(); return; }
    if (ch && /[a-zа-яё ]/i.test(ch)) { if (e.preventDefault) e.preventDefault(); if (Q.q.length < 40) Q.q += ch; Q.i = 0; searchSoon(); return; }
    if (code === KEY.BKSP || code === KEY.DEL) { if (e.preventDefault) e.preventDefault(); Q.q = Q.q.slice(0, -1); Q.i = 0; searchSoon(); return; }
    if (code === KEY.LEFT) { if (Q.kc > 0) Q.kc--; }
    else if (code === KEY.RIGHT) {
      if (Q.kc < row.length - 1) Q.kc++;
      else if (Q.items.length) { clearTimeout(Q.timer); runSearch(); S.zone = 'results'; markSeenLater('progsearch'); }
    } else if (code === KEY.UP || code === KEY.DOWN) {
      var r = Q.kr + (code === KEY.UP ? -1 : 1);
      if (r < 0 || r >= rows.length) return;
      // остаёмся в той же колонке: ищем клавишу, которая накрывает ту же позицию
      var x = kbX(row, Q.kc), acc = 0, col = rows[r].length - 1;
      for (var i = 0; i < rows[r].length; i++) { if (x < acc + rows[r][i].w) { col = i; break; } acc += rows[r][i].w; }
      Q.kr = r; Q.kc = col;
    } else if (code === KEY.ENTER) { kbPress(); return; }
    else return;
    renderSearch();
  }
  function resultChoose() {
    var it = Q.items[Q.i], now = Date.now();
    if (!it) return;
    var v = it.v;
    if (it.type === 'ch') tuneTo(v, S.channels, 'all');
    else if (it.type === 'prog') {
      if (v.s <= now && v.e > now) tuneTo(v.ch, S.channels, 'all');
      else if (v.rec) { closeLayers(); play(v.ch, { s: v.s, e: v.e, t: v.t || 'Без названия' }); if (deskMode()) renderGuide(); }
      else { closeLayers(); S.zone = 'play'; openGuide(); showProgramme(v.ch, v.s); }
    } else if (it.type === 'group') { closeLayers(); openGuide(); selectMenu(v.id); S.zone = S.list.length ? 'channels' : 'groups'; renderGuide(); }
    else if (it.type === 'pl') { closeLayers(); activatePlaylist(v, function (ok) { if (ok) openGuide(); }); if (v.id === S.activePl) openGuide(); }
  }
  function keyResults(code) {
    if (code === KEY.UP) { if (Q.i > 0) Q.i--; }
    else if (code === KEY.DOWN) { if (Q.i < Q.items.length - 1) Q.i++; }
    else if (code === KEY.LEFT) S.zone = 'search';
    else if (code === KEY.ENTER) { resultChoose(); return; }
    else return;
    renderSearch();
  }

  // ---------- обновления ----------
  // Приложение обновляется само: скачивает из релиза файл iptv-web.json (разметка, стили и код одним
  // набором), кладёт его в хранилище и перезапускается — загрузчик boot.js запустит уже новую версию.
  // Каналы, избранное и настройки лежат в том же хранилище и никуда не деваются.
  var BOOT = window.__iptvBoot || null;        // чем нас запустили; нет — открыта страница без загрузчика
  var SHELL = window.__iptvShell || 0;
  var U = { xhr: null, status: '', kind: '' };

  function setUpd(text, kind) { U.status = text; U.kind = kind || ''; if (S.zone === 'setup' || S.zone === 'pane') renderSetup(); }

  function checkUpdate(manual) {
    setUpd('Проверяю…');
    fetchText('https://api.github.com/repos/' + REPO + '/releases/latest', function (text) {
      var version = '';
      try { version = String(JSON.parse(text).tag_name || '').replace(/^v/, ''); } catch (e) { /* не JSON */ }
      save('updCheck', Date.now());
      if (version && newer(version, VERSION)) {
        S.update = version;
        setUpd('Вышла версия ' + version, 'ok');
        if (manual || splashGone) updateDialog();
      } else setUpd('У вас последняя версия');
    }, function () { setUpd('Не получилось проверить', 'err'); });
  }

  function updateLater() {
    if (U.xhr) { try { U.xhr.abort(); } catch (e) { /* уже завершён */ } U.xhr = null; }
    S.update = null; // до следующего запуска не напоминаем
    closeDialog();
  }
  function updateDialog() {
    if (!S.update || S.zone === 'dialog') return;
    if (S.zone !== 'play' && S.zone !== 'setup' && S.zone !== 'pane') return; // не перебиваем выбор канала
    if (!SHELL) { manualDialog(); return; }
    openDialog({ title: 'Вышла версия ' + S.update, onBack: updateLater,
      text: 'Сейчас установлена версия ' + VERSION + '. Обновление скачается и установится само, это займёт несколько секунд.',
      buttons: [{ t: 'Обновить', primary: true, a: startUpdate }, { t: 'Позже', a: updateLater }] });
  }
  function openPage() { window.open('https://github.com/' + REPO + '/releases/latest', '_blank'); updateLater(); }
  /** Обновление, которое приложение не может поставить само: меняется сам пакет, а не только его содержимое. */
  function manualDialog() {
    if (PC) {
      openDialog({ title: 'Вышла версия ' + S.update, onBack: updateLater,
        text: 'Это обновление меняет саму программу, поэтому его нужно скачать: откройте страницу загрузки и запустите новый файл вместо старого. Каналы и настройки сохранятся.',
        buttons: [{ t: 'Открыть страницу загрузки', primary: true, a: openPage }, { t: 'Позже', a: updateLater }] });
    } else {
      openDialog({ title: 'Вышла версия ' + S.update, onBack: updateLater,
        text: 'Это обновление меняет сам пакет приложения, поэтому на телевизоре ' + PLATFORM_NAME + ' его нужно поставить с компьютера — так же, как в первый раз. Каналы и настройки сохранятся.',
        buttons: [{ t: 'Понятно', primary: true, a: updateLater }] });
    }
  }
  function updateFailed(reason) {
    U.xhr = null;
    var buttons = [{ t: 'Повторить', primary: true, a: startUpdate }];
    if (PC) buttons.push({ t: 'Открыть страницу загрузки', a: openPage });
    buttons.push({ t: 'Отмена', a: updateLater });
    openDialog({ title: 'Не удалось обновиться', onBack: updateLater, buttons: buttons,
      text: 'Причина: ' + reason + '. Приложение работает как раньше, обновиться можно позже из настроек.' });
  }
  function startUpdate() {
    var version = S.update, x = new XMLHttpRequest();
    if (!version) { updateLater(); return; }
    openDialog({ title: 'Скачиваю версию ' + version + '…', text: 'Это займёт несколько секунд. Каналы, избранное и настройки сохранятся.',
      progress: true, onBack: updateLater, buttons: [{ t: 'Отмена', a: updateLater }] });
    U.xhr = x;
    try { x.open('GET', 'https://github.com/' + REPO + '/releases/download/v' + version + '/iptv-web.json', true); } catch (e) { updateFailed('неверный адрес обновления'); return; }
    x.timeout = 60000;
    x.onprogress = function (e) { if (e.lengthComputable && e.total && $('dFill')) $('dFill').style.width = Math.round(e.loaded * 100 / e.total) + '%'; };
    x.onload = function () {
      if (U.xhr !== x) return;
      if (x.status >= 200 && x.status < 300) installUpdate(x.responseText, version);
      else updateFailed(x.status === 404 ? 'в релизе ' + version + ' нет файла обновления' : 'сервер ответил ' + x.status);
    };
    x.onerror = function () { if (U.xhr === x) updateFailed('нет связи с сервером'); };
    x.ontimeout = function () { if (U.xhr === x) updateFailed('сервер не отвечает'); };
    try { x.send(); } catch (e2) { updateFailed('нет связи с сервером'); }
  }
  /** Проверяет скачанный набор, сохраняет его и перезапускает приложение уже в новой версии. */
  function installUpdate(text, version) {
    var b = null, stored = false;
    U.xhr = null;
    try { b = JSON.parse(text); } catch (e) { /* не JSON */ }
    if (!b || b.format !== 1 || typeof b.js !== 'string' || typeof b.html !== 'string' || typeof b.css !== 'string' ||
      typeof b.version !== 'string' || !newer(b.version, VERSION) || b.js.indexOf('__iptvState') < 0) {
      updateFailed('файл обновления повреждён');
      return;
    }
    if (b.shell > SHELL) { manualDialog(); return; }
    function put() {
      try { window.localStorage.setItem('iptv_bundle', text); return true; } catch (e) { return false; }
    }
    stored = put();
    if (!stored) {
      // Места не хватило: освобождаем его за счёт сохранённого списка каналов — он скачается заново
      try { window.localStorage.removeItem('iptv_m3u'); } catch (e2) { /* хранилище недоступно */ }
      stored = put();
    }
    if (!stored) { updateFailed('не хватило памяти, отведённой приложению'); return; }
    try { window.localStorage.removeItem('iptv_bundleBoot'); } catch (e3) { /* хранилище недоступно */ }
    save('updatedTo', b.version);
    openDialog({ title: 'Версия ' + b.version + ' установлена', text: 'Перезапускаю приложение…', progress: true, buttons: [] });
    D.busy = true;
    $('dFill').style.width = '100%';
    stopPlayer();
    setTimeout(function () { window.location.reload(); }, 900);
  }
  /** После заставки говорим, чем закончилось обновление. */
  function bootReport() {
    if (load('updatedTo', '') === VERSION) toast('Приложение обновлено до версии ' + VERSION, 'ok');
    else if (BOOT && BOOT.failed) toast('Обновление не запустилось — работает версия ' + VERSION, 'warn');
    save('updatedTo', '');
  }

  // ---------- плагины: запуск ----------
  function plugName(p) { return p.name || p.id || p.url; }
  function pluginError(index, hook, text) {
    var p = S.plugins[index];
    if (!p) return;
    p.lastError = { time: Date.now(), message: String(text).substr(0, 300), hook: hook };
    save('plugins', S.plugins);
  }
  function runPlugin(code, index, quiet) {
    PL.setSource(index);
    try { (new Function(code))(); return true; }
    catch (e) { if (!quiet) pluginError(index, 'file', e); return false; }
    finally { PL.setSource(-1); }
  }
  function syncPluginMeta() {
    PL.list().forEach(function (m) {
      var p = S.plugins[m.source];
      if (!p) return;
      p.author = m.author; p.hasPlaylist = !!m.hasPlaylist; p.hasStream = !!m.hasStream;
    });
  }
  function bootPlugins() {
    PL.setHost({ version: VERSION, platform: PLATFORM, toast: function (t) { toast(t); }, log: function (t) { if (window.console) window.console.log('[plugin] ' + t); },
      // Ошибки плагинов вне раздела «Плагины» не показываются — только записываются в карточку
      error: function (id, hook, text, source) { pluginError(source, hook, text); } });
    S.plugins.forEach(function (p, i) { if (p.enabled && p.code) { p.lastError = null; runPlugin(p.code, i); } });
    PL.start();
    syncPluginMeta();
    save('plugins', S.plugins);
    // Свежие версии плагинов подтягиваем в фоне — они заработают со следующего запуска
    S.plugins.forEach(function (p) {
      if (!p.enabled) return;
      fetchText(p.url, function (code) { if (code && code !== p.code && /IPTV\s*\.\s*plugin/.test(code)) { p.code = code; save('plugins', S.plugins); } }, function () {});
    });
  }

  // ---------- настройки: разделы и панель (SPEC 5, 6) ----------
  // Панель раздела — список элементов: строки, поля, кнопки. У каждого номер ряда r: ▲▼ ходят по рядам, ◀▶ — внутри ряда.
  var ST = { cat: 0, view: 'playlists', i: 0, back: 'play', items: [], form: null, plug: -1, busy: null, pending: null, ret: null };
  var CATS = [
    { id: 'playlists', title: 'Плейлисты', icon: 'list', isNew: 'playlists' },
    { id: 'plugins', title: 'Плагины', icon: 'plug' },
    { id: 'app', title: 'Приложение', icon: 'info' }];
  function catCount(id) {
    if (id === 'playlists') return String(S.playlists.length);
    if (id === 'plugins') return S.plugins.length ? S.plugins.filter(function (p) { return p.enabled; }).length + ' из ' + S.plugins.length : 'нет';
    return '';
  }
  function catOf(view) { return view === 'plform' ? 0 : view === 'plugin' || view === 'plugadd' || view === 'plugins' ? 1 : view === 'app' ? 2 : 0; }

  function openSetup(view) {
    var back = inGuide() || S.zone === 'plm' || shown('guide') && !deskMode() ? 'guide' : 'play';
    if (!activePlaylist()) { openOnboarding(); return; }
    closeLayers();
    show('guide', false);
    hideSplash(true);
    ST.back = back;
    ST.view = view || 'playlists'; ST.cat = catOf(ST.view); ST.i = 0; ST.form = null;
    S.zone = view ? 'pane' : 'setup';
    if (ST.view === 'playlists') markSeenLater('playlists');
    cls(document.body, 'setup', true);
    show('setup', true);
    renderSetup();
  }
  function closeSetup() {
    finalizeDelete();
    if (S.pluginsDirty) { reloadForPlugins(); return; }
    show('setup', false);
    cls(document.body, 'setup', false);
    S.zone = 'play';
    if (ST.back === 'guide' && !deskMode()) { openGuide('groups'); S.gi = S.menu.length - 1; renderGuide(); }
    else { showInfo(); renderZone(); }
  }
  function reloadForPlugins() {
    // Плагины подключаются при запуске, поэтому после изменений перезапускаем приложение
    toast('Перезапускаю, чтобы применить плагины…', 'wait');
    setTimeout(function () { window.location.reload(); }, 600);
  }
  function setView(view, focus) {
    ST.view = view; ST.cat = catOf(view); ST.i = focus || 0;
    S.zone = 'pane';
    renderSetup();
  }

  function plStatus(p) {
    if (p.lastError && !(p.id === S.activePl && S.channels.length)) return '<span class="status-v err">' + svg('alert') + 'Не загрузился</span>';
    return (p.id === S.activePl ? '<span class="status-v ok">' + svg('check') + 'Активный</span>' : '') + (p.count ? '<span class="v">' + chCount(p.count) + '</span>' : '');
  }
  function plugStatus(p) {
    if (!p.enabled) return '<span class="status-v dim">Выключен</span>';
    if (p.lastError) return '<span class="status-v err">' + svg('alert') + 'Ошибка запуска</span>';
    return '<span class="status-v ok">' + svg('check') + 'Работает</span>';
  }

  /** Собирает элементы панели для текущего вида. head — заголовок и пояснение. */
  function paneItems() {
    var v = ST.view, items = [], head = '', now = Date.now(), a = activePlaylist(), r = 0;
    function row(o) { o.type = 'row'; o.r = r++; items.push(o); }
    if (v === 'playlists') {
      head = '<div class="ph1">Плейлисты' + newTag('playlists') + '</div><div class="lead">Каналы берутся из активного плейлиста. Переключить его можно и в гиде — сверху слева.</div>';
      S.playlists.forEach(function (p) {
        var bad = p.lastError && !(p.id === S.activePl && S.channels.length);
        row({ icon: 'list', nm: p.name, ds: bad ? p.lastError.message + ' · ' + whenText(p.lastError.time, now) : midCut(p.url, 46), val: plStatus(p), chev: true,
          a: function () { openForm(p); }, hold: function () { playlistMenu(p); }, pl: p });
      });
      row({ icon: 'plus', nm: 'Добавить плейлист', ds: 'По ссылке от провайдера', a: function () { openForm(null); } });
      items.push({ type: 'html', html: '<div class="gt">Список каналов</div>' });
      row({ icon: 'refresh', nm: 'Обновить каналы', ds: refreshBusy ? 'Обновляем список…' : 'Активный плейлист' + (a && a.updated ? ' · обновлён ' + whenText(a.updated, now) : ''), spin: refreshBusy,
        a: function () { refresh(true); renderSetup(); } });
    } else if (v === 'app') {
      head = '<div class="ph1">Приложение</div>';
      row({ icon: 'download', nm: 'Обновлять автоматически', sw: S.auto, a: function () { S.auto = !S.auto; save('auto', S.auto); toast('Обновлять автоматически — ' + (S.auto ? 'включено' : 'выключено')); renderSetup(); } });
      row({ icon: 'refresh', nm: 'Проверить обновления', val: '<span class="v ' + U.kind + '">' + esc(U.status || 'Версия ' + VERSION) + '</span>', a: function () { checkUpdate(true); } });
      items.push({ type: 'html', html: '<div class="ver">Версия ' + VERSION + '</div>' });
    } else if (v === 'plugins') {
      head = '<div class="ph1">Плагины</div><div class="lead">Плагин меняет список каналов или адрес потока. Работают по порядку — сверху вниз. Плагин ставится по ссылке на его файл; каталога плагинов нет.</div>';
      S.plugins.forEach(function (p, i) {
        row({ order: i + 1, nm: plugName(p), ds: (p.version ? 'Версия ' + p.version + (p.description ? ' · ' : '') : '') + (p.description || (p.version ? '' : midCut(p.url, 46))), val: plugStatus(p), chev: true,
          a: function () { ST.plug = i; setView('plugin', 0); }, hold: function () { pluginMenu(i); }, del: function () { askDeletePlugin(i); } });
      });
      row({ icon: 'plus', nm: 'Добавить плагин', ds: 'По ссылке на файл .js', a: function () { ST.form = { url: '', err: '', busy: false }; setView('plugadd', 0); } });
      items.push({ type: 'html', html: '<div class="banner">' + svg('info') + '<span><b>На Samsung, LG и компьютерах изменения применятся, когда вы выйдете из раздела</b>' +
        '<small>Приложение перезапустит плагины — это займёт секунду.</small></span></div>' });
    } else if (v === 'plugin') {
      var p = S.plugins[ST.plug], n = S.plugins.length;
      if (!p) return { head: '', items: [] };
      head = '<div class="ph1">' + esc(plugName(p)) + '</div>' + (p.description ? '<div class="lead">' + esc(p.description) + '</div>' : '');
      if (p.lastError) {
        items.push({ type: 'html', html: '<div class="banner err">' + svg('alert') + '<span><b>Плагин не запустился ' + whenText(p.lastError.time, now).replace(', ', ' в ') + '</b><small>' + esc(p.lastError.message) + '. ' +
          (p.lastError.hook === 'stream' ? 'Потоки включаются без него.' : 'Каналы показаны без него.') + '</small></span></div>' });
        markSeenLater('plugerr');
      }
      items.push({ type: 'btn', r: 0, t: p.enabled ? 'Выключить' : 'Включить', a: function () { togglePlugin(ST.plug); } });
      items.push({ type: 'btn', r: 0, t: 'Удалить', icon: 'trash', danger: true, a: function () { askDeletePlugin(ST.plug); } });
      var can = [];
      if (p.hasPlaylist) can.push('менять список каналов');
      if (p.hasStream) can.push('менять адреса потоков');
      can.push('показывать уведомления', 'хранить свои данные');
      var canText = can.join(', ') + '.';
      canText = canText.charAt(0).toUpperCase() + canText.substr(1) + (p.hasStream ? '' : ' Адреса потоков не меняет.') + (p.hasPlaylist ? '' : ' Список каналов не меняет.');
      items.push({ type: 'html', html: '<div class="kv"><span>Версия и автор</span><b>' + esc([p.version, p.author].filter(function (x) { return x; }).join(' · ') || '—') + '</b></div>' +
        '<div class="kv"><span>Файл</span><b class="mono">' + esc(p.url) + '</b></div>' +
        '<div class="kv"><span>Порядок</span><b>' + (ST.plug + 1) + ' из ' + n + (ST.plug > 0 ? ' — работает после «' + esc(plugName(S.plugins[ST.plug - 1])) + '»' : ' — работает первым') + '</b></div>' +
        (p.hasPlaylist !== undefined ? '<div class="can"><b>Что может плагин' + newTag('plugcan') + '</b><p>' + canText + '</p></div>' : '') });
      if (p.hasPlaylist !== undefined) markSeenLater('plugcan');
    } else if (v === 'plugadd') {
      var f = ST.form;
      head = '<div class="ph1">Добавить плагин</div>';
      items.push({ type: 'field', r: 0, id: 'f_purl', label: 'Ссылка на файл плагина', key: 'url', ph: 'https://…/plugin.js', err: f.err, help: 'Файл .js, который написан для IPTV (API версии 1)' });
      items.push({ type: 'html', html: '<div class="warnbox">' + svg('shield') + '<span><b>Ставьте плагины только из источников, которым доверяете</b>' +
        '<small>Плагин видит список каналов и адреса потоков и может их менять: например, отправить поток через чужой сервер. Ещё он может показывать уведомления и хранить свои данные на устройстве.</small></span></div>' });
      items.push({ type: 'btn', r: 1, t: f.busy ? 'Загружаю плагин…' : 'Установить', primary: true, spin: f.busy, a: function () { addPlugin(); } });
    } else if (v === 'plform') {
      var fm = ST.form, pl = fm.pl, dirty = formDirty();
      head = '<div class="ph1">' + esc(pl ? pl.name : 'Новый плейлист') + '</div>' + (pl ? '<div class="lead">' + (pl.count ? chCount(pl.count) + ' · ' : '') +
        (pl.updated ? 'обновлён ' + whenText(pl.updated, now) + ' · ' : '') + (pl.id === S.activePl ? 'активный' : 'не активный') + '</div>' : '');
      items.push({ type: 'field', r: 0, id: 'f_name', label: 'Название', key: 'name', max: 40, help: 'Пусто — имя сервера из ссылки' });
      items.push({ type: 'field', r: 1, id: 'f_url', label: 'Ссылка на плейлист', key: 'url', ph: 'http://… .m3u или .m3u8', err: fm.err,
        help: fm.err ? fm.err + (S.channels.length ? ' Работает прежний список: ' + chCount(S.channels.length) + '.' : '') : 'Новая ссылка заменит список только после проверки' });
      items.push({ type: 'field', r: 2, id: 'f_epg', label: 'Ссылка на телепрограмму', key: 'epgUrl', ph: 'Необязательно — если провайдер дал отдельную ссылку', help: 'Если пусто — берём ссылку из самого плейлиста (url-tvg)' });
      items.push({ type: 'btn', r: 3, t: fm.busy ? 'Проверяем ссылку…' : 'Проверить и сохранить', primary: true, spin: fm.busy, off: !dirty && !fm.busy, id: 'f_save', a: function () { checkAndSave(); } });
      if (pl) items.push({ type: 'btn', r: 3, t: 'Удалить плейлист', icon: 'trash', danger: true, off: pl.id === S.activePl, a: function () { askDeletePlaylist(pl); } });
      items.push({ type: 'html', html: '<div class="note" id="f_note">' + (pl && pl.id === S.activePl ? 'Сначала сделайте активным другой' : '') + '</div>' +
        '<div class="dirty" id="f_dirty"' + (dirty ? '' : ' style="display:none"') + '>Есть несохранённые изменения</div>' });
    }
    return { head: head, items: items };
  }

  function renderSetup() {
    if (!shown('setup')) return;
    var cats = '', pane = paneItems(), html = pane.head, items = pane.items, inPane = S.zone === 'pane', i, k = 0, open = false;
    var focusables = [];
    for (i = 0; i < items.length; i++) if (items[i].type !== 'html') focusables.push(items[i]);
    ST.items = focusables;
    if (ST.i >= focusables.length) ST.i = Math.max(0, focusables.length - 1);
    for (i = 0; i < CATS.length; i++) {
      cats += '<div class="mi' + (i === ST.cat ? ' act' : '') + (i === ST.cat && S.zone === 'setup' ? ' sel' : '') + '" data-z="setup" data-i="' + i + '" title="' + CATS[i].title + '">' + svg(CATS[i].icon) +
        '<span class="t">' + CATS[i].title + (CATS[i].isNew ? newTag(CATS[i].isNew) : '') + '</span><span class="n">' + catCount(CATS[i].id) + '</span></div>';
    }
    for (i = 0; i < items.length; i++) {
      var it = items[i], sel = inPane && it.type !== 'html' && k === ST.i ? ' sel' : '';
      if (open && it.type !== 'btn') { html += '</div>'; open = false; }
      if (it.type === 'html') { html += it.html; continue; }
      var at = ' data-z="pane" data-i="' + k + '"';
      if (it.type === 'row') {
        html += '<div class="set' + sel + (it.off ? ' off' : '') + '"' + at + (it.sw !== undefined ? ' role="switch" aria-checked="' + !!it.sw + '"' : '') + '>' +
          (it.order ? '<span class="ord">' + it.order + '</span>' : it.spin ? '<span class="spin"></span>' : svg(it.icon)) +
          '<span class="tx"><span class="nm">' + esc(it.nm) + '</span>' + (it.ds ? '<span class="ds">' + esc(it.ds) + '</span>' : '') + '</span>' + (it.val || '') +
          (it.sw !== undefined ? '<span class="sw' + (it.sw ? ' on' : '') + '"><i></i></span>' : it.chev ? svg('chevron-right', 'chev') : '') + '</div>';
      } else if (it.type === 'field') {
        var f = ST.form || {};
        html += '<div class="lbl">' + it.label + '</div><input id="' + it.id + '" class="field' + sel + (it.err ? ' err' : '') + '"' + at + ' type="text" autocomplete="off" spellcheck="false" placeholder="' + esc(it.ph || '') + '"' +
          (it.max ? ' maxlength="' + it.max + '"' : '') + (f.busy ? ' disabled' : '') + ' data-k="' + it.key + '" value="' + esc(f[it.key] || '') + '">' +
          '<div class="help' + (it.err ? ' err' : '') + '">' + (it.err ? svg('alert') : '') + esc(it.help || '') + '</div>';
      } else {
        if (!open) { html += '<div class="btns">'; open = true; }
        html += '<span class="btn' + sel + (it.primary ? ' primary' : '') + (it.danger ? ' danger' : '') + (it.off ? ' off' : '') + '"' + at + (it.id ? ' id="' + it.id + '"' : '') + '>' +
          (it.spin ? '<span class="spin"></span>' : it.icon ? svg(it.icon) : '') + it.t + '</span>';
      }
      k++;
    }
    if (open) html += '</div>';
    var v = ST.view, cur = focusables[ST.i], h;
    if (S.zone === 'setup') h = hint('ud', 'Выбор') + hint('ok', 'Открыть', 'ok') + hint('back', 'Назад', 'back');
    else if (v === 'plform') h = ST.form.err ? hint('ok', 'Клавиатура', 'ok') + hint('ud', 'Поля') + hint('back', 'Отменить изменения', 'back') : hint('ud', 'Поля') + hint('ok', 'Изменить / нажать', 'ok') + hint('back', 'Отменить', 'back');
    else if (v === 'plugadd') h = hint('ud', 'Поля') + hint('ok', 'Изменить / нажать', 'ok') + hint('back', 'Отменить', 'back');
    else if (v === 'plugin') h = hint('lr', 'Кнопки') + hint('ok', 'Нажать', 'ok') + hint('back', 'К списку', 'back');
    else if (v === 'plugins') h = hint('ok', 'Открыть', 'ok') + (cur && cur.hold ? hint('hold', 'Вкл/выкл · Удалить', 'ctx') : '') + hint('back', 'Закрыть', 'back');
    else if (v === 'playlists') h = hint('ok', 'Открыть', 'ok') + (cur && cur.hold ? hint('hold', 'Сделать активным · Обновить · Удалить', 'ctx') : '') + hint('l', 'Разделы', 'left') + hint('back', 'Назад', 'back');
    else h = hint('ud', 'Выбор') + hint('ok', 'Открыть / Переключить', 'ok') + hint('l', 'Разделы', 'left') + hint('back', 'Назад', 'back');
    $('setup').innerHTML = '<div class="top"><b>Настройки</b><span>' + headDate(Date.now()) + '</span></div>' +
      '<div class="cats" role="listbox">' + cats + '</div><div class="pane" id="pane">' + html + '</div><div class="hints">' + h + '</div>';
    // Панель длиннее экрана — подвигаем её так, чтобы элемент в фокусе был виден
    var pe = $('pane'), se = pe.querySelector('.sel');
    if (se && se.offsetTop + se.offsetHeight > pe.clientHeight) pe.scrollTop = se.offsetTop + se.offsetHeight - pe.clientHeight + 24;
  }

  function paneMove(code) {
    var items = ST.items, cur = items[ST.i], i;
    if (!cur) { if (code === KEY.LEFT) { S.zone = 'setup'; renderSetup(); } return; }
    if (code === KEY.UP || code === KEY.DOWN) {
      var dir = code === KEY.UP ? -1 : 1;
      for (i = ST.i + dir; i >= 0 && i < items.length; i += dir) if (items[i].r !== cur.r && !items[i].off) {
        // в ряду кнопок встаём на первую доступную
        while (dir < 0 && i > 0 && items[i - 1].r === items[i].r && !items[i - 1].off) i--;
        ST.i = i; break;
      }
    } else if (code === KEY.LEFT) {
      if (ST.i > 0 && items[ST.i - 1].r === cur.r && !items[ST.i - 1].off) ST.i--;
      else if (ST.view === 'playlists' || ST.view === 'app' || ST.view === 'plugins') S.zone = 'setup';
    } else if (code === KEY.RIGHT) {
      if (ST.i < items.length - 1 && items[ST.i + 1].r === cur.r && !items[ST.i + 1].off) ST.i++;
    }
    renderSetup();
  }
  function paneOk() {
    var it = ST.items[ST.i];
    if (!it || it.off) return;
    if (it.type === 'field') { var el = $(it.id); if (el && !el.disabled) el.focus(); return; }
    if (it.a) it.a();
  }
  function keySetup(code, e) {
    var active = document.activeElement;
    if (active && active.tagName === 'INPUT' && has(active, 'field')) {
      // Идёт ввод: системная клавиатура телевизора или клавиатура компьютера
      if (code === KEY.ENTER || code === KEY.IME_DONE || code === KEY.DOWN) { active.blur(); paneMove(KEY.DOWN); if (e.preventDefault) e.preventDefault(); }
      else if (code === KEY.UP) { active.blur(); paneMove(KEY.UP); if (e.preventDefault) e.preventDefault(); }
      else if (code === KEY.IME_CANCEL) active.blur();
      return;
    }
    if (S.zone === 'setup') {
      if (code === KEY.UP) { if (ST.cat > 0) pickCat(ST.cat - 1); }
      else if (code === KEY.DOWN) { if (ST.cat < CATS.length - 1) pickCat(ST.cat + 1); }
      else if (code === KEY.ENTER || code === KEY.RIGHT) { if (ST.items.length) { S.zone = 'pane'; ST.i = 0; renderSetup(); } }
      return;
    }
    if (code === KEY.ENTER) return; // OK обрабатывается по отпусканию: короткое — действие, удержание — меню
    if (code === KEY.DEL) { var it = ST.items[ST.i]; if (it && it.del) it.del(); return; }
    paneMove(code);
  }
  function pickCat(i) {
    finalizeDelete();
    if (CATS[ST.cat].id === 'plugins' && CATS[i].id !== 'plugins' && S.pluginsDirty) { reloadForPlugins(); return; }
    ST.cat = i; ST.view = CATS[i].id; ST.i = 0;
    if (ST.view === 'playlists') markSeenLater('playlists');
    renderSetup();
  }
  function backSetup() {
    var active = document.activeElement, v = ST.view;
    if (active && active.tagName === 'INPUT' && has(active, 'field')) { active.blur(); return; }
    if (S.zone === 'setup') { closeSetup(); return; }
    if (v === 'plform') {
      var f = ST.form;
      if (f.busy) { if (f.xhr) { try { f.xhr.abort(); } catch (e) { /* уже завершён */ } } f.busy = false; f.xhr = null; renderSetup(); return; } // проверка отменена, изменения остаются в форме
      if (formDirty()) toast('Изменения не сохранены');
      backToList('playlists', f.pl ? S.playlists.indexOf(f.pl) : S.playlists.length);
    } else if (v === 'plugadd') {
      if (ST.form.busy) { if (ST.form.xhr) { try { ST.form.xhr.abort(); } catch (e2) { /* уже завершён */ } } ST.form.busy = false; renderSetup(); return; }
      backToList('plugins', S.plugins.length);
    } else if (v === 'plugin') backToList('plugins', ST.plug);
    else if (v === 'plugins' && S.pluginsDirty) { finalizeDelete(); reloadForPlugins(); }
    else { finalizeDelete(); S.zone = 'setup'; renderSetup(); }
  }
  function backToList(view, focus) { ST.form = null; setView(view, Math.max(0, focus)); }

  // ---------- форма плейлиста: новая ссылка заменяет список только после проверки (SPEC 5.3) ----------
  function openForm(pl) {
    ST.form = { pl: pl, name: pl ? pl.name : '', url: pl ? pl.url : '', epgUrl: pl ? pl.epgUrl || '' : '', err: '', busy: false, xhr: null };
    setView('plform', 0);
  }
  function formDirty() {
    var f = ST.form, pl = f && f.pl;
    if (!f) return false;
    if (!pl) return !!(trim(f.name) || trim(f.url) || trim(f.epgUrl));
    return trim(f.name) !== pl.name || trim(f.url) !== pl.url || trim(f.epgUrl) !== (pl.epgUrl || '');
  }
  function formInput(el) {
    var f = ST.form, k = el.getAttribute('data-k');
    if (!f || !k) return;
    f[k] = el.value;
    if (ST.view !== 'plform') return;
    // Без полной перерисовки: поле в фокусе нельзя пересоздавать
    var d = formDirty();
    if ($('f_dirty')) $('f_dirty').style.display = d ? '' : 'none';
    if ($('f_save')) cls($('f_save'), 'off', !d);
    for (var i = 0; i < ST.items.length; i++) if (ST.items[i].id === 'f_save') ST.items[i].off = !d;
  }
  function formError(text) {
    var f = ST.form;
    f.busy = false; f.xhr = null; f.err = text;
    ST.i = 1; // фокус на поле ссылки
    renderSetup();
  }
  function checkAndSave() {
    var f = ST.form, pl = f.pl, url = trim(f.url), epgUrl = trim(f.epgUrl), name = trim(f.name).substr(0, 40) || hostOf(url) || 'Плейлист';
    if (f.busy) return;
    if (!/^https?:\/\//i.test(url)) { formError('Ссылка должна начинаться с http:// или https://'); return; }
    f.err = '';
    function done(text, count) {
      var isNew = !pl, active;
      if (isNew) { pl = { id: newPlaylistId(), name: name, url: url, epgUrl: epgUrl, updated: 0, count: 0, lastError: null }; S.playlists.push(pl); }
      active = pl.id === S.activePl || !S.activePl;
      var epgChanged = (pl.epgUrl || '') !== epgUrl;
      pl.name = name; pl.url = url; pl.epgUrl = epgUrl;
      if (text !== null) { pl.updated = Date.now(); pl.count = count; pl.lastError = null; }
      if (!S.activePl) S.activePl = pl.id;
      savePlaylists();
      if (active) {
        syncActive();
        if (text !== null) { save('m3u', text); S.epg = null; epgSeq++; epgBusy = false; applyPlaylist(parseM3U(text), false); loadEpg(true); }
        else if (epgChanged) { S.epg = null; epgSeq++; epgBusy = false; loadEpg(true); }
      }
      toast('Плейлист «' + name + '» сохранён' + (pl.count ? ' · ' + chCount(pl.count) : ''), 'ok');
      backToList('playlists', S.playlists.indexOf(pl));
    }
    if (pl && pl.url === url) { done(null, 0); return; } // ссылка не менялась — проверять нечего
    f.busy = true;
    renderSetup();
    f.xhr = fetchText(url, function (text) {
      if (ST.form !== f || !f.busy) return;
      var parsed = parseM3U(text);
      if (!parsed.channels.length) { formError(linkError('', -3)); return; }
      f.busy = false;
      done(text, parsed.channels.length);
    }, function (reason, status) { if (ST.form === f && f.busy) formError(linkError(reason, status)); });
  }
  function askDeletePlaylist(pl) {
    if (pl.id === S.activePl) return;
    confirmDialog('Удалить плейлист «' + pl.name + '»?', 'Избранное останется. Чтобы вернуть плейлист, понадобится его ссылка.', function () {
      var i = S.playlists.indexOf(pl);
      if (i >= 0) S.playlists.splice(i, 1);
      savePlaylists();
      toast('Плейлист «' + pl.name + '» удалён');
      backToList('playlists', Math.min(i, S.playlists.length - 1));
    });
  }
  function playlistMenu(pl) {
    var active = pl.id === S.activePl;
    openPop('ctx', pl.name, [
      { t: 'Сделать активным', icon: 'check', off: active, a: function () { activatePlaylist(pl, function () { if (shown('setup')) renderSetup(); }); } },
      { t: 'Обновить', icon: 'refresh', a: function () { if (active) { refresh(true); renderSetup(); } else recheckPlaylist(pl); } },
      { t: 'Изменить', icon: 'edit', a: function () { openForm(pl); } },
      { t: 'Удалить…', icon: 'trash', danger: true, off: active, a: function () { askDeletePlaylist(pl); } }],
      popAtFocus(), active ? 1 : 0);
  }
  /** Проверяет неактивный плейлист: обновляет число каналов или записывает ошибку. */
  function recheckPlaylist(pl) {
    toast('Обновляем «' + pl.name + '»…', 'wait');
    fetchText(pl.url, function (text) {
      var n = parseM3U(text).channels.length;
      if (!n) { pl.lastError = { time: Date.now(), message: 'В файле нет ни одного канала' }; toast('«' + pl.name + '» не загрузился: в файле нет ни одного канала', 'warn'); }
      else { pl.count = n; pl.updated = Date.now(); pl.lastError = null; toast('Список обновлён · ' + chCount(n), 'ok'); }
      savePlaylists();
      if (shown('setup')) renderSetup();
    }, function (reason) {
      pl.lastError = { time: Date.now(), message: reason.charAt(0).toUpperCase() + reason.substr(1) };
      savePlaylists();
      toast('«' + pl.name + '» не загрузился: ' + reason, 'warn');
      if (shown('setup')) renderSetup();
    });
  }
  /** Где открыть меню удержания: справа от строки в фокусе (на компьютере — у курсора). */
  function popAtFocus() {
    if (deskMode()) return { x: lastMouse.x, y: lastMouse.y, cursor: true };
    var el = document.querySelector('#pane .sel'), y = 300;
    if (el) y = Math.min(640, 150 + el.offsetTop - $('pane').scrollTop);
    return { x: 1240, y: y };
  }

  // ---------- плагины: установка, включение, удаление (SPEC 6) ----------
  function addPlugin() {
    var f = ST.form, url = rawUrl(f.url);
    if (f.busy) return;
    function fail(text) { f.busy = false; f.err = text; ST.i = 0; renderSetup(); }
    if (!/^https?:\/\//i.test(url)) { fail('Ссылка должна начинаться с http:// или https://'); return; }
    for (var i = 0; i < S.plugins.length; i++) if (S.plugins[i].url === url) { fail('Этот плагин уже добавлен'); return; }
    f.busy = true; f.err = '';
    renderSetup();
    f.xhr = fetchText(url, function (code) {
      if (ST.form !== f || !f.busy) return;
      var index = S.plugins.length, before = PL.list().length;
      var ok = runPlugin(code, index, true), meta = PL.list().filter(function (m) { return m.source === index; })[0];
      if (!ok || !meta || PL.list().length === before) { fail('В этом файле нет плагина: не найден вызов IPTV.plugin({ id: … })'); return; }
      S.plugins.push({ url: url, enabled: true, code: code, id: meta.id, name: meta.name, version: meta.version, description: meta.description, author: meta.author,
        hasPlaylist: !!meta.hasPlaylist, hasStream: !!meta.hasStream, lastError: null });
      save('plugins', S.plugins);
      S.pluginsDirty = true;
      toast('Плагин «' + meta.name + '» добавлен. Применится, когда вы выйдете из раздела', 'ok');
      backToList('plugins', index);
    }, function (reason) { if (ST.form === f && f.busy) fail('Не удалось загрузить плагин: ' + reason); });
  }
  function togglePlugin(i) {
    var p = S.plugins[i];
    if (!p) return;
    p.enabled = !p.enabled;
    if (!p.enabled) p.lastError = null;
    save('plugins', S.plugins);
    S.pluginsDirty = true;
    toast('Плагин ' + (p.enabled ? 'включён' : 'выключен'));
    renderSetup();
  }
  function pluginMenu(i) {
    var p = S.plugins[i];
    if (!p) return;
    openPop('ctx', plugName(p), [
      { t: p.enabled ? 'Выключить' : 'Включить', icon: p.enabled ? 'close' : 'check', a: function () { togglePlugin(i); } },
      { t: 'Удалить…', icon: 'trash', danger: true, a: function () { askDeletePlugin(i); } }], popAtFocus(), 0);
  }
  function askDeletePlugin(i) {
    var p = S.plugins[i];
    if (!p) return;
    confirmDialog('Удалить плагин «' + plugName(p) + '»?', 'Каналы останутся без его изменений. Чтобы вернуть плагин, понадобится ссылка на его файл.', function () { deletePlugin(i); });
  }
  /** Плагин убирается из списка, но запись держится в памяти 6 с: тост «Вернуть» ставит её на прежнее место. */
  function deletePlugin(i) {
    finalizeDelete();
    var p = S.plugins.splice(i, 1)[0];
    ST.pending = { p: p, i: i };
    // Фокус — на следующем плагине, если его нет — на предыдущем, если список пуст — на «Добавить плагин»
    setView('plugins', Math.max(0, Math.min(i, S.plugins.length - 1)));
    toast('Плагин «' + plugName(p) + '» удалён', '', { label: 'Вернуть', fn: restorePlugin, expire: finalizeDelete });
  }
  function restorePlugin() {
    var d = ST.pending;
    if (!d) return;
    ST.pending = null;
    S.plugins.splice(Math.min(d.i, S.plugins.length), 0, d.p);
    toast('Плагин возвращён');
    if (shown('setup')) { if (ST.view === 'plugins') ST.i = d.i; renderSetup(); }
  }
  function finalizeDelete() {
    if (!ST.pending) return;
    ST.pending = null;
    T.action = null;
    save('plugins', S.plugins);
    S.pluginsDirty = true;
  }

  // ---------- первый запуск (SPEC 3.11, O1/O2) ----------
  var OB = { i: 0, url: '', err: '', busy: false, xhr: null };
  function openOnboarding() {
    closeLayers();
    show('guide', false); show('setup', false);
    hideSplash(true);
    S.zone = 'onb';
    OB.i = 0; OB.err = ''; OB.busy = false;
    show('onb', true);
    renderOnb();
  }
  function renderOnb() {
    var html;
    if (OB.busy) {
      html = '<div class="load"><span class="spin big"></span><div class="h">Загружаем каналы</div><div class="x">' + esc(hostOf(OB.url)) + ' · обычно это занимает несколько секунд</div>' +
        '<div class="x">Первый канал включится сам. Телепрограмма догрузится потом.</div></div>';
    } else {
      html = '<div class="wrap"><div class="h">Добро пожаловать</div><div class="x">Вставьте ссылку на плейлист от вашего провайдера — и можно смотреть.</div>' +
        '<div class="lbl">Ссылка на плейлист</div><input id="f_onb" class="field' + (OB.i === 0 ? ' sel' : '') + (OB.err ? ' err' : '') + '" data-z="onb" data-i="0" type="text" autocomplete="off" spellcheck="false" placeholder="http://… .m3u или .m3u8" value="' + esc(OB.url) + '">' +
        '<div class="help' + (OB.err ? ' err' : '') + '">' + (OB.err ? svg('alert') + esc(OB.err) : 'Ссылка должна начинаться с http:// или https://') + '</div>' +
        '<div class="btns"><span class="btn primary' + (OB.i === 1 ? ' sel' : '') + '" data-z="onb" data-i="1">Открыть</span></div>' +
        '<div class="x small">' + (PC ? 'Ссылку можно вставить из буфера обмена: Ctrl+V.' : 'Нажмите OK на поле — откроется клавиатура телевизора.') + '</div></div>';
    }
    $('onb').innerHTML = html;
    if (!OB.busy && PC && OB.i === 0 && $('f_onb')) $('f_onb').focus();
  }
  function onbFailed(text) { OB.busy = false; OB.err = text; OB.i = 0; renderOnb(); }
  function closeOnboarding() { show('onb', false); S.zone = 'play'; }
  function onbOpen() {
    var url = trim(OB.url);
    if (OB.busy) return;
    if (!/^https?:\/\//i.test(url)) { onbFailed('Ссылка должна начинаться с http:// или https://'); return; }
    OB.busy = true; OB.err = '';
    renderOnb();
    OB.xhr = fetchText(url, function (text) {
      var parsed = parseM3U(text);
      if (!OB.busy) return;
      if (!parsed.channels.length) { onbFailed(linkError('', -3)); return; }
      var pl = { id: newPlaylistId(), name: hostOf(url) || 'Мой плейлист', url: url, epgUrl: '', updated: Date.now(), count: parsed.channels.length, lastError: null };
      S.playlists.push(pl); S.activePl = pl.id;
      savePlaylists(); syncActive();
      save('m3u', text);
      OB.busy = false;
      closeOnboarding();
      applyPlaylist(parsed, true);
      loadEpg(true);
      if (deskMode()) renderGuide();
    }, function (reason, status) { if (OB.busy) onbFailed(linkError(reason, status)); });
  }
  function keyOnb(code, e) {
    var active = document.activeElement, typing = active && active.id === 'f_onb';
    if (OB.busy) return;
    if (typing) {
      if (code === KEY.ENTER || code === KEY.IME_DONE) { active.blur(); if (e.preventDefault) e.preventDefault(); onbOpen(); }
      else if (code === KEY.DOWN) { active.blur(); OB.i = 1; renderOnb(); }
      else if (code === KEY.IME_CANCEL) active.blur();
      return;
    }
    if (code === KEY.UP) { OB.i = 0; renderOnb(); }
    else if (code === KEY.DOWN) { OB.i = 1; renderOnb(); }
    else if (code === KEY.ENTER) { if (OB.i === 0) { if ($('f_onb')) $('f_onb').focus(); } else onbOpen(); }
  }

  // ---------- заставка ----------
  var splashGone = false, bornAt = Date.now();
  function hideSplash(now) {
    if (splashGone) return;
    var wait = 1500 - (Date.now() - bornAt);
    if (!now && wait > 0) { setTimeout(hideSplash, wait); return; }
    splashGone = true;
    cls($('splash'), now ? 'gone' : 'off', true);
    setTimeout(function () { cls($('splash'), 'gone', true); }, 400);
    showInfo();
    bootReport();
    if (S.update && S.zone === 'play') updateDialog();
  }

  // ---------- пульт и клавиатура ----------
  var enterTimer = null, holdZone = '', lastBack = 0, swallowEnter = false;
  var STUB = { preventDefault: function () {}, repeat: false };
  var HOLD = { channels: 1, results: 1, grid: 1, pane: 1, play: 1 };

  function exitApp() {
    try {
      if (TIZEN) window.tizen.application.getCurrentApplication().exit();
      else if (!PC) window.close();
    } catch (e) { toast('Нажмите кнопку «Домой» на пульте'); }
  }

  /** «Назад» всегда снимает ровно один слой. */
  function onBack() {
    var z = S.zone;
    if (N.on) { numClose(); return; }
    if (z === 'dialog') { if (D.busy) return; if (D.onBack) D.onBack(); else closeDialog(); return; }
    if (z === 'ctx' || z === 'plm') { closePop(); return; }
    if (z === 'ctl') { closeCtl(); return; }
    if (z === 'mini') { closeMini(); return; }
    if (z === 'grid') { if (G.head >= 0) { G.head = -1; renderGrid(); } else closeGrid(); return; }
    if (z === 'results') { S.zone = 'search'; renderSearch(); return; }
    if (z === 'search') { closeSearch(); return; }
    if (z === 'setup' || z === 'pane') { backSetup(); return; }
    if (z === 'onb') {
      var a = document.activeElement;
      if (a && a.id === 'f_onb' && !PC) { a.blur(); return; }
      if (OB.busy) { if (OB.xhr) { try { OB.xhr.abort(); } catch (e) { /* уже завершён */ } } OB.busy = false; renderOnb(); return; }
      exitApp(); return;
    }
    if (z === 'date') { S.zone = 'epg'; renderGuide(); return; }
    if (z === 'head') { S.zone = 'groups'; S.gi = 0; renderGuide(); return; }
    if (deskMode()) { deskBack(); return; }
    if (inGuide()) { closeGuide(); showInfo(); return; }
    if (S.arch) { goLive(); return; }
    if (isFull()) { toggleFull(); return; }
    if (shown('osd') && !P.state && Date.now() - lastBack >= 2000) { hideInfo(); lastBack = 0; return; }
    if (PC) return; // окно клавишей Esc не закрываем
    if (Date.now() - lastBack < 2000) { exitApp(); return; }
    lastBack = Date.now();
    toast('Нажмите «Назад» ещё раз, чтобы выйти');
  }

  function digitOf(code, e) {
    if (e && (e.shiftKey || e.ctrlKey || e.altKey || e.metaKey)) return -1;
    return code >= 48 && code <= 57 ? code - 48 : code >= 96 && code <= 105 ? code - 96 : -1;
  }
  /** Буква с клавиатуры компьютера — сразу начинаем поиск. У пультов таких кнопок нет. */
  function letterOf(e) {
    var k = e && e.key;
    return k && k.length === 1 && !e.ctrlKey && !e.altKey && !e.metaKey && /[a-zа-яё]/i.test(k) ? k : '';
  }

  /** Канал под фокусом в текущем слое (для зелёной, жёлтой и меню действий). */
  function focusedChannel() {
    var z = S.zone;
    if (z === 'channels') return S.list[S.ci];
    if (z === 'epg' || z === 'date') return S.epgCh;
    if (z === 'mini') return MG.list[MG.i];
    if (z === 'grid') return G.list[G.row];
    if (z === 'results') { var it = Q.items[Q.i]; return it ? it.type === 'ch' ? it.v : it.type === 'prog' ? it.v.ch : null : null; }
    return S.cur;
  }
  function playOk() {
    if (!S.channels.length) { openGuide(); return; }
    if (stateAction()) return;
    if (S.arch) { togglePause(); openCtl('pause'); }
    else openCtl();
  }
  /** Короткое нажатие OK. */
  function shortOk(z) {
    if (z === 'channels') chooseChannel();
    else if (z === 'results') resultChoose();
    else if (z === 'grid') gridChoose();
    else if (z === 'pane') paneOk();
    else if (z === 'play') playOk();
  }
  /** Удержание OK (650 мс): меню действий. */
  function holdOk(z) {
    var ch = focusedChannel();
    if (z === 'pane') { var it = ST.items[ST.i]; if (it && it.hold) it.hold(); else paneOk(); return; }
    if (!ch) return;
    if (z === 'play') { openGuide(); openChannelMenu(S.cur, null, 'guide'); }
    else if (z === 'channels') openChannelMenu(ch, deskMode() ? { x: lastMouse.x, y: lastMouse.y, cursor: true } : null, 'guide');
    else if (z === 'results') openChannelMenu(ch, deskMode() ? { x: lastMouse.x, y: lastMouse.y, cursor: true } : { x: 1180, y: 330 }, 'results');
    else if (z === 'grid') openChannelMenu(ch, deskMode() ? { x: lastMouse.x, y: lastMouse.y, cursor: true } : { x: 500, y: 330 }, 'grid');
  }

  /** Кнопки, которые работают одинаково и в эфире, и в гиде: цветные, «Гид», «Список каналов». */
  function shortcut(code) {
    var ch = focusedChannel();
    if (code === KEY.RED) openSearch();
    else if (code === KEY.GREEN) { if (ch) toggleFav(ch); }
    else if (code === KEY.YELLOW || code === KEY.GUIDE) { if (ch) showProgramme(ch); }
    else if (code === KEY.BLUE) openSetup();
    else if (code === KEY.CH_LIST) { if (inGuide() && !deskMode()) { closeGuide(); showInfo(); } else openGuide(); }
    else if (code === KEY.MUTE) toggleMute();
    else if (code === KEY.VOL_UP) setVolume(S.vol + 5);
    else if (code === KEY.VOL_DOWN) setVolume(S.vol - 5);
    else return false;
    return true;
  }

  function handleKey(code, e) {
    var z = S.zone;
    if (N.on && numKey(code, e)) return;
    if (code === KEY.ENTER && T.action && z !== 'dialog') { swallowEnter = true; toastAct(); return; }
    if (z === 'dialog') { keyDialog(code); return; }
    if (z === 'ctx' || z === 'plm') { keyPop(code); return; }
    if (z === 'onb') { keyOnb(code, e); return; }
    if (z === 'setup' || z === 'pane') {
      if (code === KEY.ENTER && z === 'pane') { armHold(e); return; }
      keySetup(code, e);
      return;
    }
    if (z === 'search') { keySearch(code, e); return; }
    if (z === 'results') { if (code === KEY.ENTER) armHold(e); else keyResults(code); return; }

    var digit = digitOf(code, e), letter = letterOf(e);
    if (digit >= 0) { typeDigit(digit); return; }
    if (PC && code === 77 && e.key && /^m$/i.test(e.key)) { toggleMute(); return; }
    if (shortcut(code)) return;
    if (letter && PC && S.channels.length) { if (e.preventDefault) e.preventDefault(); openSearch(letter); return; }
    if (PC && (code === KEY.MENU || code === KEY.F10 && e.shiftKey)) { if (HOLD[z]) holdOk(z); return; }
    if (inGuide()) {
      if (code === KEY.ENTER && z === 'channels') { armHold(e); return; }
      keyGuide(code);
      return;
    }
    if (z === 'mini') { keyMini(code); return; }
    if (z === 'grid') { if (code === KEY.ENTER && G.head < 0) armHold(e); else keyGrid(code); return; }
    if (z === 'ctl' && keyCtl(code)) return;
    // Плеер
    if (code === KEY.ENTER) armHold(e);
    else if (code === KEY.LEFT) { if (S.arch) { seek(-30); openCtl('pause'); } else if (deskMode()) deskFocus('channels'); else openMini(); }
    else if (code === KEY.RIGHT) { if (S.arch) { seek(30); openCtl('pause'); } else if (S.cur) showProgramme(S.cur); }
    else if (code === KEY.UP || code === KEY.CH_UP) zap(1);
    else if (code === KEY.DOWN || code === KEY.CH_DOWN) zap(-1);
    else if (code === KEY.INFO) { if (shown('osd')) hideInfo(); else showInfo(); }
    else if (code === KEY.PRE_CH || code === KEY.BKSP) playPrev();
    else if (code === KEY.PLAY || code === KEY.PAUSE || code === KEY.PLAY_PAUSE || code === KEY.SPACE) { if (S.arch) { togglePause(); openCtl('pause'); } }
    else if (code === KEY.REW) { seek(-30); if (S.arch) openCtl('pause'); }
    else if (code === KEY.FF) { seek(30); if (S.arch) openCtl('pause'); }
  }
  /** Удержание OK: существующий механизм — таймер на нажатии, короткое действие на отпускании. */
  function armHold(e) {
    if (enterTimer || e.repeat) return;
    holdZone = S.zone === 'ctl' ? 'play' : S.zone;
    if (e === STUB) { shortOk(holdZone); return; }
    enterTimer = setTimeout(function () { enterTimer = -1; if (S.zone === holdZone || holdZone === 'play' && S.zone === 'ctl') holdOk(holdZone); }, 650);
  }

  document.addEventListener('keydown', function (e) {
    var code = e.keyCode, a = document.activeElement, typing = a && a.tagName === 'INPUT';
    if (code === KEY.BACK_LG) code = KEY.BACK;
    if (code === KEY.PAGE_UP) code = KEY.CH_UP;
    if (code === KEY.PAGE_DOWN) code = KEY.CH_DOWN;
    if (code >= KEY.LEFT && code <= KEY.DOWN) { cls(document.body, 'mouse', false); cls(document.body, 'keys', true); }
    if (code === KEY.BACK || code === KEY.ESC) { e.preventDefault(); if (typing && a.id === 'q') deskQKey(code, e); else onBack(); return; }
    if (!splashGone && S.zone !== 'onb') return;
    if (typing) {
      if (S.zone === 'pane' || S.zone === 'setup') keySetup(code, e);
      else if (S.zone === 'onb') keyOnb(code, e);
      else if (a.id === 'q') deskQKey(code, e);
      return;
    }
    if (code === KEY.ENTER && swallowEnter) return;
    if (code === KEY.TAB) { e.preventDefault(); if (S.zone === 'dialog') keyDialog(code); else if (deskMode()) deskTab(e.shiftKey ? -1 : 1); return; }
    if (code >= KEY.LEFT && code <= KEY.DOWN || code === KEY.SPACE || code === KEY.BKSP) e.preventDefault();
    handleKey(code, e);
  });

  document.addEventListener('keyup', function (e) {
    if (e.keyCode !== KEY.ENTER) return;
    swallowEnter = false;
    if (!enterTimer) return;
    var held = enterTimer === -1;
    if (!held) clearTimeout(enterTimer);
    enterTimer = null;
    if (held) return;
    if (S.zone === holdZone || holdZone === 'play' && S.zone === 'ctl') shortOk(holdZone);
  });

  document.addEventListener('input', function (e) {
    var el = e.target;
    if (!el || el.tagName !== 'INPUT') return;
    if (el.id === 'f_onb') OB.url = el.value;
    else if (el.id === 'q') deskQInput();
    else formInput(el);
  });
  document.addEventListener('focusin', function (e) {
    var el = e.target, i = el && el.getAttribute ? el.getAttribute('data-i') : null;
    if (!el || el.tagName !== 'INPUT' || i === null) return;
    if (S.zone === 'pane' || S.zone === 'setup') { if (S.zone !== 'pane' || ST.i !== +i) { S.zone = 'pane'; ST.i = +i; cls(el, 'sel', true); } }
    else if (S.zone === 'onb') OB.i = 0;
  });

  // ---------- мышь, указка пульта и сенсорный экран ----------
  var lastMouse = { x: 960, y: 540 }, SC = { s: 1, x: 0, y: 0 }, overCtl = false;
  function stageW() { return deskMode() ? window.innerWidth : 1920; }
  function stageH() { return deskMode() ? window.innerHeight : 1080; }
  function closest(el, attr) {
    while (el && el !== document) { if (el.getAttribute && el.getAttribute(attr) !== null) return el; el = el.parentNode; }
    return null;
  }
  function inside(el, id) {
    while (el && el !== document) { if (el.id === id) return true; el = el.parentNode; }
    return false;
  }
  /** Какой слой сейчас верхний: указка и мышь работают только с ним. */
  function topLayer(z) {
    var cur = S.zone;
    if (N.on) return z === 'num';
    if (cur === 'dialog') return z === 'dialog';
    if (cur === 'ctx' || cur === 'plm') return z === cur;
    if (cur === 'onb') return z === 'onb';
    if (cur === 'setup' || cur === 'pane') return z === 'setup' || z === 'pane';
    if (cur === 'search' || cur === 'results') return z === 'search' || z === 'results';
    if (cur === 'grid') return z === 'grid' || z === 'gridh';
    if (cur === 'mini') return z === 'mini';
    return z === 'groups' || z === 'channels' || z === 'epg' || z === 'date' || z === 'head' || z === 'ctl';
  }
  /** Ставит фокус на элемент под указкой. Возвращает true, если фокус изменился. */
  function setFocus(z, i, v) {
    var changed = true;
    if (z === 'groups') {
      changed = S.zone !== z || S.gi !== i; S.zone = z; S.gi = i;
      var m = S.menu[i];
      if (changed && m && !m.action) { S.menuId = m.id; S.list = m.list; S.ci = Math.max(0, S.list.indexOf(S.cur)); }
    }
    else if (z === 'channels') { changed = S.zone !== z || S.ci !== i; S.zone = z; S.ci = i; }
    else if (z === 'epg') { changed = S.zone !== z || S.pi !== i; S.zone = z; S.pi = i; }
    else if (z === 'date' || z === 'head') { changed = S.zone !== z; S.zone = z; }
    else if (z === 'ctx' || z === 'plm') { changed = M.i !== i; if (!M.items[i] || M.items[i].off) return false; M.i = i; }
    else if (z === 'ctl') { changed = S.zone !== z || P.bi !== i; if (P.btns[i] && P.btns[i].off) return false; if (S.zone === 'play') S.zone = 'ctl'; P.bi = i; }
    else if (z === 'mini') { changed = MG.i !== i; MG.i = i; }
    else if (z === 'grid') {
      var ch = G.list[i], e = ch ? epgFor(ch) : null, k = parseInt(v, 10), ft = e && k >= 0 && e.s[k] !== undefined ? Math.max(e.s[k], G.t0) + MIN : G.ft;
      changed = G.row !== i || G.ft !== ft || G.head >= 0; G.row = i; G.ft = ft; G.head = -1;
    }
    else if (z === 'gridh') { changed = G.head !== i; G.head = i; }
    else if (z === 'search') { var r = Math.floor(i / 10), c = i % 10; changed = S.zone !== z || Q.kr !== r || Q.kc !== c; S.zone = z; Q.kr = r; Q.kc = c; }
    else if (z === 'results') { changed = S.zone !== z || Q.i !== i; S.zone = z; Q.i = i; }
    else if (z === 'setup') return false;
    else if (z === 'pane') { if (ST.items[i] && ST.items[i].off) return false; changed = S.zone !== z || ST.i !== i; S.zone = z; ST.i = i; }
    else if (z === 'dialog') { changed = D.i !== i; D.i = i; }
    else if (z === 'num') { changed = N.sel !== i; N.sel = i; }
    else if (z === 'onb') { changed = OB.i !== i; OB.i = i; }
    return changed;
  }
  function renderFocus(z) {
    if (z === 'dialog') renderDialog();
    else if (z === 'num') { renderNum(); numArm(3000); }
    else if (z === 'ctx' || z === 'plm') renderPop();
    else if (z === 'ctl') { renderCtl(); armCtl(); }
    else if (z === 'onb') { if (document.activeElement && document.activeElement.id === 'f_onb') return; renderOnb(); }
    else if (z === 'pane') { if (document.activeElement && document.activeElement.tagName === 'INPUT') return; renderSetup(); }
    else renderZone();
  }
  /** Щелчок или OK указки по элементу слоя. */
  function activate(z, i, v, target) {
    if (z === 'groups') keyGroups(KEY.ENTER);
    else if (z === 'channels') chooseChannel();
    else if (z === 'epg') chooseProgramme();
    else if (z === 'head') openPlaylistMenu(deskMode() ? { x: 248, y: 44, cursor: true } : null);
    else if (z === 'ctx' || z === 'plm') popChoose();
    else if (z === 'ctl') ctlPress(i);
    else if (z === 'mini') keyMini(KEY.ENTER);
    else if (z === 'grid') { if (v === 'ch') tuneTo(G.list[i], G.list, G.group); else gridChoose(); }
    else if (z === 'gridh') keyGrid(KEY.ENTER);
    else if (z === 'search') kbPress();
    else if (z === 'results') resultChoose();
    else if (z === 'setup') { if (ST.cat !== i || S.zone !== 'setup') { S.zone = 'setup'; pickCat(i); if (S.zone === 'setup') renderSetup(); } }
    else if (z === 'pane') { if (target && target.tagName === 'INPUT') return; paneOk(); }
    else if (z === 'dialog') { var b = D.buttons[i]; if (b && !D.busy) b.a(); }
    else if (z === 'num') numCommit();
    else if (z === 'onb') { if (i === 1) onbOpen(); }
  }

  function isFull() { var d = document; return !!(d.fullscreenElement || d.webkitFullscreenElement); }
  function toggleFull() {
    var d = document, el = d.documentElement;
    try {
      if (isFull()) (d.exitFullscreen || d.webkitExitFullscreen).call(d);
      else (el.requestFullscreen || el.webkitRequestFullscreen).call(el);
    } catch (e) { /* без полноэкранного режима */ }
  }

  /** Действие подсказки или кнопки: то же, что соответствующая кнопка пульта. */
  function act(name, el) {
    var z = S.zone;
    if (name === 'back') onBack();
    else if (name === 'ok') { if (N.on) numCommit(); else if (T.action) toastAct(); else if (z === 'ctl') ctlPress(P.bi); else if (HOLD[z]) shortOk(z); else handleKey(KEY.ENTER, STUB); }
    else if (name === 'ctx') { if (HOLD[z]) holdOk(z); }
    else if (name === 'left') handleKey(KEY.LEFT, STUB);
    else if (name === 'right') handleKey(KEY.RIGHT, STUB);
    else if (name === 'down') handleKey(KEY.DOWN, STUB);
    else if (name === 'toast') toastAct();
    else if (name === 'retry') retryPlay();
    else if (name === 'guide') openGuide();
    else if (name === 'refresh') refresh(true);
    else if (name === 'editlink') { openSetup('playlists'); var a = activePlaylist(); if (a) openForm(a); }
    else if (name === 'fav') { var row = closest(el, 'data-i'), list = row && row.getAttribute('data-z') === 'results' ? null : S.list; var c = list ? list[+row.getAttribute('data-i')] : focusedChannel(); if (c) toggleFav(c); }
    else if (name === 'day') { loadProg(S.epgCh, S.days[+el.getAttribute('data-v')]); renderGuide(); }
    else if (name === 'seek') { var r = el.getBoundingClientRect(), x = lastClick.x; seekTo((x - r.left) / Math.max(1, r.width) * archLength()); armCtl(); }
    else deskAct(name, el);
  }

  var mouseTimer = null, lastXY = '', lastClick = { x: 0, y: 0 };
  function pointerIdle() {
    if (overCtl) { mouseTimer = setTimeout(pointerIdle, 1500); return; }
    cls(document.body, 'idle', true);
    if (S.zone === 'play' || S.zone === 'ctl') cls(document.body, 'mouse', false);
  }
  document.addEventListener('mousemove', function (e) {
    var xy = e.screenX + ':' + e.screenY;
    if (xy === lastXY) return; // некоторые телевизоры шлют событие и без движения
    lastXY = xy;
    lastMouse.x = (e.clientX - SC.x) / SC.s; lastMouse.y = (e.clientY - SC.y) / SC.s;
    cls(document.body, 'mouse', true); cls(document.body, 'keys', false); cls(document.body, 'idle', false);
    clearTimeout(mouseTimer);
    mouseTimer = setTimeout(pointerIdle, 4000);
    overCtl = inside(e.target, 'ctl') || inside(e.target, 'timeline');
    if (!splashGone) return;
    if (deskMode()) { deskMove(e); return; }
    // Указка (Magic Remote) и мышь в режиме ТВ: фокус следует за указкой
    var el = closest(e.target, 'data-z');
    if (el) {
      var z = el.getAttribute('data-z'), i = +el.getAttribute('data-i');
      if (topLayer(z) && setFocus(z, i, el.getAttribute('data-v'))) renderFocus(z);
    } else if (S.zone === 'play' && PC) { openCtl(); P.bi = -1; renderCtl(); }
    if (S.zone === 'ctl') armCtl();
  });
  document.addEventListener('click', function (e) {
    lastClick.x = e.clientX; lastClick.y = e.clientY;
    if (!splashGone && S.zone !== 'onb') return;
    var a = closest(e.target, 'data-a'), el = closest(e.target, 'data-z');
    if (a) { act(a.getAttribute('data-a'), a); return; }
    if (el) {
      var z = el.getAttribute('data-z'), i = +el.getAttribute('data-i'), v = el.getAttribute('data-v');
      if (!topLayer(z)) return;
      setFocus(z, i, v);
      activate(z, i, v, e.target);
      return;
    }
    if (N.on) { numClose(); return; }
    if (S.zone === 'ctx' || S.zone === 'plm') { closePop(); return; }
    if (deskMode()) { deskClick(e); return; }
    if (S.zone === 'ctl') closeCtl();
    else if (S.zone === 'play') playOk();
  });
  document.addEventListener('contextmenu', function (e) {
    e.preventDefault();
    if (!splashGone) return;
    var el = closest(e.target, 'data-z'), z = el ? el.getAttribute('data-z') : '', i = el ? +el.getAttribute('data-i') : -1, pos = { x: lastMouse.x, y: lastMouse.y, cursor: true };
    if (S.zone === 'ctx' || S.zone === 'plm') closePop();
    if (z && !topLayer(z)) return;
    if (z === 'channels') { S.ci = i; S.zone = z; renderGuide(); openChannelMenu(S.list[i], pos, 'guide'); }
    else if (z === 'mini') openChannelMenu(MG.list[i], pos, 'play');
    else if (z === 'grid') { setFocus(z, i, el.getAttribute('data-v')); renderGrid(); openChannelMenu(G.list[i], pos, 'grid'); }
    else if (z === 'results') { Q.i = i; S.zone = z; var c = focusedChannel(); if (c) openChannelMenu(c, pos, 'results'); }
    else if (z === 'pane') { ST.i = i; S.zone = z; renderSetup(); var it = ST.items[i]; if (it && it.hold) { lastMouse.x = pos.x; it.hold(); } }
    else if (!z && (S.zone === 'play' || S.zone === 'ctl') && S.cur && (e.target === video || e.target.id === 'shadeB' || e.target.id === 'state' || e.target === document.body)) openChannelMenu(S.cur, pos, 'play');
  });
  document.addEventListener('dblclick', function (e) {
    if (PC && (e.target === video || e.target.id === 'shadeB' || e.target.id === 'shadeT')) toggleFull();
  });
  document.addEventListener('wheel', function (e) {
    var d = e.deltaY > 0 ? 1 : -1, t = e.target;
    if (inside(t, 'pop')) return;
    if (inside(t, 'gchs')) {
      if (deskMode()) { tops.c = (tops.c || 0) + d * 2; renderGuide(); }
      else if (S.zone === 'channels') keyChannels(d > 0 ? KEY.DOWN : KEY.UP);
    }
    else if (inside(t, 'gmenu')) { tops.g = (tops.g || 0) + d; renderGuide(); }
    else if (inside(t, 'gdet')) { tops.p = (tops.p || 0) + d * 2; renderGuide(); }
    else if (inside(t, 'mini')) keyMini(d > 0 ? KEY.DOWN : KEY.UP);
    else if (inside(t, 'grid') && S.zone === 'grid') {
      if (e.shiftKey || Math.abs(e.deltaX) > Math.abs(e.deltaY)) { G.t0 += d * 30 * MIN; G.ft += d * 30 * MIN; }
      else tops.gr = (tops.gr || 0) + d;
      renderGrid();
    }
    else if (inside(t, 'pane')) $('pane').scrollTop += d * 80;
    else if (inside(t, 'searchScr')) { var sr = document.querySelector('#searchScr .sr'); if (sr) sr.scrollTop += d * 104; }
  });

  // ---------- режим окна (компьютер) ----------
  function deskMode() { return false; }
  function deskProgHead() { return ''; }
  function deskCtlExtra() { return ''; }
  function deskTick() {}
  function deskSearch() {}
  function deskRenderSearch() {}
  function deskQKey() {}
  function deskQInput() {}
  function deskTab() {}
  function deskBack() {}
  function deskFocus() {}
  function deskMove() {}
  function deskClick() {}
  function deskAct() {}
  function deskLayout() {}

  // ---------- масштаб под окно ----------
  function fit() {
    var w = window.innerWidth || 1920, h = window.innerHeight || 1080, b = document.body;
    if (deskMode()) {
      SC.s = 1; SC.x = 0; SC.y = 0;
      b.style.webkitTransform = b.style.transform = '';
      b.style.left = b.style.top = '0';
      deskLayout();
      return;
    }
    var s = Math.min(w / 1920, h / 1080);
    var t = s === 1 ? '' : 'scale(' + s + ')';
    SC.s = s; SC.x = Math.round((w - 1920 * s) / 2); SC.y = Math.round((h - 1080 * s) / 2);
    b.style.webkitTransform = t;
    b.style.transform = t;
    b.style.left = SC.x + 'px';
    b.style.top = SC.y + 'px';
  }
  window.addEventListener('resize', fit);
  fit();

  // Телевизор выключили или свернули приложение — останавливаем поток; вернулись — включаем тот же канал.
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) stopPlayer();
    else if (S.cur) start();
  });

  // Часы в шапках и «сейчас» в списках — раз в минуту
  setInterval(function () {
    if (inGuide() || deskMode()) { progKey = ''; renderGuide(); }
    else if (S.zone === 'grid') renderGrid();
    else if (S.zone === 'mini') renderMini();
  }, MIN);

  // ---------- запуск ----------
  try {
    var keys = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'ChannelUp', 'ChannelDown', 'ChannelList', 'PreviousChannel', 'Info', 'Guide',
      'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue', 'MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaRewind', 'MediaFastForward'];
    for (var k = 0; k < keys.length; k++) try { window.tizen.tvinputdevice.registerKey(keys[k]); } catch (e1) { /* такой кнопки на этом пульте нет */ }
  } catch (e) { /* не Samsung */ }

  migratePlaylists();
  bootPlugins();
  muteTag();
  setTimeout(hideSplash, 8000);
  setInterval(function () { loadEpg(false); }, 3600000);
  preparePlayer(function () {
    if (!activePlaylist()) {
      openOnboarding();
    } else {
      var cached = load('m3u', '');
      if (cached) applyPlaylist(parseM3U(cached), false); // мгновенный старт с сохранённого списка
      refresh(false);
    }
    if (S.auto && Date.now() - load('updCheck', 0) > 3600000) checkUpdate(false);
  });
  // Запуск дошёл до конца — снимаем отметку загрузчика. Останется она, только если эта версия упала
  // при старте: тогда в следующий раз загрузчик вернётся к версии из пакета.
  try { window.localStorage.removeItem('iptv_bundleBoot'); } catch (e) { /* хранилище недоступно */ }
})();

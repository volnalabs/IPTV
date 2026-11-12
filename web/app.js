/*
 * IPTV — общая веб-основа для компьютеров (Electron), телевизоров Samsung (Tizen) и LG (webOS).
 * Каналы по группам, телепрограмма, записи передач, поиск, избранное, плагины, проверка обновлений.
 * Экран «гид» показывает сразу группы, каналы и программу; внизу — подсказки по кнопкам.
 * Цифры открывают ввод номера с подходящими каналами.
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
    PLAY: 415, PAUSE: 19, PLAY_PAUSE: 10252, REW: 412, FF: 417 };
  var TIZEN = !!window.tizen;
  var WEBOS = !!(window.PalmSystem || window.webOS);
  var DESKTOP = /Electron/.test(navigator.userAgent);
  var PLATFORM = TIZEN ? 'samsung' : WEBOS ? 'lg' : DESKTOP ? 'desktop' : 'web';
  var PLATFORM_NAME = { samsung: 'Samsung', lg: 'LG', desktop: 'компьютера', web: 'браузера' }[PLATFORM];
  var DAY = 86400000;
  // Телевизоры 2016–2018 годов: без анимаций и градиентов (SPEC 10.4)
  var UA = navigator.userAgent;
  var LITE = /Tizen [12]\.|Tizen 3\.0/.test(UA) || /Web0S|webOS/i.test(UA) && /Chrom(e|ium)\/(3\d|4[0-9])\./.test(UA);
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
  var KEYS = PC ? { ok: 'Enter', back: 'Esc', hold: 'ПКМ' } : { ok: 'OK', back: 'Назад', hold: 'удерж. OK' };
  // Сколько строк помещается в каждой колонке гида
  var ROWS = { g: 7, c: 6, p: 9 };

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
  function plural(n, one, few, many) {
    var a = n % 10, b = n % 100;
    return n + ' ' + (a === 1 && b !== 11 ? one : a >= 2 && a <= 4 && (b < 12 || b > 14) ? few : many);
  }
  function norm(s) { return trim(String(s).toLowerCase().replace(/ё/g, 'е')); }
  function key2(s) { return String(s).toLowerCase().replace(/[^0-9a-zа-яё]/g, ''); }
  function two(n) { return ('0' + n).slice(-2); }
  function clock(t) { var d = new Date(t); return two(d.getHours()) + ':' + two(d.getMinutes()); }
  function dayStart(t) { var d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); }
  function dayLabel(t, now) {
    var diff = Math.round((dayStart(t) - dayStart(now)) / DAY);
    if (diff === 0) return '';
    if (diff === -1) return 'вчера';
    if (diff === 1) return 'завтра';
    var d = new Date(t);
    return two(d.getDate()) + '.' + two(d.getMonth() + 1);
  }
  function duration(sec) {
    sec = Math.max(0, Math.floor(sec));
    var h = Math.floor(sec / 3600), m = Math.floor(sec % 3600 / 60), s = sec % 60;
    return h ? h + ':' + two(m) + ':' + two(s) : m + ':' + two(s);
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

  // ---------- состояние ----------
  var S = {
    url: load('url', ''),
    epgUrl: load('epgurl', ''),
    favs: load('favs', {}),
    auto: load('auto', true),
    plugins: load('plugins', []),
    channels: [], epgUrls: [],
    menu: [], menuId: 'all', list: [],
    gi: 0, ci: 0, pi: 0,
    cur: null, arch: null, playList: [],
    epg: null, epgCh: null, prog: [],
    // zone (ниже): play | channels | groups | search | epg | setup | plugins | dialog
    si: 0, pli: 0, di: 0, prev: null,
    update: null, pluginsDirty: false
  };
  window.__iptvState = S; // для отладки и автотестов
  // Текущий экран записываем и в <body data-z>: по нему CSS показывает кнопки поверх эфира
  var zoneName = 'play';
  Object.defineProperty(S, 'zone', {
    enumerable: true,
    get: function () { return zoneName; },
    set: function (z) { zoneName = z; document.body.setAttribute('data-z', z); }
  });
  S.zone = 'play';

  var PL = window.__iptvPlugins;

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

  function fetchText(url, ok, fail) {
    var x = new XMLHttpRequest(), done = false;
    function finish(fn, arg) { if (!done) { done = true; fn(arg); } }
    try { x.open('GET', url, true); } catch (e) { fail('неверная ссылка'); return; }
    x.timeout = 30000;
    x.onload = function () { if (x.status >= 200 && x.status < 300) finish(ok, x.responseText); else finish(fail, 'сервер ответил ' + x.status); };
    x.onerror = function () { finish(fail, 'нет связи с сервером'); };
    x.ontimeout = function () { finish(fail, 'сервер не отвечает'); };
    try { x.send(); } catch (e2) { finish(fail, 'неверная ссылка'); }
  }

  function applyPlaylist(parsed) {
    var channels = parsed.channels;
    if (PL.hasPlaylist()) channels = PL.playlist(channels);
    channels.forEach(function (c, i) { c.number = i + 1; c.group = c.group || 'Без группы'; });
    S.channels = channels;
    S.epgUrls = parsed.epgUrls;
    buildMenu();
    if (!channels.length) return;
    var keep = S.cur ? findByUrl(S.cur.url) : null;
    if (keep) { S.cur = keep; S.playList = listOf(load('group', 'all')) || channels; return; }
    var ch = findByUrl(load('last', '')), group = load('group', 'all'), list = listOf(group);
    if (!list || !ch || list.indexOf(ch) < 0) list = channels;
    S.playList = list;
    play(ch || channels[0], null);
  }

  function findByUrl(url) {
    for (var i = 0; i < S.channels.length; i++) if (S.channels[i].url === url) return S.channels[i];
    return null;
  }

  function refresh(manual) {
    if (!S.url) return;
    setStatus('Загружаю каналы…', false);
    $('splashCap').textContent = S.channels.length ? '' : 'Загружаю каналы…';
    fetchText(S.url, function (text) {
      var parsed = parseM3U(text);
      if (!parsed.channels.length) { loadFailed('в плейлисте нет каналов'); return; }
      save('m3u', text);
      $('splashCap').textContent = '';
      setStatus('Каналов: ' + parsed.channels.length, false);
      applyPlaylist(parsed);
      loadEpg(manual);
      if (S.zone === 'setup' && manual) closeSetup();
    }, loadFailed);
  }

  function loadFailed(reason) {
    var text = 'Не удалось загрузить плейлист: ' + reason + '.';
    setStatus(text + (S.channels.length ? ' Показываю сохранённый список.' : ' Проверьте ссылку и интернет.'), true);
    hideSplash();
    if (!S.channels.length && S.zone !== 'setup') openSetup();
    else if (S.zone !== 'setup') toast(text);
  }

  // ---------- телепрограмма ----------
  var epgBusy = false;

  function setEpgStatus(text, isError) { $('epgStatus').textContent = text; cls($('epgStatus'), 'err', !!isError); }

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
    var now = Date.now(), from = now - (Math.min(maxDays, 14) + 1) * DAY, to = now + 2 * DAY;
    var byId = {}, nameToId = {}, keepId = {}, count = 0, buf = '';
    var reId = /channel="([^"]*)"/, reStart = /start="([^"]*)"/, reStop = /stop="([^"]*)"/, reTitle = /<title[^>]*>([^<]*)<\/title>/;

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
        var rec = byId[id] || (byId[id] = { s: [], e: [], t: [] });
        // Название копируем: иначе короткая строка держит в памяти весь прочитанный кусок файла
        rec.s.push(s); rec.e.push(e); rec.t.push(t ? detach(unxml(trim(t[1]))) : '');
        count++;
      }
    }
    function finish() {
      eat(true);
      var ids = 0;
      for (var id in byId) if (byId.hasOwnProperty(id)) {
        ids++;
        var r = byId[id], sorted = true;
        for (var i = 1; i < r.s.length; i++) if (r.s[i] < r.s[i - 1]) { sorted = false; break; }
        if (!sorted) {
          var order = r.s.map(function (v, k) { return k; }).sort(function (a, b) { return r.s[a] - r.s[b]; });
          byId[id] = { s: order.map(function (k) { return r.s[k]; }), e: order.map(function (k) { return r.e[k]; }), t: order.map(function (k) { return r.t[k]; }) };
        }
      }
      epgBusy = false;
      if (!ids) { setEpgStatus('В телепрограмме нет передач для этих каналов', true); return; }
      S.epg = { byId: byId, nameToId: nameToId, updated: Date.now() };
      var d = new Date();
      setEpgStatus('Программа: ' + two(d.getDate()) + '.' + two(d.getMonth() + 1) + '.' + String(d.getFullYear()).slice(-2) + ', ' + clock(d.getTime()), false);
      if (has($('panel'), 'on')) renderPanel();
      if (has($('info'), 'on')) updateInfo();
    }
    function failed(err) {
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

  // ---------- меню и списки ----------
  function listOf(id) {
    if (id === 'all') return S.channels;
    if (id === 'fav') return S.channels.filter(function (c) { return S.favs[c.url]; });
    for (var i = 0; i < S.menu.length; i++) if (S.menu[i].id === id) return S.menu[i].list || null;
    return null;
  }

  /** Левая колонка: поиск, избранное, все каналы, группы. Настройки и плагины закреплены внизу. */
  var FOOT = 2;
  function buildMenu() {
    var menu = [{ id: 'search', title: 'Поиск', icon: 'search', action: true }];
    var favs = S.channels.filter(function (c) { return S.favs[c.url]; });
    if (favs.length) menu.push({ id: 'fav', title: 'Избранное', icon: 'star', list: favs });
    menu.push({ id: 'all', title: 'Все каналы', icon: 'tv', list: S.channels });
    var seen = {}, order = [];
    S.channels.forEach(function (c) {
      if (!seen[c.group]) { seen[c.group] = { id: 'g:' + c.group, title: c.group, list: [] }; order.push(seen[c.group]); }
      seen[c.group].list.push(c);
    });
    S.menu = menu.concat(order, [
      { id: 'settings', title: 'Настройки', icon: 'settings', action: true },
      { id: 'plugins', title: 'Плагины', icon: 'plug', action: true }
    ]);
    if (S.menuId !== 'search' && !listOf(S.menuId)) S.menuId = 'all';
  }

  function menuIndex(id) {
    for (var i = 0; i < S.menu.length; i++) if (S.menu[i].id === id) return i;
    return 0;
  }

  /**
   * Показываем только строки вокруг выбранной — список из тысячи каналов телевизор не потянет.
   * Пока выбор двигают пультом, он держится посередине; колесо мыши прокручивает список, не трогая выбор.
   */
  var tops = { g: 0, c: 0, p: 0 }, seenSel = {};
  function winTop(name, sel, total, visible, stamp, lead) {
    var key = sel + '|' + stamp;
    if (seenSel[name] !== key) { seenSel[name] = key; tops[name] = sel - (lead === undefined ? Math.floor(visible / 2) : lead); }
    tops[name] = Math.max(0, Math.min(tops[name], total - visible));
    return tops[name];
  }
  /** Полоска справа: видно, что список длиннее экрана и где мы в нём находимся. */
  function scrollbar(start, total, visible) {
    if (total <= visible) return '';
    return '<div class="sb"><i style="top:' + (start * 100 / total).toFixed(1) + '%;height:' + Math.max(6, visible * 100 / total).toFixed(1) + '%"></i></div>';
  }
  function svg(name, fill) {
    return '<svg class="ic' + (fill ? ' fill' : '') + '" viewBox="0 0 24 24"><path d="' + ICON[name] + '"/></svg>';
  }
  /** Буквы вместо логотипа: первые буквы слов названия («Россия К» → «РК», «5 канал» → «5К»), не больше трёх. */
  function initials(name) {
    var words = String(name).split(/[^0-9A-Za-zА-Яа-яЁё]+/), out = '';
    for (var i = 0; i < words.length && out.length < 3; i++) if (words[i]) out += /^\d+$/.test(words[i]) ? words[i].substr(0, 3 - out.length) : words[i].charAt(0);
    if (out.length === 1 && words[0] && words[0].length > 1 && !/^\d/.test(words[0])) out = words[0].substr(0, 2);
    return (out || '•').toUpperCase();
  }
  function logoHtml(ch) {
    return '<div class="logo"><b>' + esc(initials(ch.name)) + '</b>' +
      (ch.logo ? '<img src="' + esc(ch.logo) + '" onerror="this.style.display=\'none\'">' : '') + '</div>';
  }
  function setLogo(id, ch) {
    var el = $(id), url = ch ? ch.url : '';
    if (el.getAttribute('data-url') === url) return; // не перезагружаем картинку без нужды
    el.innerHTML = ch ? logoHtml(ch) : '';
    el.setAttribute('data-url', url);
  }
  function progress(from, to, now) {
    return '<div class="bar"><i style="width:' + Math.round((now - from) * 100 / Math.max(1, to - from)) + '%"></i></div>';
  }
  /** Подсказка «кнопка — что она делает». С действием её можно нажать мышью или указкой. */
  function hintHtml(key, label, action) {
    return '<span class="h' + (action === 'back' ? ' go' : '') + '"' + (action ? ' data-a="' + action + '"' : '') + '><b>' + key + '</b>' + label + '</span>';
  }

  function groupRow(i) {
    var m = S.menu[i];
    return '<div data-i="' + i + '" class="row g' + (i === S.gi && S.zone === 'groups' ? ' sel' : '') + (m.id === S.menuId ? ' act' : '') + '">' +
      (m.icon ? svg(m.icon) : '<span class="dot"></span>') +
      '<span class="t">' + esc(m.title) + '</span>' +
      (m.action ? '' : '<span class="n">' + m.list.length + '</span>') + '</div>';
  }

  function renderGroups() {
    var n = S.menu.length - FOOT, visible = ROWS.g, html = '', foot = '', i;
    var start = winTop('g', Math.min(S.gi, n - 1), n, visible, n);
    for (i = start; i < Math.min(n, start + visible); i++) html += groupRow(i);
    for (i = n; i < S.menu.length; i++) foot += groupRow(i);
    $('glist').innerHTML = html + scrollbar(start, n, visible);
    $('gfoot').innerHTML = foot;
  }

  function renderChannels() {
    var visible = ROWS.c, n = S.list.length, html = '', now = Date.now();
    var start = winTop('c', S.ci, n, visible, S.menuId + ':' + n);
    for (var i = start; i < Math.min(n, start + visible); i++) {
      var c = S.list[i], e = epgFor(c), p = e ? progAt(e, now) : -1, cur = S.cur && c.url === S.cur.url, fav = !!S.favs[c.url];
      html += '<div data-i="' + i + '" class="row c' + (i === S.ci && S.zone === 'channels' ? ' sel' : '') + (cur ? ' act' : '') + '">' +
        '<span class="no">' + c.number + '</span><span class="lg">' + logoHtml(c) + '</span>' +
        '<span class="tx"><div class="nm">' + esc(c.name) + '</div><div class="gr">' + esc(p >= 0 ? e.t[p] : c.group) + '</div>' +
        (p >= 0 ? progress(e.s[p], e.e[p], now) : '') + '</span>' +
        (cur ? '<span class="eq"><i></i><i></i><i></i></span>' : '') +
        (e ? '<span class="bt pg" title="Телепрограмма">' + svg('list') + '</span>' : '') +
        '<span class="bt fv' + (fav ? ' on' : '') + '" title="' + (fav ? 'Убрать из избранного' : 'В избранное') + '">' + svg('star', fav) + '</span></div>';
    }
    if (!n) html = '<div class="empty">' + svg('search') + (S.menuId === 'search' ? 'Ничего не нашлось.<br>Попробуйте короче или введите номер канала.' : 'Здесь пока пусто') + '</div>';
    $('clist').innerHTML = html + scrollbar(start, n, visible);
  }

  function renderEpg() {
    var ch = S.epgCh, n = S.prog.length, visible = ROWS.p, html = '', now = Date.now(), inEpg = S.zone === 'epg';
    $('etitle').textContent = ch ? ch.name : 'Телепрограмма';
    setLogo('elogo', ch);
    $('ehint').textContent = ch && ch.catchupDays && n ? 'Записи передач за ' + plural(ch.catchupDays, 'день', 'дня', 'дней') : 'Телепрограмма';
    cls($('ehint'), 'arch', !!(ch && ch.catchupDays && n));
    if (!n) {
      $('elist').innerHTML = !ch ? '' : '<div class="empty">' + svg('guide') + (epgBusy ? 'Телепрограмма загружается…'
        : S.epg || S.epgUrl || S.epgUrls[0] ? 'Для этого канала телепрограммы нет'
        : 'В плейлисте нет ссылки на телепрограмму.<br>Её можно указать в настройках.') + '</div>';
      return;
    }
    // Пока смотрим со стороны — показываем «сейчас и дальше»; когда вошли в программу — выбор посередине
    var start = winTop('p', S.pi, n, visible, ch.url + ':' + inEpg, inEpg ? undefined : 1);
    for (var i = start; i < Math.min(n, start + visible); i++) {
      var p = S.prog[i], day = dayLabel(p.s, now), isNow = p.s <= now && p.e > now, past = p.e <= now;
      var playing = S.arch && S.cur && S.cur.url === ch.url && S.arch.s === p.s;
      html += '<div data-i="' + i + '" class="row p' + (i === S.pi && inEpg ? ' sel' : '') + (isNow || playing ? ' act' : '') + (past && !p.arch ? ' dim' : '') + '">' +
        '<span class="tm">' + (day ? '<small>' + day + '</small>' : '') + clock(p.s) + '</span>' +
        '<span class="tt"><div class="t1">' + esc(p.t) + '</div>' + (isNow ? progress(p.s, p.e, now) : '') + '</span>' +
        (playing ? svg('play', true) : isNow ? '<span class="bd">ЭФИР</span>' : past && p.arch ? svg('history') : '') + '</div>';
    }
    $('elist').innerHTML = html + scrollbar(start, n, visible);
  }

  function renderHints() {
    var h = '', ch = S.list[S.ci], now = Date.now(), searching = S.menuId === 'search';
    if (S.zone === 'channels') {
      h = (ch ? hintHtml(KEYS.ok, 'Смотреть', 'ok') : '') + hintHtml('◀', 'Группы и поиск', 'left') +
        (ch && epgFor(ch) ? hintHtml('▶', 'Программа', 'right') : '') +
        (ch ? hintHtml(KEYS.hold, S.favs[ch.url] ? 'Из избранного' : 'В избранное', 'fav') : '') +
        hintHtml('0–9', 'Номер канала') + (PC && !searching ? hintHtml('А–Я', 'Поиск', 'search') : '') +
        hintHtml(KEYS.back, searching ? 'Выйти из поиска' : 'Закрыть', 'back');
    } else if (S.zone === 'groups') {
      var m = S.menu[S.gi];
      h = hintHtml('▲▼', 'Выбрать раздел') + hintHtml(KEYS.ok, m && m.action ? 'Открыть «' + m.title + '»' : 'К каналам группы', 'ok') +
        hintHtml(KEYS.back, 'Закрыть', 'back');
    } else if (S.zone === 'epg') {
      var p = S.prog[S.pi];
      h = (p && p.s <= now && p.e > now ? hintHtml(KEYS.ok, 'Смотреть эфир', 'ok') : p && p.e <= now && p.arch ? hintHtml(KEYS.ok, 'Смотреть запись', 'ok') : '') +
        hintHtml('▲▼', 'Передачи') + hintHtml('◀', 'К каналам', 'left') + hintHtml(KEYS.back, 'Назад', 'back');
    } else if (S.zone === 'search') {
      h = hintHtml('▼', 'К найденным каналам', 'down') + hintHtml(KEYS.back, 'Выйти из поиска', 'back');
    }
    $('hints').innerHTML = h;
  }

  var lastZone = 'play', progKey = '';

  function renderPanel() {
    var searching = S.menuId === 'search', p = $('panel'), n = S.list.length;
    cls(p, 'zg', S.zone === 'groups');
    cls(p, 'zc', S.zone === 'channels' || S.zone === 'search');
    cls(p, 'ze', S.zone === 'epg');
    cls($('chans'), 's', searching);
    cls($('search'), 'focus', S.zone === 'search');
    $('ctitle').textContent = searching ? '' : S.menu[menuIndex(S.menuId)].title;
    $('ccount').textContent = !searching ? plural(n, 'канал', 'канала', 'каналов') : !norm($('q').value) ? 'все каналы' : n ? 'нашлось ' + n : 'нет';
    $('pclock').textContent = clock(Date.now());
    if (lastZone === 'epg' && S.zone !== 'epg') progKey = '';
    lastZone = S.zone;
    syncEpg();
    renderGroups();
    renderChannels();
    renderEpg();
    renderHints();
  }

  function selectMenu(id) {
    S.menuId = id;
    S.gi = menuIndex(id);
    S.list = listOf(id) || [];
    var at = S.cur ? S.list.indexOf(S.cur) : -1;
    S.ci = at < 0 ? 0 : at;
  }

  function openPanel() {
    if (!S.channels.length) { openSetup(); return; }
    cls($('info'), 'on', false);
    var g = load('group', 'all'), gl = listOf(g);
    selectMenu(gl && gl.indexOf(S.cur) >= 0 ? g : 'all');
    S.zone = 'channels';
    progKey = '';
    cls($('panel'), 'on', true);
    renderPanel();
  }

  function closePanel() {
    $('q').blur();
    if (S.menuId === 'search') S.menuId = 'all';
    S.zone = 'play';
    cls($('panel'), 'on', false);
  }

  /** Передачи канала: от начала архива (или от полуночи) до завтра. Возвращает false, если программы нет. */
  function loadProg(ch) {
    var e = ch ? epgFor(ch) : null, now = Date.now();
    S.epgCh = ch || null;
    S.prog = [];
    S.pi = 0;
    if (!e) return false;
    var from = ch.catchupDays ? now - ch.catchupDays * DAY : dayStart(now), to = now + DAY;
    for (var i = 0; i < e.s.length; i++) {
      if (e.e[i] <= from || e.s[i] >= to) continue;
      if (e.s[i] <= now && e.e[i] > now) S.pi = S.prog.length;
      if (S.arch && S.cur === ch && S.arch.s === e.s[i]) S.pi = S.prog.length;
      S.prog.push({ s: e.s[i], e: e.e[i], t: e.t[i] || 'Без названия', arch: !!ch.catchupDays && e.s[i] >= from });
    }
    return S.prog.length > 0;
  }

  /** Правая колонка следует за выбранным каналом — программу видно, ещё не заходя в неё. */
  function syncEpg() {
    if (S.zone === 'epg') return;
    var ch = S.list[S.ci] || S.cur;
    var key = !ch ? '' : ch.url + '|' + (S.epg ? S.epg.updated : 0) + '|' + (S.arch && S.cur === ch ? S.arch.s : 0) + '|' + Math.floor(Date.now() / 60000);
    if (key === progKey) return;
    progKey = key;
    loadProg(ch);
  }

  function openEpg(ch) {
    progKey = '';
    if (!loadProg(ch)) { toast(epgBusy ? 'Телепрограмма ещё загружается…' : 'Для этого канала телепрограммы нет'); return false; }
    S.zone = 'epg';
    renderPanel();
    return true;
  }

  function chooseProgramme() {
    var p = S.prog[S.pi], ch = S.epgCh, now = Date.now();
    if (!p || !ch) return;
    if (p.s <= now && p.e > now) { closePanel(); play(ch, null); }
    else if (p.e <= now && p.arch) { closePanel(); play(ch, p); }
    else if (p.e <= now) toast('Эта передача уже прошла, записи нет');
    else toast('Передача начнётся в ' + clock(p.s));
  }

  function startSearch(text) {
    S.menuId = 'search';
    S.gi = menuIndex('search');
    $('q').value = text || '';
    applySearch();
    S.zone = 'search';
    renderPanel();
    $('q').focus();
  }

  /** Ищем по названию, а если введены цифры — ещё и по номеру канала (такие совпадения идут первыми). */
  function applySearch() {
    var q = norm($('q').value), num = /^\d+$/.test(q), first = [], rest = [];
    if (!q) S.list = S.channels;
    else {
      S.channels.forEach(function (c) {
        if (num && String(c.number).indexOf(q) === 0) first.push(c);
        else if (norm(c.name).indexOf(q) >= 0) rest.push(c);
      });
      S.list = first.concat(rest);
    }
    S.ci = 0;
  }

  function leaveSearch() {
    $('q').blur();
    var g = load('group', 'all'), gl = listOf(g);
    selectMenu(gl && gl.indexOf(S.cur) >= 0 ? g : 'all');
    S.zone = 'channels';
    renderPanel();
  }

  function toggleFav(ch) {
    if (S.favs[ch.url]) delete S.favs[ch.url]; else S.favs[ch.url] = 1;
    save('favs', S.favs);
    toast('«' + ch.name + '» ' + (S.favs[ch.url] ? 'добавлен в избранное' : 'убран из избранного'));
    var id = S.menuId;
    buildMenu();
    if (id === 'fav') {
      if (listOf('fav').length) { S.list = listOf('fav'); S.ci = Math.min(S.ci, S.list.length - 1); S.menuId = 'fav'; }
      else selectMenu('all');
    } else if (id !== 'search') {
      S.menuId = id; S.list = listOf(id) || S.list;
    }
    S.gi = menuIndex(S.menuId);
    renderPanel();
  }

  // ---------- плеер ----------
  // Samsung — собственный плеер AVPlay; LG и Safari умеют HLS сами; в остальных браузерах помогает hls.js.
  var video = $('video');
  var av = null, hls = null, paused = false;
  var retries = 0, retryTimer = null;
  var nativeHls = !!video.canPlayType && video.canPlayType('application/vnd.apple.mpegurl') !== '';

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

  function loading(on) { cls($('loading'), 'on', on); }

  function stopPlayer() {
    clearTimeout(retryTimer);
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
    message('');
    loading(true);
    if (av) {
      try {
        av.open(url);
        av.setDisplayRect(0, 0, 1920, 1080);
        av.setListener({
          onbufferingstart: function () { loading(true); },
          onbufferingcomplete: function () { loading(false); },
          onstreamcompleted: function () { ended(ch); },
          onerror: function () { playFailed(ch); }
        });
        av.prepareAsync(function () { try { av.play(); } catch (e) {} playing(); }, function () { playFailed(ch); });
      } catch (e) { playFailed(ch); }
    } else {
      var isHls = /\.m3u8?($|\?)|\/hls\//i.test(url);
      if (!nativeHls && isHls && window.Hls && window.Hls.isSupported()) {
        hls = new window.Hls({ enableWorker: true });
        hls.on(window.Hls.Events.ERROR, function (ev, data) { if (data && data.fatal) playFailed(ch); });
        hls.loadSource(url);
        hls.attachMedia(video);
      } else {
        video.src = url;
      }
      var p = video.play();
      if (p && p['catch']) p['catch'](function () {});
    }
  }

  function playing() { retries = 0; loading(false); message(''); hideSplash(); }

  function ended(ch) {
    if (S.arch && S.cur === ch) { toast('Передача закончилась — возвращаю прямой эфир'); play(ch, null); }
    else playFailed(ch);
  }

  function playFailed(ch) {
    if (!S.cur || S.cur.url !== ch.url) return;
    loading(false);
    if (retries < 2) {
      retries++;
      clearTimeout(retryTimer);
      retryTimer = setTimeout(function () { if (S.cur && S.cur.url === ch.url) start(); }, 2000 * retries);
    } else {
      hideSplash();
      message('Не удалось включить «' + ch.name + '».\nНажмите ' + KEYS.ok + ', чтобы попробовать снова, или ▲▼ — другой канал.');
    }
  }

  video.addEventListener('playing', playing);
  video.addEventListener('waiting', function () { loading(true); });
  video.addEventListener('ended', function () { if (S.cur) ended(S.cur); });
  video.addEventListener('error', function () { if (S.cur && !hls && video.getAttribute('src')) playFailed(S.cur); });

  function play(ch, programme) {
    if (S.cur && S.cur.url !== ch.url) S.prev = S.cur; // для кнопки «предыдущий канал»
    S.cur = ch;
    S.arch = programme || null;
    retries = 0;
    if (!programme) save('last', ch.url);
    start();
    showInfo();
  }

  function position() {
    try { return av ? av.getCurrentTime() / 1000 : video.currentTime || 0; } catch (e) { return 0; }
  }
  function seek(sec) {
    if (!S.arch) return;
    try {
      if (av) { if (sec > 0) av.jumpForward(sec * 1000); else av.jumpBackward(-sec * 1000); }
      else video.currentTime = Math.max(0, position() + sec);
    } catch (e) { /* поток не перематывается */ }
    showInfo();
  }
  function togglePause() {
    paused = !paused;
    try {
      if (av) { if (paused) av.pause(); else av.play(); }
      else if (paused) video.pause(); else video.play();
    } catch (e) { /* ничего */ }
    showInfo();
  }

  function step(dir) {
    var list = S.playList.length ? S.playList : S.channels;
    if (!list.length) return;
    var i = list.indexOf(S.cur);
    play(list[i < 0 ? 0 : (i + dir + list.length) % list.length], null);
  }

  // ---------- плашка, сообщения ----------
  var infoTimer = null, toastTimer = null, infoHintsKey = '';

  function renderInfoHints() {
    var key = (S.arch ? 'a' : 'l') + (paused ? 'p' : '');
    if (key === infoHintsKey) return;
    infoHintsKey = key;
    $('infoHints').innerHTML = S.arch
      ? hintHtml('◀', '−30 с', 'left') + hintHtml('▶', '+30 с', 'right') + hintHtml(KEYS.ok, paused ? 'Продолжить' : 'Пауза', 'ok') + hintHtml(KEYS.back, 'В прямой эфир', 'back')
      : hintHtml(KEYS.ok, 'Каналы', 'ok') + hintHtml('▲▼', 'Переключить') + hintHtml('▶', 'Программа', 'right') + hintHtml('0–9', 'Номер канала') +
        (PC ? hintHtml('А–Я', 'Поиск', 'search') : '');
  }

  function updateInfo() {
    var ch = S.cur;
    if (!ch) return;
    var now = Date.now();
    $('infoNum').textContent = ch.number;
    $('infoName').textContent = ch.name;
    $('infoClock').textContent = clock(now);
    setLogo('infoLogo', ch);
    renderInfoHints();
    if (S.arch) {
      var dur = (S.arch.e - S.arch.s) / 1000, pos = position(), day = dayLabel(S.arch.s, now);
      cls($('infoBadge'), 'on', true);
      $('infoBadge').textContent = paused ? 'ПАУЗА' : 'ЗАПИСЬ';
      $('infoTitle').textContent = S.arch.t;
      cls($('infoLine'), 'on', true);
      $('infoStart').textContent = duration(pos);
      $('infoEnd').textContent = duration(dur);
      $('infoFill').style.width = Math.min(100, pos * 100 / Math.max(1, dur)) + '%';
      $('infoNext').textContent = 'Запись: ' + (day ? day + ', ' : 'сегодня, ') + clock(S.arch.s) + ' – ' + clock(S.arch.e);
      return;
    }
    cls($('infoBadge'), 'on', false);
    var e = epgFor(ch), p = e ? progAt(e, now) : -1;
    if (p >= 0) {
      $('infoTitle').textContent = e.t[p];
      cls($('infoLine'), 'on', true);
      $('infoStart').textContent = clock(e.s[p]);
      $('infoEnd').textContent = clock(e.e[p]);
      $('infoFill').style.width = Math.round((now - e.s[p]) * 100 / Math.max(1, e.e[p] - e.s[p])) + '%';
      $('infoNext').textContent = p + 1 < e.s.length ? 'Далее в ' + clock(e.s[p + 1]) + '  ·  ' + e.t[p + 1] : '';
    } else {
      $('infoTitle').textContent = ch.group;
      cls($('infoLine'), 'on', false);
      $('infoNext').textContent = '';
    }
  }

  function showInfo() {
    if (!S.cur || S.zone !== 'play') return;
    updateInfo();
    cls($('info'), 'on', true);
    clearTimeout(infoTimer);
    if (!(S.arch && paused)) infoTimer = setTimeout(function () { cls($('info'), 'on', false); }, 5000);
  }
  setInterval(function () { if (has($('info'), 'on')) updateInfo(); }, 1000);

  function toast(text) {
    $('toast').textContent = text;
    cls($('toast'), 'on', true);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { cls($('toast'), 'on', false); }, 3500);
  }

  function message(text) { $('msg').textContent = text; cls($('msg'), 'on', !!text); }

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
    if (S.update && S.zone === 'play') openDialog();
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
    N.items = numMatches(N.digits, 5);
    N.sel = 0;
    N.on = true;
    cls($('info'), 'on', false); // плашка канала не должна выглядывать из-под подсказок
    renderNum();
    // Продолжения у номера нет — переключаем почти сразу; есть из чего выбрать — даём время прочитать
    numArm(!N.items.length ? 2000 : n * 10 > S.channels.length ? 1000 : 3000);
  }

  function renderNum() {
    var html = '', now = Date.now(), n = parseInt(N.digits, 10), len = String(n).length;
    $('numVal').innerHTML = esc(N.digits) + '<i></i>';
    for (var i = 0; i < N.items.length; i++) {
      var c = N.items[i], e = epgFor(c), p = e ? progAt(e, now) : -1, num = String(c.number);
      html += '<div data-i="' + i + '" class="row nr' + (i === N.sel ? ' sel' : '') + '">' +
        '<span class="no"><em>' + num.substr(0, len) + '</em>' + num.substr(len) + '</span><span class="lg">' + logoHtml(c) + '</span>' +
        '<span class="tx"><div class="nm">' + esc(c.name) + '</div><div class="gr">' + esc(p >= 0 ? e.t[p] : c.group) + '</div></span></div>';
    }
    $('numList').innerHTML = html;
    $('numNone').textContent = 'Канала с номером ' + n + ' нет. Всего каналов: ' + S.channels.length + '.';
    $('numHint').innerHTML = (N.items.length > 1 ? hintHtml('▲▼', 'Выбрать') : '') + hintHtml(KEYS.ok, 'Включить', 'ok') + hintHtml(KEYS.back, 'Отмена', 'back');
    cls($('numpad'), 'none', !N.items.length);
    cls($('numpad'), 'on', true);
  }

  /** Отсчёт до переключения: полоска под списком убывает, по окончании включается выбранный канал. */
  function numArm(ms) {
    var b = $('numBar');
    clearTimeout(N.timer);
    N.timer = setTimeout(numCommit, ms);
    b.style.webkitTransition = b.style.transition = 'none';
    b.style.width = '100%';
    void b.offsetWidth;
    b.style.webkitTransition = b.style.transition = 'width ' + ms + 'ms linear';
    b.style.width = '0';
  }

  function numClose() {
    clearTimeout(N.timer);
    N.on = false;
    N.digits = '';
    cls($('numpad'), 'on', false);
  }

  function numCommit() {
    var ch = N.items[N.sel];
    numClose();
    if (!ch) return;
    if (S.zone !== 'play') closePanel();
    S.playList = S.channels;
    save('group', 'all');
    if (S.cur && S.cur.url === ch.url && !S.arch && !has($('msg'), 'on')) showInfo(); else play(ch, null);
  }

  function numKey(code, e) {
    var d = digitOf(code, e);
    if (d >= 0) typeDigit(d);
    else if (code === KEY.UP || code === KEY.DOWN) {
      if (N.items.length > 1) { N.sel = (N.sel + (code === KEY.DOWN ? 1 : N.items.length - 1)) % N.items.length; renderNum(); numArm(3000); }
    } else if (code === KEY.ENTER) { swallowEnter = true; numCommit(); }
    else if (code === KEY.LEFT || code === KEY.BKSP || code === KEY.DEL) {
      N.digits = N.digits.slice(0, -1);
      if (N.digits) numUpdate(); else numClose();
    } else if (code !== KEY.RIGHT) { numClose(); return false; }
    return true;
  }

  // ---------- обновления ----------
  // Приложение обновляется само: скачивает из релиза файл iptv-web.json (разметка, стили и код одним
  // набором), кладёт его в хранилище и перезапускается — загрузчик boot.js запустит уже новую версию.
  // Каналы, избранное и настройки лежат в том же хранилище и никуда не деваются.
  var BOOT = window.__iptvBoot || null;        // чем нас запустили; нет — открыта страница без загрузчика
  var SHELL = window.__iptvShell || 0;
  var U = { buttons: [], xhr: null, busy: false };

  function setUpd(text) { $('updStatus').textContent = text; }

  function checkUpdate(manual) {
    setUpd('Проверяю…');
    fetchText('https://api.github.com/repos/' + REPO + '/releases/latest', function (text) {
      var version = '';
      try { version = String(JSON.parse(text).tag_name || '').replace(/^v/, ''); } catch (e) { /* не JSON */ }
      save('updCheck', Date.now());
      if (version && newer(version, VERSION)) {
        S.update = version;
        setUpd('Вышла версия ' + version);
        if (manual || splashGone) openDialog();
      } else setUpd('Версия ' + VERSION + ' · новее нет');
    }, function () { setUpd('Не получилось проверить'); });
  }

  var dialogBack = 'play';

  /** Показывает окно обновления: заголовок, пояснение и кнопки вида { t: надпись, a: действие }. */
  function setDialog(title, text, buttons, progress) {
    var html = '';
    U.buttons = buttons;
    $('dTitle').textContent = title;
    $('dText').textContent = text;
    for (var i = 0; i < buttons.length; i++) html += '<div class="btn' + (i === 0 && buttons.length > 1 ? ' primary' : '') + '" data-d="' + i + '">' + esc(buttons[i].t) + '</div>';
    $('dButtons').innerHTML = html;
    cls($('dBar'), 'on', !!progress);
    $('dFill').style.width = '0';
    S.di = 0;
    renderDialog();
  }

  function openDialog() {
    if (!S.update || S.zone === 'dialog') return;
    if (S.zone !== 'play' && S.zone !== 'setup') return; // не перебиваем выбор канала
    dialogBack = S.zone;
    S.zone = 'dialog';
    cls($('dialog'), 'on', true);
    if (SHELL) {
      setDialog('Вышла версия ' + S.update, 'Сейчас установлена версия ' + VERSION + '. Обновление скачается и установится само, это займёт несколько секунд. ' +
        'Каналы, избранное и настройки сохранятся.', [{ t: 'Обновить', a: 'update' }, { t: 'Позже', a: 'later' }]);
    } else manualDialog();
  }

  /** Обновление, которое приложение не может поставить само: меняется сам пакет, а не только его содержимое. */
  function manualDialog() {
    if (PC) {
      setDialog('Вышла версия ' + S.update, 'Это обновление меняет саму программу, поэтому его нужно скачать: откройте страницу загрузки и запустите новый файл вместо старого. ' +
        'Каналы и настройки сохранятся.', [{ t: 'Открыть страницу загрузки', a: 'page' }, { t: 'Позже', a: 'later' }]);
    } else {
      setDialog('Вышла версия ' + S.update, 'Это обновление меняет сам пакет приложения, поэтому на телевизоре ' + PLATFORM_NAME + ' его нужно поставить с компьютера — так же, как в первый раз. ' +
        'Каналы и настройки сохранятся.', [{ t: 'Понятно', a: 'later' }]);
    }
  }

  function renderDialog() {
    var b = $('dButtons').children;
    for (var i = 0; i < b.length; i++) cls(b[i], 'sel', i === S.di);
  }

  function hideDialog() {
    if (U.xhr) { try { U.xhr.abort(); } catch (e) { /* уже завершён */ } U.xhr = null; }
    cls($('dialog'), 'on', false);
    S.zone = dialogBack;
    S.update = null; // до следующего запуска не напоминаем
    if (S.zone === 'play') showInfo();
  }

  /** Нажата кнопка окна с номером index; -1 — «Назад». */
  function closeDialog(index) {
    var b = index < 0 ? null : U.buttons[index], action = b ? b.a : 'later';
    if (U.busy) return; // обновление уже записывается — ждём перезапуска
    if (action === 'update') startUpdate();
    else if (action === 'page') { window.open('https://github.com/' + REPO + '/releases/latest', '_blank'); hideDialog(); }
    else hideDialog();
  }

  function updateFailed(reason) {
    U.xhr = null;
    var buttons = [{ t: 'Повторить', a: 'update' }];
    if (PC) buttons.push({ t: 'Открыть страницу загрузки', a: 'page' });
    buttons.push({ t: 'Закрыть', a: 'later' });
    setDialog('Не удалось обновиться', 'Причина: ' + reason + '. Приложение работает как раньше, обновиться можно позже из настроек.', buttons);
  }

  function startUpdate() {
    var version = S.update, x = new XMLHttpRequest();
    if (!version) { hideDialog(); return; }
    setDialog('Скачиваю версию ' + version + '…', 'Это займёт несколько секунд. Каналы, избранное и настройки сохранятся.', [{ t: 'Отмена', a: 'later' }], true);
    U.xhr = x;
    try { x.open('GET', 'https://github.com/' + REPO + '/releases/download/v' + version + '/iptv-web.json', true); } catch (e) { updateFailed('неверный адрес обновления'); return; }
    x.timeout = 60000;
    x.onprogress = function (e) { if (e.lengthComputable && e.total) $('dFill').style.width = Math.round(e.loaded * 100 / e.total) + '%'; };
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
    U.busy = true;
    setDialog('Версия ' + b.version + ' установлена', 'Перезапускаю приложение…', []);
    $('dFill').style.width = '100%';
    cls($('dBar'), 'on', true);
    stopPlayer();
    setTimeout(function () { window.location.reload(); }, 900);
  }

  /** После заставки говорим, чем закончилось обновление. */
  function bootReport() {
    if (load('updatedTo', '') === VERSION) toast('Приложение обновлено до версии ' + VERSION);
    else if (BOOT && BOOT.failed) toast('Обновление не запустилось — работает версия ' + VERSION);
    save('updatedTo', '');
  }

  // ---------- плагины ----------
  function runPlugin(code, index) {
    PL.setSource(index);
    try { (new Function(code))(); return true; } catch (e) { return false; } finally { PL.setSource(-1); }
  }

  function bootPlugins() {
    PL.setHost({ version: VERSION, platform: PLATFORM, toast: toast, log: function (t) { if (window.console) window.console.log('[plugin] ' + t); } });
    S.plugins.forEach(function (p, i) { if (p.enabled && p.code) runPlugin(p.code, i); });
    PL.start();
    // Свежие версии плагинов подтягиваем в фоне — они заработают со следующего запуска
    S.plugins.forEach(function (p) {
      if (!p.enabled) return;
      fetchText(p.url, function (code) { if (code && code !== p.code && /IPTV\s*\.\s*plugin/.test(code)) { p.code = code; save('plugins', S.plugins); } }, function () {});
    });
  }

  function setPlugStatus(text, isError) { $('plugStatus').textContent = text; cls($('plugStatus'), 'err', !!isError); }

  function renderPlugins() {
    var html = '<div data-i="0" class="row pl' + (S.pli === 0 ? ' sel' : '') + '">' + svg('plus') +
      '<span class="tx"><div class="nm">Добавить плагин по ссылке</div><div class="ds">Подойдёт ссылка на файл .js на GitHub</div></span></div>';
    S.plugins.forEach(function (p, i) {
      html += '<div data-i="' + (i + 1) + '" class="row pl' + (S.pli === i + 1 ? ' sel' : '') + (p.enabled ? '' : ' off') + '">' + svg('plug') +
        '<span class="tx"><div class="nm">' + esc(p.name || p.id || p.url) + (p.version ? '  ·  ' + esc(p.version) : '') + '</div>' +
        '<div class="ds">' + esc(p.description || p.url) + '</div></span><span class="tg' + (p.enabled ? ' on' : '') + '"><i></i></span></div>';
    });
    $('pllist').innerHTML = html;
    $('plugCount').textContent = S.plugins.length ? String(S.plugins.length) : 'нет';
  }

  function openPlugins() {
    closePanel();
    cls($('setup'), 'on', false);
    cls($('info'), 'on', false);
    S.zone = 'plugins';
    S.pli = 0;
    setPlugStatus(S.plugins.length ? (PC ? 'Щелчок — включить или выключить  ·  правая кнопка или Delete — удалить  ·  Esc — готово'
      : 'ОК — включить или выключить  ·  удержать ОК — удалить  ·  Назад — готово') : '', false);
    cls($('plugins'), 'on', true);
    renderPlugins();
  }

  function closePlugins() {
    $('purl').blur();
    cls($('plugins'), 'adding', false);
    cls($('plugins'), 'on', false);
    if (S.pluginsDirty) {
      // Плагины подключаются при запуске, поэтому после изменений перезапускаем приложение
      toast('Перезапускаю, чтобы применить плагины…');
      setTimeout(function () { window.location.reload(); }, 600);
      return;
    }
    S.zone = 'play';
    if (!S.channels.length) openSetup(); else showInfo();
  }

  function addPlugin(input) {
    var url = rawUrl(input);
    if (!/^https?:\/\//i.test(url)) { setPlugStatus('Ссылка должна начинаться с http:// или https://', true); return; }
    for (var i = 0; i < S.plugins.length; i++) if (S.plugins[i].url === url) { setPlugStatus('Этот плагин уже добавлен', true); return; }
    setPlugStatus('Загружаю плагин…', false);
    fetchText(url, function (code) {
      var index = S.plugins.length, before = PL.list().length;
      var ok = runPlugin(code, index), meta = PL.list().filter(function (m) { return m.source === index; })[0];
      if (!ok || !meta || PL.list().length === before) { setPlugStatus('В этом файле нет плагина: не найден вызов IPTV.plugin({ id: … })', true); return; }
      S.plugins.push({ url: url, enabled: true, code: code, id: meta.id, name: meta.name, version: meta.version, description: meta.description });
      save('plugins', S.plugins);
      S.pluginsDirty = true;
      $('purl').value = '';
      $('purl').blur();
      cls($('plugins'), 'adding', false);
      S.pli = S.plugins.length;
      setPlugStatus('Плагин «' + meta.name + '» добавлен. Нажмите «Назад», чтобы применить.', false);
      renderPlugins();
    }, function (reason) { setPlugStatus('Не удалось загрузить плагин: ' + reason, true); });
  }

  function pluginAction(remove) {
    if (S.pli === 0) {
      if (remove) return;
      cls($('plugins'), 'adding', true);
      $('purl').focus();
      return;
    }
    var p = S.plugins[S.pli - 1];
    if (!p) return;
    if (remove) {
      S.plugins.splice(S.pli - 1, 1);
      S.pli = Math.min(S.pli, S.plugins.length);
      setPlugStatus('Плагин «' + (p.name || p.id) + '» удалён.', false);
    } else p.enabled = !p.enabled;
    save('plugins', S.plugins);
    S.pluginsDirty = true;
    renderPlugins();
  }

  // ---------- экран настроек ----------
  function setStatus(text, isError) { $('setupStatus').textContent = text; cls($('setupStatus'), 'err', !!isError); }

  function setupItems() {
    var ids = ['url', 'epgurl', 'save', 'refresh'];
    if (S.channels.length) ids.push('cancel');
    return ids.concat(['autoRow', 'checkRow', 'plugRow']);
  }

  function renderSetup() {
    var items = setupItems(), all = ['url', 'epgurl', 'save', 'refresh', 'cancel', 'autoRow', 'checkRow', 'plugRow'];
    all.forEach(function (id) { cls($(id), 'sel', items[S.si] === id); });
    $('cancel').style.display = S.channels.length ? '' : 'none';
    cls($('autoToggle'), 'on', S.auto);
    cls($('setupBack'), 'can', S.channels.length > 0);
    $('setupTitle').textContent = S.url ? 'Настройки' : 'Добро пожаловать';
    $('setupLead').textContent = S.url ? '' : 'Вставьте ссылку на плейлист от вашего провайдера — и можно смотреть.';
    $('plugCount').textContent = S.plugins.length ? String(S.plugins.length) : 'нет';
    if (!$('updStatus').textContent) setUpd('Версия ' + VERSION);
  }

  function openSetup() {
    closePanel();
    cls($('info'), 'on', false);
    hideSplash(true);
    S.zone = 'setup';
    S.si = S.url ? setupItems().indexOf('save') : 0;
    $('url').value = S.url;
    $('epgurl').value = S.epgUrl;
    cls($('setup'), 'on', true);
    renderSetup();
  }

  function closeSetup() {
    $('url').blur(); $('epgurl').blur();
    cls($('setup'), 'on', false);
    S.zone = 'play';
    showInfo();
  }

  function saveSetup() {
    var url = trim($('url').value), epgUrl = trim($('epgurl').value);
    if (!/^https?:\/\//i.test(url)) { setStatus('Ссылка должна начинаться с http:// или https://', true); S.si = 0; renderSetup(); return; }
    if (epgUrl !== S.epgUrl) { S.epgUrl = epgUrl; save('epgurl', epgUrl); S.epg = null; }
    if (url !== S.url) {
      S.url = url;
      save('url', url);
      save('m3u', '');
      S.channels = []; S.cur = null; S.playList = []; S.epg = null;
      stopPlayer();
    }
    refresh(true);
  }

  function setupAction(id) {
    if (id === 'url' || id === 'epgurl') $(id).focus();
    else if (id === 'save') saveSetup();
    else if (id === 'refresh') { if (S.url) { S.epg = null; refresh(false); } else saveSetup(); }
    else if (id === 'cancel') closeSetup();
    else if (id === 'autoRow') { S.auto = !S.auto; save('auto', S.auto); }
    else if (id === 'checkRow') checkUpdate(true);
    else if (id === 'plugRow') openPlugins();
    if (S.zone === 'setup') renderSetup();
  }

  // ---------- пульт и клавиатура ----------
  var enterTimer = null, lastBack = 0, swallowEnter = false;
  var STUB = { preventDefault: function () {}, repeat: false };

  function exitApp() {
    try {
      if (TIZEN) window.tizen.application.getCurrentApplication().exit();
      else window.close();
    } catch (e) { toast('Нажмите кнопку «Домой» на пульте'); }
  }

  function onBack() {
    if (N.on) { numClose(); return; }
    if (S.zone === 'dialog') { closeDialog(-1); return; }
    if (S.zone === 'plugins') {
      if (has($('plugins'), 'adding')) { $('purl').blur(); cls($('plugins'), 'adding', false); return; }
      closePlugins(); return;
    }
    if (S.zone === 'setup') { if (S.channels.length) closeSetup(); else exitApp(); return; }
    if (S.zone === 'epg') { S.zone = 'channels'; renderPanel(); return; }
    if (S.zone !== 'play' && S.menuId === 'search') { leaveSearch(); return; }
    if (S.zone !== 'play') { closePanel(); showInfo(); return; }
    if (S.arch) { play(S.cur, null); toast('Прямой эфир'); return; }
    if (has($('info'), 'on') && !has($('msg'), 'on') && Date.now() - lastBack >= 2000) { cls($('info'), 'on', false); lastBack = 0; return; }
    if (Date.now() - lastBack < 2000) { exitApp(); return; }
    lastBack = Date.now();
    toast('Нажмите «' + KEYS.back + '» ещё раз, чтобы выйти');
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

  function keySetup(code, e) {
    var items = setupItems(), active = document.activeElement;
    if (active === $('url') || active === $('epgurl')) {
      if (code === KEY.ENTER || code === KEY.IME_DONE || code === KEY.DOWN) { active.blur(); S.si = Math.min(items.length - 1, S.si + 1); renderSetup(); e.preventDefault(); }
      else if (code === KEY.UP) { active.blur(); S.si = Math.max(0, S.si - 1); renderSetup(); e.preventDefault(); }
      else if (code === KEY.IME_CANCEL) active.blur();
      return;
    }
    if (code === KEY.UP || code === KEY.LEFT) S.si = Math.max(0, S.si - 1);
    else if (code === KEY.DOWN || code === KEY.RIGHT) S.si = Math.min(items.length - 1, S.si + 1);
    else if (code === KEY.ENTER) { e.preventDefault(); setupAction(items[S.si]); return; }
    renderSetup();
  }

  function keyPlugins(code, e) {
    if (document.activeElement === $('purl')) {
      if (code === KEY.ENTER || code === KEY.IME_DONE) { e.preventDefault(); addPlugin($('purl').value); }
      else if (code === KEY.IME_CANCEL) { $('purl').blur(); cls($('plugins'), 'adding', false); }
      return;
    }
    if (code === KEY.UP) S.pli = Math.max(0, S.pli - 1);
    else if (code === KEY.DOWN) S.pli = Math.min(S.plugins.length, S.pli + 1);
    else if (code === KEY.DEL) pluginAction(true);
    else if (code === KEY.ENTER) {
      if (!enterTimer && !e.repeat) enterTimer = setTimeout(function () { enterTimer = -1; pluginAction(true); }, 700);
      return;
    }
    renderPlugins();
  }

  function keySearch(code, e) {
    if (code === KEY.DOWN || code === KEY.ENTER || code === KEY.IME_DONE) {
      e.preventDefault();
      $('q').blur();
      if (S.list.length) S.zone = 'channels';
      renderPanel();
    } else if (code === KEY.LEFT && $('q').selectionStart === 0) {
      $('q').blur(); S.zone = 'groups'; renderPanel();
    } else if (code === KEY.IME_CANCEL) {
      $('q').blur();
    } else if (code === KEY.UP) e.preventDefault();
  }

  function keyGroups(code) {
    var last = S.menu.length - 1;
    // По кругу: с «Поиска» вверх — сразу к настройкам внизу, и обратно
    if (code === KEY.UP) S.gi = S.gi > 0 ? S.gi - 1 : last;
    else if (code === KEY.DOWN) S.gi = S.gi < last ? S.gi + 1 : 0;
    else if (code === KEY.CH_UP) S.gi = Math.max(0, S.gi - ROWS.g);
    else if (code === KEY.CH_DOWN) S.gi = Math.min(last, S.gi + ROWS.g);
    else if (code === KEY.ENTER || code === KEY.RIGHT) {
      var m = S.menu[S.gi];
      if (m.id === 'settings') { if (code === KEY.ENTER) openSetup(); return; }
      if (m.id === 'plugins') { if (code === KEY.ENTER) openPlugins(); return; }
      if (m.id === 'search') { startSearch(); return; }
      selectMenu(m.id);
      S.zone = 'channels';
    } else return;
    // При движении по группам сразу показываем их каналы
    var cur = S.menu[S.gi];
    if (S.zone === 'groups' && !cur.action) { S.menuId = cur.id; S.list = cur.list; S.ci = Math.max(0, S.list.indexOf(S.cur)); }
    renderPanel();
  }

  function chooseChannel() {
    var ch = S.list[S.ci];
    if (!ch) return;
    S.playList = S.menuId === 'search' ? S.channels : S.list;
    save('group', S.menuId === 'search' ? 'all' : S.menuId);
    var same = S.cur && S.cur.url === ch.url && !S.arch;
    closePanel();
    if (!same || has($('msg'), 'on')) play(ch, null); else showInfo();
  }

  function keyChannels(code) {
    if (code === KEY.UP) {
      if (S.ci === 0 && S.menuId === 'search') { S.zone = 'search'; renderPanel(); $('q').focus(); return; }
      S.ci = Math.max(0, S.ci - 1);
    } else if (code === KEY.DOWN) S.ci = Math.min(S.list.length - 1, S.ci + 1);
    else if (code === KEY.CH_UP) S.ci = Math.max(0, S.ci - ROWS.c);
    else if (code === KEY.CH_DOWN) S.ci = Math.min(S.list.length - 1, S.ci + ROWS.c);
    else if (code === KEY.LEFT) { S.zone = 'groups'; S.gi = menuIndex(S.menuId); }
    else if (code === KEY.RIGHT) { if (S.list[S.ci]) openEpg(S.list[S.ci]); return; }
    else return;
    renderPanel();
  }

  function keyEpg(code) {
    if (code === KEY.UP) S.pi = Math.max(0, S.pi - 1);
    else if (code === KEY.DOWN) S.pi = Math.min(S.prog.length - 1, S.pi + 1);
    else if (code === KEY.CH_UP) S.pi = Math.max(0, S.pi - ROWS.p);
    else if (code === KEY.CH_DOWN) S.pi = Math.min(S.prog.length - 1, S.pi + ROWS.p);
    else if (code === KEY.LEFT) S.zone = 'channels';
    else if (code === KEY.ENTER) { chooseProgramme(); if (S.zone === 'epg') renderPanel(); return; }
    else return;
    renderPanel();
  }

  /** Кнопки, которые работают одинаково и в эфире, и в гиде: цветные, «Гид», «Список каналов». */
  function shortcut(code) {
    var inGuide = S.zone === 'channels' || S.zone === 'groups' || S.zone === 'epg';
    if (code === KEY.RED) { if (S.channels.length) { if (!inGuide) openPanel(); startSearch(); } }
    else if (code === KEY.GREEN) { var ch = S.zone === 'channels' ? S.list[S.ci] : S.cur; if (ch) toggleFav(ch); }
    else if (code === KEY.YELLOW || code === KEY.GUIDE) { if (S.cur) { if (!inGuide) openPanel(); openEpg(S.zone === 'channels' && S.list[S.ci] ? S.list[S.ci] : S.cur); } }
    else if (code === KEY.BLUE) openSetup();
    else if (code === KEY.CH_LIST) { if (inGuide) { closePanel(); showInfo(); } else openPanel(); }
    else return false;
    return true;
  }

  function handleKey(code, e) {
    if (N.on && numKey(code, e)) return;
    if (S.zone === 'dialog') {
      var n = $('dButtons').children.length;
      if (code === KEY.LEFT) S.di = Math.max(0, S.di - 1);
      else if (code === KEY.RIGHT) S.di = Math.min(n - 1, S.di + 1);
      else if (code === KEY.ENTER) { closeDialog(S.di); return; }
      renderDialog();
      return;
    }
    if (S.zone === 'setup') { keySetup(code, e); return; }
    if (S.zone === 'plugins') { keyPlugins(code, e); return; }
    if (S.zone === 'search') { keySearch(code, e); return; }

    var digit = digitOf(code, e), letter = letterOf(e);
    if (digit >= 0) { typeDigit(digit); return; }
    if (shortcut(code)) return;
    if (letter && S.channels.length) {
      e.preventDefault();
      if (S.zone === 'play') openPanel();
      startSearch((S.menuId === 'search' ? $('q').value : '') + letter);
      return;
    }
    if (S.zone === 'groups') { keyGroups(code); return; }
    if (S.zone === 'epg') { keyEpg(code); return; }
    if (S.zone === 'channels') {
      if (code === KEY.ENTER) {
        // Короткое нажатие — включить канал, удержание — избранное
        if (!enterTimer && !e.repeat) enterTimer = setTimeout(function () { enterTimer = -1; if (S.list[S.ci]) toggleFav(S.list[S.ci]); }, 650);
        return;
      }
      keyChannels(code);
      return;
    }
    // Эфир
    if (code === KEY.ENTER) {
      if (!S.channels.length) openSetup();
      else if (has($('msg'), 'on') && S.cur) { retries = 0; start(); }
      else if (S.arch) togglePause();
      else openPanel();
    } else if (code === KEY.LEFT) { if (S.arch) seek(-30); else openPanel(); }
    else if (code === KEY.RIGHT) { if (S.arch) seek(30); else if (S.cur) { openPanel(); openEpg(S.cur); } }
    else if (code === KEY.UP || code === KEY.CH_UP) step(1);
    else if (code === KEY.DOWN || code === KEY.CH_DOWN) step(-1);
    else if (code === KEY.INFO) { if (has($('info'), 'on')) cls($('info'), 'on', false); else showInfo(); }
    else if (code === KEY.PRE_CH || code === KEY.BKSP) { if (S.prev) play(findByUrl(S.prev.url) || S.prev, null); }
    else if (code === KEY.PLAY || code === KEY.PAUSE || code === KEY.PLAY_PAUSE) { if (S.arch) togglePause(); }
    else if (code === KEY.REW) seek(-30);
    else if (code === KEY.FF) seek(30);
  }

  document.addEventListener('keydown', function (e) {
    var code = e.keyCode;
    if (code === KEY.BACK_LG) code = KEY.BACK;
    if (code === KEY.PAGE_UP) code = KEY.CH_UP;
    if (code === KEY.PAGE_DOWN) code = KEY.CH_DOWN;
    if (code >= KEY.LEFT && code <= KEY.DOWN) cls(document.body, 'mouse', false); // взяли пульт — убираем подсветку под указкой
    if (code === KEY.BACK || code === KEY.ESC) { e.preventDefault(); onBack(); return; }
    if (!splashGone) return;
    if (code === KEY.ENTER && swallowEnter) return;
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
    if (S.zone === 'channels') chooseChannel();
    else if (S.zone === 'plugins') pluginAction(false);
  });

  $('q').addEventListener('input', function () { applySearch(); renderPanel(); });

  // ---------- мышь, указка пульта и сенсорный экран ----------
  function rowOf(el, list) {
    while (el && el !== list) { if (el.getAttribute && el.getAttribute('data-i') !== null) return parseInt(el.getAttribute('data-i'), 10); el = el.parentNode; }
    return -1;
  }
  function inside(el, name) {
    while (el && el !== document) { if (el.id === name || (typeof el.className === 'string' && has(el, name))) return true; el = el.parentNode; }
    return false;
  }
  function attrOf(el, name, root) {
    while (el && el !== root.parentNode) { if (el.getAttribute && el.getAttribute(name) !== null) return el.getAttribute(name); el = el.parentNode; }
    return null;
  }
  // stopPropagation: иначе тот же щелчок дойдёт до общего обработчика и снова откроет панель
  function onClick(id, fn) { $(id).addEventListener('click', function (e) { e.stopPropagation(); fn(e, this); }); }
  function wheel(id, name) {
    $(id).addEventListener('wheel', function (e) { e.preventDefault(); tops[name] += e.deltaY > 0 ? 2 : -2; renderPanel(); });
  }

  function toggleFull() {
    var d = document, el = d.documentElement;
    try {
      if (d.fullscreenElement || d.webkitFullscreenElement) (d.exitFullscreen || d.webkitExitFullscreen).call(d);
      else (el.requestFullscreen || el.webkitRequestFullscreen).call(el);
    } catch (e) { /* без полноэкранного режима */ }
  }

  /** Действие подсказки или кнопки: то же, что соответствующая кнопка пульта. */
  function act(name) {
    if (name === 'back') onBack();
    else if (name === 'ok') { if (N.on) numCommit(); else if (S.zone === 'channels') chooseChannel(); else handleKey(KEY.ENTER, STUB); }
    else if (name === 'left') handleKey(KEY.LEFT, STUB);
    else if (name === 'right') handleKey(KEY.RIGHT, STUB);
    else if (name === 'down') handleKey(KEY.DOWN, STUB);
    else if (name === 'fav') { if (S.list[S.ci]) toggleFav(S.list[S.ci]); }
    else if (name === 'search') { if (S.zone === 'play') openPanel(); startSearch(); }
    else if (name === 'guide') openPanel();
    else if (name === 'epg') { if (S.cur) { openPanel(); openEpg(S.cur); } }
    else if (name === 'prev') step(-1);
    else if (name === 'next') step(1);
    else if (name === 'settings') openSetup();
    else if (name === 'full') toggleFull();
  }
  ['bar', 'hints', 'infoHints', 'numHint'].forEach(function (id) {
    onClick(id, function (e, el) { var a = attrOf(e.target, 'data-a', el); if (a) act(a); });
  });
  onClick('setupBack', onBack);
  onClick('plugBack', onBack);

  function button(action, icon, label, title) {
    return '<div class="tb' + (label ? '' : ' i') + '" data-a="' + action + '"' + (title ? ' title="' + title + '"' : '') + '>' + svg(icon) + (label || '') + '</div>';
  }
  $('bar').innerHTML = button('guide', 'list', 'Каналы') + button('epg', 'guide', 'Программа') + button('search', 'search', 'Поиск') +
    '<div class="sp"></div>' + button('prev', 'chevron-down', '', 'Предыдущий канал') + button('next', 'chevron-up', '', 'Следующий канал') +
    button('settings', 'settings', '', 'Настройки') +
    (PC && (document.documentElement.requestFullscreen || document.documentElement.webkitRequestFullscreen) ? button('full', 'fullscreen', '', 'На весь экран') : '');

  // Пока двигают мышь, поверх эфира видны кнопки; замерла — прячем их вместе с указателем
  var mouseTimer = null, lastXY = '', overBar = false;
  function pointerIdle() {
    if (overBar) { mouseTimer = setTimeout(pointerIdle, 1500); return; }
    if (S.zone !== 'play') return;
    cls(document.body, 'mouse', false);
    cls(document.body, 'idle', true);
  }
  document.addEventListener('mousemove', function (e) {
    var xy = e.screenX + ':' + e.screenY;
    if (xy === lastXY) return; // некоторые телевизоры шлют событие и без движения
    lastXY = xy;
    cls(document.body, 'mouse', true);
    cls(document.body, 'idle', false);
    clearTimeout(mouseTimer);
    mouseTimer = setTimeout(pointerIdle, 3500);
    if (splashGone && S.zone === 'play' && !has($('info'), 'on')) showInfo();
  });
  $('bar').addEventListener('mouseenter', function () { overBar = true; });
  $('bar').addEventListener('mouseleave', function () { overBar = false; });

  onClick('glist', function (e, el) { groupClick(rowOf(e.target, el)); });
  onClick('gfoot', function (e, el) { groupClick(rowOf(e.target, el)); });
  function groupClick(i) {
    if (i < 0) return;
    S.zone = 'groups'; S.gi = i;
    keyGroups(KEY.ENTER);
  }
  onClick('clist', function (e, el) {
    var i = rowOf(e.target, el);
    if (i < 0) return;
    if (inside(e.target, 'fv')) { toggleFav(S.list[i]); return; }
    S.ci = i; S.zone = 'channels';
    if (inside(e.target, 'pg')) { if (!openEpg(S.list[i])) renderPanel(); } else chooseChannel();
  });
  $('clist').addEventListener('contextmenu', function (e) {
    var i = rowOf(e.target, this);
    e.preventDefault();
    if (i >= 0) toggleFav(S.list[i]);
  });
  onClick('elist', function (e, el) {
    var i = rowOf(e.target, el);
    if (i < 0) return;
    S.pi = i; S.zone = 'epg';
    chooseProgramme();
    if (S.zone === 'epg') renderPanel();
  });
  onClick('numList', function (e, el) {
    var i = rowOf(e.target, el);
    if (i >= 0) { N.sel = i; numCommit(); }
  });
  onClick('numpad', function () {});
  wheel('clist', 'c');
  wheel('glist', 'g');
  wheel('elist', 'p');
  $('q').addEventListener('focus', function () { if (S.menuId === 'search') { S.zone = 'search'; renderPanel(); } });
  ['save', 'refresh', 'cancel', 'autoRow', 'checkRow', 'plugRow'].forEach(function (id) {
    onClick(id, function () { S.si = Math.max(0, setupItems().indexOf(id)); setupAction(id); });
  });
  ['url', 'epgurl'].forEach(function (id) {
    $(id).addEventListener('focus', function () { S.si = setupItems().indexOf(id); renderSetup(); });
  });
  onClick('pllist', function (e, el) {
    var i = rowOf(e.target, el);
    if (i < 0) return;
    S.pli = i;
    pluginAction(false);
    renderPlugins();
  });
  $('pllist').addEventListener('contextmenu', function (e) {
    var i = rowOf(e.target, this);
    e.preventDefault();
    if (i > 0) { S.pli = i; pluginAction(true); }
  });
  onClick('dButtons', function (e) {
    var d = e.target.getAttribute('data-d');
    if (d !== null) closeDialog(parseInt(d, 10));
  });
  document.addEventListener('click', function (e) {
    if (!splashGone || S.zone === 'setup' || S.zone === 'plugins' || S.zone === 'dialog') return;
    if (N.on) { numClose(); return; }
    if (S.zone === 'play') {
      if (!S.channels.length) openSetup();
      else if (has($('msg'), 'on') && S.cur) { retries = 0; start(); }
      else openPanel();
    } else if (!inside(e.target, 'card')) { closePanel(); showInfo(); }
  });

  // ---------- масштаб под окно ----------
  function fit() {
    var w = window.innerWidth || 1920, h = window.innerHeight || 1080;
    var s = Math.min(w / 1920, h / 1080);
    var t = s === 1 ? '' : 'scale(' + s + ')';
    document.body.style.webkitTransform = t;
    document.body.style.transform = t;
    document.body.style.left = Math.round((w - 1920 * s) / 2) + 'px';
    document.body.style.top = Math.round((h - 1080 * s) / 2) + 'px';
  }
  window.addEventListener('resize', fit);
  fit();

  // Телевизор выключили или свернули приложение — останавливаем поток; вернулись — включаем тот же канал.
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) stopPlayer();
    else if (S.cur) start();
  });

  // ---------- запуск ----------
  try {
    var keys = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'ChannelUp', 'ChannelDown', 'ChannelList', 'PreviousChannel', 'Info', 'Guide',
      'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue', 'MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaRewind', 'MediaFastForward'];
    for (var k = 0; k < keys.length; k++) try { window.tizen.tvinputdevice.registerKey(keys[k]); } catch (e1) { /* такой кнопки на этом пульте нет */ }
  } catch (e) { /* не Samsung */ }

  bootPlugins();
  setTimeout(hideSplash, 8000);
  setInterval(function () { loadEpg(false); }, 3600000);
  preparePlayer(function () {
    if (!S.url) {
      openSetup();
    } else {
      var cached = load('m3u', '');
      if (cached) applyPlaylist(parseM3U(cached)); // мгновенный старт с сохранённого списка
      refresh(false);
    }
    if (S.auto && Date.now() - load('updCheck', 0) > 6 * 3600000) checkUpdate(false);
  });
  // Запуск дошёл до конца — снимаем отметку загрузчика. Останется она, только если эта версия упала
  // при старте: тогда в следующий раз загрузчик вернётся к версии из пакета.
  try { window.localStorage.removeItem('iptv_bundleBoot'); } catch (e) { /* хранилище недоступно */ }
})();

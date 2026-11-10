/*
 * IPTV — общая веб-основа для компьютеров (Electron), телевизоров Samsung (Tizen) и LG (webOS).
 * Каналы по группам, телепрограмма, записи передач, поиск, избранное, плагины, проверка обновлений.
 * Код на ES5: он должен запускаться и на телевизорах 2016–2017 годов. Возможности, которых там нет
 * (например, потоковая распаковка телепрограммы), проверяются перед использованием.
 */
(function () {
  'use strict';

  var VERSION = '1.0.0';
  var REPO = 'volnalabs/IPTV';
  var KEY = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACK: 10009, BACK_LG: 461, ESC: 27, DEL: 46,
    CH_UP: 427, CH_DOWN: 428, PAGE_UP: 33, PAGE_DOWN: 34, IME_DONE: 65376, IME_CANCEL: 65385 };
  var TIZEN = !!window.tizen;
  var WEBOS = !!(window.PalmSystem || window.webOS);
  var DESKTOP = /Electron/.test(navigator.userAgent);
  var PLATFORM = TIZEN ? 'samsung' : WEBOS ? 'lg' : DESKTOP ? 'desktop' : 'web';
  var PLATFORM_NAME = { samsung: 'Samsung', lg: 'LG', desktop: 'компьютера', web: 'браузера' }[PLATFORM];
  var DAY = 86400000;
  var ICON = {
    search: 'M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0M16 16l4.5 4.5',
    settings: 'M4 7h5.5M14.5 7H20M9.5 7a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M4 17h3.5M12.5 17H20M7.5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0',
    star: 'M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1.05 5.9L12 16.9l-5.25 2.8 1.05-5.9-4.3-4.1 5.9-.8z',
    tv: 'M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM8.5 20.5h7',
    history: 'M5.2 8.8A7.5 7.5 0 1 1 4.5 12M4.6 4.9v4h4M12 8.5V12l2.5 1.5',
    play: 'M8.5 6L18 12l-9.5 6z',
    plug: 'M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0zM12 17v4',
    plus: 'M12 5v14M5 12h14',
    list: 'M8 7h12M8 12h12M8 17h12M4 7h.01M4 12h.01M4 17h.01'
  };

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
    zone: 'play',      // play | channels | groups | search | epg | setup | plugins | dialog
    si: 0, pli: 0, di: 0,
    update: null, pluginsDirty: false
  };
  window.__iptvState = S; // для отладки и автотестов

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
    if (!window.fetch || !window.ReadableStream || !window.TextDecoder) { setEpgStatus('Телепрограмма на этой модели не поддерживается', true); return; }

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
        rec.s.push(s); rec.e.push(e); rec.t.push(t ? unxml(trim(t[1])) : '');
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

    epgBusy = true;
    setEpgStatus('Загружаю программу…', false);
    window.fetch(url).then(function (res) {
      if (!res.ok) throw new Error('сервер ответил ' + res.status);
      var reader = res.body.getReader();
      return reader.read().then(function (first) {
        var gz = first.value && first.value[0] === 0x1f && first.value[1] === 0x8b;
        if (gz) {
          if (!window.DecompressionStream) throw new Error('эта модель не умеет распаковывать .gz');
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
    })['catch'](failed);
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

  function buildMenu() {
    var menu = [
      { id: 'search', title: 'Поиск', icon: 'search', action: true },
      { id: 'settings', title: 'Настройки', icon: 'settings', action: true },
      { id: 'plugins', title: 'Плагины', icon: 'plug', action: true }
    ];
    var favs = S.channels.filter(function (c) { return S.favs[c.url]; });
    if (favs.length) menu.push({ id: 'fav', title: 'Избранное', icon: 'star', list: favs });
    menu.push({ id: 'all', title: 'Все каналы', icon: 'tv', list: S.channels });
    var seen = {}, order = [];
    S.channels.forEach(function (c) {
      if (!seen[c.group]) { seen[c.group] = { id: 'g:' + c.group, title: c.group, list: [] }; order.push(seen[c.group]); }
      seen[c.group].list.push(c);
    });
    S.menu = menu.concat(order);
    if (!listOf(S.menuId)) S.menuId = 'all';
  }

  function menuIndex(id) {
    for (var i = 0; i < S.menu.length; i++) if (S.menu[i].id === id) return i;
    return 0;
  }

  /** Показываем только строки вокруг выделенной — список из тысячи каналов телевизор не потянет. */
  function windowStart(sel, total, visible) {
    return Math.max(0, Math.min(sel - Math.floor(visible / 2), total - visible));
  }
  function svg(name, fill) {
    return '<svg class="ic' + (fill ? ' fill' : '') + '" viewBox="0 0 24 24"><path d="' + ICON[name] + '"/></svg>';
  }
  function logoHtml(ch) {
    var letter = (ch.name.match(/[0-9A-Za-zА-Яа-яЁё]/) || ['•'])[0].toUpperCase();
    return '<div class="logo"><b>' + esc(letter) + '</b>' +
      (ch.logo ? '<img src="' + esc(ch.logo) + '" onerror="this.style.display=\'none\'">' : '') + '</div>';
  }

  function renderGroups() {
    var visible = 8, start = windowStart(S.gi, S.menu.length, visible), html = '';
    for (var i = start; i < Math.min(S.menu.length, start + visible); i++) {
      var m = S.menu[i];
      html += '<div data-i="' + i + '" class="row g' + (i === S.gi && S.zone === 'groups' ? ' sel' : '') + (m.id === S.menuId ? ' act' : '') + '">' +
        (m.icon ? svg(m.icon) : '<span class="sp"></span>') +
        '<span class="t">' + esc(m.title) + '</span>' +
        (m.action ? '' : '<span class="n">' + m.list.length + '</span>') + '</div>';
    }
    $('glist').innerHTML = html;
  }

  function renderChannels() {
    var visible = 6, start = windowStart(S.ci, S.list.length, visible), html = '', now = Date.now();
    for (var i = start; i < Math.min(S.list.length, start + visible); i++) {
      var c = S.list[i], e = epgFor(c), p = e ? progAt(e, now) : -1;
      html += '<div data-i="' + i + '" class="row c' + (i === S.ci && S.zone === 'channels' ? ' sel' : '') + (S.cur && c.url === S.cur.url ? ' act' : '') + '">' +
        '<span class="no">' + c.number + '</span><span class="lg">' + logoHtml(c) + '</span>' +
        '<span class="tx"><div class="nm">' + esc(c.name) + '</div><div class="gr">' + esc(p >= 0 ? e.t[p] : c.group) + '</div>' +
        (p >= 0 ? '<div class="bar"><i style="width:' + Math.round((now - e.s[p]) * 100 / Math.max(1, e.e[p] - e.s[p])) + '%"></i></div>' : '') + '</span>' +
        (S.favs[c.url] ? svg('star', true) : '') +
        (e ? '<span class="pg" title="Телепрограмма">' + svg('list') + '</span>' : '') + '</div>';
    }
    if (!S.list.length) html = '<div class="empty">' + (S.menuId === 'search' ? 'Ничего не нашлось. Попробуйте короче.' : 'Здесь пока пусто') + '</div>';
    $('clist').innerHTML = html;
  }

  function renderEpg() {
    var visible = 8, start = windowStart(S.pi, S.prog.length, visible), html = '', now = Date.now();
    for (var i = start; i < Math.min(S.prog.length, start + visible); i++) {
      var p = S.prog[i], day = dayLabel(p.s, now), isNow = p.s <= now && p.e > now, past = p.e <= now;
      var playing = S.arch && S.cur === S.epgCh && S.arch.s === p.s;
      html += '<div data-i="' + i + '" class="row p' + (i === S.pi && S.zone === 'epg' ? ' sel' : '') + (isNow || playing ? ' act' : '') + (past && !p.arch ? ' dim' : '') + '">' +
        '<span class="tm">' + (day ? day + ' ' : '') + clock(p.s) + '</span><span class="tt">' + esc(p.t) + '</span>' +
        (isNow ? '<span class="bd">СЕЙЧАС</span>' : playing ? svg('play', true) : past && p.arch ? svg('history') : '') + '</div>';
    }
    $('elist').innerHTML = html;
  }

  function renderPanel() {
    var searching = S.menuId === 'search';
    cls($('panel'), 'g', S.zone === 'groups');
    cls($('panel'), 'e', S.zone === 'epg');
    cls($('chans'), 's', searching);
    cls($('search'), 'focus', S.zone === 'search');
    if (searching) {
      var q = norm($('q').value);
      $('chint').textContent = !q ? 'Начните вводить название — список сузится сам'
        : S.list.length ? 'Нашлось: ' + plural(S.list.length, 'канал', 'канала', 'каналов') + '  ·  ▼ к списку' : '';
    } else {
      $('ctitle').textContent = S.menu[menuIndex(S.menuId)].title;
      $('chint').textContent = plural(S.list.length, 'канал', 'канала', 'каналов') +
        (S.zone === 'channels' ? '  ·  ◀ меню  ·  ▶ программа' : '');
    }
    renderGroups();
    renderChannels();
    if (S.zone === 'epg') renderEpg();
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
    cls($('panel'), 'on', true);
    renderPanel();
  }

  function closePanel() {
    $('q').blur();
    if (S.menuId === 'search') S.menuId = 'all';
    S.zone = 'play';
    cls($('panel'), 'on', false);
  }

  function openEpg(ch) {
    var e = epgFor(ch);
    if (!e || !e.s.length) { toast(epgBusy ? 'Телепрограмма ещё загружается…' : 'Для этого канала телепрограммы нет'); return false; }
    var now = Date.now(), from = ch.catchupDays ? now - ch.catchupDays * DAY : dayStart(now), to = now + DAY;
    S.epgCh = ch;
    S.prog = [];
    S.pi = 0;
    for (var i = 0; i < e.s.length; i++) {
      if (e.e[i] <= from || e.s[i] >= to) continue;
      if (e.s[i] <= now && e.e[i] > now) S.pi = S.prog.length;
      if (S.arch && S.cur === ch && S.arch.s === e.s[i]) S.pi = S.prog.length;
      S.prog.push({ s: e.s[i], e: e.e[i], t: e.t[i] || 'Без названия', arch: !!ch.catchupDays && e.s[i] >= from });
    }
    if (!S.prog.length) { toast('Для этого канала телепрограммы нет'); return false; }
    $('etitle').textContent = ch.name;
    $('ehint').textContent = ch.catchupDays ? 'Прошедшие передачи можно смотреть в записи' : 'Телепрограмма';
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

  function startSearch() {
    S.menuId = 'search';
    S.gi = menuIndex('search');
    $('q').value = '';
    applySearch();
    S.zone = 'search';
    renderPanel();
    $('q').focus();
  }

  function applySearch() {
    var q = norm($('q').value);
    S.list = q ? S.channels.filter(function (c) { return norm(c.name).indexOf(q) >= 0; }) : S.channels;
    S.ci = 0;
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
      message('Не удалось включить «' + ch.name + '».\nНажмите ОК, чтобы попробовать снова, или ▲▼ — другой канал.');
    }
  }

  video.addEventListener('playing', playing);
  video.addEventListener('waiting', function () { loading(true); });
  video.addEventListener('ended', function () { if (S.cur) ended(S.cur); });
  video.addEventListener('error', function () { if (S.cur && !hls && video.getAttribute('src')) playFailed(S.cur); });

  function play(ch, programme) {
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
  var infoTimer = null, toastTimer = null, numTimer = null, digits = '';

  function updateInfo() {
    var ch = S.cur;
    if (!ch) return;
    var now = Date.now();
    $('infoNum').textContent = ch.number;
    $('infoName').textContent = ch.name;
    $('infoClock').textContent = clock(now);
    if ($('infoLogo').getAttribute('data-url') !== ch.url) { $('infoLogo').innerHTML = logoHtml(ch); $('infoLogo').setAttribute('data-url', ch.url); }
    if (S.arch) {
      var dur = (S.arch.e - S.arch.s) / 1000, pos = position(), day = dayLabel(S.arch.s, now);
      cls($('infoBadge'), 'on', true);
      $('infoBadge').textContent = paused ? 'ПАУЗА' : 'ЗАПИСЬ';
      $('infoTitle').textContent = S.arch.t;
      cls($('infoLine'), 'on', true);
      $('infoStart').textContent = duration(pos);
      $('infoEnd').textContent = duration(dur);
      $('infoFill').style.width = Math.min(100, pos * 100 / Math.max(1, dur)) + '%';
      $('infoNext').textContent = (day ? day + ', ' : 'сегодня, ') + clock(S.arch.s) + '  ·  ◀ ▶ перемотка  ·  ОК пауза  ·  Назад — в эфир';
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
    if (S.update && S.zone === 'play') openDialog();
  }

  function typeDigit(d) {
    if (digits.length >= 4) digits = '';
    digits += d;
    $('num').textContent = digits;
    cls($('num'), 'on', true);
    clearTimeout(numTimer);
    numTimer = setTimeout(function () {
      var n = parseInt(digits, 10), ch = S.channels[n - 1];
      digits = '';
      cls($('num'), 'on', false);
      if (!ch) { toast('Канала с номером ' + n + ' нет'); return; }
      S.playList = S.channels;
      save('group', 'all');
      play(ch, null);
    }, 1500);
  }

  // ---------- обновления ----------
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
  function openDialog() {
    if (!S.update || S.zone === 'dialog') return;
    if (S.zone !== 'play' && S.zone !== 'setup') return; // не перебиваем выбор канала
    dialogBack = S.zone;
    $('dTitle').textContent = 'Вышла версия ' + S.update;
    if (DESKTOP || PLATFORM === 'web') {
      $('dText').textContent = 'Сейчас установлена версия ' + VERSION + '. Скачайте новый файл со страницы релиза и запустите его вместо старого — каналы и настройки сохранятся.';
      $('dButtons').innerHTML = '<div class="btn primary" data-d="0">Открыть страницу загрузки</div><div class="btn" data-d="1">Позже</div>';
    } else {
      $('dText').textContent = 'Сейчас установлена версия ' + VERSION + '. Телевизоры ' + PLATFORM_NAME + ' не разрешают приложениям обновляться самим: новый файл ставится с компьютера, так же как в первый раз.';
      $('dButtons').innerHTML = '<div class="btn primary" data-d="0">Понятно</div>';
    }
    S.di = 0;
    S.zone = 'dialog';
    cls($('dialog'), 'on', true);
    renderDialog();
  }
  function renderDialog() {
    var b = $('dButtons').children;
    for (var i = 0; i < b.length; i++) cls(b[i], 'sel', i === S.di);
  }
  function closeDialog(action) {
    cls($('dialog'), 'on', false);
    S.zone = dialogBack;
    S.update = null; // до следующего запуска не напоминаем
    if (action === 0 && (DESKTOP || PLATFORM === 'web')) window.open('https://github.com/' + REPO + '/releases/latest', '_blank');
    if (S.zone === 'play') showInfo();
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
    setPlugStatus(S.plugins.length ? 'ОК — включить или выключить  ·  удержать ОК — удалить  ·  Назад — готово' : '', false);
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
  var enterTimer = null, lastBack = 0;

  function exitApp() {
    try {
      if (TIZEN) window.tizen.application.getCurrentApplication().exit();
      else window.close();
    } catch (e) { toast('Нажмите кнопку «Домой» на пульте'); }
  }

  function onBack() {
    if (S.zone === 'dialog') { closeDialog(1); return; }
    if (S.zone === 'plugins') {
      if (has($('plugins'), 'adding')) { $('purl').blur(); cls($('plugins'), 'adding', false); return; }
      closePlugins(); return;
    }
    if (S.zone === 'setup') { if (S.channels.length) closeSetup(); else exitApp(); return; }
    if (S.zone === 'epg') { S.zone = 'channels'; renderPanel(); return; }
    if (S.zone !== 'play') { closePanel(); showInfo(); return; }
    if (S.arch) { play(S.cur, null); toast('Прямой эфир'); return; }
    if (Date.now() - lastBack < 2000) { exitApp(); return; }
    lastBack = Date.now();
    toast('Нажмите «Назад» ещё раз, чтобы выйти');
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
    if (code === KEY.UP) S.gi = Math.max(0, S.gi - 1);
    else if (code === KEY.DOWN) S.gi = Math.min(S.menu.length - 1, S.gi + 1);
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
    else if (code === KEY.CH_UP) S.ci = Math.max(0, S.ci - 6);
    else if (code === KEY.CH_DOWN) S.ci = Math.min(S.list.length - 1, S.ci + 6);
    else if (code === KEY.LEFT) { S.zone = 'groups'; S.gi = menuIndex(S.menuId); }
    else if (code === KEY.RIGHT) { if (S.list[S.ci]) openEpg(S.list[S.ci]); return; }
    else return;
    renderPanel();
  }

  function keyEpg(code) {
    if (code === KEY.UP) S.pi = Math.max(0, S.pi - 1);
    else if (code === KEY.DOWN) S.pi = Math.min(S.prog.length - 1, S.pi + 1);
    else if (code === KEY.CH_UP) S.pi = Math.max(0, S.pi - 8);
    else if (code === KEY.CH_DOWN) S.pi = Math.min(S.prog.length - 1, S.pi + 8);
    else if (code === KEY.LEFT) S.zone = 'channels';
    else if (code === KEY.ENTER) { chooseProgramme(); return; }
    else return;
    renderPanel();
  }

  document.addEventListener('keydown', function (e) {
    var code = e.keyCode;
    if (code === KEY.BACK_LG) code = KEY.BACK;
    if (code === KEY.PAGE_UP) code = KEY.CH_UP;
    if (code === KEY.PAGE_DOWN) code = KEY.CH_DOWN;
    if (code === KEY.BACK || code === KEY.ESC) { e.preventDefault(); onBack(); return; }
    if (!splashGone) return;
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
    var digit = code >= 48 && code <= 57 ? code - 48 : code >= 96 && code <= 105 ? code - 96 : -1;
    if (digit >= 0) { typeDigit(digit); return; }
    if (code === KEY.ENTER) {
      if (!S.channels.length) openSetup();
      else if (has($('msg'), 'on') && S.cur) { retries = 0; start(); }
      else if (S.arch) togglePause();
      else openPanel();
    } else if (code === KEY.LEFT) { if (S.arch) seek(-30); else openPanel(); }
    else if (code === KEY.RIGHT) { if (S.arch) seek(30); else if (S.cur) { openPanel(); if (!openEpg(S.cur)) renderPanel(); } }
    else if (code === KEY.UP || code === KEY.CH_UP) step(1);
    else if (code === KEY.DOWN || code === KEY.CH_DOWN) step(-1);
  });

  document.addEventListener('keyup', function (e) {
    if (e.keyCode !== KEY.ENTER || !enterTimer) return;
    var held = enterTimer === -1;
    if (!held) clearTimeout(enterTimer);
    enterTimer = null;
    if (held) return;
    if (S.zone === 'channels') chooseChannel();
    else if (S.zone === 'plugins') pluginAction(false);
  });

  $('q').addEventListener('input', function () { applySearch(); renderPanel(); });

  // ---------- мышь и сенсорный экран ----------
  function rowOf(el, list) {
    while (el && el !== list) { if (el.getAttribute && el.getAttribute('data-i') !== null) return parseInt(el.getAttribute('data-i'), 10); el = el.parentNode; }
    return -1;
  }
  function inside(el, name) {
    while (el && el !== document) { if (el.id === name || (el.className && has(el, name))) return true; el = el.parentNode; }
    return false;
  }
  // stopPropagation: иначе тот же щелчок дойдёт до общего обработчика и снова откроет панель
  function onClick(id, fn) { $(id).addEventListener('click', function (e) { e.stopPropagation(); fn(e, this); }); }
  function wheel(id, move) {
    $(id).addEventListener('wheel', function (e) { e.preventDefault(); move(e.deltaY > 0 ? 1 : -1); renderPanel(); });
  }

  onClick('glist', function (e, el) {
    var i = rowOf(e.target, el);
    if (i < 0) return;
    S.zone = 'groups'; S.gi = i;
    keyGroups(KEY.ENTER);
  });
  onClick('clist', function (e, el) {
    var i = rowOf(e.target, el);
    if (i < 0) return;
    S.ci = i; S.zone = 'channels';
    if (inside(e.target, 'pg')) { if (!openEpg(S.list[i])) renderPanel(); } else chooseChannel();
  });
  $('clist').addEventListener('contextmenu', function (e) {
    var i = rowOf(e.target, this);
    e.preventDefault();
    if (i >= 0) { S.ci = i; S.zone = 'channels'; toggleFav(S.list[i]); }
  });
  onClick('elist', function (e, el) {
    var i = rowOf(e.target, el);
    if (i < 0) return;
    S.pi = i; S.zone = 'epg';
    chooseProgramme();
    if (S.zone === 'epg') renderPanel();
  });
  wheel('clist', function (d) { if (S.zone !== 'epg') S.zone = 'channels'; S.ci = Math.max(0, Math.min(S.list.length - 1, S.ci + d)); });
  wheel('glist', function (d) { S.zone = 'groups'; S.gi = Math.max(0, Math.min(S.menu.length - 1, S.gi + d)); });
  wheel('elist', function (d) { S.zone = 'epg'; S.pi = Math.max(0, Math.min(S.prog.length - 1, S.pi + d)); });
  onClick('ctitle', function () { S.zone = 'groups'; S.gi = menuIndex(S.menuId); renderPanel(); });
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
    var keys = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'ChannelUp', 'ChannelDown'];
    for (var k = 0; k < keys.length; k++) window.tizen.tvinputdevice.registerKey(keys[k]);
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
})();

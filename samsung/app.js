/*
 * IPTV для телевизоров Samsung (Tizen).
 * Облегчённая версия: каналы по группам, поиск, избранное, запуск с последнего канала.
 * Код на ES5 — он должен работать и на телевизорах 2016–2017 годов.
 */
(function () {
  'use strict';

  var VERSION = '1.0.0';
  var KEY = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACK: 10009, ESC: 27, CH_UP: 427, CH_DOWN: 428, IME_DONE: 65376, IME_CANCEL: 65385 };
  var ICON = {
    search: 'M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0M16 16l4.5 4.5',
    settings: 'M4 7h5.5M14.5 7H20M9.5 7a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M4 17h3.5M12.5 17H20M7.5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0',
    star: 'M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1.05 5.9L12 16.9l-5.25 2.8 1.05-5.9-4.3-4.1 5.9-.8z',
    tv: 'M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM8.5 20.5h7'
  };

  // ---------- хранилище ----------
  function load(key, def) {
    try { var v = window.localStorage.getItem('iptv_' + key); return v === null ? def : JSON.parse(v); } catch (e) { return def; }
  }
  function save(key, value) {
    try { window.localStorage.setItem('iptv_' + key, JSON.stringify(value)); } catch (e) { /* хранилище переполнено — не страшно */ }
  }
  function $(id) { return document.getElementById(id); }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function cls(el, name, on) {
    var list = el.className ? el.className.split(/\s+/) : [], i = list.indexOf(name);
    if (on && i < 0) list.push(name);
    if (!on && i >= 0) list.splice(i, 1);
    el.className = list.join(' ');
  }
  function plural(n, one, few, many) {
    var a = n % 10, b = n % 100;
    return n + ' ' + (a === 1 && b !== 11 ? one : a >= 2 && a <= 4 && (b < 12 || b > 14) ? few : many);
  }
  function norm(s) { return String(s).toLowerCase().replace(/ё/g, 'е').replace(/^\s+|\s+$/g, ''); }

  // ---------- состояние ----------
  var S = {
    url: load('url', ''),
    favs: load('favs', {}),
    channels: [],
    menu: [],        // пункты левой колонки
    menuId: 'all',   // выбранный пункт
    list: [],        // каналы в правой колонке
    gi: 0, ci: 0,    // позиции выделения
    cur: null,       // играющий канал
    playList: [],    // список, по которому листаем ▲▼
    zone: 'play',    // play | channels | groups | search | setup
    si: 0            // выделение на экране настройки: 0 поле, 1 сохранить, 2 назад
  };

  // ---------- разбор плейлиста ----------
  function parseM3U(text) {
    var lines = text.replace(/﻿/g, '').split(/\r?\n/), out = [], info = null, grp = null;
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i].replace(/^\s+|\s+$/g, '');
      if (!line) continue;
      if (/^#EXTINF/i.test(line)) {
        info = { attrs: {}, name: '' };
        grp = null;
        var re = /([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"/g, m;
        while ((m = re.exec(line))) info.attrs[m[1].toLowerCase()] = m[2];
        var quoted = false;
        for (var k = 0; k < line.length; k++) {
          if (line.charAt(k) === '"') quoted = !quoted;
          else if (line.charAt(k) === ',' && !quoted) { info.name = line.substring(k + 1).replace(/^\s+|\s+$/g, ''); break; }
        }
      } else if (/^#EXTGRP:/i.test(line)) {
        grp = line.substring(8).replace(/^\s+|\s+$/g, '');
      } else if (line.charAt(0) !== '#') {
        var a = info ? info.attrs : {};
        out.push({
          number: out.length + 1,
          name: (info && info.name) || a['tvg-name'] || ('Канал ' + (out.length + 1)),
          url: line,
          group: a['group-title'] || grp || 'Без группы',
          logo: a['tvg-logo'] || ''
        });
        info = null; grp = null;
      }
    }
    return out;
  }

  function fetchText(url, ok, fail) {
    var x = new XMLHttpRequest(), done = false;
    function finish(fn, arg) { if (!done) { done = true; fn(arg); } }
    x.open('GET', url, true);
    x.timeout = 30000;
    x.onload = function () { if (x.status >= 200 && x.status < 300) finish(ok, x.responseText); else finish(fail, 'сервер ответил ' + x.status); };
    x.onerror = function () { finish(fail, 'нет связи с сервером'); };
    x.ontimeout = function () { finish(fail, 'сервер не отвечает'); };
    try { x.send(); } catch (e) { finish(fail, 'неверная ссылка'); }
  }

  function applyPlaylist(channels) {
    S.channels = channels;
    buildMenu();
    if (!channels.length) return;
    var last = load('last', ''), ch = null, i;
    for (i = 0; i < channels.length; i++) if (channels[i].url === last) { ch = channels[i]; break; }
    var keep = S.cur ? findByUrl(S.cur.url) : null;
    if (keep) { S.cur = keep; S.playList = listOf(S.menuId) || channels; return; }
    S.menuId = load('group', 'all');
    var list = listOf(S.menuId);
    if (!list || !ch || list.indexOf(ch) < 0) { S.menuId = 'all'; list = channels; }
    S.playList = list;
    play(ch || channels[0]);
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
      var channels = parseM3U(text);
      if (!channels.length) { loadFailed('в плейлисте нет каналов'); return; }
      save('m3u', text);
      $('splashCap').textContent = '';
      setStatus('Загружено: ' + plural(channels.length, 'канал', 'канала', 'каналов'), false);
      applyPlaylist(channels);
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

  // ---------- меню и списки ----------
  function listOf(id) {
    var i;
    if (id === 'all') return S.channels;
    if (id === 'fav') return S.channels.filter(function (c) { return S.favs[c.url]; });
    for (i = 0; i < S.menu.length; i++) if (S.menu[i].id === id) return S.menu[i].list;
    return null;
  }

  function buildMenu() {
    var menu = [{ id: 'search', title: 'Поиск', icon: 'search', action: true }, { id: 'settings', title: 'Настройки', icon: 'settings', action: true }];
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
      html += '<div class="row g' + (i === S.gi && S.zone === 'groups' ? ' sel' : '') + (m.id === S.menuId ? ' act' : '') + '">' +
        (m.icon ? svg(m.icon) : '<span class="sp"></span>') +
        '<span class="t">' + esc(m.title) + '</span>' +
        (m.action ? '' : '<span class="n">' + m.list.length + '</span>') + '</div>';
    }
    $('glist').innerHTML = html;
  }

  function renderChannels() {
    var visible = 6, start = windowStart(S.ci, S.list.length, visible), html = '';
    for (var i = start; i < Math.min(S.list.length, start + visible); i++) {
      var c = S.list[i];
      html += '<div class="row c' + (i === S.ci && S.zone === 'channels' ? ' sel' : '') + (S.cur && c.url === S.cur.url ? ' act' : '') + '">' +
        '<span class="no">' + c.number + '</span><span class="lg">' + logoHtml(c) + '</span>' +
        '<span class="tx"><div class="nm">' + esc(c.name) + '</div><div class="gr">' + esc(c.group) + '</div></span>' +
        (S.favs[c.url] ? svg('star', true) : '') + '</div>';
    }
    if (!S.list.length) html = '<div class="empty">' + (S.menuId === 'search' ? 'Ничего не нашлось. Попробуйте короче.' : 'Здесь пока пусто') + '</div>';
    $('clist').innerHTML = html;
  }

  function renderPanel() {
    var searching = S.menuId === 'search';
    cls($('panel'), 'g', S.zone === 'groups');
    cls($('chans'), 's', searching);
    cls($('search'), 'focus', S.zone === 'search');
    if (searching) {
      var q = norm($('q').value);
      $('chint').textContent = !q ? 'Начните вводить название — список сузится сам'
        : S.list.length ? 'Нашлось: ' + plural(S.list.length, 'канал', 'канала', 'каналов') + '  ·  ▼ к списку' : '';
    } else {
      $('ctitle').textContent = S.menu[menuIndex(S.menuId)].title;
      $('chint').textContent = plural(S.list.length, 'канал', 'канала', 'каналов') +
        (S.zone === 'groups' ? '' : '  ·  ◀ группы и поиск  ·  удержать ОК — в избранное');
    }
    renderGroups();
    renderChannels();
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
  var video = $('video');
  var av = window.webapis && window.webapis.avplay ? window.webapis.avplay : null;
  var retries = 0, retryTimer = null;
  if (av) cls(document.body, 'av', true);

  function loading(on) { cls($('loading'), 'on', on); }

  function stopPlayer() {
    clearTimeout(retryTimer);
    if (av) { try { av.stop(); } catch (e) {} try { av.close(); } catch (e2) {} }
    else { try { video.pause(); video.removeAttribute('src'); video.load(); } catch (e3) {} }
  }

  function start(ch) {
    stopPlayer();
    message('');
    loading(true);
    if (av) {
      try {
        av.open(ch.url);
        av.setDisplayRect(0, 0, 1920, 1080);
        av.setListener({
          onbufferingstart: function () { loading(true); },
          onbufferingcomplete: function () { loading(false); },
          onstreamcompleted: function () { playFailed(ch); },
          onerror: function () { playFailed(ch); }
        });
        av.prepareAsync(function () { try { av.play(); } catch (e) {} playing(); }, function () { playFailed(ch); });
      } catch (e) { playFailed(ch); }
    } else {
      video.src = ch.url;
      var p = video.play();
      if (p && p.catch) p.catch(function () {});
    }
  }

  function playing() { retries = 0; loading(false); message(''); hideSplash(); }

  function playFailed(ch) {
    if (!S.cur || S.cur.url !== ch.url) return;
    loading(false);
    if (retries < 2) {
      retries++;
      clearTimeout(retryTimer);
      retryTimer = setTimeout(function () { if (S.cur && S.cur.url === ch.url) start(ch); }, 2000 * retries);
    } else {
      hideSplash();
      message('Не удалось включить «' + ch.name + '».\nНажмите ОК, чтобы попробовать снова, или ▲▼ — другой канал.');
    }
  }

  video.addEventListener('playing', playing);
  video.addEventListener('waiting', function () { loading(true); });
  video.addEventListener('error', function () { if (S.cur && video.getAttribute('src')) playFailed(S.cur); });

  function play(ch) {
    S.cur = ch;
    retries = 0;
    save('last', ch.url);
    start(ch);
    showInfo();
  }

  function step(dir) {
    var list = S.playList.length ? S.playList : S.channels;
    if (!list.length) return;
    var i = list.indexOf(S.cur);
    play(list[i < 0 ? 0 : (i + dir + list.length) % list.length]);
  }

  // ---------- плашка, сообщения ----------
  var infoTimer = null, toastTimer = null, numTimer = null, digits = '';

  function showInfo() {
    var ch = S.cur;
    if (!ch || S.zone !== 'play') return;
    var d = new Date();
    $('infoNum').textContent = ch.number;
    $('infoGroup').textContent = ch.group;
    $('infoName').textContent = ch.name;
    $('infoLogo').innerHTML = logoHtml(ch);
    $('infoClock').textContent = ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);
    cls($('info'), 'on', true);
    clearTimeout(infoTimer);
    infoTimer = setTimeout(function () { cls($('info'), 'on', false); }, 5000);
  }

  function toast(text) {
    $('toast').textContent = text;
    cls($('toast'), 'on', true);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { cls($('toast'), 'on', false); }, 3000);
  }

  function message(text) { $('msg').textContent = text; cls($('msg'), 'on', !!text); }

  var splashGone = false, bornAt = Date.now();
  function hideSplash() {
    if (splashGone) return;
    var wait = 1500 - (Date.now() - bornAt);
    if (wait > 0) { setTimeout(hideSplash, wait); return; }
    splashGone = true;
    cls($('splash'), 'off', true);
    setTimeout(function () { cls($('splash'), 'gone', true); }, 400);
    showInfo();
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
      S.menuId = 'all';
      save('group', 'all');
      play(ch);
    }, 1500);
  }

  // ---------- экран настройки ----------
  function setStatus(text, isError) { $('setupStatus').textContent = text; cls($('setupStatus'), 'err', !!isError); }

  function renderSetup() {
    cls($('url'), 'sel', S.si === 0);
    cls($('save'), 'sel', S.si === 1);
    cls($('cancel'), 'sel', S.si === 2);
    $('cancel').style.display = S.channels.length ? '' : 'none';
  }

  function openSetup() {
    closePanel();
    cls($('info'), 'on', false);
    splashGone = true;
    cls($('splash'), 'gone', true);
    S.zone = 'setup';
    S.si = S.url ? 1 : 0;
    $('url').value = S.url;
    $('version').textContent = 'IPTV для Samsung · версия ' + VERSION;
    cls($('setup'), 'on', true);
    renderSetup();
  }

  function closeSetup() {
    $('url').blur();
    cls($('setup'), 'on', false);
    S.zone = 'play';
    showInfo();
  }

  function saveSetup() {
    var url = $('url').value.replace(/^\s+|\s+$/g, '');
    if (!/^https?:\/\//i.test(url)) { setStatus('Ссылка должна начинаться с http:// или https://', true); S.si = 0; renderSetup(); return; }
    if (url !== S.url) {
      S.url = url;
      save('url', url);
      save('m3u', '');
      S.channels = []; S.cur = null; S.playList = [];
      stopPlayer();
    }
    refresh(true);
  }

  // ---------- пульт ----------
  var enterTimer = null, lastBack = 0;

  function exitApp() {
    try { window.tizen.application.getCurrentApplication().exit(); } catch (e) { toast('Нажмите кнопку «Домой» на пульте'); }
  }

  function onBack() {
    if (S.zone === 'setup') { if (S.channels.length) closeSetup(); else exitApp(); return; }
    if (S.zone !== 'play') { closePanel(); showInfo(); return; }
    if (Date.now() - lastBack < 2000) { exitApp(); return; }
    lastBack = Date.now();
    toast('Нажмите «Назад» ещё раз, чтобы выйти');
  }

  function keySetup(code, e) {
    var typing = document.activeElement === $('url');
    if (typing) {
      if (code === KEY.ENTER || code === KEY.IME_DONE || code === KEY.DOWN) { $('url').blur(); S.si = 1; renderSetup(); e.preventDefault(); }
      else if (code === KEY.IME_CANCEL) $('url').blur();
      return;
    }
    var max = S.channels.length ? 2 : 1;
    if (code === KEY.UP) S.si = 0;
    else if (code === KEY.DOWN && S.si === 0) S.si = 1;
    else if (code === KEY.RIGHT && S.si >= 1) S.si = Math.min(max, S.si + 1);
    else if (code === KEY.LEFT && S.si >= 1) S.si = Math.max(1, S.si - 1);
    else if (code === KEY.ENTER) {
      e.preventDefault();
      if (S.si === 0) $('url').focus();
      else if (S.si === 1) saveSetup();
      else closeSetup();
    }
    renderSetup();
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
    var same = S.cur && S.cur.url === ch.url;
    closePanel();
    if (!same || $('msg').className) play(ch); else showInfo();
  }

  function keyChannels(code) {
    if (code === KEY.UP) {
      if (S.ci === 0 && S.menuId === 'search') { S.zone = 'search'; renderPanel(); $('q').focus(); return; }
      S.ci = Math.max(0, S.ci - 1);
    } else if (code === KEY.DOWN) S.ci = Math.min(S.list.length - 1, S.ci + 1);
    else if (code === KEY.CH_UP) S.ci = Math.max(0, S.ci - 6);
    else if (code === KEY.CH_DOWN) S.ci = Math.min(S.list.length - 1, S.ci + 6);
    else if (code === KEY.LEFT) { S.zone = 'groups'; S.gi = menuIndex(S.menuId); }
    else return;
    renderPanel();
  }

  document.addEventListener('keydown', function (e) {
    var code = e.keyCode;
    if (code === KEY.BACK || code === KEY.ESC) { e.preventDefault(); onBack(); return; }
    if (S.zone === 'setup') { keySetup(code, e); return; }
    if (S.zone === 'search') { keySearch(code, e); return; }
    if (S.zone === 'groups') { keyGroups(code); return; }
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
      else if ($('msg').className && S.cur) { retries = 0; start(S.cur); }
      else openPanel();
    } else if (code === KEY.LEFT) openPanel();
    else if (code === KEY.RIGHT) showInfo();
    else if (code === KEY.UP || code === KEY.CH_UP) step(1);
    else if (code === KEY.DOWN || code === KEY.CH_DOWN) step(-1);
  });

  document.addEventListener('keyup', function (e) {
    if (e.keyCode !== KEY.ENTER || !enterTimer) return;
    var held = enterTimer === -1;
    if (!held) clearTimeout(enterTimer);
    enterTimer = null;
    if (!held && S.zone === 'channels') chooseChannel();
  });

  $('q').addEventListener('input', function () { applySearch(); renderPanel(); });

  // Телевизор выключили или свернули приложение — останавливаем поток; вернулись — включаем тот же канал.
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) stopPlayer();
    else if (S.cur) start(S.cur);
  });

  // ---------- запуск ----------
  try {
    var keys = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'ChannelUp', 'ChannelDown'];
    for (var k = 0; k < keys.length; k++) window.tizen.tvinputdevice.registerKey(keys[k]);
  } catch (e) { /* не телевизор */ }

  setTimeout(hideSplash, 8000);
  if (!S.url) {
    splashGone = true;
    cls($('splash'), 'gone', true);
    openSetup();
  } else {
    var cached = load('m3u', '');
    if (cached) applyPlaylist(parseM3U(cached)); // мгновенный старт с сохранённого списка
    refresh(false);
  }
})();

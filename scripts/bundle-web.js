#!/usr/bin/env node
/*
 * Собирает веб-версию в один файл: разметка, стили и код.
 *   node scripts/bundle-web.js <версия> <файл.json> [base.js]
 *
 * <файл.json> — iptv-web.json, который прикладывается к релизу: установленные приложения на компьютерах,
 * Samsung и LG скачивают его и обновляются сами. base.js — тот же набор для пакета (с него приложение
 * запускается, пока не скачало ничего новее).
 */
'use strict';
const fs = require('fs');
const path = require('path');

const [version, jsonOut, baseOut] = process.argv.slice(2);
if (!version || !jsonOut) {
  console.error('Использование: node scripts/bundle-web.js <версия> <файл.json> [base.js]');
  process.exit(1);
}
const root = path.join(__dirname, '..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

const page = read('web/index.html');
const body = /<body[^>]*>([\s\S]*)<\/body>/i.exec(page);
if (!body) throw new Error('В web/index.html не найден <body>');
// Скрипты подключает загрузчик, поэтому из разметки их убираем
const html = body[1].replace(/<script\b[^>]*>[\s\S]*?<\/script>\s*/gi, '').trim();

let app = read('web/app.js');
if (!/var VERSION = '[^']*'/.test(app)) throw new Error('В web/app.js не найдена строка с версией');
app = app.replace(/var VERSION = '[^']*'/, "var VERSION = '" + version + "'");
const shell = /var NEEDS_SHELL = (\d+)/.exec(app);
if (!shell) throw new Error('В web/app.js не найдена строка NEEDS_SHELL');
const boot = /var SHELL = (\d+)/.exec(read('web/boot.js'));
if (!boot || +boot[1] < +shell[1]) throw new Error('SHELL в web/boot.js меньше NEEDS_SHELL в web/app.js');

const bundle = {
  format: 1,
  version: version,
  shell: +shell[1],
  css: read('web/style.css'),
  html: html,
  js: read('shared/plugin-runtime.js') + '\n' + app,
};
const json = JSON.stringify(bundle);
fs.mkdirSync(path.dirname(path.resolve(jsonOut)), { recursive: true });
fs.writeFileSync(jsonOut, json);
// В JS-файле разделители строк U+2028/2029 внутри строки недопустимы на старых телевизорах
if (baseOut) fs.writeFileSync(baseOut, 'window.__iptvBase = ' + json.replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029') + ';\n');
console.log('Версия ' + version + ': ' + Math.round(json.length / 1024) + ' КБ');

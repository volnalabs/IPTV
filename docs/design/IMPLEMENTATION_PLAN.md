# План реализации редизайна «Тихий эфир»

Источник истины — `docs/design/handoff/SPEC.md`. Расхождения с кодом записываются в `docs/design/DEVIATIONS.md`,
результат приёмки — в `docs/design/QA.md`. Архитектура не меняется: `app/` — Kotlin Views + Media3,
`web/` — один `app.js` (ES5), `style.css`, `index.html`; `shared/plugin-runtime.js` — общий рантайм.

## Порядок и коммиты

| Этап | Web (`web/`, `shared/`) | Android (`app/`) |
| --- | --- | --- |
| 1. Основа | токены готовыми значениями, Onest base64 WOFF, 48 иконок, нейтральная плитка, `body.lite`, подсказки | `colors.xml`, `dimens.xml`, `styles.xml`, `res/font`, `ic_*.xml`, `LetterDrawable` → нейтральная плитка |
| 2. Гид ТВ | `#guide`: шапка, меню, плитки, подробности, дата, меню плейлистов и действий | `activity_main.xml`, `MainActivity`, `Adapters.kt` |
| 3. Плеер | zap 400 мс, панель, OSD, запись, номер, громкость, `#state` | `infoBar`, панель, состояния, `numberPad` |
| 4. Мини-гид, сетка, поиск | зоны `mini`, `grid`, `search`/`results` | новые View |
| 5. Настройки | `#setup`: разделы, плейлисты, форма, миграция | `SettingsActivity`, `Prefs`, `Repo` |
| 6. Плагины | список, карточка, установка, удаление с «Вернуть», `host.error` | `PluginsActivity`, `Plugins.kt` |
| 7. Mobile/Tablet | — | layout-варианты телефона и планшета |
| 8. Desktop | `body.desk`, брейкпоинты, мышь, клавиатура | — |
| 9. Проверка | `QA.md`, скриншоты | `ScreenshotTest`, `UiFlowTest` |

В web гид, плеер, поиск и настройки завязаны на общие зоны `S.zone` и общий рендер, поэтому этапы 2–6
для web пишутся одним переписыванием `app.js`/`style.css`/`index.html` и коммитятся вместе; Android идёт
отдельными коммитами после web. Это отступление от «каждый этап — отдельный коммит» записано в `DEVIATIONS.md`.

## По разделам SPEC

### 1. Карта экранов → зоны
`S.zone`: `play`, `ctl`, `mini`, `grid`, `ctx` (меню действий), `plm` (меню плейлистов), `head` (заголовок-плейлист),
`groups`, `channels`, `epg`, `date`, `search`, `results`, `setup` (разделы), `pane` (содержимое раздела и формы),
`onb` (первый запуск), `dialog`. Каждый слой помнит зону, из которой открыт (`back`), «Назад» снимает один слой.

### 2. Токены
- `web/style.css` — значения подставлены напрямую, без `var()`; `@font-face` Onest в base64 WOFF (8 файлов, ≈150 КБ).
  `SHELL`/`NEEDS_SHELL` не меняются. Риск: размер бандла в `localStorage` (≈ +200 КБ) — проверяется в QA.
- Android — `colors.xml`/`dimens.xml` из пакета, `res/font/onest_*.ttf` (из `@fontsource/onest`, OFL), `styles.xml`.

### 3. Компоненты (web: функции в `app.js`)
- 3.1 Гид: `buildMenu`, `renderMenu`, `renderTiles`, `renderDetails`, `renderHints`, `openPop` (G1/G3).
  Плитки — 4–5 абсолютно позиционированных узлов, фокус на второй позиции.
- 3.2 Строка `ip-ch`: `chRow()` — мини-гид, поиск, номер, список Desktop.
- 3.3 Мини-гид: `openMini`, `renderMini`, `keyMini`.
- 3.4 Сетка: `openGrid`, `renderGrid`, `keyGrid` — ≤ 6 строк × ≤ 6 ячеек, абсолютные координаты, якорь времени.
- 3.5 Поиск: своя клавиатура `renderKb`, `runSearch` (каналы, передачи, записи, группы, плейлисты), подсветка `hl()`.
- 3.6 Плеер: `renderOsd`, `renderCtl`, `renderTimeline`, `showVol`, `renderNum`, `setState`.
- 3.7–3.8 Настройки и плагины: единый список элементов панели `paneItems()` с рядами, `renderPane`, `keyPane`.
- 3.9 Диалоги: `openDialog({title,text,buttons,focus})` — обновление, удаление плагина и плейлиста.
- 3.10 Тосты: `toast(text, kind, action)`; метки «новое» — `isNew(key)`/`markSeen(key)`, хранение `seen`.
- 3.11 Первый запуск O1/O2 и пустые состояния.

### 4. Плеер
`S.cur` — играющий канал, `S.sel` — выбранный ▲▼; `zap()` ставит таймер 400 мс и только потом зовёт `play()`.
OK — панель (`ctl`), ◀ — мини-гид, ▶ — программа канала, CH LIST — гид. Состояния 4.4 — блок `#state`
(`role="alert"`); код 403/404 берётся из hls.js, на AVPlay и `<video src>` — общая ошибка.
Риски: громкость Samsung/LG (нельзя проверить без устройства), коды AVPlay.

### 5. Настройки
`playlists`/`activePl` в `localStorage`; миграция `url`/`epgurl` → «Мой плейлист»; `url`/`epgurl`/`m3u` держатся
равными активному. Форма: `checkAndSave()` сначала грузит и разбирает, сохраняет только при успехе.
«Воспроизведение» в web не показывается.

### 6. Плагины
`shared/plugin-runtime.js`: `host.error(id, hook, text, source)` из `safe()`, `hasPlaylist/hasStream` в `list()` —
обратно совместимо (старый хост без `error` игнорируется). Web: `lastError`, `author` в записи плагина,
удаление через диалог + отложенное на 6 с. Android: `@JavascriptInterface error`, `Plugins.Item` + поля.

### 7. Платформы
- ТВ-web: `fit()`; `body.lite` по UA (Tizen ≤ 3.0, Chrome/38).
- Desktop: `body.desk` (окно), `fit()` только в полном экране; раскладка по ширине классами `w1680/w1360/w1024/w640`
  (media queries допустимы, но классы проще проверять); список каналов строками `ip-ch`.
- Android TV / Mobile / Tablet: layout-варианты `layout/`, `layout-sw600dp/`, `layout-w900dp/`.

### 8. Поведение
Таблицы 8.1–8.2 реализуются в `keyGuide*` и `renderHints`; удержание OK — существующий `enterTimer` (650 мс),
действие — меню G3. Мышь: делегированный обработчик по `data-z`/`data-i`, ПКМ — меню действий.

### 10. Техническое
ES5 проверяется `acorn --ecma5`; запрещённый CSS — `grep` из задания. Описание передач `d[]` — только
«сегодня ± 1 день», до 400 символов.

## Риски
1. Объём: Android TV + Mobile — фактически новый `MainActivity`; делается после web.
2. Режим окна Desktop — второй набор стилей на той же логике зон.
3. Samsung/LG: громкость, AVPlay, производительность Tizen 2.4 / webOS 3 — только на живых устройствах.
4. Скриншотные тесты Android переснимаются под новый интерфейс.

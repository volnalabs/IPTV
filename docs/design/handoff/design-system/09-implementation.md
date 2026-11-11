# Внедрение в Android и веб

Система не требует смены архитектуры: Android остаётся на нативных View (Kotlin, Media3), Samsung/LG/компьютер — на общем вебе (ES5) в Electron и в пакетах ТВ.

## Один источник значений

`tokens.json` — единственный источник. Скрипт сборки (рядом с `scripts/build-web.sh`) генерирует:

- **Android:** `res/values/colors.xml` (имена токенов через подчёркивание: `surface_1`, `text_primary`), `res/values/dimens.xml` (отступы, размеры, радиусы в dp), `res/font/onest.xml` + файлы Onest.
- **Веб для ТВ:** CSS с подставленными значениями (без `var()`): Samsung и LG 2016–2017 не знают CSS-переменных. Токены — константы в шаблоне стилей, которые сборка заменяет на значения.
- **Компьютер (Electron, современный Chromium):** можно оставить `var(--…)` из `tokens.css` и отдельную таблицу стилей с grid для адаптивной раскладки.

Цвета в Kotlin-коде (`0xFFFF7A45` в `Widgets.kt`, `0xFFD2501C` в `MainActivity.kt`) и hex-значения в `style.css` заменяются ссылками на токены.

## Соответствие текущим файлам

| Сейчас | Становится |
|---|---|
| `colors.xml`: `accent #FF7A45`, `accent2 #FF5470`, `on_air #E5484D`, `archive #7FD6A8`, `star #FFC24A` | `accent`, `live`; зелёный и жёлтый уходят, `star` → `text-secondary` |
| `progress_bar.xml`, `button_primary_bg.xml` (градиенты) | Плоские `progress-fill` / `progress-live`, `paper` |
| `column_bg.xml`, `column_focus_bg.xml`, `sheet_bg.xml`, `info_bg.xml`, `card_bg.xml` (радиус 22, обводка) | `surface-1` без обводки, разделители `line-subtle`; плавающее — `surface-2` + `radius-lg` |
| `item_bg.xml` (фокус `#F5F3EE`, радиус 14) | `focus-fill` (`paper`), радиус `radius-md` (12 холста) |
| `LetterDrawable` и `COLORS` в `app.js` | Одна нейтральная заглушка ChannelLogo |
| `ToggleView` (оранжевый трек) | Toggle на `paper` |
| `item_hint.xml`, `setHints()` | KeyHint, не больше трёх |
| `infoBar` / `#info` | PlayerInfo + новый PlayerControls |
| `fit()` в `app.js` для Electron | Адаптивная раскладка (раздел «Сетка»), `fit()` остаётся только для ТВ |
| `style.css`: 22 размера шрифта | Шкала `tv-*` / `desk-*` |

## Шрифт

- Android: Onest TTF (OFL) в `res/font`, `fontFamily="@font/onest"` в `AppTheme`, `fontFeatureSettings="tnum"` у номеров и времени.
- Веб для ТВ: Onest в пакете, `@font-face` в `style.css`: WOFF2 и запасной WOFF (поддержку WOFF2 проверить на телевизорах 2016–2017 годов).
- Компьютер: тот же файл из пакета; Google Fonts — только для витрины системы.

## Ограничения старых ТВ (сохранить)

ES5 в скриптах; в CSS — без переменных, `grid`, `gap`, `backdrop-filter`, `:focus-visible`, `inset`; раскладка на flex с отступами `margin`. Фокус пульта на вебе рисуется классом (`is-focus` / сегодняшний `sel`), а не псевдоклассом.

## Порядок работы

1. Сгенерировать ресурсы из `tokens.json` и подключить шрифт — без изменения раскладок.
2. Заменить фоны, радиусы, градиенты и цвета (раздел «Правильно и неправильно»).
3. Переделать строки (ChannelRow, ProgrammeRow, SettingsRow) и логотипы.
4. Новые слои: PlayerControls, ContextMenu, Dialog удаления, состояния ошибок.
5. Платформенные раскладки: Electron, телефон, планшет.

Шаги 4–5 зависят от решений этапа 1 аудита; шаги 1–3 можно делать сразу.

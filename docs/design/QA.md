# Приёмка редизайна «Тихий эфир»

Чек-лист — `docs/design/handoff/SPEC.md`, раздел 11. Отступления — `docs/design/DEVIATIONS.md`.

**Как проверялось**

- **Web (Samsung, LG, Desktop)** — сборки `scripts/build-web.sh samsung|lg|desktop`, `scripts/bundle-web.js`; приложение из собранной оболочки (`index.html` + `boot.js` + `base.js`) в Chromium (Playwright) с локальным M3U (162 и 1000 каналов) и XMLTV с `<desc>`. Режим телевизора — признак `PalmSystem` (как на LG), окна 1920×1080 и 1280×720; режим окна — 1920×1080, 1440×900, 1280×800, 1024×768, 800×600, 640×600. Сценарии проходились клавишами (стрелки, Enter, Escape, цифры, цветные кнопки кодами пульта).
- **Android TV, телефон, планшет** — `./gradlew :app:assembleDebug :app:testDebugUnitTest` (Robolectric): `UiFlowTest` — ключевые сценарии SPEC 1 пультом; `ScreenshotTest` — снимки ТВ (960×540 dp), телефона 360 и 390 dp вертикально и горизонтально, планшета 768 и 1280 dp.
- **ES5 и CSS** — `npx acorn --ecma5 web/app.js`, `shared/plugin-runtime.js` — без ошибок; запрещённый CSS (`grep` из задания) — пусто.
- **Живые устройства не проверялись** — см. список в конце.

**Снимки** (сверка с `docs/design/handoff/screens/`): web — `docs/design/qa/web/`, Android — `docs/design/qa/android/`.

| Эталон | Web | Android |
| --- | --- | --- |
| TV-Guide | `web/tv-guide.jpg`, `web/tv720-guide.jpg` | `android/tv-20-guide.jpg`, `android/tv-24-settings-button.jpg` |
| TV-Guide-Context | `web/tv-actions.jpg` | `android/tv-22-actions.jpg` |
| Прототип ТВ: меню плейлистов, строка даты | `web/tv-playlists-menu.jpg`, `web/tv-date.jpg` | `android/tv-23-menu.jpg` |
| TV-Player-Info, TV-Player-Zap, TV-Player-Number (панели в эфире и мини-гида больше нет — DEVIATIONS) | `web/tv-osd.jpg`, `web/tv-zap.jpg`, `web/tv-number.jpg`, `web/tv-number-none.jpg` | `android/tv-10-osd.jpg`, `tv-12-zap.jpg`, `tv-13-number.jpg` |
| TV-Archive, TV-Player-States | `web/tv-archive.jpg`, `web/tv-error.jpg` | `android/tv-33-archive.jpg`, `tv-34-error.jpg` |
| TV-EPG-Grid | `web/tv-grid.jpg` | `android/tv-30-grid.jpg` |
| TV-Search, TV-Search-Results | `web/tv-search-keyboard.jpg`, `web/tv-search-results.jpg` | `android/tv-31-search.jpg`, `android/tv-31-results.jpg` |
| TV-Settings, TV-Settings-Playlists, TV-Playlist-Edit / States | `web/tv-settings.jpg`, `web/tv-settings-playlists.jpg`, `web/tv-playlist-error.jpg`, `web/tv-app.jpg` | `android/tv-40-settings.jpg`, `tv-41-playlist-error.jpg`, `tv-42-playback.jpg`, `tv-47-app.jpg` |
| TV-Plugins, TV-Plugin-Detail, TV-Plugin-Delete, TV-Plugin-Add | `web/tv-plugins.jpg`, `web/tv-plugin-card.jpg`, `web/tv-plugin-delete.jpg`, `web/tv-plugin-undo.jpg`, `web/tv-plugin-add.jpg` | `android/tv-43-plugins.jpg`, `tv-44-plugin.jpg`, `tv-45-plugin-delete.jpg`, `tv-46-plugin-add.jpg` |
| TV-Onboarding, TV-Onboarding-Web | `web/tv-onboarding.jpg` | `android/tv-01-onboarding.jpg`, `tv-02-onboarding-field.jpg` |
| Desk-Guide, Desk-1280, Desk-1920, Desk-Search | `web/desk-1440-guide.jpg`, `desk-1280-guide.jpg`, `desk-1920-guide.jpg`, `desk-1024-guide.jpg`, `desk-800-guide.jpg`, `desk-1440-search.jpg`, `desk-1440-context.jpg`, `desk-1440-settings.jpg` | — |
| Phone-Guide, Phone-360, Phone-Programme, Phone-Player, Phone-Settings | — | `android/phone-10-channels.jpg`, `phone-12-channels-360.jpg`, `phone-11-programme.jpg`, `phone-13-landscape.jpg`, `phone-20-settings.jpg`, `phone-01-onboarding.jpg` |
| Tablet-768, Tablet-1024 | — | `android/tablet-768.jpg`, `android/tablet-1280.jpg` |

Результат: ✅ выполнено и проверено · ⚠️ выполнено с оговоркой (см. примечание) · 🔲 нужно проверить на устройстве.

## Визуальная система

| Пункт | Платформа | Как проверено | Результат |
| --- | --- | --- | --- |
| Цвета только из токенов; в `style.css` нет `var()`; в Android нет `#FF7A45`, `#0B0C0F`, `#7FD6A8`, `#FFC24A` | Web, Android | `grep` по `web/style.css` и `app/src/main`; цвета Android — `colors.xml` из пакета и `C` в `Ui.kt` | ✅ |
| Медь только у «сейчас» | Все | Просмотр снимков: «Эфир», играющий канал (кольцо, значок), прогресс текущей передачи, линия «сейчас», точка группы. Фокус — «бумага» | ✅ |
| Onest, табличные цифры, на ТВ нет текста мельче 22 px холста | Web, Android TV | Onest WOFF в `style.css`, `res/font/onest*`; `font-variant-numeric: tabular-nums`, `fontFeatureSettings = "tnum"`; мельче 22 px только надпись на кнопке-подсказке 19 px и метка «новое» 20 px — так задано в SPEC 3.1 и 3.10 | ✅ / ⚠️ Android 5–7 — системный шрифт (DEVIATIONS) |
| Второстепенный текст на видео не темнее `text-secondary` | Все | OSD, панель, подсказки на видео — `#B8B5AF` и светлее | ✅ |
| Нет цветных буквенных плашек | Все | `COLORS` и цветной `LetterDrawable` удалены; буквы на `#1F1F22` / `#36363B` | ✅ |
| «Новое» помечено и пропадает после первого использования | Все | Флаги `seen` (web) / `seen_<feature>` (Android): плейлисты, недавние, описание, громкость, сетка, поиск передач, что может плагин | ✅ |

## Фокус и пульт (Android TV, Samsung, LG)

| Пункт | Платформа | Как проверено | Результат |
| --- | --- | --- | --- |
| Фокус один и виден; плитка — кольцо 6 px «бумаги», строки и кнопки — заливка; без масштаба | Web ТВ, Android TV | Снимки гида, меню, настроек | ✅ |
| Играющий канал отличим от фокуса | Web ТВ, Android TV | Кольцо `accent-ring` 4 px + значок «играет» (`tv-guide.jpg`, `tv-20-guide.jpg`) | ✅ |
| Переходы 8.1; ▲ с «Поиска» → заголовок-плейлист; ▲ с первой передачи → строка даты | Web ТВ, Android TV | Playwright-сценарии; `UiFlowTest.playlists`, `archiveAndFavourites` | ✅ |
| «Назад» снимает один слой; в чистом плеере — тост и выход за 2 с | Web ТВ, Android TV | Сценарии web; `UiFlowTest.searchAndGrid` | ✅ |
| Удержание OK 650 мс — меню действий в гиде, поиске, сетке, плеере; зелёная — избранное сразу | Web ТВ, Android TV | `tv-actions.jpg`; `UiFlowTest.archiveAndFavourites` | ✅ / 🔲 keyup/keydown по моделям ТВ |
| Подсказки по зонам 8.2, новый вид кнопок | Web ТВ, Android TV | Снимки всех зон | ✅ |
| Нет тупиков из пустых состояний | Все | Пустое избранное и недавние — фокус в меню; «нет каналов» — кнопки «Изменить ссылку», «Обновить»; нет программы — ▶ не уводит | ✅ |

## Гид

| Пункт | Платформа | Как проверено | Результат |
| --- | --- | --- | --- |
| Колонки 450 + 56 / 520 / остаток | Web ТВ, Android TV | Разметка (px холста; Android ÷ 2 в dp-масштабе холста) | ✅ |
| Фокус плиток на второй позиции, в DOM ≤ 5 плиток | Web ТВ, Android TV | 1000 каналов, 120 нажатий ▼ подряд по 33 мс: 120 шагов без пропусков, плиток в DOM — 4; `UiFlowTest.guideZapNumber` | ✅ / 🔲 Tizen 2.4, webOS 3 |
| Программа: 5 строк, прогресс 6 px, значок записи, описание до 4 строк только при `<desc>` | Web ТВ, Android TV | `tv-programme.jpg`, `tv-21-programme.jpg`; канал без `<desc>` — строки нет | ✅ |
| Меню плейлистов и действий по 3.1 | Web ТВ, Android TV | `tv-playlists-menu.jpg` (не загрузившийся — серым, пропускается), `tv-actions.jpg` | ✅ |

## Плеер

| Пункт | Платформа | Как проверено | Результат |
| --- | --- | --- | --- |
| OSD 5 с со стрелками ◀ ▶; OK и ◀ — гид, ▶ — программа; ▲▼ — поток через 400 мс (10 нажатий = 1 запуск) | Web, Android TV | web: счётчик `loadstart` = 1 после 10 ▲; Android: `UiFlowTest.guideZapNumber` (`sel` меняется, `cur` — через 400 мс) | ✅ |
| OSD не перекрывается тостом, громкостью, шкалой записи | Web ТВ, Android TV | Тост в плеере вверху; OSD поднимается до 200/240 при панели | ✅ |
| Метка «Запись · …» видна всё время; ◀▶ ±30 с, OK пауза, «Назад» — эфир с тостом | Web, Android TV | `tv-archive.jpg`; `UiFlowTest.archiveAndFavourites` | ✅ |
| Состояния 4.4; 403/404 без автоповтора | Web, Android | Загрузка, буфер, «Канал не включился», «Канал недоступен» (hls.js и Media3), «Есть звук, нет изображения» | ✅ / 🔲 коды AVPlay (Samsung не отдаёт — общая ошибка) |
| Ввод номера: подсказки, таймеры 3/1/2 с, ◀ стирает, «Назад» отменяет | Web, Android TV | `UiFlowTest.guideZapNumber` («1» → 1, 10, 11, 12; «99» — «Канала с номером 99 нет», закрытие через 2 с) | ✅ |
| Громкость: шкала 2 с и «Без звука» где возможно | Desktop, Android TV, Samsung, LG | Desktop — ползунок и M; Android — системная громкость и шкала; LG — `video.muted` и значок; Samsung — только через `tizen.tvaudiocontrol` | ✅ / 🔲 Samsung и LG |

## Поиск и сетка

| Пункт | Платформа | Как проверено | Результат |
| --- | --- | --- | --- |
| Разделы, до 5 в каждом, подсветка подчёркиванием (`border-bottom`) | Web, Android | `tv-search-results.jpg`, `desk-1440-search.jpg`, `tv-31-search.jpg` | ✅ |
| Поиск передач только по загруженной программе; статус во время загрузки | Web, Android | Разделы «Передачи», «Записи»; при загрузке — «Телепрограмма загружается — передачи появятся через минуту» | ✅ |
| Сетка: ≤ 40 ячеек, «до HH:MM», линия «сейчас», строка фокуса | Web ТВ, Android TV, планшет | `tv-grid.jpg`, `tv-30-grid.jpg`; `UiFlowTest.searchAndGrid` проверяет ≤ 40 ячеек | ✅ / ⚠️ Desktop — ТВ-сетка в масштабе окна |

## Настройки и плагины

| Пункт | Платформа | Как проверено | Результат |
| --- | --- | --- | --- |
| Разделы по платформам; «Воспроизведение» только на Android | Web, Android | `tv-settings.jpg` (без «Воспроизведения»), `tv-42-playback.jpg` | ✅ |
| Новая ссылка заменяет список только после проверки; при ошибке — прежний список и причина | Web, Android | 404 → «Сервер ответил 404 — по этой ссылке плейлиста нет. Работает прежний список: N каналов.»; `UiFlowTest.playlists` | ✅ |
| Миграция в «Мой плейлист»; `url`/`epgurl` синхронны с активным | Web, Android | Старый `iptv_url` → «Мой плейлист»; смена активного обновляет `iptv_url` / `playlist_url` (`UiFlowTest.playlists`) | ✅ |
| Плагины: «Добавить» в конце, статусы, карточка, ошибка запуска, удаление с диалогом и «Вернуть» 6 с; web — перезапуск при выходе | Web, Android | `tv-plugin-*.jpg`; `UiFlowTest.pluginDelete`; `host.error` в рантайме, `hasPlaylist/hasStream` в `list()` | ✅ |
| Нет каталога, настроек плагинов, сортировки, истории поиска, субтитров, дорожек, тем, родительского контроля | Все | Просмотр кода и экранов | ✅ |

## Платформы и производительность

| Пункт | Платформа | Как проверено | Результат |
| --- | --- | --- | --- |
| ТВ 1280×720, 1920×1080, 3840×2160 — ничего не обрезано в безопасных полях | Web ТВ | 720p и 1080p — снимки; 4K — тот же холст ×2 через `fit()` | ✅ / 🔲 4K на устройстве |
| Desktop 1920/1440/1280/1024/640: раскладки 7.6, Tab и стрелки, кольцо фокуса только с клавиатуры | Desktop | Снимки `desk-*`; класс `keys` только после клавиш, мышь его снимает | ✅ / ⚠️ настройки и сетка — ТВ-холст в окне |
| Телефон 360 без горизонтальной прокрутки; цели ≥ 48 dp; горизонтально — плеер на весь экран | Android | `phone-12-channels-360.jpg`, `phone-13-landscape.jpg` | ✅ |
| Планшет ≥ 900 dp — список и программа рядом | Android | `tablet-1280.jpg` | ✅ |
| Samsung 2016–2017 и LG webOS 3: `body.lite`, 1000 каналов без пропуска нажатий | Samsung, LG | `body.lite` по UA (Tizen ≤ 3.0, Chrome/Chromium 3x–4x на webOS); производительность — только в Chromium | 🔲 |
| Обновление через `boot.js` и откат | Web | Обновление 1.0.5 в `iptv_bundle` запускается (`updated: true`); сломанное 1.0.6 — откат на базовую (`failed: true`); `url`, `favs`, `auto` сохраняются; бандл 409 КБ (лимит хранилища ~5 МБ) | ✅ |

## Проверить на настоящих телевизорах

1. **Громкость Samsung**: доступна ли `tizen.tvaudiocontrol` с привилегией `tv.audio` (добавлена в `samsung/config.xml`); если нет — своё «Без звука» не работает, громкостью управляет телевизор, это ожидаемо.
2. **Громкость LG**: приходят ли VOL±/MUTE в приложение; сейчас наша шкала не показывается, «Без звука» — `video.muted`.
3. **Коды ошибок AVPlay**: Samsung не отдаёт код ответа — 403/404 там выглядят как «Канал не включился» с двумя повторами.
4. **Производительность Tizen 2.4 и webOS 3**: удержание ▼ в гиде на 1000 каналах, открытие сетки, поиск по программе; `body.lite` должен включаться автоматически.
5. **Удержание OK** на разных пультах Samsung/LG (порядок keydown/keyup).
6. **Шрифт Onest на Tizen 2.4** (WOFF в base64) и запасной SamsungOne.
7. **Android TV**: CH LIST (кнопка «Содержание»), шкала громкости при фиксированной громкости (HDMI-CEC), `api.toast()` плагинов в своём тосте.
8. **Electron**: F11 и Esc в установленной программе со старой оболочкой (`main.js` обновится только с новым установщиком).
9. **Android TV, поиск**: экранная клавиатура приложения, системная не появляется; буквы с пульта-клавиатуры попадают в строку.
10. **Номера и стрелки**: на плейлисте с 1000+ каналов номер «1465» помещается в OSD и в колонку гида.
11. **Заставка**: канал идёт час без нажатий — заставка Android TV и Samsung не включается; на паузе записи включается как обычно.

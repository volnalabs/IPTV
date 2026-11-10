/*
 * Пример плагина IPTV: меняет адрес потока перед включением канала.
 * Здесь к каждому адресу дописывается параметр — так можно, например,
 * выбрать качество или сервер, если ваш провайдер это поддерживает.
 */
IPTV.plugin({
  id: 'stream-param',
  name: 'Параметр в адресе потока',
  version: '1.0',
  description: 'Дописывает ?quality=high к адресу каждого канала',
  author: 'volnalabs',

  start: function (api) {
    // Настройки плагина хранятся на устройстве и переживают перезапуск
    if (!api.storage.get('quality')) api.storage.set('quality', 'high');
  },

  stream: function (channel, api) {
    if (channel.archive) return channel.url; // записи не трогаем
    var separator = channel.url.indexOf('?') < 0 ? '?' : '&';
    return channel.url + separator + 'quality=' + api.storage.get('quality', 'high');
  }
});

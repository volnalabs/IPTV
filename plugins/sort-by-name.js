/*
 * Пример плагина IPTV: внутри каждой группы расставляет каналы по алфавиту.
 * Порядок самих групп остаётся таким же, как в плейлисте.
 */
IPTV.plugin({
  id: 'sort-by-name',
  name: 'Каналы по алфавиту',
  version: '1.0',
  description: 'Сортирует каналы по названию внутри каждой группы',
  author: 'volnalabs',

  playlist: function (channels) {
    var order = {};
    channels.forEach(function (channel) {
      if (!(channel.group in order)) order[channel.group] = Object.keys(order).length;
    });
    return channels.sort(function (a, b) {
      if (a.group !== b.group) return order[a.group] - order[b.group];
      return a.name.localeCompare(b.name, 'ru');
    });
  }
});

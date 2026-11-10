/*
 * Пример плагина IPTV: прячет ненужные группы каналов.
 * Впишите свои названия в список HIDE и подключите файл в приложении: Меню → Плагины.
 */
IPTV.plugin({
  id: 'hide-groups',
  name: 'Скрыть группы',
  version: '1.0',
  description: 'Убирает из списка группы каналов, перечисленные в файле плагина',
  author: 'volnalabs',

  playlist: function (channels, api) {
    var HIDE = ['Взрослые', 'Телемагазины'];
    var kept = channels.filter(function (channel) {
      return HIDE.indexOf(channel.group) < 0;
    });
    api.log('Скрыто каналов: ' + (channels.length - kept.length));
    return kept;
  }
});

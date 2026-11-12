using System.Net;
using System.Windows;

namespace Volna.TVInstaller;

public partial class MainWindow : Window
{
    private readonly InstallerService _service = new();
    private ConnectedTv? _connectedTv;
    private DownloadedPackage? _package;
    private bool _certificateReady;
    private bool _busy;

    public MainWindow()
    {
        InitializeComponent();
        IpBox.Text = string.Empty;
        Log("ВОЛНА TV Installer 0.1.0 запущен.");
        Log("Подсказка: IP телевизора можно посмотреть в Настройки → Общие → Сеть → Состояние сети.");
        SetButtonsEnabled(true);
    }

    private async void Connect_Click(object sender, RoutedEventArgs e)
    {
        if (!IPAddress.TryParse(IpBox.Text.Trim(), out var address))
        {
            MessageBox.Show("Введите корректный IP-адрес телевизора, например 192.168.1.25.",
                "Проверьте IP-адрес", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        if (!int.TryParse(PortBox.Text.Trim(), out var port) || port is < 1 or > 65535)
        {
            MessageBox.Show("Порт должен быть числом от 1 до 65535.", "Проверьте порт",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        await RunBusyAsync(async () =>
        {
            if (!_service.IsSdkInstalled)
            {
                var answer = MessageBox.Show(
                    "При первом запуске приложение загрузит официальный командный Tizen SDK Samsung и TV Extension. " +
                    "Графическая Tizen Studio устанавливаться не будет. Для SDK рекомендуется иметь около 15 ГБ свободного места. " +
                    "Продолжить?",
                    "Первоначальная настройка", MessageBoxButton.YesNo, MessageBoxImage.Question);
                if (answer != MessageBoxResult.Yes)
                    return;
            }

            ConnectionStatus.Text = "Подготовка инструментов…";
            ConnectionStatus.Foreground = (System.Windows.Media.Brush)FindResource("WarningBrush");
            await _service.PrepareToolchainAsync(Log);

            ConnectionStatus.Text = "Подключаюсь…";
            var device = await _service.ConnectAndGetDuidAsync(address.ToString(), port, Log);
            _connectedTv = device;
            DuidText.Text = "DUID: " + device.Duid + Environment.NewLine + "Устройство: " + device.Serial;
            ConnectionStatus.Text = "Подключён";
            ConnectionStatus.Foreground = (System.Windows.Media.Brush)FindResource("SuccessBrush");
            CertificateStatus.Text = "Телевизор подключён — можно создать сертификат.";
            InstallStatus.Text = "Телевизор готов к установке.";
            Log("Готово. Можно переходить к созданию сертификата.");
        });
    }

    private async void CreateCertificate_Click(object sender, RoutedEventArgs e)
    {
        if (_connectedTv is null)
        {
            MessageBox.Show("Сначала подключите телевизор и получите его DUID.", "Нет подключения",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        var profile = ProfileBox.Text.Trim();
        var author = AuthorBox.Text.Trim();
        var password = PasswordBox.Password;

        if (!System.Text.RegularExpressions.Regex.IsMatch(profile, @"^[A-Za-z0-9_-]{3,32}$"))
        {
            MessageBox.Show("Имя профиля: от 3 до 32 символов, только латинские буквы, цифры, дефис и подчёркивание.",
                "Проверьте имя профиля", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        if (author.Length < 2)
        {
            MessageBox.Show("Введите имя автора сертификата.", "Проверьте имя",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        if (password.Length < 8 || !password.Any(char.IsUpper) ||
            !password.Any(char.IsLower) || !password.Any(char.IsDigit))
        {
            MessageBox.Show("Пароль должен содержать не менее 8 символов, заглавную и строчную латинскую букву и цифру.",
                "Требования к паролю", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        await RunBusyAsync(async () =>
        {
            CertificateStatus.Text = "Создаю сертификат…";
            try
            {
                await _service.CreateSamsungCertificateAsync(profile, author, password, _connectedTv.Duid, Log);
                _certificateReady = true;
                CertificateStatus.Text = "Профиль " + profile + " создан";
            }
            finally
            {
                PasswordBox.Clear();
            }
            CertificateStatus.Foreground = (System.Windows.Media.Brush)FindResource("SuccessBrush");
            InstallStatus.Text = "Сертификат готов. Скачайте последнюю версию IPTV.";
            Log("Сертификат сохранён в профиле Tizen SDK. Приложение не хранит пароль.");
        });
    }

    private async void Download_Click(object sender, RoutedEventArgs e)
    {
        await RunBusyAsync(async () =>
        {
            var package = await _service.DownloadLatestIptvAsync(Log);
            _package = package;
            PackageNameText.Text = package.FileName;
            PackageVersionText.Text = "Последний релиз: " + package.Version;
            InstallStatus.Text = _certificateReady
                ? "Пакет и сертификат готовы — можно устанавливать."
                : "Пакет скачан. Сначала создайте сертификат Samsung.";
        });
    }

    private async void Install_Click(object sender, RoutedEventArgs e)
    {
        if (_connectedTv is null || !_certificateReady || _package is null)
        {
            MessageBox.Show("Для установки нужны подключённый телевизор, созданный сертификат и скачанный WGT.",
                "Не все шаги завершены", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        await RunBusyAsync(async () =>
        {
            InstallStatus.Text = "Подписываю и устанавливаю…";
            var signed = await _service.SignAndInstallAsync(
                _package.Path, ProfileBox.Text.Trim(), _connectedTv.Serial, Log);
            InstallStatus.Text = "IPTV установлено";
            InstallStatus.Foreground = (System.Windows.Media.Brush)FindResource("SuccessBrush");
            Log("Подписанный WGT: " + signed);
            MessageBox.Show("IPTV успешно установлено на телевизор Samsung.",
                "Готово", MessageBoxButton.OK, MessageBoxImage.Information);
        });
    }

    private async Task RunBusyAsync(Func<Task> action)
    {
        if (_busy) return;
        _busy = true;
        SetButtonsEnabled(false);
        try
        {
            await action();
        }
        catch (OperationCanceledException)
        {
            Log("Операция отменена.");
        }
        catch (Exception ex)
        {
            Log("ОШИБКА: " + ex.Message);
            MessageBox.Show(ex.Message, "Не удалось выполнить операцию",
                MessageBoxButton.OK, MessageBoxImage.Error);
            if (_connectedTv is null)
            {
                ConnectionStatus.Text = "Не подключён";
                ConnectionStatus.Foreground = (System.Windows.Media.Brush)FindResource("WarningBrush");
            }
            if (!_certificateReady)
                CertificateStatus.Text = "Сертификат ещё не создан.";
        }
        finally
        {
            _busy = false;
            SetButtonsEnabled(true);
        }
    }

    private void SetButtonsEnabled(bool enabled)
    {
        ConnectButton.IsEnabled = enabled;
        CertificateButton.IsEnabled = enabled && _connectedTv is not null;
        DownloadButton.IsEnabled = enabled;
        InstallButton.IsEnabled = enabled && _connectedTv is not null && _certificateReady && _package is not null;
    }

    private void Log(string message)
    {
        Dispatcher.Invoke(() =>
        {
            var time = DateTime.Now.ToString("HH:mm:ss");
            LogBox.AppendText("[" + time + "] " + message + Environment.NewLine);
            LogBox.ScrollToEnd();
        });
    }

    private void ClearLog_Click(object sender, RoutedEventArgs e) => LogBox.Clear();
}

using System.Diagnostics;
using System.IO.Compression;
using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text.Json;

namespace Volna.TVInstaller;

public sealed record ConnectedTv(string Ip, int Port, string Serial, string Duid);
public sealed record DownloadedPackage(string Path, string Version, string FileName);

public sealed class InstallerService
{
    private static readonly HttpClient Http = CreateHttpClient();
    private const string SamsungRepo = "https://github.com/Samsung/tizen-agent-skills";
    private const string IptvRepoApi = "https://api.github.com/repos/volnalabs/IPTV/releases/latest";
    private readonly string _root = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "Volna", "TVInstaller");
    private readonly string _sdkRoot = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "tizen-sdk");

    private string RuntimeRoot => Path.Combine(_root, "runtime");
    private string ToolchainRoot => Path.Combine(_root, "toolchain");
    private string DownloadsRoot => Path.Combine(_root, "downloads");
    private string WorkRoot => Path.Combine(_root, "work");
    private string NodeExe => Path.Combine(RuntimeRoot, "node.exe");
    private string NpmCli => Path.Combine(RuntimeRoot, "node_modules", "npm", "bin", "npm-cli.js");
    private string? _cliScript;
    private string? _toolchainVersion;

    public bool IsSdkInstalled => File.Exists(Path.Combine(_sdkRoot, "sdk.info"));
    public string SdkRoot => _sdkRoot;

    public InstallerService()
    {
        Directory.CreateDirectory(_root);
        Directory.CreateDirectory(DownloadsRoot);
        Directory.CreateDirectory(WorkRoot);
    }

    public async Task PrepareToolchainAsync(Action<string> log, CancellationToken cancellationToken = default)
    {
        log("Проверяю свободное место для командного SDK Samsung…");
        if (!IsSdkInstalled)
        {
            var drive = new DriveInfo(Path.GetPathRoot(_sdkRoot)!);
            var freeGb = drive.AvailableFreeSpace / 1024d / 1024d / 1024d;
            if (freeGb < 15)
                throw new InvalidOperationException(
                    "Для первоначальной установки Tizen SDK Samsung рекомендует иметь около 15 ГБ свободного места. " +
                    "Сейчас доступно " + freeGb.ToString("0.0") + " ГБ. Освободите место и повторите попытку.");
        }

        var node = await EnsureNodeAsync(log, cancellationToken);
        var cli = await EnsureSamsungCliAsync(node, log, cancellationToken);

        log("Настраиваю отдельный каталог SDK: " + _sdkRoot);
        await RunCliAsync(node, cli, new[] { "sdk-init", "--sdk-path", _sdkRoot }, log, cancellationToken);

        if (!IsSdkInstalled)
        {
            log("Устанавливаю базовые командные инструменты Tizen. Это может занять 10–30 минут.");
            await RunCliAsync(node, cli, new[] { "sdk-install" }, log, cancellationToken, inlineInstaller: true);
        }
        else
        {
            log("Базовый Tizen SDK уже установлен — повторная загрузка не требуется.");
        }

        log("Проверяю и устанавливаю Samsung TV Extension…");
        await RunCliAsync(node, cli, new[] { "tv-sdk-install" }, log, cancellationToken, inlineInstaller: true);

        if (!IsSdkInstalled)
            throw new InvalidOperationException("SDK installer завершился, но файл sdk.info не найден. Установка не подтверждена.");

        log("Инструменты Samsung готовы.");
    }

    public async Task<ConnectedTv> ConnectAndGetDuidAsync(
        string ip, int port, Action<string> log, CancellationToken cancellationToken = default)
    {
        var (node, cli) = await GetCliAsync();
        var serial = ip + ":" + port.ToString();

        log("Подключаюсь к " + serial + " через Samsung SDB…");
        await RunCliAsync(node, cli, new[] { "remote-device", "--action", "connect", "--ip", ip, "--port", port.ToString() },
            log, cancellationToken);

        log("Запрашиваю DUID телевизора…");
        var output = await RunCliAsync(node, cli,
            new[] { "certificate-manager", "--action", "acquire-duid", "--serial", serial },
            log, cancellationToken);

        var duid = ReadJsonString(output.StdOut, "duid") ?? FindDuid(output.StdOut + Environment.NewLine + output.StdErr);
        if (string.IsNullOrWhiteSpace(duid))
            throw new InvalidOperationException(
                "SDB подключился, но DUID не удалось прочитать. Проверьте, что на телевизоре включён Developer Mode. " +
                "Ответ Samsung CLI: " + TrimForMessage(output.StdOut + output.StdErr));

        log("Телевизор подключён. DUID получен.");
        return new ConnectedTv(ip, port, serial, duid);
    }

    public async Task CreateSamsungCertificateAsync(
        string profileName, string authorName, string password, string duid,
        Action<string> log, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(password) || password.Length < 8)
            throw new InvalidOperationException("Пароль сертификата должен содержать не менее 8 символов.");
        if (string.IsNullOrWhiteSpace(duid))
            throw new InvalidOperationException("Сначала подключите телевизор и получите его DUID.");

        var (node, cli) = await GetCliAsync();
        var passwordFile = Path.Combine(_root, "certificate-password-" + Guid.NewGuid().ToString("N") + ".txt");
        await File.WriteAllTextAsync(passwordFile, password, cancellationToken);
        try
        {
            File.SetAttributes(passwordFile, FileAttributes.Hidden);
            log("Шаг 1/3. Создаю Samsung Author Certificate. Сейчас откроется браузер для входа в Samsung Account.");
            await RunCliAsync(node, cli, new[]
            {
                "certificate-manager", "--action", "generate-samsung-author",
                "--profile-name", profileName, "--identity", authorName, "--password-file", passwordFile
            }, log, cancellationToken, timeout: TimeSpan.FromMinutes(20));

            log("Шаг 2/3. Запрашиваю Distributor Certificate для DUID этого телевизора…");
            await RunCliAsync(node, cli, new[]
            {
                "certificate-manager", "--action", "generate-samsung-distributor",
                "--profile-name", profileName, "--password-file", passwordFile,
                "--duid-list", duid, "--privilege", "Public"
            }, log, cancellationToken, timeout: TimeSpan.FromMinutes(10));

            log("Шаг 3/3. Создаю профиль подписи Samsung…");
            await RunCliAsync(node, cli, new[]
            {
                "certificate-manager", "--action", "create-samsung-profile",
                "--profile-name", profileName, "--active"
            }, log, cancellationToken, timeout: TimeSpan.FromMinutes(3));
        }
        finally
        {
            try
            {
                if (File.Exists(passwordFile))
                {
                    File.SetAttributes(passwordFile, FileAttributes.Normal);
                    File.Delete(passwordFile);
                }
            }
            catch { /* Best-effort cleanup of the temporary password file. */ }
        }

        log("Профиль сертификата '" + profileName + "' создан.");
    }

    public async Task<DownloadedPackage> DownloadLatestIptvAsync(
        Action<string> log, CancellationToken cancellationToken = default)
    {
        log("Проверяю последний релиз IPTV на GitHub…");
        using var response = await Http.GetAsync(IptvRepoApi, cancellationToken);
        response.EnsureSuccessStatusCode();
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken));
        var root = json.RootElement;
        var version = root.TryGetProperty("tag_name", out var tag) ? tag.GetString() ?? "latest" : "latest";
        if (!root.TryGetProperty("assets", out var assets))
            throw new InvalidOperationException("GitHub Release не содержит списка файлов.");

        string? url = null;
        string fileName = "iptv-samsung.wgt";
        foreach (var asset in assets.EnumerateArray())
        {
            var name = asset.TryGetProperty("name", out var n) ? n.GetString() : null;
            if (string.Equals(name, fileName, StringComparison.OrdinalIgnoreCase) &&
                asset.TryGetProperty("browser_download_url", out var link))
            {
                url = link.GetString();
                break;
            }
        }

        if (string.IsNullOrWhiteSpace(url))
            throw new InvalidOperationException("В последнем релизе " + version + " не найден файл iptv-samsung.wgt.");

        var destination = Path.Combine(DownloadsRoot, "iptv-samsung-" + SafeFilePart(version) + ".wgt");
        log("Скачиваю IPTV " + version + "…");
        using var packageResponse = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        packageResponse.EnsureSuccessStatusCode();
        await using (var source = await packageResponse.Content.ReadAsStreamAsync(cancellationToken))
        await using (var target = new FileStream(destination, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true))
            await source.CopyToAsync(target, cancellationToken);

        if (new FileInfo(destination).Length < 1024)
            throw new InvalidOperationException("Скачанный WGT слишком мал и, вероятно, повреждён.");

        log("IPTV " + version + " загружен: " + destination);
        return new DownloadedPackage(destination, version, fileName);
    }

    public async Task<string> SignAndInstallAsync(
        string sourceWgt, string profileName, string serial,
        Action<string> log, CancellationToken cancellationToken = default)
    {
        if (!File.Exists(sourceWgt))
            throw new FileNotFoundException("Файл IPTV WGT не найден.", sourceWgt);

        var tz = Path.Combine(_sdkRoot, "tools", "tizen-core", "tz.exe");
        if (!File.Exists(tz))
            throw new InvalidOperationException("Не найден Tizen Core CLI (tz.exe). Повторите подготовку инструментов Samsung.");

        var runDir = Path.Combine(WorkRoot, "iptv-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(runDir);
        var unsigned = Path.Combine(runDir, "iptv-samsung.wgt");
        File.Copy(sourceWgt, unsigned, true);

        log("Подготавливаю WGT к повторной подписи…");
        using (var archive = ZipFile.Open(unsigned, ZipArchiveMode.Update))
        {
            var signatures = archive.Entries
                .Where(e => string.Equals(e.FullName, "author-signature.xml", StringComparison.OrdinalIgnoreCase) ||
                            string.Equals(e.FullName, "signature1.xml", StringComparison.OrdinalIgnoreCase))
                .ToList();
            foreach (var signature in signatures)
                signature.Delete();
        }

        log("Подписываю пакет профилем '" + profileName + "'…");
        var packResult = await RunProcessAsync(tz,
            new[] { "pack", "-b", unsigned, "-t", "wgt", "-s", profileName },
            runDir, log, cancellationToken, TimeSpan.FromMinutes(5));

        if (packResult.ExitCode != 0)
            throw new InvalidOperationException("Tizen Core CLI не смог подписать WGT: " + TrimForMessage(packResult.StdOut + packResult.StdErr));

        var candidates = Directory.GetFiles(runDir, "*.wgt", SearchOption.AllDirectories)
            .OrderByDescending(File.GetLastWriteTimeUtc).ToList();
        var signed = candidates.FirstOrDefault(IsSamsungSignedWgt);
        if (signed is null)
            throw new InvalidOperationException(
                "Команда подписи завершилась, но в результате не найдены обе подписи Samsung (author-signature.xml и signature1.xml). " +
                "Установка остановлена, чтобы не отправлять на телевизор неподписанный пакет. " +
                TrimForMessage(packResult.StdOut + packResult.StdErr));

        var finalPath = Path.Combine(DownloadsRoot, "iptv-samsung-signed-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + ".wgt");
        File.Copy(signed, finalPath, true);

        log("Подпись проверена. Запрашиваю установку на телевизор…");
        var installResult = await RunProcessAsync(tz,
            new[] { "install", "-e", serial, "-p", finalPath },
            runDir, log, cancellationToken, TimeSpan.FromMinutes(5));
        if (installResult.ExitCode != 0)
            throw new InvalidOperationException("Не удалось установить IPTV: " + TrimForMessage(installResult.StdOut + installResult.StdErr));

        log("IPTV успешно установлено. Файл с подписью сохранён: " + finalPath);
        return finalPath;
    }

    private async Task<(string Node, string Cli)> GetCliAsync()
    {
        var node = File.Exists(NodeExe) ? NodeExe : await EnsureNodeAsync(_ => { }, CancellationToken.None);
        if (!string.IsNullOrWhiteSpace(_cliScript) && File.Exists(_cliScript))
            return (node, _cliScript);

        var cliDirectory = Directory.Exists(ToolchainRoot)
            ? Directory.GetDirectories(ToolchainRoot, "tizen-cli", SearchOption.AllDirectories)
                .FirstOrDefault(d => File.Exists(Path.Combine(d, "bin", "tizen-sdk.js")))
            : null;

        if (cliDirectory is null)
            throw new InvalidOperationException("Инструменты Samsung ещё не подготовлены. Нажмите «Подключить» и подтвердите первоначальную установку.");

        _cliScript = Path.Combine(cliDirectory, "bin", "tizen-sdk.js");
        return (node, _cliScript);
    }

    private async Task<string> EnsureNodeAsync(Action<string> log, CancellationToken cancellationToken)
    {
        if (File.Exists(NodeExe))
        {
            var existing = await RunProcessAsync(NodeExe, new[] { "--version" }, RuntimeRoot, log, cancellationToken, TimeSpan.FromSeconds(15));
            if (existing.ExitCode == 0)
                return NodeExe;
        }

        log("Загружаю официальный Node.js LTS runtime (он нужен командным инструментам Samsung)…");
        Directory.CreateDirectory(RuntimeRoot);

        using var indexResponse = await Http.GetAsync("https://nodejs.org/dist/index.json", cancellationToken);
        indexResponse.EnsureSuccessStatusCode();
        using var index = JsonDocument.Parse(await indexResponse.Content.ReadAsStringAsync(cancellationToken));

        JsonElement? selected = null;
        foreach (var item in index.RootElement.EnumerateArray())
        {
            if (item.TryGetProperty("lts", out var lts) &&
                lts.ValueKind == JsonValueKind.String &&
                !string.Equals(lts.GetString(), "false", StringComparison.OrdinalIgnoreCase))
            {
                selected = item;
                break;
            }
        }

        if (selected is null || !selected.Value.TryGetProperty("version", out var versionElement))
            throw new InvalidOperationException("Не удалось определить актуальную LTS-версию Node.js.");

        var version = versionElement.GetString()!;
        var archiveName = "node-" + version + "-win-x64.zip";
        var baseUrl = "https://nodejs.org/dist/" + version + "/";
        using var sumsResponse = await Http.GetAsync(baseUrl + "SHASUMS256.txt", cancellationToken);
        sumsResponse.EnsureSuccessStatusCode();
        var sums = await sumsResponse.Content.ReadAsStringAsync(cancellationToken);
        var expectedHash = sums.Split('\n', StringSplitOptions.RemoveEmptyEntries)
            .Select(line => line.Trim().Split(' ', StringSplitOptions.RemoveEmptyEntries))
            .FirstOrDefault(parts => parts.Length >= 2 && parts[^1] == archiveName)?[0];

        if (string.IsNullOrWhiteSpace(expectedHash))
            throw new InvalidOperationException("Не удалось получить SHA-256 контрольную сумму Node.js.");

        var archivePath = Path.Combine(RuntimeRoot, archiveName);
        using (var downloadResponse = await Http.GetAsync(baseUrl + archiveName, HttpCompletionOption.ResponseHeadersRead, cancellationToken))
        {
            downloadResponse.EnsureSuccessStatusCode();
            await using var input = await downloadResponse.Content.ReadAsStreamAsync(cancellationToken);
            await using var output = new FileStream(archivePath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);
            await input.CopyToAsync(output, cancellationToken);
        }

        await using (var stream = File.OpenRead(archivePath))
        {
            var actualHash = Convert.ToHexString(await SHA256.HashDataAsync(stream, cancellationToken));
            if (!actualHash.Equals(expectedHash, StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("SHA-256 Node.js не совпадает. Архив удалён для безопасности.");
        }

        var extracted = Path.Combine(RuntimeRoot, "node-extract");
        if (Directory.Exists(extracted)) Directory.Delete(extracted, true);
        Directory.CreateDirectory(extracted);
        ZipFile.ExtractToDirectory(archivePath, extracted, true);
        var nodeFolder = Directory.GetDirectories(extracted).FirstOrDefault()
            ?? throw new InvalidOperationException("В архиве Node.js не найдена папка runtime.");
        CopyDirectory(nodeFolder, RuntimeRoot);
        Directory.Delete(extracted, true);
        File.Delete(archivePath);

        if (!File.Exists(NodeExe) || !File.Exists(NpmCli))
            throw new InvalidOperationException("Не удалось подготовить Node.js runtime.");
        log("Node.js " + version + " установлен локально для ВОЛНА TV Installer.");
        return NodeExe;
    }

    private async Task<string> EnsureSamsungCliAsync(string node, Action<string> log, CancellationToken cancellationToken)
    {
        Directory.CreateDirectory(ToolchainRoot);
        var latestTag = await GetLatestSamsungCliTagAsync(cancellationToken);
        var versionFolder = Path.Combine(ToolchainRoot, latestTag.Replace('/', '_'));
        var cliDirectory = Directory.GetDirectories(ToolchainRoot, "tizen-cli", SearchOption.AllDirectories)
            .FirstOrDefault(d => File.Exists(Path.Combine(d, "bin", "tizen-sdk.js")) &&
                                 File.Exists(Path.Combine(d, "dist", "tizen-sdk.js")));

        if (cliDirectory is not null && File.Exists(Path.Combine(cliDirectory, "dist", "tizen-sdk.js")))
        {
            _cliScript = Path.Combine(cliDirectory, "bin", "tizen-sdk.js");
            log("Командный инструментарий Samsung уже подготовлен.");
            return _cliScript;
        }

        log("Загружаю официальный Samsung tizen-agent-skills (" + latestTag + ")…");
        Directory.CreateDirectory(versionFolder);
        var zipPath = Path.Combine(ToolchainRoot, "samsung-tizen-tools.zip");
        var url = SamsungRepo + "/archive/refs/tags/" + Uri.EscapeDataString(latestTag) + ".zip";
        using (var response = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cancellationToken))
        {
            response.EnsureSuccessStatusCode();
            await using var input = await response.Content.ReadAsStreamAsync(cancellationToken);
            await using var output = new FileStream(zipPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);
            await input.CopyToAsync(output, cancellationToken);
        }

        var extractRoot = Path.Combine(versionFolder, "source");
        if (Directory.Exists(extractRoot))
            Directory.Delete(extractRoot, true);
        Directory.CreateDirectory(extractRoot);
        ExtractZipSafely(zipPath, extractRoot);
        File.Delete(zipPath);

        cliDirectory = Directory.GetDirectories(extractRoot, "tizen-cli", SearchOption.AllDirectories)
            .FirstOrDefault(d => File.Exists(Path.Combine(d, "package.json")) &&
                                 File.Exists(Path.Combine(d, "bin", "tizen-sdk.js")));
        if (cliDirectory is null)
            throw new InvalidOperationException("В официальном архиве Samsung не найдена папка tizen-cli.");

        log("Подготавливаю официальный CLI Samsung. Загружаются его npm-зависимости…");
        var installResult = await RunProcessAsync(node, new[] { NpmCli, "install", "--no-audit", "--no-fund" },
            cliDirectory, log, cancellationToken, TimeSpan.FromMinutes(15));
        if (installResult.ExitCode != 0)
            throw new InvalidOperationException("Не удалось установить npm-зависимости Samsung CLI: " + TrimForMessage(installResult.StdOut + installResult.StdErr));
        log("Собираю командный интерфейс Samsung…");
        var buildResult = await RunProcessAsync(node, new[] { NpmCli, "run", "build" },
            cliDirectory, log, cancellationToken, TimeSpan.FromMinutes(10));
        if (buildResult.ExitCode != 0)
            throw new InvalidOperationException("Не удалось собрать Samsung CLI: " + TrimForMessage(buildResult.StdOut + buildResult.StdErr));

        var builtBundle = Path.Combine(cliDirectory, "dist", "tizen-sdk.js");
        if (!File.Exists(builtBundle))
            throw new InvalidOperationException("Сборка Samsung CLI не создала dist/tizen-sdk.js.");

        _cliScript = Path.Combine(cliDirectory, "bin", "tizen-sdk.js");
        _toolchainVersion = latestTag;
        log("Официальный Samsung CLI готов.");
        return _cliScript;
    }

    private async Task<string> GetLatestSamsungCliTagAsync(CancellationToken cancellationToken)
    {
        using var response = await Http.GetAsync("https://api.github.com/repos/Samsung/tizen-agent-skills/tags?per_page=100", cancellationToken);
        response.EnsureSuccessStatusCode();
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken));
        var tags = new List<(Version Version, string Name)>();
        foreach (var item in json.RootElement.EnumerateArray())
        {
            var name = item.TryGetProperty("name", out var n) ? n.GetString() : null;
            const string prefix = "tizen-sdk-skills-v";
            if (name is not null && name.StartsWith(prefix, StringComparison.Ordinal) &&
                Version.TryParse(name[prefix.Length..], out var version))
                tags.Add((version, name));
        }
        return tags.OrderByDescending(x => x.Version).Select(x => x.Name).FirstOrDefault()
            ?? "tizen-sdk-skills-v1.4.5";
    }

    private async Task<CommandResult> RunCliAsync(
        string node, string cli, IReadOnlyList<string> arguments, Action<string> log,
        CancellationToken cancellationToken, bool inlineInstaller = false, TimeSpan? timeout = null)
    {
        var args = new List<string> { cli };
        args.AddRange(arguments);
        var result = await RunProcessAsync(node, args, Path.GetDirectoryName(cli)!, log, cancellationToken,
            timeout ?? TimeSpan.FromMinutes(5), inlineInstaller ? new Dictionary<string, string>
            {
                ["TIZEN_SDK_INLINE_INSTALLER"] = "1"
            } : null);

        if (result.ExitCode != 0)
            throw new InvalidOperationException("Samsung CLI завершился с кодом " + result.ExitCode + ": " +
                                                TrimForMessage(result.StdOut + result.StdErr));
        EnsureJsonSuccess(result.StdOut, "Samsung CLI");
        return result;
    }

    private static void EnsureJsonSuccess(string output, string source)
    {
        try
        {
            using var json = JsonDocument.Parse(output);
            if (json.RootElement.TryGetProperty("status", out var status) &&
                !string.Equals(status.GetString(), "success", StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException(source + " вернул статус '" + status.GetString() + "': " +
                                                    TrimForMessage(output));
        }
        catch (JsonException)
        {
            // Some Tizen tools write ordinary text. Exit code remains the primary result.
        }
    }

    private static async Task<CommandResult> RunProcessAsync(
        string executable, IReadOnlyList<string> arguments, string workingDirectory,
        Action<string> log, CancellationToken cancellationToken, TimeSpan timeout,
        IDictionary<string, string>? environment = null)
    {
        var start = new ProcessStartInfo
        {
            FileName = executable,
            WorkingDirectory = Directory.Exists(workingDirectory) ? workingDirectory : Environment.CurrentDirectory,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        };
        foreach (var argument in arguments) start.ArgumentList.Add(argument);
        if (environment is not null)
            foreach (var pair in environment) start.Environment[pair.Key] = pair.Value;

        using var process = new Process { StartInfo = start, EnableRaisingEvents = true };
        var stdout = new System.Text.StringBuilder();
        var stderr = new System.Text.StringBuilder();
        process.OutputDataReceived += (_, e) =>
        {
            if (e.Data is null) return;
            lock (stdout) stdout.AppendLine(e.Data);
            if (!string.IsNullOrWhiteSpace(e.Data)) log(e.Data);
        };
        process.ErrorDataReceived += (_, e) =>
        {
            if (e.Data is null) return;
            lock (stderr) stderr.AppendLine(e.Data);
            if (!string.IsNullOrWhiteSpace(e.Data)) log(e.Data);
        };

        log("Запуск: " + Path.GetFileName(executable) + " " + string.Join(" ", arguments.Select(SafeLogArgument)));
        if (!process.Start())
            throw new InvalidOperationException("Не удалось запустить " + executable);
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();

        using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeoutCts.CancelAfter(timeout);
        try
        {
            await process.WaitForExitAsync(timeoutCts.Token);
        }
        catch (OperationCanceledException)
        {
            try { process.Kill(true); } catch { }
            if (cancellationToken.IsCancellationRequested) throw;
            throw new TimeoutException("Команда " + Path.GetFileName(executable) + " превысила время ожидания " + timeout.TotalMinutes + " мин.");
        }

        lock (stdout)
        lock (stderr)
            return new CommandResult(process.ExitCode, stdout.ToString(), stderr.ToString());
    }

    private static bool IsSamsungSignedWgt(string path)
    {
        try
        {
            using var archive = ZipFile.OpenRead(path);
            var entries = archive.Entries.Select(e => e.FullName).ToHashSet(StringComparer.OrdinalIgnoreCase);
            return entries.Contains("author-signature.xml") && entries.Contains("signature1.xml");
        }
        catch { return false; }
    }

    private static string? ReadJsonString(string json, string property)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.TryGetProperty("result", out var result) &&
                result.TryGetProperty(property, out var value))
                return value.GetString();
        }
        catch { }
        return null;
    }

    private static string? FindDuid(string text)
    {
        var match = System.Text.RegularExpressions.Regex.Match(text, "(?im)(?:duid[\"'\\s:=]+)([A-Z0-9#._-]{6,})");
        return match.Success ? match.Groups[1].Value.Trim() : null;
    }

    private static string SafeFilePart(string value) =>
        string.Concat(value.Select(c => Path.GetInvalidFileNameChars().Contains(c) ? '_' : c));

    private static string SafeLogArgument(string value)
    {
        if (value.Contains("password", StringComparison.OrdinalIgnoreCase)) return "<скрыто>";
        return value.Length > 100 ? value[..100] + "…" : value;
    }

    private static string TrimForMessage(string value)
    {
        var cleaned = value.Trim();
        return cleaned.Length <= 1600 ? cleaned : cleaned[^1600..];
    }

    private static void ExtractZipSafely(string zipPath, string destination)
    {
        var basePath = Path.GetFullPath(destination) + Path.DirectorySeparatorChar;
        using var archive = ZipFile.OpenRead(zipPath);
        foreach (var entry in archive.Entries)
        {
            var fullPath = Path.GetFullPath(Path.Combine(destination, entry.FullName.Replace('/', Path.DirectorySeparatorChar)));
            if (!fullPath.StartsWith(basePath, StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("Архив содержит недопустимый путь.");
            if (string.IsNullOrEmpty(entry.Name))
            {
                Directory.CreateDirectory(fullPath);
                continue;
            }
            Directory.CreateDirectory(Path.GetDirectoryName(fullPath)!);
            entry.ExtractToFile(fullPath, true);
        }
    }

    private static void CopyDirectory(string source, string destination)
    {
        foreach (var directory in Directory.GetDirectories(source, "*", SearchOption.AllDirectories))
            Directory.CreateDirectory(directory.Replace(source, destination, StringComparison.OrdinalIgnoreCase));
        foreach (var file in Directory.GetFiles(source, "*", SearchOption.AllDirectories))
        {
            var target = file.Replace(source, destination, StringComparison.OrdinalIgnoreCase);
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            File.Copy(file, target, true);
        }
    }

    private static HttpClient CreateHttpClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromMinutes(30) };
        client.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue("Volna-TV-Installer", "0.1.0"));
        client.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
        return client;
    }

    private sealed record CommandResult(int ExitCode, string StdOut, string StdErr);
}

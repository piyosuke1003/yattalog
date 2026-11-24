using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace Yattalog;

internal static class CalendarHandoff
{
    // Google credentials stay in the authorized Pages origin. The loopback
    // callback carries only the result for this one pending entry.
    internal static async Task SaveAsync(string title, DateTimeOffset start, DateTimeOffset end,
        CancellationToken token, Action<string>? launch = null)
    {
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        int port = ((IPEndPoint)listener.LocalEndpoint).Port;
        string nonce = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
        string payload = JsonSerializer.Serialize(new {
            title, start = start.ToUnixTimeMilliseconds(), end = end.ToUnixTimeMilliseconds(),
            port, nonce, expires = DateTimeOffset.UtcNow.AddMinutes(5).ToUnixTimeMilliseconds()
        });
        string url = "https://piyosuke1003.github.io/yattalog/desktop-save.html#" + Uri.EscapeDataString(payload);
        if (launch != null) launch(url);
        else Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });

        while (true)
        {
            using var client = await listener.AcceptTcpClientAsync(token);
            using var limit = CancellationTokenSource.CreateLinkedTokenSource(token);
            limit.CancelAfter(TimeSpan.FromSeconds(2));
            try
            {
                var stream = client.GetStream();
                var request = new byte[8192];
                int n = 0;
                string header = "";
                while (n < request.Length && !header.Contains("\r\n\r\n", StringComparison.Ordinal))
                {
                    int count = await stream.ReadAsync(request.AsMemory(n), limit.Token);
                    if (count == 0) break;
                    n += count;
                    header = Encoding.ASCII.GetString(request, 0, n);
                }
                bool complete = header.Contains("\r\n\r\n", StringComparison.Ordinal);
                bool ok = complete && header.StartsWith($"GET /{nonce}/saved HTTP/1.", StringComparison.Ordinal);
                bool cancel = complete && header.StartsWith($"GET /{nonce}/cancel HTTP/1.", StringComparison.Ordinal);
                string message = ok ? "Google カレンダーに記録しました。このタブは閉じられます。" :
                    cancel ? "入力内容はアプリに残っています。このタブは閉じられます。" : "この操作は無効です。";
                var body = Encoding.UTF8.GetBytes("<!doctype html><html lang=ja><meta charset=utf-8><title>やったこと</title><p>" + message + "</p></html>");
                var response = Encoding.ASCII.GetBytes("HTTP/1.1 " + (ok || cancel ? "200 OK" : "404 Not Found") +
                    "\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\nContent-Length: " + body.Length + "\r\n\r\n");
                await stream.WriteAsync(response, limit.Token);
                await stream.WriteAsync(body, limit.Token);
                if (cancel) throw new OperationCanceledException(token);
                if (ok) return;
            }
            catch (OperationCanceledException) when (limit.IsCancellationRequested && !token.IsCancellationRequested) { }
            catch (IOException) when (!token.IsCancellationRequested) { }
        }
    }
}

using System;
using System.Linq;
using System.Globalization;
using System.Threading;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;

namespace Yattalog;

public partial class MainWindow : Window
{
    private enum Mode { Normal, Tile, Panel }
    private Mode mode;
    private CancellationTokenSource? pendingSave;
    private Rect normalBounds;
    private bool wasMaximized;
    private DateTime anchor = DateTime.Now;
    private static readonly int[] Minutes = new[] { 1, 2, 3, 4 }.Concat(Enumerable.Range(1, 288).Select(n => n * 5)).ToArray();
    private int index = Array.IndexOf(Minutes, 15);

    public MainWindow()
    {
        InitializeComponent();
        Closed += (_, _) => pendingSave?.Cancel();
        anchor = anchor.Date.AddHours(anchor.Hour).AddMinutes(anchor.Minute);
        RefreshForm();
    }

    private void Fold(object sender, RoutedEventArgs e) => Change(Mode.Tile);
    private void OpenPanel(object sender, RoutedEventArgs e) => Change(Mode.Panel);
    private void RestoreNormal(object sender, RoutedEventArgs e) => Change(Mode.Normal);

    private void Change(Mode next)
    {
        if (mode == next || (mode != Mode.Tile && next != Mode.Tile)) return;
        var handle = new WindowInteropHelper(this).Handle;
        var monitor = MonitorFromWindow(handle, 2);
        if (mode == Mode.Normal)
        {
            wasMaximized = WindowState == WindowState.Maximized;
            normalBounds = RestoreBounds;
        }
        mode = next;
        bool normal = next == Mode.Normal;
        WindowState = WindowState.Normal;
        WindowStyle = normal ? WindowStyle.SingleBorderWindow : WindowStyle.None;
        ResizeMode = normal ? ResizeMode.CanResize : ResizeMode.NoResize;
        Topmost = !normal;
        Tile.Visibility = next == Mode.Tile ? Visibility.Visible : Visibility.Collapsed;
        Form.Visibility = next == Mode.Tile ? Visibility.Collapsed : Visibility.Visible;
        if (normal)
        {
            SetWindowRgn(handle, IntPtr.Zero, true);
            Left = normalBounds.Left; Top = normalBounds.Top;
            Width = normalBounds.Width; Height = normalBounds.Height;
            if (wasMaximized) WindowState = WindowState.Maximized;
            TitleBox.Focus();
            return;
        }
        var info = new MonitorInfo { Size = Marshal.SizeOf<MonitorInfo>() };
        if (!GetMonitorInfo(monitor, ref info)) return;
        double scale = GetDpiForWindow(handle) / 96.0;
        int gap = (int)Math.Round(12 * scale);
        int width = Math.Min((int)Math.Round((next == Mode.Tile ? 151 : 356) * scale), info.Work.Right - info.Work.Left - 2 * gap);
        int height = Math.Min((int)Math.Round((next == Mode.Tile ? 113 : 460) * scale), info.Work.Bottom - info.Work.Top - 2 * gap);
        SetWindowPos(handle, IntPtr.Zero, info.Work.Right - gap - width, info.Work.Bottom - gap - height, width, height, 0x0004);
        int diameter = (int)Math.Round(30 * scale);
        var region = CreateRoundRectRgn(0, 0, width + 1, height + 1, diameter, diameter);
        if (region != IntPtr.Zero && SetWindowRgn(handle, region, true) == 0) DeleteObject(region);
        if (next == Mode.Tile) OpenPanelButton.Focus(); else TitleBox.Focus();
    }

    private void Earlier(object sender, RoutedEventArgs e) => StepClock(-1);
    private void Later(object sender, RoutedEventArgs e) => StepClock(1);
    private void ClockWheel(object sender, MouseWheelEventArgs e) { StepClock(e.Delta > 0 ? 1 : -1); e.Handled = true; }
    private void StepClock(int direction)
    {
        int remainder = anchor.Minute % 5;
        int step = direction > 0 ? 5 - remainder : -(remainder == 0 ? 5 : remainder);
        anchor = anchor.AddMinutes(step);
        RefreshForm();
    }
    private void PreviousDay(object sender, RoutedEventArgs e) => StepDay(-1);
    private void NextDay(object sender, RoutedEventArgs e) => StepDay(1);
    private void DayWheel(object sender, MouseWheelEventArgs e) { StepDay(e.Delta > 0 ? 1 : -1); e.Handled = true; }
    private void StepDay(int days) { anchor = anchor.AddDays(days); RefreshForm(); }
    private void AnchorChanged(object sender, RoutedEventArgs e) => RefreshForm();
    private void Shorter(object sender, RoutedEventArgs e) => ChangeDuration(-1);
    private void Longer(object sender, RoutedEventArgs e) => ChangeDuration(1);
    private void DurationWheel(object sender, MouseWheelEventArgs e) { ChangeDuration(e.Delta > 0 ? -1 : 1); e.Handled = true; }
    private void ChangeDuration(int delta) { index = Math.Clamp(index + delta, 0, Minutes.Length - 1); RefreshForm(); }
    private void RefreshForm()
    {
        if (Range == null) return;
        Day.Text = anchor.ToString("yyyy/M/d (ddd)", CultureInfo.GetCultureInfo("ja-JP"));
        Clock.Text = anchor.ToString("HH:mm");
        Before.Content = index > 0 ? $"{Minutes[index - 1]}分" : "";
        After.Content = index + 1 < Minutes.Length ? $"{Minutes[index + 1]}分" : "";
        Before.IsEnabled = index > 0; After.IsEnabled = index + 1 < Minutes.Length;
        Duration.Text = $"{Minutes[index]}分";
        var start = Start.IsChecked == true ? anchor : anchor.AddMinutes(-Minutes[index]);
        var end = start.AddMinutes(Minutes[index]);
        Range.Text = start.Date == end.Date ? $"{start:HH:mm} ～ {end:HH:mm}" : $"{start:M/d HH:mm} ～ {end:M/d HH:mm}";
    }

    private void StopWaiting(object sender, RoutedEventArgs e) => pendingSave?.Cancel();
    private async void Save(object sender, RoutedEventArgs e)
    {
        if (pendingSave != null) return;
        var title = TitleBox.Text.Trim();
        if (title.Length == 0) { Status.Text = "やったことを入力してください。"; TitleBox.Focus(); return; }
        var duration = TimeSpan.FromMinutes(Minutes[index]);
        if (TimeZoneInfo.Local.IsInvalidTime(anchor)) { Status.Text = "この時刻は存在しません。時刻を変更してください。"; return; }
        var instant = new DateTimeOffset(anchor);
        var start = Start.IsChecked == true ? instant : instant - duration;
        using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(5));
        pendingSave = timeout;
        Fields.IsEnabled = false;
        CancelWait.Visibility = Visibility.Visible;
        Status.Text = "ブラウザーで登録を確認してください。";
        try
        {
            await CalendarHandoff.SaveAsync(title, start, start + duration, timeout.Token);
            TitleBox.Clear();
            anchor = DateTime.Now;
            anchor = anchor.Date.AddHours(anchor.Hour).AddMinutes(anchor.Minute);
            RefreshForm();
            Status.Text = "カレンダーに記録しました。";
            if (mode == Mode.Panel) Change(Mode.Tile);
        }
        catch (OperationCanceledException) { Status.Text = "保存結果を確認できませんでした。入力内容は残っています。"; }
        catch (Exception) { Status.Text = "登録を完了できませんでした。もう一度お試しください。"; }
        finally { pendingSave = null; Fields.IsEnabled = true; CancelWait.Visibility = Visibility.Collapsed; }
    }

    [DllImport("gdi32.dll")] private static extern IntPtr CreateRoundRectRgn(int left, int top, int right, int bottom, int w, int h);
    [DllImport("gdi32.dll")] private static extern bool DeleteObject(IntPtr obj);
    [DllImport("user32.dll")] private static extern int SetWindowRgn(IntPtr hwnd, IntPtr region, bool redraw);
    [StructLayout(LayoutKind.Sequential)] private struct NativeRect { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] private struct MonitorInfo { public int Size; public NativeRect Monitor, Work; public uint Flags; }
    [DllImport("user32.dll")] private static extern IntPtr MonitorFromWindow(IntPtr hwnd, uint flags);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] private static extern bool GetMonitorInfo(IntPtr monitor, ref MonitorInfo info);
    [DllImport("user32.dll")] private static extern uint GetDpiForWindow(IntPtr hwnd);
    [DllImport("user32.dll")] private static extern bool SetWindowPos(IntPtr hwnd, IntPtr after, int x, int y, int w, int h, uint flags);
}

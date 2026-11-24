using System;
using System.Linq;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;

namespace Yattalog;

public partial class MainWindow : Window
{
    private enum Mode { Normal, Tile, Panel }
    private Mode mode;
    private Rect normalBounds;
    private bool wasMaximized;
    private DateTime anchor = DateTime.Now;
    private static readonly int[] Minutes = new[] { 1, 2, 3, 4 }.Concat(Enumerable.Range(1, 288).Select(n => n * 5)).ToArray();
    private int index = Array.IndexOf(Minutes, 15);

    public MainWindow()
    {
        InitializeComponent();
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
        int height = Math.Min((int)Math.Round((next == Mode.Tile ? 113 : 442) * scale), info.Work.Bottom - info.Work.Top - 2 * gap);
        SetWindowPos(handle, IntPtr.Zero, info.Work.Right - gap - width, info.Work.Bottom - gap - height, width, height, 0x0004);
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
    private void DayChanged(object? sender, RoutedEventArgs e)
    {
        if (Day.SelectedDate is DateTime date) anchor = date.Date + anchor.TimeOfDay;
        RefreshForm();
    }
    private void AnchorChanged(object sender, RoutedEventArgs e) => RefreshForm();
    private void Shorter(object sender, RoutedEventArgs e) => ChangeDuration(-1);
    private void Longer(object sender, RoutedEventArgs e) => ChangeDuration(1);
    private void DurationWheel(object sender, MouseWheelEventArgs e) { ChangeDuration(e.Delta > 0 ? -1 : 1); e.Handled = true; }
    private void ChangeDuration(int delta) { index = Math.Clamp(index + delta, 0, Minutes.Length - 1); RefreshForm(); }
    private void RefreshForm()
    {
        if (Range == null) return;
        if (Day.SelectedDate != anchor.Date) Day.SelectedDate = anchor.Date;
        Clock.Text = anchor.ToString("HH:mm");
        Before.Content = index > 0 ? $"{Minutes[index - 1]}分" : "";
        After.Content = index + 1 < Minutes.Length ? $"{Minutes[index + 1]}分" : "";
        Before.IsEnabled = index > 0; After.IsEnabled = index + 1 < Minutes.Length;
        Duration.Text = $"{Minutes[index]}分";
        var start = Start.IsChecked == true ? anchor : anchor.AddMinutes(-Minutes[index]);
        var end = start.AddMinutes(Minutes[index]);
        Range.Text = start.Date == end.Date ? $"{start:HH:mm} ～ {end:HH:mm}" : $"{start:M/d HH:mm} ～ {end:M/d HH:mm}";
    }

    [StructLayout(LayoutKind.Sequential)] private struct NativeRect { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] private struct MonitorInfo { public int Size; public NativeRect Monitor, Work; public uint Flags; }
    [DllImport("user32.dll")] private static extern IntPtr MonitorFromWindow(IntPtr hwnd, uint flags);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] private static extern bool GetMonitorInfo(IntPtr monitor, ref MonitorInfo info);
    [DllImport("user32.dll")] private static extern uint GetDpiForWindow(IntPtr hwnd);
    [DllImport("user32.dll")] private static extern bool SetWindowPos(IntPtr hwnd, IntPtr after, int x, int y, int w, int h, uint flags);
}

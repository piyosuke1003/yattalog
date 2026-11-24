using System;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using Yattalog;
class Smoke {
 [STAThread] static void Main() {
  var app=new Application();
  var w=new MainWindow { ShowActivated=false, ShowInTaskbar=false, Opacity=0 };
  w.Show();
  var handle=new WindowInteropHelper(w).Handle;
  var original=w.RestoreBounds;
  T Find<T>(string id) where T: FrameworkElement => (T)w.FindName(id);
  void Click(string id) => Find<Button>(id).RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
  void Check(bool test, string name) { if (!test) throw new Exception(name); Console.WriteLine(name); }
  Find<TextBox>("TitleBox").Text="draft";
  Click("FoldButton"); w.UpdateLayout();
  Check(Find<Grid>("Tile").IsVisible && !Find<Grid>("Form").IsVisible && w.Topmost,"normal -> tile only");
  Check(Math.Abs(w.ActualWidth-151)<2 && Math.Abs(w.ActualHeight-113)<2,"tile physical layout at current DPI");
  Click("OpenPanelButton"); w.UpdateLayout();
  Check(!Find<Grid>("Tile").IsVisible && Find<Grid>("Form").IsVisible && w.Topmost,"tile -> panel only");
  Click("RestoreButton"); Check(w.Topmost,"direct panel -> normal blocked");
  Click("FoldButton"); Click("RestoreButton"); w.UpdateLayout();
  Check(!w.Topmost && Find<TextBox>("TitleBox").Text=="draft","normal restored with draft");
  Check(Math.Abs(w.ActualWidth-original.Width)<2 && Math.Abs(w.Left-original.Left)<2,"window bounds restored");
  Check(handle==new WindowInteropHelper(w).Handle && app.Windows.Count==1,"one window and same native handle");
  var anchor=typeof(MainWindow).GetField("anchor",System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance);
  anchor.SetValue(w,new DateTime(2026,11,24,23,55,0));
  typeof(MainWindow).GetMethod("StepClock",System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance).Invoke(w,new object[]{1});
  Check(Find<TextBlock>("Clock").Text=="00:00" && Find<DatePicker>("Day").SelectedDate.Value.Day==25,"clock carries day at midnight");
  w.Close();
 }
}

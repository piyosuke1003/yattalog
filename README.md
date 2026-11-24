# やったこと

Google カレンダーの「やったこと リスト」に、終えた作業を時間ブロックとして記録します。[iPad・ブラウザで開く](https://piyosuke1003.github.io/yattalog/) · [Android 安定版](https://github.com/piyosuke1003/yattalog/releases/latest/download/Yattalog.apk)

## 共通にするもの

記録の意味と操作をそろえます。タイトル・基準日時・開始／終了の指定・所要時間から一つの時間帯を作ります。初期値は開いた時刻、終了基準、15分。時計は5分刻みで日付をまたげます。表示を切り替えても書きかけの入力を引き継ぎます。

Windows と Android の表示は **通常画面 ↔ 待機タイル ↔ 入力パネル** の3状態です。待機タイルが入口を兼ね、画面には常に一つの状態だけを表示します。

## OS ごとに実装するもの

| 場所 | 実装と意図 |
| --- | --- |
| `windows/` | C#・WPF。一つのウィンドウの大きさと最前面設定を変え、裏に元の窓が残る問題を防ぎます。タイル中央で入力、右上で通常画面に戻れます。「記録する」でブラウザーに入力内容を渡し、確認すると Google カレンダーに保存します。認証と書き込みは Web 版と共通です。 |
| `android/` | Kotlin・Jetpack Compose。通常画面と入力パネルでフォームを共有し、他アプリに重ねる処理と端末のカレンダーへの書き込みは Android API に任せます。丸をタップすると入力、長押しで通常画面です。 |
| リポジトリ直下 | iPad・ブラウザ用の PWA。GitHub Pages で配信します。 |

同じリポジトリで変更を追えるようにし、UIコード・認証・配信は端末ごとに管理します。仕様を変更するときは、上の共通部分に影響するかを確認します。

## 開発と配信

Windows は .NET 10 SDK で `dotnet run --project windows/Yattalog.csproj`。Android は JDK 17 と Android SDK 36 を用意し、`android` ディレクトリで `./gradlew assembleDebug`（Windows は `.\gradlew.bat assembleDebug`）。デバッグ版は安定版と別アプリとして試せます。

安定版 Android の更新には同じアプリID・署名鍵と、より大きい versionCode を使います。署名済みの `android/releases/v<versionName>/Yattalog.apk` を main に入れると GitHub Releases に公開され、Obtainium で更新を追えます。鍵とパスワードは端末側で保管します。

(() => {
  const main = document.querySelector('main');
  const button = document.getElementById('quick-window');
  const download = document.getElementById('android-download');
  const isAndroid = navigator.userAgentData?.platform === 'Android' || /Android/i.test(navigator.userAgent);
  download.hidden = !isAndroid;
  if (!window.documentPictureInPicture || isAndroid) return;
  button.hidden = false;
  let floating;
  button.addEventListener('click', async () => {
    if (floating) { floating.focus(); return; }
    button.disabled = true;
    try {
      const win = await documentPictureInPicture.requestWindow({ width: 350, height: 470 });
      floating = win;
      const mini = win.document;
      mini.documentElement.lang = 'ja';
      mini.documentElement.classList.add('compact');
      mini.title = 'やったこと';
      for (const style of document.querySelectorAll('style')) mini.head.append(style.cloneNode(true));
      const selectedMinutes = minutes;
      win.addEventListener('pagehide', () => {
        const currentMinutes = minutes;
        document.body.insertBefore(main, button.parentElement);
        go(currentMinutes);
        floating = null;
        button.textContent = '小窓で記録';
      }, { once: true });
      mini.body.append(main);
      go(selectedMinutes);
      button.textContent = '小窓を表示';
      main.querySelector('#title').focus({ preventScroll: true });
    } catch {
      main.querySelector('#status').textContent = '小窓を開けませんでした。通常画面で記録できます。';
    } finally {
      button.disabled = false;
    }
  });
})();

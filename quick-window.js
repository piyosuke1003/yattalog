(() => {
  const main = document.querySelector('main');
  const button = document.getElementById('quick-window');
  const footer = button.parentElement;
  const download = document.getElementById('android-download');
  const android = navigator.userAgentData?.platform === 'Android' || /Android/i.test(navigator.userAgent);
  download.hidden = !android;
  if (!window.documentPictureInPicture || android) return;
  document.documentElement.classList.add('controlled');
  button.hidden = false;
  let floating;

  function label(control, text) {
    control.setAttribute('aria-label', text);
    control.title = text;
  }
  function returnToForm() {
    window.focus();
    floating?.close();
  }
  button.addEventListener('click', async () => {
    if (floating) { returnToForm(); return; }
    button.disabled = true;
    try {
      const win = await documentPictureInPicture.requestWindow({ width: 360, height: 520 });
      floating = win;
      const mini = win.document;
      mini.documentElement.lang = 'ja';
      mini.documentElement.classList.add('compact','controlled');
      mini.title = 'やったこと';
      for (const style of document.querySelectorAll('style')) mini.head.append(style.cloneNode(true));
      const bar = mini.createElement('footer');
      bar.className = 'tools';
      const restore = button.cloneNode(true);
      restore.removeAttribute('id');
      restore.disabled = false;
      restore.querySelector('img').src = button.querySelector('img').src;
      label(restore, '通常のウィンドウに戻る');
      restore.addEventListener('click',returnToForm);
      bar.append(restore);
      const selected = minutes;
      win.addEventListener('pagehide', () => {
        const current = minutes;
        document.body.insertBefore(main,footer);
        go(current);
        floating = null;
        label(button,'小窓で記録');
        button.focus({preventScroll:true});
      }, { once:true });
      label(button,'通常のウィンドウに戻る');
      mini.body.append(main,bar);
      go(selected);
      main.querySelector('#title').focus({preventScroll:true});
    } catch {
      if (floating) floating.close();
      main.querySelector('#status').textContent = '小窓を開けませんでした。通常画面で記録できます。';
    } finally {
      button.disabled = false;
    }
  });
})();

(() => {
  const get = id => document.getElementById(id);
  const save = get('save'), cancel = get('cancel'), message = get('message');
  const fragment = location.hash.slice(1);
  history.replaceState(null, '', location.pathname);
  let entry;
  try {
    entry = JSON.parse(decodeURIComponent(fragment));
    const { title, start, end, port, nonce, expires } = entry;
    if (typeof title !== 'string' || !title.trim() || title.length > 200 ||
        !Number.isSafeInteger(start) || !Number.isSafeInteger(end) || end <= start ||
        end - start > 86400000 || !Number.isFinite(new Date(start).getTime()) ||
        !Number.isSafeInteger(expires) || expires <= Date.now() || expires > Date.now() + 310000 ||
        !Number.isInteger(port) || port < 1024 || port > 65535 || !/^[A-F0-9]{64}$/.test(nonce)) throw Error();
  } catch {
    save.hidden = true;
    message.textContent = 'Windowsアプリで「記録する」を押して開いてください。';
    return;
  }
  const callback = result => location.replace('http://127.0.0.1:' + entry.port + '/' + entry.nonce + '/' + result);
  const fmt = new Intl.DateTimeFormat('ja-JP', { dateStyle: 'medium', timeStyle: 'short' });
  get('title').textContent = entry.title;
  get('range').textContent = fmt.formatRange(new Date(entry.start), new Date(entry.end));
  cancel.hidden = false;
  cancel.onclick = () => callback('cancel');
  function load(src) {
    return new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.onload = resolve;
      s.onerror = reject;
      s.src = src;
      document.head.append(s);
    });
  }
  Promise.all([load('./calendar-v1.js'), load('https://accounts.google.com/gsi/client')])
    .then(() => { save.disabled = false; })
    .catch(() => { message.textContent = 'Google に接続できません。取り消して、接続後にお試しください。'; });
  save.onclick = async () => {
    if (save.disabled) return;
    if (Date.now() >= entry.expires) { save.disabled = true; message.textContent = '時間切れです。アプリからもう一度お試しください。'; return; }
    save.disabled = cancel.disabled = true;
    message.textContent = '保存中…';
    try {
      await writeRecord(entry.title, entry.start, entry.end);
      message.textContent = 'カレンダーに記録しました。';
      callback('saved');
    } catch (e) {
      message.textContent = e.message || '登録できませんでした。もう一度お試しください。';
      save.disabled = cancel.disabled = false;
    }
  };
})();

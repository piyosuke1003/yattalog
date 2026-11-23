const calendarScope = 'https://www.googleapis.com/auth/calendar.events.owned';
const listScope = 'https://www.googleapis.com/auth/calendar.calendarlist.readonly';
const clientId = '';
let token = '', validUntil = 0;
let selectedCalendar = '';

function authorize() {
  if (!clientId) throw new Error('Google カレンダーとの初期接続を設定中です。入力内容はこの画面に保持されています。');
  if (token && Date.now() < validUntil) return Promise.resolve();
  if (!window.google?.accounts?.oauth2) throw new Error('Google に接続できません。通信状況を確認してからもう一度押してください。');
  return new Promise((resolve, reject) => {
    const auth = google.accounts.oauth2.initTokenClient({
      client_id: clientId,
      scope: calendarScope + ' ' + listScope,
      callback: response => {
        if (response.error || !response.access_token) return reject(new Error('Google カレンダーへのアクセスを許可してください。'));
        if (!google.accounts.oauth2.hasGrantedAllScopes(response, calendarScope, listScope)) return reject(new Error('カレンダーの選択と予定の記録に必要な2つの権限を許可してください。'));
        token = response.access_token;
        validUntil = Date.now() + Math.max(0, Number(response.expires_in) - 60) * 1000;
        selectedCalendar = '';
        resolve();
      },
      error_callback: () => reject(new Error('Google の認証を完了できませんでした。もう一度「記録する」を押してください。'))
    });
    auth.requestAccessToken({prompt:''});
  });
}

async function request(path, options={}) {
  const result = await fetch('https://www.googleapis.com/calendar/v3/' + path, {
    ...options,
    cache: 'no-store',
    headers: {Authorization: 'Bearer ' + token, ...options.headers}
  });
  if (result.status === 401) {
    token = '';
    selectedCalendar = '';
    throw new Error('Google の認証が切れました。もう一度「記録する」を押してください。');
  }
  return result;
}

async function calendarId() {
  if (selectedCalendar) return selectedCalendar;
  let page = '';
  const matches = [];
  do {
    const params = new URLSearchParams({minAccessRole:'owner', showHidden:'true', maxResults:'250', fields:'nextPageToken,items(id,summary,summaryOverride,primary)'});
    if (page) params.set('pageToken', page);
    const result = await request('users/me/calendarList?' + params);
    if (!result.ok) throw new Error('カレンダー一覧を取得できませんでした。Google のアクセス許可を確認してください。');
    const data = await result.json();
    for (const c of data.items || []) {
      if (!c.primary && [c.summaryOverride, c.summary].some(name => (name || '').replace(/\s/g,'') === 'やったことリスト')) matches.push(c.id);
    }
    page = data.nextPageToken || '';
  } while (page);
  if (matches.length !== 1) throw new Error(matches.length ? '同じ名前のカレンダーが複数あります。名前を確認してください。' : 'このアカウントに「やったこと リスト」が見つかりません。Google カレンダーで名前を確認してください。');
  selectedCalendar = matches[0];
  return selectedCalendar;
}

async function writeRecord(title,start,end) {
  if (!navigator.onLine) throw new Error('オフラインです。接続後にもう一度押してください。');
  await authorize();
  const calendar = await calendarId();
  const bytes = new TextEncoder().encode(JSON.stringify([calendar,title,start,end]));
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  const id = 'd' + Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2,'0')).join('');
  const path = 'calendars/' + encodeURIComponent(calendar) + '/events';
  const event = {id, summary:title, start:{dateTime:new Date(start).toISOString()}, end:{dateTime:new Date(end).toISOString()}};
  const result = await request(path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(event)});
  if (result.ok) return;
  if (result.status === 409) {
    const found = await request(path + '/' + id);
    if (found.ok) {
      const previous = await found.json();
      if (previous.status !== 'cancelled' && previous.summary === title && Date.parse(previous.start?.dateTime) === start && Date.parse(previous.end?.dateTime) === end) return;
    }
  }
  if (result.status===403 || result.status===404) selectedCalendar='';
  throw new Error('カレンダーへの保存を確認できませんでした（' + result.status + '）。入力内容を保持しています。');
}

if ('serviceWorker' in navigator) window.addEventListener('load',() => navigator.serviceWorker.register('./sw.js').catch(()=>{}));
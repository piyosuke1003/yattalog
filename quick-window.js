(() => {
  const main=document.querySelector('main');
  const entry=document.getElementById('quick-window');
  const footer=entry.parentElement;
  const download=document.getElementById('android-download');
  const android=navigator.userAgentData?.platform==='Android'||/Android/i.test(navigator.userAgent);
  download.hidden=!android;
  if (!window.documentPictureInPicture || android) return;
  document.documentElement.classList.add('controlled');
  entry.hidden=false;
  entry.title='待機タイルにする';
  entry.setAttribute('aria-label',entry.title);

  entry.addEventListener('click',async () => {
    entry.disabled=true;
    let win;
    try {
      win=await documentPictureInPicture.requestWindow({width:151,height:113});
      const doc=win.document;
      doc.documentElement.lang='ja';
      doc.title='やったこと';
      for (const style of document.querySelectorAll('style')) doc.head.append(style.cloneNode(true));
      const tile=doc.createElement('div');
      tile.className='tile';
      const icon=entry.querySelector('img').cloneNode(true);
      icon.src=entry.querySelector('img').src;
      const open=doc.createElement('button');
      open.className='tile-primary';
      open.title='入力パネルを開く';
      open.setAttribute('aria-label',open.title);
      open.append(icon);
      const expand=doc.createElement('button');
      expand.className='tile-expand';
      expand.title='通常画面へ';
      expand.setAttribute('aria-label',expand.title);
      expand.textContent='⤢';
      tile.append(open,expand);
      const bar=doc.createElement('footer');
      bar.className='tools';
      const fold=entry.cloneNode(true);
      fold.removeAttribute('id');
      fold.disabled=false;
      fold.querySelector('img').src=icon.src;
      bar.append(fold);
      entry.hidden=true;
      main.hidden=true;
      function resize(w,h) {
        // resizeTo takes outer bounds, while the PiP request takes content bounds.
        try { win.resizeTo(w+win.outerWidth-win.innerWidth,h+win.outerHeight-win.innerHeight); } catch {}
      }
      function waitMode(shrink) {
        main.hidden=true;
        doc.documentElement.className='waiting';
        doc.body.replaceChildren(tile);
        if(shrink) resize(151,113);
        open.focus({preventScroll:true});
      }
      open.onclick=()=>{
        const selected=minutes;
        doc.documentElement.className='controlled compact';
        main.hidden=false;
        doc.body.replaceChildren(main,bar);
        resize(360,520);
        go(selected);
        main.querySelector('#title').focus({preventScroll:true});
      };
      fold.onclick=()=>waitMode(true);
      expand.onclick=()=>{ window.focus(); win.close(); };
      win.addEventListener('pagehide',()=>{
        const selected=minutes;
        main.hidden=false;
        document.body.insertBefore(main,footer);
        entry.hidden=false;
        go(selected);
        entry.focus({preventScroll:true});
      },{once:true});
      waitMode(false);
    } catch {
      if(win) win.close();
      if(main.parentElement!==document.body) document.body.insertBefore(main,footer);
      main.hidden=false;
      entry.hidden=false;
      main.querySelector('#status').textContent='待機タイルを開けませんでした。通常画面で記録できます。';
    } finally {
      entry.disabled=false;
    }
  });
})();

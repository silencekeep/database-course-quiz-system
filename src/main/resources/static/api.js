const API = (()=>{
  const state = { token: localStorage.getItem('token') || null };
  function setToken(t){ state.token=t; if(t) localStorage.setItem('token', t); }
  async function refreshToken(){
    if(!state.token) return false;
    try {
      const res = await fetch('/api/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:state.token})});
      if(!res.ok) return false; const data = await res.json(); if(data && data.token){ setToken(data.token); return true; }
      return false;
    } catch{ return false; }
  }
  async function req(path, opt={}, retry=true){
    opt.headers = opt.headers || {};
    if(state.token) opt.headers['X-Auth-Token'] = state.token;
    if(opt.body && !(opt.body instanceof FormData)) opt.headers['Content-Type'] = 'application/json';
    const res = await fetch(path, opt);
    let txt = await res.text();
    let data = null; try { data = txt? JSON.parse(txt): null; } catch{ data = { raw: txt }; }
    if(res.status===401){
      if(retry && await refreshToken()){
        return req(path, opt, false);
      }
      setToken(null); localStorage.removeItem('token'); if(location.pathname!=='/static/login.html'){ location.href='/static/login.html'; }
      throw { status:401, data:data||{} };
    }
    if(!res.ok){
      const msg = (data && (data.message||data.error)) ? (data.message||data.error) : ('请求失败 '+res.status);
      if(typeof toast==='function') toast(msg,'error');
      try { alert(msg); } catch(_){}
      throw { status: res.status, data };
    }
    return data;
  }
  // 受保护页面守卫：除 index/login/register 以外，必须已登录并通过 /api/auth/me
  async function guard(){
  const publicPages = ['/static/index.html','/static/login.html','/static/register.html','/static/survey.html','/',''];
    const path = location.pathname;
    if(publicPages.includes(path)) return; // 公共页不拦截
    if(!state.token){
      // 无 token 直接跳转
      location.href='/static/login.html';
      return;
    }
    try { await req('/api/auth/me'); }
    catch(e){
      // 任意失败都视为未登录
      try { alert('请先登录'); } catch(_){ }
      location.href='/static/login.html';
    }
  }
  return {
    token: ()=>state.token,
    setToken,
    register: (account,name,password)=>req('/api/auth/register',{method:'POST',body:JSON.stringify({account,name,password})}),
    login: (account,password)=>req('/api/auth/login',{method:'POST',body:JSON.stringify({account,password})}),
    me: ()=>req('/api/auth/me'),
    logout: ()=>req('/api/auth/logout',{method:'POST'}),
    createSurvey: (payload)=>req('/api/surveys',{method:'POST',body:JSON.stringify(payload)}),
    getSurvey: id=>req('/api/surveys/'+id),
    batchSubmit: (surveyId, arr)=>req('/api/surveys/'+surveyId+'/submitList',{method:'POST',body:JSON.stringify(arr)}),
    shareSurvey: (surveyId, allowEdit)=>req('/api/surveys/'+surveyId+'/share',{method:'POST',body:JSON.stringify({allow_edit:!!allowEdit})}),
  // 返回分页对象; 为兼容旧调用可在页面自行处理 data.items
  listMySurveys: (page=1)=>req('/api/surveys/mine?page='+page),
    listSharedSurveys: (page=1)=>req('/api/surveys/shared?page='+page),
    listShares: (surveyId)=>req('/api/surveys/'+surveyId+'/shares'),
    scoreboard: (surveyId)=>req('/api/surveys/'+surveyId+'/scoreboard'),
    updateSurvey: (id,data)=>req('/api/surveys/'+id,{method:'PUT',body:JSON.stringify(data)}),
    deleteSurvey: (id)=>req('/api/surveys/'+id,{method:'DELETE'}),
    injectUserNav: async ()=>{ if(!state.token) return; try { const u = await req('/api/auth/me'); const header=document.querySelector('header'); if(header && !header.querySelector('.userbox')){ const span=document.createElement('span'); span.className='userbox'; span.style.marginLeft='auto'; span.style.display='flex'; span.style.gap='8px'; span.innerHTML=`<span>${u.name||u.account}</span><button id="logoutBtn" class="secondary" style="padding:2px 8px;">退出</button>`; const nav=header.querySelector('nav')||header; nav.appendChild(span); span.querySelector('#logoutBtn').onclick=async ()=>{ try{ await req('/api/auth/logout',{method:'POST'}); }catch{} setToken(null); location.href='/static/login.html'; }; } } catch(e) { /* ignore */ } },
    refresh: refreshToken,
    guard,
  };
})();

// 将 API 挂到 window 以便 AppBoot.waitAPI 检测
try { if(typeof window!=='undefined' && !window.API) window.API = API; } catch(_){}

function $(q, root=document){ return root.querySelector(q); }
function el(tag, attrs={}, ...children){ const e=document.createElement(tag); Object.entries(attrs).forEach(([k,v])=>{ if(k==='class') e.className=v; else if(k==='html') e.innerHTML=v; else e.setAttribute(k,v);}); children.forEach(c=>{ if(typeof c==='string') e.appendChild(document.createTextNode(c)); else if(c) e.appendChild(c);}); return e; }
function toast(msg, type='info'){ let box = $('#toast-box'); if(!box){ box=el('div',{id:'toast-box',style:'position:fixed;top:12px;right:12px;display:flex;flex-direction:column;gap:8px;z-index:9999;'}); document.body.appendChild(box);} const t=el('div',{class:'panel',style:'padding:8px 12px;min-width:180px;background:#1f2530;font-size:13px;border-left:4px solid '+(type==='error'?'#ef4444':'#3b82f6')}, msg); box.appendChild(t); setTimeout(()=>t.remove(),3000); }
// 页面加载后立即执行访问守卫（api.js defer，DOM 解析完成后运行）
try { API.guard(); } catch(e){}

// 全局引导工具，统一等待 DOM & API 就绪
window.AppBoot = (function(){
  const domReady = new Promise(res=>{
    if(document.readyState==='loading') document.addEventListener('DOMContentLoaded', ()=>res()); else res();
  });
  function ready(fn){ domReady.then(()=>{ try{ fn(); }catch(e){ console.error('[AppBoot.ready] 执行出错', e); toast && toast('初始化失败: '+(e.message||''),'error'); } }); }
  function waitAPI(fn, opts={}){
    const {retries=20, interval=30} = opts;
    ready(()=>{
      let attempt=0;
      function loop(){
        if(typeof window.API!=='undefined'){
          try { fn(API); } catch(e){ console.error('[AppBoot.waitAPI] 回调出错', e); toast && toast('初始化出错: '+(e.message||''),'error'); }
          return;
        }
        if(++attempt>retries){ console.error('[AppBoot.waitAPI] API 未就绪(超出重试)'); toast && toast('页面初始化失败(API)','error'); return; }
        setTimeout(loop, interval);
      }
      loop();
    });
  }
  return { ready, waitAPI };
})();

// 捕获未处理错误，统一提示
window.addEventListener('error', e=>{ try { if(typeof toast==='function') toast('脚本错误: '+(e.message||''),'error'); } catch(_){ } });
window.addEventListener('unhandledrejection', e=>{ try { if(typeof toast==='function') toast('异步错误: '+(e.reason&&e.reason.message?e.reason.message: e.reason),'error'); } catch(_){ } });

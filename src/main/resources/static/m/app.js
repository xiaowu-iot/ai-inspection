/* AI 安全巡检 · 手机端 */
(function () {
  const { createApp, reactive, ref, computed, onMounted, watch } = Vue;
  const { showToast, showFailToast, showSuccessToast, showLoadingToast, closeToast, showDialog, showConfirmDialog, showImagePreview } = vant;

  const MAX_EDGE = 1568;
  const LEVEL_CLASS = { '一般': 'l1', '较大': 'l2', '重大': 'l3' };

  // ---------- 全局状态 ----------
  const store = reactive({ me: null, meta: null, pendingReview: 0, ready: false });
  const route = reactive({ path: '/', params: {} });

  const can = p => !!store.me && store.me.permissions.includes(p);
  const canSeeAllHazards = () => can('VIEW_ALL') || can('HAZARD_REVIEW');

  // ---------- 工具 ----------
  class ApiError extends Error {
    constructor(message, status, code) { super(message); this.status = status; this.code = code; }
  }
  async function api(path, { method = 'GET', body, form } = {}) {
    const opt = { method, headers: {}, credentials: 'same-origin' };
    if (form) opt.body = form;
    else if (body !== undefined) { opt.headers['Content-Type'] = 'application/json'; opt.body = JSON.stringify(body); }
    let resp;
    try { resp = await fetch('api' + path, opt); } catch (e) { throw new ApiError('网络连接失败，请检查网络', 0); }
    const ct = resp.headers.get('content-type') || '';
    const data = ct.includes('json') ? await resp.json() : null;
    if (resp.status === 401) {
      store.me = null;
      // 启动时检查登录状态由 boot 自己处理；在登录、注册这些公开页面也不跳
      if (store.ready && !current.def.pub) go('/login');
      throw new ApiError((data && data.message) || '请先登录', 401);
    }
    if (!resp.ok) throw new ApiError((data && data.message) || ('请求失败：' + resp.status), resp.status, data && data.code);
    return data;
  }
  function fail(e) {
    if (e && e.status === 401) return;
    showFailToast({ message: (e && e.message) || '操作失败', duration: 2600 });
  }
  function go(path) { if (location.hash !== '#' + path) location.hash = path; else parseRoute(); }
  function back() { history.length > 1 ? history.back() : go('/'); }
  const pad = n => String(n).padStart(2, '0');
  function fmt(s, withYear) {
    if (!s) return '';
    const d = new Date(s);
    const md = pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    return withYear ? d.getFullYear() + '-' + md : md;
  }
  const photoUrl = id => id ? 'api/photos/' + id : '';
  const lvClass = l => LEVEL_CLASS[l] || 'l1';

  // 手机原图动辄四五千像素、好几 MB，先压到 1568 像素以内再上传
  function compress(file) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => {
        const scale = Math.min(1, MAX_EDGE / Math.max(img.naturalWidth, img.naturalHeight));
        const c = document.createElement('canvas');
        c.width = Math.round(img.naturalWidth * scale);
        c.height = Math.round(img.naturalHeight * scale);
        c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
        URL.revokeObjectURL(img.src);
        c.toBlob(b => b ? resolve(b) : reject(new Error('图片压缩失败')), 'image/jpeg', 0.85);
      };
      img.onerror = () => reject(new Error('无法读取这张图片'));
      img.src = URL.createObjectURL(file);
    });
  }
  function pickImage(capture) {
    return new Promise(resolve => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = 'image/*';
      if (capture) input.capture = 'environment';
      input.onchange = () => resolve(input.files[0] || null);
      input.click();
    });
  }

  async function refreshMe() {
    const me = await api('/auth/me');
    store.me = me.user;
  }
  // 待审核隐患数，首页和底部"隐患"角标用
  async function refreshBadges() {
    if (!store.me || !can('HAZARD_REVIEW')) return;
    try {
      store.pendingReview = (await api('/hazards?status=PENDING_REVIEW&size=1')).total;
    } catch (e) { /* 角标刷新失败不打扰用户 */ }
  }

  // ---------- 通用组件 ----------
  const PhotoBoxes = {
    // src：直接指定图片地址，不传时按 photoId 取
    props: { photoId: String, src: String, hazards: { type: Array, default: () => [] } },
    methods: {
      style(h) {
        const [x1, y1, x2, y2] = h.box;
        return { left: x1 / 10 + '%', top: y1 / 10 + '%', width: (x2 - x1) / 10 + '%', height: (y2 - y1) / 10 + '%' };
      },
      // 框贴着照片顶边时，编号标签放到框里面，免得被裁掉
      cls(h) { return ((h.status === 'REJECTED' || h.status === 'DISCARDED') ? 'dim' : lvClass(h.level)) + (h.box[1] < 60 ? ' in' : ''); },
      preview() { showImagePreview([this.src || photoUrl(this.photoId)]); },
    },
    template: `<div class="photo" @click="preview">
      <img :src="src || ('api/photos/' + photoId)" alt="现场照片">
      <template v-for="(h, i) in hazards" :key="i">
        <div v-if="h.box" class="pbox" :class="cls(h)" :style="style(h)"><span>{{ i + 1 }}</span></div>
      </template>
    </div>`,
  };

  const Hero = {
    props: { title: String, back: Boolean, sub: String },
    emits: ['back'],
    template: `<div class="head"><van-nav-bar :title="title" :left-arrow="back" @click-left="$emit('back')" :border="false"><template #right><slot name="right"></slot></template></van-nav-bar></div>`,
  };

  // ---------- 登录 / 注册 ----------
  const LoginPage = {
    setup() {
      const form = reactive({ username: '', password: '' });
      const loading = ref(false);
      const options = ref({ allowRegister: false });
      onMounted(async () => { try { options.value = await api('/auth/options'); } catch (e) { } });
      async function submit() {
        if (!form.username || !form.password) return showToast('请输入用户名和密码');
        loading.value = true;
        try {
          const me = await api('/auth/login', { method: 'POST', body: form });
          store.me = me.user;
          refreshBadges();
          go('/');
        } catch (e) { fail(e); } finally { loading.value = false; }
      }
      return { form, loading, options, submit };
    },
    template: `<div class="auth">
      <div class="auth-brand">
        <div class="logo"><svg width="32" height="32" viewBox="0 0 64 64"><path d="M32 4 8 13v17c0 15 10 26 24 30 14-4 24-15 24-30V13L32 4z" fill="#fff" opacity=".95"/><path d="m21 32 8 8 15-16" fill="none" stroke="#1659d6" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/></svg></div>
        <h1>AI 安全巡检</h1>
        <p>拍照识别隐患 · 上报审核留痕</p>
      </div>
      <div class="auth-card">
        <van-form @submit="submit">
          <van-field v-model="form.username" label="用户名" placeholder="请输入用户名" left-icon="manager-o" autocomplete="username" />
          <van-field v-model="form.password" type="password" label="密码" placeholder="请输入密码" left-icon="lock" autocomplete="current-password" />
          <div style="margin-top:22px"><van-button round block type="primary" native-type="submit" :loading="loading" loading-text="登录中…">登 录</van-button></div>
        </van-form>
      </div>
      <div class="auth-foot" v-if="options.allowRegister">还没有账号？<a href="#/register">注册</a></div>
    </div>`,
  };

  // ---------- 首页 ----------
  const HomePage = {
    setup() {
      const sceneId = ref(localStorage.getItem('sceneId') || 'general');
      const location_ = ref(localStorage.getItem('location') || '');
      const recent = ref([]);
      const busy = ref(false);
      const scenes = computed(() => (store.meta && store.meta.scenes) || []);
      const greeting = computed(() => { const h = new Date().getHours(); return h < 6 ? '夜深了' : h < 12 ? '上午好' : h < 18 ? '下午好' : '晚上好'; });

      async function load() {
        try {
          await refreshMe();
          refreshBadges();
          const r = await api('/inspections?mine=true&size=5');
          recent.value = r.items;
        } catch (e) { fail(e); }
      }
      onMounted(load);
      function selectScene(id) { sceneId.value = id; localStorage.setItem('sceneId', id); }

      async function shoot(capture) {
        const file = await pickImage(capture);
        if (!file) return;
        localStorage.setItem('location', location_.value);
        let blob;
        try { blob = await compress(file); } catch (e) { return fail(e); }
        const note = await askNote(URL.createObjectURL(blob));
        if (note === null) return;
        busy.value = true;
        try {
          const form = new FormData();
          form.append('image', blob, 'photo.jpg');
          form.append('sceneId', sceneId.value);
          form.append('location', location_.value);
          form.append('note', note);
          const r = await api('/inspections', { method: 'POST', form });
          busy.value = false;
          if (r.error) showDialog({ title: '识别失败', message: r.error });
          go('/inspection/' + r.id);
        } catch (e) { busy.value = false; fail(e); }
      }
      return { store, scenes, sceneId, location_, recent, busy, greeting, can, selectScene, shoot, fmt, photoUrl, load };
    },
    template: `<div class="page">
      <div class="hero">
        <van-nav-bar title="AI 安全巡检" :border="false" />
        <div class="hero-body">
          <div class="hero-title">{{ greeting }}，{{ store.me && store.me.displayName }}</div>
          <div class="hero-sub">拍一张照片，AI 帮你找出现场的安全隐患</div>
        </div>
      </div>
      <div class="lift">
        <div class="card review-entry" v-if="can('HAZARD_REVIEW') && store.pendingReview > 0" @click="$router('/hazards')">
          <span>📋 有 <b>{{ store.pendingReview }}</b> 条隐患等待审核</span><van-icon name="arrow" />
        </div>
        <div class="card" v-if="can('INSPECT')">
          <div class="card-title">拍照识别</div>
          <div class="scenes">
            <div v-for="s in scenes" :key="s.id" class="scene" :class="{ on: s.id === sceneId }" @click="selectScene(s.id)"><span class="ic">{{ s.icon }}</span>{{ s.name }}</div>
          </div>
          <van-field v-model="location_" placeholder="位置（选填），如：二号车间东侧配电箱" left-icon="location-o" style="margin-top:12px;background:#f5f7fb;border-radius:10px" />
          <div class="shoot">
            <van-button type="primary" icon="photograph" @click="shoot(true)">拍照识别</van-button>
            <van-button plain type="primary" icon="photo-o" @click="shoot(false)">相册</van-button>
          </div>
          <div class="hint">尽量拍全景；压力表、标签等细节看不清时，凑近再补拍一张。<span v-if="can('HAZARD_REPORT')"> 也可以 <a href="#/report">人工上报隐患</a>。</span></div>
        </div>
        <div class="section">最近识别 <a href="#/history">全部 ›</a></div>
        <div v-if="recent.length === 0"><van-empty image-size="80" description="还没有识别记录" /></div>
        <div v-for="r in recent" :key="r.id" class="item" @click="$router('/inspection/' + r.id)">
          <img class="thumb" :src="photoUrl(r.photoId)" loading="lazy">
          <div class="meta">
            <div class="t">{{ r.sceneSummary || (r.error ? '识别失败' : r.sceneName) }}</div>
            <div class="s"><span>{{ r.sceneName }}</span><span v-if="r.location">· {{ r.location }}</span><span>· {{ fmt(r.createdAt) }}</span></div>
            <div class="s"><span v-if="r.error" class="st REJECTED">失败</span><span v-else-if="r.hazardCount" class="lv l2">{{ r.hazardCount }} 处隐患</span><span v-else class="st RECTIFIED">未发现隐患</span></div>
          </div>
        </div>
      </div>
      <van-overlay :show="busy" z-index="2000"><div class="overlay-box"><van-loading size="40" color="#fff" vertical>AI 正在查找隐患，请稍候…</van-loading></div></van-overlay>
    </div>`,
  };

  // ---------- 识别结果 ----------
  const HazardCard = {
    props: { h: Object, index: Number, showPhoto: Boolean, hideStatus: Boolean },
    setup(props) {
      const cls = computed(() => (props.h.status === 'REJECTED' || props.h.status === 'DISCARDED') ? 'dim' : lvClass(props.h.level));
      return { cls, lvClass };
    },
    template: `<div class="card hz" :class="cls">
      <div class="hz-head">
        <span class="no" v-if="index !== undefined">{{ index + 1 }}</span>
        <span class="lv" :class="lvClass(h.level)">{{ h.level }}</span>
        <span class="st" v-if="h.category">{{ h.category }}</span>
        <span class="st" :class="h.status" v-if="!hideStatus">{{ h.statusLabel }}</span>
      </div>
      <div class="desc">{{ h.description }}</div>
      <div class="advice" v-if="h.advice"><b>整改建议：</b>{{ h.advice }}</div>
      <div class="advice" v-if="h.reviewComment"><b>审核意见：</b>{{ h.reviewComment }}</div>
      <slot></slot>
    </div>`,
  };

  // 审核相关的操作放在一个 mixin 里，识别结果页和隐患详情页共用
  function useReview(reload) {
    const sheet = reactive({ show: false, hazard: null });
    const levels = [{ name: '一般' }, { name: '较大' }, { name: '重大' }];
    function openConfirm(h) { sheet.hazard = h; sheet.show = true; }
    async function doConfirm(action) {
      sheet.show = false;
      try {
        await api('/hazards/' + sheet.hazard.id + '/review', { method: 'POST', body: { action: 'CONFIRM', level: action.name } });
        showSuccessToast('已确认');
        reload();
      } catch (e) { fail(e); }
    }
    async function reject(h) {
      try {
        await showConfirmDialog({ title: '判定为误报？', message: '这条隐患会标记为误报。' });
      } catch (e) { return; }
      try {
        await api('/hazards/' + h.id + '/review', { method: 'POST', body: { action: 'REJECT', comment: '审核判定为误报' } });
        showToast('已标记误报');
        reload();
      } catch (e) { fail(e); }
    }
    async function selfReject(h) {
      try {
        await api('/hazards/' + h.id + '/false-positive', { method: 'POST', body: {} });
        showToast('已标记误报');
        reload();
      } catch (e) { fail(e); }
    }
    return { sheet, levels, openConfirm, doConfirm, reject, selfReject };
  }

  const InspectionPage = {
    components: { PhotoBoxes, HazardCard, Hero },
    setup() {
      const data = ref(null);
      const selected = ref([]);
      const submitting = ref(false);
      async function load() {
        try {
          data.value = await api('/inspections/' + route.params.id);
          // 待上报的默认全选，拍照人把误报的去掉就行
          selected.value = drafts.value.map(h => h.id);
        } catch (e) { fail(e); }
      }
      onMounted(load);
      const review = useReview(load);
      const isMine = h => store.me && h.reporter && h.reporter.id === store.me.id;
      const isOwner = computed(() => data.value && store.me && data.value.user && data.value.user.id === store.me.id);
      const drafts = computed(() => (data.value && data.value.hazards || []).filter(h => h.status === 'DRAFT'));
      const deciding = computed(() => isOwner.value && drafts.value.length > 0);
      function toggle(id) {
        const i = selected.value.indexOf(id);
        i >= 0 ? selected.value.splice(i, 1) : selected.value.push(id);
      }
      async function submit(ids) {
        if (ids.length === 0) {
          try { await showConfirmDialog({ title: '不上报？', message: '这次识别的结果都会标记为“已忽略”，审核员看不到。' }); } catch (e) { return; }
        }
        submitting.value = true;
        try {
          data.value = await api('/inspections/' + data.value.id + '/submit', { method: 'POST', body: { hazardIds: ids } });
          showSuccessToast(ids.length ? '已上报 ' + ids.length + ' 处，等待审核' : '已忽略');
        } catch (e) { fail(e); } finally { submitting.value = false; }
      }
      const canDelete = computed(() => isOwner.value && !data.value.deleted);
      async function removeIt() {
        if (!(await confirmDelete())) return;
        try {
          await api('/inspections/' + data.value.id, { method: 'DELETE' });
          showSuccessToast('已删除');
          go('/history');
        } catch (e) { fail(e); }
      }
      return { data, can, isMine, isOwner, drafts, deciding, selected, submitting, toggle, submit, fmt, back,
        canDelete, removeIt, ...review };
    },
    template: `<div class="page no-tab" :style="deciding && can('HAZARD_REPORT') ? 'padding-bottom:90px' : ''">
      <hero title="识别结果" back @back="back" />
      <div v-if="!data" style="padding:40px;text-align:center"><van-loading /></div>
      <template v-else>
        <div class="card notice" v-if="data.deleted" style="margin-top:14px;color:var(--l3)">这条记录已被拍照人删除，只有管理员能看到。</div>
        <div style="padding:14px 14px 0"><photo-boxes :photo-id="data.photoId" :hazards="data.hazards || []" /></div>
        <div class="card" style="margin-top:12px">
          <div class="card-title" style="margin-bottom:6px">
            <span v-if="data.error" style="color:var(--l3)">识别失败</span>
            <span v-else-if="data.hazardCount">发现 {{ data.hazardCount }} 处隐患</span>
            <span v-else style="color:var(--ok)">未发现明显隐患</span>
            <small>{{ data.sceneName }} · {{ (data.durationMs / 1000).toFixed(1) }} 秒</small>
          </div>
          <div class="hint" style="margin-top:0">{{ data.error || data.sceneSummary }}</div>
          <div class="note-show" v-if="data.note">📝 拍照说明：{{ data.note }}</div>
          <div class="hint" v-if="data.hazards && data.hazards.length">点开照片可放大；框选位置为大致范围。</div>
        </div>
        <div class="card notice" v-if="deciding && can('HAZARD_REPORT')">
          <b>请确认识别结果</b>：AI 可能误报。勾选确实存在的隐患后点“上报审核”，审核员才会收到；没勾选的会被忽略。
        </div>
        <div class="card notice" v-if="deciding && !can('HAZARD_REPORT')">
          识别结果只有你自己能看到。如需上报，请联系管理员开通巡检员权限。
        </div>
        <hazard-card v-for="(h, i) in data.hazards" :key="h.id" :h="h" :index="i">
          <div class="acts" v-if="h.status === 'DRAFT' && isOwner && can('HAZARD_REPORT')">
            <van-checkbox :model-value="selected.includes(h.id)" @click="toggle(h.id)" shape="square">确实存在，上报这条</van-checkbox>
          </div>
          <div class="acts" v-if="h.status === 'PENDING_REVIEW' && can('HAZARD_REVIEW')">
            <van-button size="small" type="primary" icon="passed" @click="openConfirm(h)">确认隐患</van-button>
            <van-button size="small" icon="close" @click="reject(h)">误报</van-button>
          </div>
          <div class="acts" v-else-if="h.status === 'PENDING_REVIEW' && isMine(h)">
            <van-button size="small" icon="close" @click="selfReject(h)">这条识别错了</van-button>
          </div>
        </hazard-card>
        <div class="card cj" v-if="data.cannotJudge && data.cannotJudge.length">
          <div class="card-title">需要人工确认或补拍</div>
          <ul><li v-for="(c, i) in data.cannotJudge" :key="i">{{ c }}</li></ul>
        </div>
        <div style="text-align:center;margin:6px 0 0" v-if="canDelete"><van-button size="small" plain type="danger" icon="delete-o" @click="removeIt">删除这条记录</van-button></div>
        <div class="foot-note">AI 结果仅供参考，请以现场人工确认为准<br>{{ data.analyzer }}<span v-if="data.inputTokens"> · token {{ data.inputTokens }} + {{ data.outputTokens }}</span> · {{ fmt(data.createdAt, true) }}</div>
      </template>
      <div class="actionbar" v-if="deciding && can('HAZARD_REPORT')">
        <van-button plain :loading="submitting" @click="submit([])" style="flex:0 0 32%">不上报</van-button>
        <van-button type="primary" :loading="submitting" :disabled="selected.length === 0" @click="submit(selected)">上报审核（{{ selected.length }}）</van-button>
      </div>
      <van-action-sheet v-model:show="sheet.show" :actions="levels" title="确认隐患等级" cancel-text="取消" @select="doConfirm" />
    </div>`,
  };

  // ---------- 作者推广 ----------
  // 公众号二维码 + 知识星球入口，配置 inspection.promo=false 可以关掉
  const PromoCard = {
    setup() {
      const show = ref(false);
      onMounted(async () => {
        try {
          if (store.promo === undefined) store.promo = (await api('/auth/options')).promo;
          show.value = !!store.promo;
        } catch (e) { }
      });
      return { show };
    },
    template: `<div class="card promo" v-if="show">
      <div class="card-title">关注作者</div>
      <div class="promo-row">
        <img class="promo-qr" src="img/wechat-qrcode.jpg" alt="公众号二维码">
        <div class="promo-txt">
          <b>公众号「小物学长」</b>
          <span>分享物联网和 AI 落地实战，这个巡检系统怎么做出来的也会在这里更新。</span>
          <small>微信里长按二维码关注，或搜索「小物学长」</small>
        </div>
      </div>
      <a class="promo-zsxq" href="https://t.zsxq.com/hldoY" target="_blank" rel="noopener">
        <div><b>🌟 知识星球「小物学长的物联网实战营」</b><small>物联网协议解析、设备接入网关源码、项目实战答疑</small></div>
        <van-icon name="arrow" />
      </a>
    </div>`,
  };

  const RegisterPage = {
    setup() {
      const form = reactive({ username: '', displayName: '', phone: '', password: '', confirm: '' });
      const loading = ref(false);
      const options = ref({ allowRegister: true });
      onMounted(async () => { try { options.value = await api('/auth/options'); } catch (e) { } });
      async function submit() {
        if (form.password !== form.confirm) return showToast('两次输入的密码不一致');
        loading.value = true;
        try {
          const me = await api('/auth/register', { method: 'POST', body: { username: form.username, displayName: form.displayName, phone: form.phone, password: form.password } });
          store.me = me.user;
          showSuccessToast('注册成功');
          go('/');
        } catch (e) { fail(e); } finally { loading.value = false; }
      }
      return { form, loading, options, submit };
    },
    template: `<div class="auth">
      <div class="auth-brand" style="padding-top:8vh">
        <h1>注册账号</h1>
        <p>注册后就可以拍照识别、上报隐患</p>
      </div>
      <div class="auth-card">
        <van-notice-bar v-if="!options.allowRegister" left-icon="info-o" text="系统暂未开放注册，请联系管理员开通账号" style="margin-bottom:10px;border-radius:8px" />
        <van-form @submit="submit">
          <van-field v-model="form.username" label="用户名" placeholder="3–20 位字母、数字" autocomplete="username" />
          <van-field v-model="form.displayName" label="姓名" placeholder="您的姓名" />
          <van-field v-model="form.phone" type="tel" label="手机号" placeholder="选填" />
          <van-field v-model="form.password" type="password" label="密码" placeholder="至少 6 位" autocomplete="new-password" />
          <van-field v-model="form.confirm" type="password" label="确认密码" placeholder="再输一次" autocomplete="new-password" />
          <div style="margin-top:22px"><van-button round block type="primary" native-type="submit" :loading="loading" :disabled="!options.allowRegister">注 册</van-button></div>
        </van-form>
      </div>
      <div class="auth-foot">已有账号？<a href="#/login">去登录</a></div>
    </div>`,
  };

  // ---------- 隐患 ----------
  const HazardsPage = {
    setup() {
      // "待上报"只会查到自己的
      const tabs = computed(() => canSeeAllHazards()
        ? [{ k: 'PENDING_REVIEW', t: '待审核' }, { k: 'CONFIRMED', t: '已确认' }, { k: 'REJECTED', t: '误报' }]
            .concat(can('INSPECT') ? [{ k: 'DRAFT', t: '我的待上报' }] : [])
        : [{ k: '', t: '全部' }, { k: 'DRAFT', t: '待上报' }, { k: 'PENDING_REVIEW', t: '待审核' }, { k: 'CONFIRMED', t: '已确认' }, { k: 'REJECTED', t: '误报' }]);
      const active = ref(sessionStorage.getItem('hazardTab') || tabs.value[0].k);
      if (!tabs.value.some(t => t.k === active.value)) active.value = tabs.value[0].k;
      const list = ref([]); const loading = ref(false); const finished = ref(false); const refreshing = ref(false);
      let page = 0;
      async function loadMore() {
        loading.value = true;
        try {
          const r = await api('/hazards?size=20&page=' + page + (active.value ? '&status=' + active.value : ''));
          list.value.push(...r.items);
          page++;
          finished.value = list.value.length >= r.total;
        } catch (e) { fail(e); finished.value = true; } finally { loading.value = false; refreshing.value = false; }
      }
      function reset() { list.value = []; page = 0; finished.value = false; loadMore(); refreshBadges(); }
      watch(active, v => { sessionStorage.setItem('hazardTab', v); reset(); });
      return { tabs, active, list, loading, finished, refreshing, loadMore, reset, fmt, photoUrl, lvClass, can };
    },
    template: `<div class="page">
      <div class="head"><van-nav-bar title="隐患" :border="false"><template #right><van-icon v-if="can('HAZARD_REPORT')" name="add-o" size="22" @click="$router('/report')" /></template></van-nav-bar></div>
      <van-tabs v-model:active="active" sticky :line-width="20">
        <van-tab v-for="t in tabs" :key="t.k" :name="t.k" :title="t.t" />
      </van-tabs>
      <van-pull-refresh v-model="refreshing" @refresh="reset">
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore" :immediate-check="true" style="padding-top:12px;min-height:60vh">
          <div v-for="h in list" :key="h.id" class="item" @click="$router('/hazard/' + h.id)">
            <img class="thumb" :src="photoUrl(h.photoId)" loading="lazy">
            <div class="meta">
              <div class="t">{{ h.description }}</div>
              <div class="s"><span class="lv" :class="lvClass(h.level)">{{ h.level }}</span><span class="st" :class="h.status">{{ h.statusLabel }}</span><span v-if="h.source === 'MANUAL'" class="st">人工</span></div>
              <div class="s"><span>{{ h.sceneName }}</span><span v-if="h.location">· {{ h.location }}</span><span>· {{ h.reporter && h.reporter.displayName }}</span><span>· {{ fmt(h.createdAt) }}</span></div>
            </div>
          </div>
          <van-empty v-if="finished && list.length === 0" description="暂无隐患" />
        </van-list>
      </van-pull-refresh>
    </div>`,
  };

  const HazardPage = {
    components: { PhotoBoxes, HazardCard, Hero },
    setup() {
      const data = ref(null);
      async function load() {
        // 接口直接返回隐患，包一层方便模板里写 data.hazard
        try { data.value = { hazard: await api('/hazards/' + route.params.id) }; } catch (e) { fail(e); }
      }
      onMounted(load);
      const review = useReview(load);
      const isMine = h => store.me && h.reporter && h.reporter.id === store.me.id;
      return { data, can, isMine, fmt, back, ...review };
    },
    template: `<div class="page no-tab">
      <hero title="隐患详情" back @back="back" />
      <div v-if="!data" style="padding:40px;text-align:center"><van-loading /></div>
      <template v-else>
        <div style="padding:14px 14px 12px"><photo-boxes :photo-id="data.hazard.photoId" :hazards="[data.hazard]" /></div>
        <hazard-card :h="data.hazard">
          <div class="acts" v-if="data.hazard.status === 'PENDING_REVIEW' && can('HAZARD_REVIEW')">
            <van-button size="small" type="primary" icon="passed" @click="openConfirm(data.hazard)">确认隐患</van-button>
            <van-button size="small" icon="close" @click="reject(data.hazard)">误报</van-button>
          </div>
          <div class="acts" v-else-if="data.hazard.status === 'PENDING_REVIEW' && isMine(data.hazard)">
            <van-button size="small" icon="close" @click="selfReject(data.hazard)">这条识别错了</van-button>
          </div>
        </hazard-card>
        <div class="card notice" v-if="data.hazard.status === 'DRAFT' && data.hazard.inspectionId">
          这条隐患还没上报，审核员看不到。<a :href="'#/inspection/' + data.hazard.inspectionId">去识别结果页上报 ›</a>
        </div>
        <div class="card">
          <div class="kv">
            <span class="k">来源</span><span>{{ data.hazard.source === 'AI' ? 'AI 识别' : '人工上报' }}</span>
            <span class="k">场景</span><span>{{ data.hazard.sceneName }}</span>
            <span class="k" v-if="data.hazard.location">位置</span><span v-if="data.hazard.location">{{ data.hazard.location }}</span>
            <span class="k">上报人</span><span>{{ data.hazard.reporter && data.hazard.reporter.displayName }}</span>
            <span class="k">发现时间</span><span>{{ fmt(data.hazard.createdAt, true) }}</span>
            <span class="k" v-if="data.hazard.reviewer">审核人</span><span v-if="data.hazard.reviewer">{{ data.hazard.reviewer.displayName }} · {{ fmt(data.hazard.reviewedAt) }}</span>
          </div>
        </div>
        <van-cell-group inset v-if="data.hazard.inspectionId">
          <van-cell title="查看原始识别结果" is-link @click="$router('/inspection/' + data.hazard.inspectionId)" icon="photo-o" />
        </van-cell-group>
      </template>
      <van-action-sheet v-model:show="sheet.show" :actions="levels" title="确认隐患等级" cancel-text="取消" @select="doConfirm" />
    </div>`,
  };

  // 选择器：单选，选项 [{text, value}]
  const SelectField = {
    props: { label: String, placeholder: String, options: Array, modelValue: [String, Number], required: Boolean },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
      const show = ref(false);
      const text = computed(() => { const o = (props.options || []).find(o => o.value === props.modelValue); return o ? o.text : ''; });
      function onConfirm({ selectedOptions }) { emit('update:modelValue', selectedOptions[0] && selectedOptions[0].value); show.value = false; }
      return { show, text, onConfirm };
    },
    template: `<div>
      <van-field :model-value="text" is-link readonly :label="label" :placeholder="placeholder" :required="required" @click="show = true" />
      <van-popup v-model:show="show" round position="bottom">
        <van-picker :columns="options" :title="label" @cancel="show = false" @confirm="onConfirm" />
      </van-popup>
    </div>`,
  };

  const ReportPage = {
    components: { Hero, SelectField },
    setup() {
      const form = reactive({ sceneId: localStorage.getItem('sceneId') || 'workshop', location: '', category: '', description: '', level: '一般', advice: '' });
      const files = ref([]);
      const saving = ref(false);
      const sceneOptions = computed(() => ((store.meta && store.meta.scenes) || []).map(s => ({ text: s.icon + ' ' + s.name, value: s.id })));
      const categoryOptions = ['人员防护', '消防', '电气', '物料与环境', '设备与车辆', '环卫专项', '其他'].map(c => ({ text: c, value: c }));
      async function submit() {
        if (files.value.length === 0) return showToast('请拍一张现场照片');
        if (!form.description.trim()) return showToast('请描述隐患情况');
        saving.value = true;
        try {
          const fd = new FormData();
          fd.append('image', await compress(files.value[0].file), 'photo.jpg');
          Object.entries(form).forEach(([k, v]) => fd.append(k, v || ''));
          const h = await api('/hazards', { method: 'POST', form: fd });
          showSuccessToast('已上报');
          history.replaceState(null, '', '#/hazard/' + h.id); parseRoute();
        } catch (e) { fail(e); } finally { saving.value = false; }
      }
      return { form, files, saving, sceneOptions, categoryOptions, submit, back };
    },
    template: `<div class="page no-tab">
      <hero title="人工上报隐患" back @back="back" />
      <van-cell-group inset title="现场照片" style="margin-top:6px">
        <div style="padding:12px 16px"><van-uploader v-model="files" :max-count="1" capture="environment" accept="image/*" preview-size="96" /></div>
      </van-cell-group>
      <van-cell-group inset title="隐患信息">
        <select-field v-model="form.sceneId" label="场景" :options="sceneOptions" required />
        <van-field v-model="form.location" label="位置" placeholder="如：2 号车间东侧通道" />
        <select-field v-model="form.category" label="类别" placeholder="选择类别" :options="categoryOptions" />
        <van-field label="等级" required><template #input><van-radio-group v-model="form.level" direction="horizontal"><van-radio name="一般">一般</van-radio><van-radio name="较大">较大</van-radio><van-radio name="重大">重大</van-radio></van-radio-group></template></van-field>
        <van-field v-model="form.description" label="隐患描述" type="textarea" rows="3" autosize required maxlength="500" show-word-limit placeholder="具体是什么问题、在什么位置" />
        <van-field v-model="form.advice" label="整改建议" type="textarea" rows="2" autosize maxlength="500" placeholder="选填" />
      </van-cell-group>
      <div style="margin:20px 14px"><van-button block round type="primary" :loading="saving" @click="submit">提交上报</van-button></div>
      <div class="hint" style="text-align:center">人工上报不消耗 AI 识别次数</div>
    </div>`,
  };


  // 选好照片后的确认框：预览照片，可以选填一段描述帮助 AI 理解。返回描述（可能为空字符串），点"重新选"返回 null
  function askNote(url) {
    return new Promise(resolve => {
      const host = document.createElement('div');
      document.body.appendChild(host);
      const app = createApp({
        setup() {
          const show = ref(true); const note = ref('');
          function start() { close(note.value.trim()); }
          const tips = ['这是配电房的总闸', '重点看看消防通道', '刚整改完，帮忙复查', '这里经常有人吸烟'];
          function close(v) { show.value = false; setTimeout(() => { app.unmount(); host.remove(); }, 300); resolve(v); }
          return { show, note, tips, url, close, start };
        },
        template: `<van-popup v-model:show="show" round position="bottom" :close-on-click-overlay="false" :style="{ padding: '18px 16px calc(16px + env(safe-area-inset-bottom))' }">
          <div style="font-weight:700;font-size:16px;text-align:center;margin-bottom:12px">确认照片</div>
          <img :src="url" style="width:100%;max-height:38vh;object-fit:contain;border-radius:10px;background:#111;display:block">
          <van-field v-model="note" type="textarea" rows="2" autosize maxlength="300" show-word-limit
            placeholder="补充说明（选填），比如这是什么地方、想重点看什么，能帮 AI 判断得更准"
            style="margin-top:12px;background:#f5f7fb;border-radius:10px" />
          <div class="note-tips"><span v-for="t in tips" :key="t" @click="note = note ? note + '；' + t : t">{{ t }}</span></div>
          <div style="display:flex;gap:10px;margin-top:14px">
            <van-button plain round style="flex:0 0 32%" @click="close(null)">重新选</van-button>
            <van-button type="primary" round style="flex:1" @click="start">开始识别</van-button>
          </div>
        </van-popup>`,
      });
      app.use(vant);
      app.mount(host);
    });
  }

  // ---------- 我的 ----------
  const MePage = {
    components: { PromoCard },
    setup() {
      onMounted(() => refreshMe().catch(fail));
      async function logout() {
        try { await showConfirmDialog({ title: '退出登录？' }); } catch (e) { return; }
        await api('/auth/logout', { method: 'POST' }).catch(() => { });
        store.me = null; go('/login');
      }
      // 中文名取最后两个字，英文名取首字母
      const initial = computed(() => {
        const n = store.me ? store.me.displayName : '';
        return /^[\x00-\x7f]+$/.test(n) ? n.charAt(0).toUpperCase() : n.slice(-2);
      });
      return { store, can, logout, initial };
    },
    template: `<div class="page">
      <div class="hero" style="padding-bottom:60px">
        <van-nav-bar title="我的" :border="false" />
        <div class="profile" v-if="store.me">
          <div class="avatar">{{ initial }}</div>
          <div>
            <div class="n">{{ store.me.displayName }}</div>
            <div style="font-size:12px;opacity:.75;margin-top:2px">{{ store.me.username }}<template v-if="store.me.department"> · {{ store.me.department }}</template></div>
            <div class="r"><span v-for="r in store.me.roles" :key="r.id">{{ r.name }}</span></div>
          </div>
        </div>
      </div>
      <div class="lift">
        <van-cell-group inset style="margin-bottom:12px">
          <van-cell title="我的识别记录" icon="photo-o" is-link @click="$router('/history')" />
          <van-cell v-if="can('HAZARD_REPORT')" title="人工上报隐患" icon="add-o" is-link @click="$router('/report')" />
          <van-cell title="修改密码" icon="lock" is-link @click="$router('/password')" />
          <van-cell title="登录和操作记录" icon="records-o" is-link @click="$router('/logs')" />
          <van-cell title="在线体验完整版" icon="gem-o" is-link url="https://inspection.xiaowuiot.com/" label="整改闭环、AI 纠错、管理后台" />
        </van-cell-group>
        <promo-card />
        <div style="margin:20px 14px"><van-button block round @click="logout">退出登录</van-button></div>
        <div class="foot-note">AI 安全巡检 · 开源基础版</div>
      </div>
    </div>`,
  };

  // 我的识别记录，左滑可删除
  const HistoryPage = {
    components: { Hero },
    setup() {
      const list = ref([]); const loading = ref(false); const finished = ref(false);
      let page = 0;
      async function loadMore() {
        loading.value = true;
        try {
          const r = await api('/inspections?mine=true&size=20&page=' + page);
          list.value.push(...r.items); page++;
          finished.value = list.value.length >= r.total;
        } catch (e) { fail(e); finished.value = true; } finally { loading.value = false; }
      }
      async function remove(r) {
        if (!(await confirmDelete())) return;
        try {
          await api('/inspections/' + r.id, { method: 'DELETE' });
          list.value.splice(list.value.indexOf(r), 1);
          showSuccessToast('已删除');
        } catch (e) { fail(e); }
      }
      return { list, loading, finished, loadMore, remove, fmt, photoUrl, back };
    },
    template: `<div class="page no-tab">
      <hero title="我的识别记录" back @back="back" />
      <div class="hint" style="margin:12px 16px 0">左滑一条记录可以删除。</div>
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore" style="padding-top:12px">
        <van-swipe-cell v-for="r in list" :key="r.id">
          <div class="item" @click="$router('/inspection/' + r.id)">
            <img class="thumb" :src="photoUrl(r.photoId)" loading="lazy">
            <div class="meta">
              <div class="t">{{ r.sceneSummary || (r.error ? '识别失败' : r.sceneName) }}</div>
              <div class="s"><span>{{ r.sceneName }}</span><span v-if="r.location">· {{ r.location }}</span><span>· {{ fmt(r.createdAt) }}</span></div>
              <div class="s"><span v-if="r.error" class="st REJECTED">失败</span><span v-else-if="r.hazardCount" class="lv l2">{{ r.hazardCount }} 处隐患</span><span v-else class="st RECTIFIED">未发现隐患</span></div>
            </div>
          </div>
          <template #right><van-button square type="danger" text="删除" class="swipe-del" @click="remove(r)" /></template>
        </van-swipe-cell>
        <van-empty v-if="finished && list.length === 0" description="还没有识别记录" />
      </van-list>
    </div>`,
  };

  async function confirmDelete() {
    try {
      await showConfirmDialog({ title: '删除这条识别记录？', message: '删除后你自己和其他人都看不到了。已经上报的隐患不受影响，会继续审核。' });
      return true;
    } catch (e) { return false; }
  }

  const PasswordPage = {
    components: { Hero },
    setup() {
      const form = reactive({ oldPassword: '', newPassword: '', confirm: '' });
      const saving = ref(false);
      async function submit() {
        if (form.newPassword !== form.confirm) return showToast('两次输入的新密码不一致');
        saving.value = true;
        try {
          await api('/auth/password', { method: 'POST', body: { oldPassword: form.oldPassword, newPassword: form.newPassword } });
          showSuccessToast('密码已修改'); back();
        } catch (e) { fail(e); } finally { saving.value = false; }
      }
      return { form, saving, submit, back };
    },
    template: `<div class="page no-tab">
      <hero title="修改密码" back @back="back" />
      <van-cell-group inset style="margin-top:14px">
        <van-field v-model="form.oldPassword" type="password" label="原密码" autocomplete="current-password" />
        <van-field v-model="form.newPassword" type="password" label="新密码" placeholder="至少 6 位" autocomplete="new-password" />
        <van-field v-model="form.confirm" type="password" label="确认新密码" autocomplete="new-password" />
      </van-cell-group>
      <div style="margin:20px 14px"><van-button block round type="primary" :loading="saving" @click="submit">保存</van-button></div>
    </div>`,
  };

  // 登录记录和操作记录：系统管理员看所有人，其他人只看自己
  const LogsPage = {
    components: { Hero },
    setup() {
      const tab = ref(route.params.tab === 'operations' ? 'operations' : 'logins');
      const all = can('ADMIN');
      const lists = reactive({
        logins: { items: [], page: 0, loading: false, finished: false },
        operations: { items: [], page: 0, loading: false, finished: false },
      });
      async function loadMore(k) {
        const l = lists[k];
        l.loading = true;
        try {
          const r = await api('/audit/' + k + '?size=20&page=' + l.page);
          l.items.push(...r.items); l.page++;
          l.finished = l.items.length >= r.total;
        } catch (e) { fail(e); l.finished = true; } finally { l.loading = false; }
      }
      const who = r => all ? (r.name || r.username) + ' · ' : '';
      return { tab, lists, loadMore, who, all, fmt, back };
    },
    template: `<div class="page no-tab">
      <hero title="登录和操作记录" back @back="back" />
      <div class="hint" style="margin:12px 16px 0">{{ all ? '你是系统管理员，这里显示所有人的记录。' : '这里只显示你自己的记录。' }}</div>
      <van-tabs v-model:active="tab" style="margin-top:8px">
        <van-tab title="登录记录" name="logins">
          <van-list v-model:loading="lists.logins.loading" :finished="lists.logins.finished" finished-text="没有更多了" @load="loadMore('logins')">
            <van-cell v-for="r in lists.logins.items" :key="r.id" :title="who(r) + r.actionLabel" :label="[r.client, r.browser, r.ip].filter(Boolean).join(' · ') + (r.message ? '，' + r.message : '')" :value="fmt(r.createdAt)" :title-class="r.action === 'LOGIN_FAILED' ? 'log-failed' : ''" />
          </van-list>
        </van-tab>
        <van-tab title="操作记录" name="operations">
          <van-list v-model:loading="lists.operations.loading" :finished="lists.operations.finished" finished-text="没有更多了" @load="loadMore('operations')">
            <van-cell v-for="r in lists.operations.items" :key="r.id" :title="who(r) + r.action + (r.targetId ? ' #' + r.targetId : '')" :label="(r.success ? '成功' : '失败' + (r.message ? '：' + r.message : '')) + ' · ' + [r.client, r.ip].filter(Boolean).join(' · ')" :value="fmt(r.createdAt)" :title-class="r.success ? '' : 'log-failed'" />
          </van-list>
        </van-tab>
      </van-tabs>
    </div>`,
  };

  // ---------- 路由 ----------
  const routes = [
    { re: /^\/login$/, c: LoginPage, pub: true },
    { re: /^\/register$/, c: RegisterPage, pub: true },
    { re: /^\/$/, c: HomePage, tab: 'home' },
    { re: /^\/hazards$/, c: HazardsPage, tab: 'hazards' },
    { re: /^\/me$/, c: MePage, tab: 'me' },
    { re: /^\/inspection\/(\d+)$/, c: InspectionPage, keys: ['id'] },
    { re: /^\/hazard\/(\d+)$/, c: HazardPage, keys: ['id'] },
    { re: /^\/report$/, c: ReportPage },
    { re: /^\/history$/, c: HistoryPage },
    { re: /^\/password$/, c: PasswordPage },
    { re: /^\/logs$/, c: LogsPage },
  ];
  const current = reactive({ def: routes[0], key: 0 });

  function parseRoute() {
    const raw = (location.hash || '#/').slice(1) || '/';
    const [path, query] = raw.split('?');
    const params = Object.fromEntries(new URLSearchParams(query || ''));
    for (const r of routes) {
      const m = path.match(r.re);
      if (m) {
        (r.keys || []).forEach((k, i) => params[k] = m[i + 1]);
        route.path = path; route.params = params;
        if (!r.pub && !store.me && store.ready) { go('/login'); return; }
        current.def = r; current.key++;
        window.scrollTo(0, 0);
        return;
      }
    }
    go('/');
  }
  window.addEventListener('hashchange', parseRoute);

  const App = {
    setup() {
      const tab = computed(() => current.def.tab);
      function switchTab(name) { go({ home: '/', hazards: '/hazards', me: '/me' }[name]); }
      return { current, tab, store, switchTab };
    },
    template: `<div v-if="store.ready">
      <component :is="current.def.c" :key="current.key" />
      <van-tabbar v-if="tab && store.me" :model-value="tab" @update:model-value="switchTab" safe-area-inset-bottom>
        <van-tabbar-item name="home" icon="home-o">首页</van-tabbar-item>
        <van-tabbar-item name="hazards" icon="warning-o" :badge="store.pendingReview || ''">隐患</van-tabbar-item>
        <van-tabbar-item name="me" icon="user-o">我的</van-tabbar-item>
      </van-tabbar>
    </div>`,
  };

  // 苹果手机弹出键盘时页面高度不变，键盘会盖住底部弹出层。
  // 用 visualViewport 算出键盘高度，写进 CSS 变量，由 app.css 把底部弹出层抬到键盘上方。
  function syncViewport() {
    const vv = window.visualViewport;
    if (!vv) return;
    const kb = Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop));
    document.documentElement.style.setProperty('--kb', kb + 'px');
    document.documentElement.style.setProperty('--vvh', Math.round(vv.height) + 'px');
  }
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', syncViewport);
    window.visualViewport.addEventListener('scroll', syncViewport);
    syncViewport();
  }

  async function boot() {
    try {
      store.meta = await api('/meta').catch(() => null);
      await refreshMe();
    } catch (e) { /* 未登录 */ }
    store.ready = true;
    if (store.me && !store.meta) store.meta = await api('/meta').catch(() => null);
    parseRoute();
    if (store.me) refreshBadges();
    setInterval(refreshBadges, 60000);
    watch(() => store.me, async me => { if (me && !store.meta) store.meta = await api('/meta').catch(() => null); });
  }

  const app = createApp(App);
  app.use(vant);
  app.config.globalProperties.$router = go;
  app.mount('#app');
  boot();
})();

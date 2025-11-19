// profile.js - Manage user profile and addresses

(function(){
  document.addEventListener('DOMContentLoaded', init);

  async function init(){
    try {
      // Auth guard
      if (!AuthHelper.isLoggedIn()) {
        Toast.warning('Vui lòng đăng nhập để chỉnh sửa hồ sơ');
        setTimeout(()=>{ window.location.href = 'index.html'; }, 1200);
        return;
      }
      // Safely call global cart badge updater if present
      try { window.updateCartBadge?.(); } catch(_) {}

      const u = AuthHelper.getUser();
      if (!u || !u.id){
        Toast.error('Thiếu thông tin người dùng trong phiên');
        return;
      }

      // Load profile + addresses
      await Promise.all([
        loadProfile(u.id),
        loadAddresses(u.id)
      ]);

      // Wire actions
      const form = document.getElementById('profileForm');
      form?.addEventListener('submit', (e)=> saveProfile(e, u.id));
      document.getElementById('pfRefresh')?.addEventListener('click', ()=> loadProfile(u.id));
      document.getElementById('addrRefresh')?.addEventListener('click', ()=> loadAddresses(u.id));
      document.getElementById('addrForm')?.addEventListener('submit', (e)=> addAddress(e, u.id));
      // Change password
      document.getElementById('pfChangePassword')?.addEventListener('click', ()=> openChangePasswordModal());
      const cpf = document.getElementById('changePwdForm');
      cpf?.addEventListener('submit', (e)=> changePassword(e, u.id));

      // Setup address map + geocoding listeners
      setupAddrMap();
      await setupVNAddressSelects();
      setupAddrGeocodeHandlers();
      // Wire 'use my location' if present
      const btnMyLoc = document.getElementById('adUseMyLoc');
      if (btnMyLoc){ btnMyLoc.addEventListener('click', useMyLocationProfile); }
    } catch (e) {
      console.warn('Profile init failed', e);
      Toast.error('Không thể tải trang hồ sơ');
    }
  }

  function openChangePasswordModal(){
    const m = document.getElementById('changePwdModal');
    if (m) m.style.display = 'flex';
    try { document.getElementById('cpCurrent').value=''; document.getElementById('cpNew').value=''; document.getElementById('cpConfirm').value=''; } catch(_){ }
  }

  async function changePassword(e, userId){
    e.preventDefault();
    try {
      const current = document.getElementById('cpCurrent')?.value || '';
      const pwd = document.getElementById('cpNew')?.value || '';
      const confirm = document.getElementById('cpConfirm')?.value || '';
      if (pwd.length < 6){ Toast.warning('Mật khẩu mới tối thiểu 6 ký tự'); return; }
      if (pwd !== confirm){ Toast.warning('Xác nhận mật khẩu không khớp'); return; }
      // Build update body using existing form values to satisfy backend validation
      const body = {
        username: document.getElementById('pfUsername')?.value || '',
        fullName: document.getElementById('pfFullName')?.value || '',
        email: document.getElementById('pfEmail')?.value || '',
        phone: document.getElementById('pfPhone')?.value || null,
        dateOfBirth: document.getElementById('pfDob')?.value || null,
        gender: (document.getElementById('pfGender')?.value || '').toUpperCase() || null,
        password: pwd
      };
      Loading.show();
      await APIHelper.put(API_CONFIG.ENDPOINTS.USER_UPDATE(userId), body);
      Toast.success('Đã đổi mật khẩu');
      const modal = document.getElementById('changePwdModal');
      if (modal) modal.style.display = 'none';
    } catch (err) {
      console.warn('changePassword error', err);
      Toast.error(err.message || 'Không thể đổi mật khẩu');
    } finally {
      Loading.hide();
    }
  }

  async function loadProfile(userId){
    try {
      Loading.show();
      // Prefer /api/v1 path via config
      const endpoint = API_CONFIG.ENDPOINTS.USER_BY_ID(userId);
      const res = await APIHelper.get(endpoint);
      const user = (res && res.result) || res;
      if (!user) throw new Error('User not found');

      // Fill form
      setValue('pfUsername', user.username || '');
      setValue('pfFullName', user.fullName || '');
      setValue('pfEmail', user.email || '');
      setValue('pfPhone', user.phone || '');
      // dateOfBirth may be LocalDate; try to format to yyyy-MM-dd
      const dob = user.dateOfBirth ? normalizeDate(user.dateOfBirth) : '';
      setValue('pfDob', dob);
      setValue('pfGender', (user.gender || '').toUpperCase());

      // Persist merged to localStorage for consistent header display
      try {
        const merged = { ...(AuthHelper.getUser()||{}), ...user };
        localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(merged));
      } catch(_){}
    } finally {
      Loading.hide();
    }
  }

  async function saveProfile(e, userId){
    e.preventDefault();
    try {
      Loading.show();
      const body = {
        username: getValue('pfUsername'),
        fullName: getValue('pfFullName'),
        email: getValue('pfEmail'),
        phone: getValue('pfPhone') || null,
        dateOfBirth: getValue('pfDob') || null,
        gender: (getValue('pfGender')||'').toUpperCase() || null,
        password: '' // keep empty to preserve existing password
      };
      const endpoint = API_CONFIG.ENDPOINTS.USER_UPDATE(userId);
      const res = await APIHelper.put(endpoint, body);
      const updated = (res && res.result) || res;
      Toast.success('Đã lưu thay đổi hồ sơ');
      // refresh view
      await loadProfile(userId);
    } catch (err) {
      console.warn('saveProfile error', err);
      Toast.error(err.message || 'Không thể lưu hồ sơ');
    } finally {
      Loading.hide();
    }
  }

  async function loadAddresses(userId){
    try {
      const listEl = document.getElementById('addrList');
      listEl.innerHTML = `<p class="text-gray">Đang tải...</p>`;
      const endpoint = API_CONFIG.ENDPOINTS.USER_ADDRESSES(userId);
      const res = await APIHelper.get(endpoint);
      const addrs = (res && res.result) || res || [];
      if (!addrs.length){
        listEl.innerHTML = `<p class="text-gray">Chưa có địa chỉ nào</p>`;
        return;
      }
      listEl.innerHTML = addrs.map(a => renderAddrRow(userId, a)).join('');
    } catch (e) {
      console.warn('loadAddresses error', e);
      document.getElementById('addrList').innerHTML = `<p class="text-danger">Không thể tải địa chỉ</p>`;
    }
  }

  function renderAddrRow(userId, a){
    const title = `${a.label || 'Địa chỉ'}${a.isDefault ? ' <span class="badge badge-success">Mặc định</span>' : ''}`;
    const loc = [a.ward, a.district, a.city, a.country].filter(Boolean).join(', ');
    return `
      <div class="mini-row" style="display:flex; justify-content:space-between; align-items:center; gap:.5rem; padding:.4rem .2rem; border-bottom:1px solid var(--light)">
        <div>
          <div style="font-weight:600">${title}</div>
          <div class="text-gray" style="font-size:.9rem">${a.addressLine || ''}</div>
          <div class="text-gray" style="font-size:.85rem">${loc}</div>
        </div>
        <div style="white-space:nowrap; display:flex; gap:.4rem;">
          ${!a.isDefault ? `<button class="btn btn-outline btn-sm" onclick="setDefaultAddr(${userId}, ${a.id})">Mặc định</button>` : ''}
          <button class="btn btn-outline btn-sm" onclick="editAddr(${userId}, ${a.id})">Sửa</button>
          <button class="btn btn-outline btn-sm" onclick="deleteAddr(${userId}, ${a.id})">Xoá</button>
        </div>
      </div>`;
  }

  async function addAddress(e, userId){
    e.preventDefault();
    try {
      const body = collectAddrForm();
      const endpoint = API_CONFIG.ENDPOINTS.USER_ADDRESSES(userId);
      await APIHelper.post(endpoint, body);
      Toast.success('Đã thêm địa chỉ');
      clearAddrForm();
      await loadAddresses(userId);
    } catch (e) {
      console.warn('addAddress error', e);
      Toast.error(e.message || 'Không thể thêm địa chỉ');
    }
  }

  // Simple inline edit via prompt for brevity
  async function editAddr(userId, addrId){
    try {
      const line = prompt('Cập nhật địa chỉ:', '');
      if (line == null) return;
      const body = { addressLine: line };
      const endpoint = API_CONFIG.ENDPOINTS.USER_ADDRESS_UPDATE(userId, addrId);
      await APIHelper.put(endpoint, body);
      Toast.success('Đã cập nhật địa chỉ');
      await loadAddresses(userId);
    } catch(e){
      Toast.error(e.message || 'Không thể cập nhật địa chỉ');
    }
  }

  async function deleteAddr(userId, addrId){
    try {
      if (!confirm('Xoá địa chỉ này?')) return;
      const endpoint = API_CONFIG.ENDPOINTS.USER_ADDRESS_DELETE(userId, addrId);
      await APIHelper.delete(endpoint);
      Toast.success('Đã xoá địa chỉ');
      await loadAddresses(userId);
    } catch(e){
      Toast.error(e.message || 'Không thể xoá địa chỉ');
    }
  }

  async function setDefaultAddr(userId, addrId){
    try {
      const endpoint = API_CONFIG.ENDPOINTS.USER_ADDRESS_SET_DEFAULT(userId, addrId);
      await APIHelper.put(endpoint, {});
      Toast.success('Đã đặt làm mặc định');
      await loadAddresses(userId);
    } catch(e){
      Toast.error(e.message || 'Không thể đặt mặc định');
    }
  }

  // Helpers
  function setValue(id, v){ const el = document.getElementById(id); if (el) el.value = v ?? ''; }
  function getValue(id){ const el = document.getElementById(id); return el ? el.value : ''; }
  function normalizeDate(d){
    try { // d might be '2025-11-17' or ISO datetime
      if (/^\d{4}-\d{2}-\d{2}$/.test(d)) return d;
      const dt = new Date(d);
      const y = dt.getFullYear();
      const m = String(dt.getMonth()+1).padStart(2,'0');
      const da = String(dt.getDate()).padStart(2,'0');
      return `${y}-${m}-${da}`;
    } catch(_) { return ''; }
  }
  function collectAddrForm(){
    return {
      label: getValue('adLabel') || null,
      receiverName: getValue('adReceiver') || null,
      phone: getValue('adPhone') || null,
      addressLine: getValue('adLine') || null,
      ward: getValue('adWard') || null,
      district: getValue('adDistrict') || null,
      city: getValue('adCity') || null,
      country: getValue('adCountry') || null,
      latitude: getValue('adLat') ? Number(getValue('adLat')) : null,
      longitude: getValue('adLng') ? Number(getValue('adLng')) : null,
      isDefault: document.getElementById('adDefault')?.checked || false
    };
  }
  function clearAddrForm(){
    ['adLabel','adReceiver','adPhone','adLine','adWard','adDistrict','adCity','adCountry','adLat','adLng'].forEach(id=> setValue(id,''));
    const cb = document.getElementById('adDefault'); if (cb) cb.checked = false;
    // Reset map to default view
    try { addrMapSetView([106.660172, 10.762622], 11); addrMapSetMarker(null); } catch(_){ }
  }

  // Expose a few functions for inline handlers
  window.setDefaultAddr = setDefaultAddr;
  window.editAddr = editAddr;
  window.deleteAddr = deleteAddr;

  // =======================
  // Address Map + Geocoding
  // =======================
  let addrMap, addrView, addrVectorSource, addrVectorLayer;
  let addrMapReadyTries = 0;
  let __adSearchDebounced;

  function setupAddrMap(){
    const el = document.getElementById('addrMap');
    if (!el) return;
    if (typeof ol === 'undefined'){
      if (addrMapReadyTries++ < 12){ // retry ~3.6s total
        return setTimeout(setupAddrMap, 300);
      }
      console.warn('OpenLayers not loaded; map disabled');
      return;
    }
    // Base map
    addrView = new ol.View({
      center: ol.proj.fromLonLat([106.660172, 10.762622]), // default HCMC
      zoom: 11
    });
    addrVectorSource = new ol.source.Vector({ features: [] });
    addrVectorLayer = new ol.layer.Vector({ source: addrVectorSource });
    addrMap = new ol.Map({
      target: el,
      layers: [
        new ol.layer.Tile({ source: new ol.source.OSM() }),
        addrVectorLayer
      ],
      view: addrView
    });
    // Click to set marker + lat/lng
    addrMap.on('click', async (evt)=>{
      try {
        const coord = ol.proj.toLonLat(evt.coordinate);
        const [lon, lat] = coord;
        setValue('adLat', Number(lat.toFixed(6)));
        setValue('adLng', Number(lon.toFixed(6)));
        addrMapSetMarker([lon, lat]);
        // Reverse geocode to fill address text automatically
        try {
          const rev = await reverseGeocode(lon, lat);
          if (rev && rev.display_name) {
            setValue('adLine', rev.display_name);
          }
        } catch(_){}
      } catch(_){ }
    });
    try {
      // Ensure map container has a minimum height for visibility
      el.style.minHeight = el.style.minHeight || '280px';
    } catch(_){ }
  }

  function addrMapSetView(lonlat, zoom){
    if (!addrView || !lonlat) return;
    addrView.setCenter(ol.proj.fromLonLat(lonlat));
    if (zoom) addrView.setZoom(zoom);
  }

  function addrMapSetMarker(lonlat){
    if (!addrVectorSource) return;
    addrVectorSource.clear();
    if (!lonlat) return;
    const Feature = ol.Feature;
    const Point = ol.geom.Point;
    const Style = ol.style.Style, CircleStyle = ol.style.Circle, Fill = ol.style.Fill, Stroke = ol.style.Stroke;
    const feat = new Feature({ geometry: new Point(ol.proj.fromLonLat(lonlat)) });
    feat.setStyle(new Style({ image: new CircleStyle({ radius: 7, fill: new Fill({ color: '#e74c3c' }), stroke: new Stroke({ color: '#ffffff', width: 2 }) }) }));
    addrVectorSource.addFeature(feat);
  }

  function setupAddrGeocodeHandlers(){
    const inputs = ['adLine','adCountry'];
    const selects = ['adWard','adDistrict','adCity'];
    const handler = debounce(async ()=>{
      const query = buildAddressQuery();
      if (!query) return;
      try {
        const res = await geocodeAddress(query);
        if (res){
          const { lat, lon, display_name } = res;
          const nlat = Number(lat), nlon = Number(lon);
          setValue('adLat', Number(nlat.toFixed(6)));
          setValue('adLng', Number(nlon.toFixed(6)));
          addrMapSetView([nlon, nlat], 16);
          addrMapSetMarker([nlon, nlat]);
          if (!getValue('adLine')) setValue('adLine', display_name || '');
        }
      } catch(e){ /* ignore */ }
    }, 500);

    inputs.forEach(id => document.getElementById(id)?.addEventListener('input', handler));
    selects.forEach(id => document.getElementById(id)?.addEventListener('change', handler));
    // If lat/lng manually edited, reflect on map
    // Lat/lng are readonly now; keep guard if changed programmatically
    document.getElementById('adLat')?.addEventListener('change', ()=> latlngChanged());
    document.getElementById('adLng')?.addEventListener('change', ()=> latlngChanged());

    // Autocomplete: input and suggestion list
    const searchEl = document.getElementById('adSearch');
    const suggEl = document.getElementById('adSuggestions');
    if (searchEl && suggEl){
      __adSearchDebounced = debounce(async ()=>{
        const q = (searchEl.value||'').trim();
        if (!q){ suggEl.style.display='none'; suggEl.innerHTML=''; return; }
        try {
          const url = `https://nominatim.openstreetmap.org/search?format=json&limit=6&addressdetails=1&accept-language=vi&countrycodes=vn&q=${encodeURIComponent(q)}`;
          const resp = await fetch(url, { headers: { 'Accept':'application/json' } });
          if (!resp.ok){ suggEl.style.display='none'; return; }
          const data = await resp.json();
          if (!Array.isArray(data) || data.length===0){ suggEl.style.display='none'; suggEl.innerHTML=''; return; }
          suggEl.innerHTML = data.map((it)=>{
            const name = it.display_name || '';
            const lat = it.lat, lon = it.lon;
            return `<div data-lat="${lat}" data-lon="${lon}" data-name="${encodeURIComponent(name)}" style="padding:.5rem .6rem; cursor:pointer; border-bottom:1px solid #f2f2f2; display:flex; align-items:center; gap:.5rem;">
              <i class='fas fa-location-dot' style='color:#888;'></i>
              <span>${escapeHtml(name)}</span>
            </div>`;
          }).join('');
          suggEl.style.display='block';
          // Click pick (use data attributes similar to Cart UI)
          Array.from(suggEl.children).forEach((child)=>{
            child.addEventListener('click', ()=>{
              const lat = parseFloat(child.getAttribute('data-lat'));
              const lon = parseFloat(child.getAttribute('data-lon'));
              const name = decodeURIComponent(child.getAttribute('data-name')||'');
              if (!Number.isFinite(lat) || !Number.isFinite(lon)) return;
              setValue('adLat', Number(lat.toFixed(6)));
              setValue('adLng', Number(lon.toFixed(6)));
              setValue('adLine', name || '');
              addrMapSetView([lon, lat], 16);
              addrMapSetMarker([lon, lat]);
              suggEl.style.display='none';
              suggEl.innerHTML='';
            });
          });
        } catch(_){ suggEl.style.display='none'; }
      }, 300);
      searchEl.addEventListener('input', __adSearchDebounced);
      // Toggle advanced section
      document.getElementById('adAdvancedToggle')?.addEventListener('click', ()=>{
        const adv = document.getElementById('adAdvanced');
        if (!adv) return;
        const isHidden = adv.style.display === 'none' || !adv.style.display;
        adv.style.display = isHidden ? 'grid' : 'none';
        const t = document.getElementById('adAdvancedToggle');
        if (t) t.innerHTML = isHidden ? '<i class="fas fa-chevron-up"></i> Ẩn nâng cao' : '<i class="fas fa-sliders-h"></i> Nâng cao';
      });
    // Also hide on blur with a slight delay to allow click
    searchEl.addEventListener('blur', ()=> setTimeout(()=>{ suggEl.style.display='none'; }, 200));
      // Hide suggestions when clicking outside
      document.addEventListener('click', (e)=>{
        if (!suggEl.contains(e.target) && e.target !== searchEl){
          suggEl.style.display='none';
        }
      });
    }
  }

  function buildAddressQuery(){
    const parts = [getValue('adLine'), getSelectedText('adWard'), getSelectedText('adDistrict'), getSelectedText('adCity'), getValue('adCountry')]
      .map(s => (s||'').trim())
      .filter(Boolean);
    return parts.join(', ');
  }

  async function geocodeAddress(query){
    const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&addressdetails=1&accept-language=vi&countrycodes=vn&q=${encodeURIComponent(query)}`;
    const resp = await fetch(url, { headers: { 'Accept': 'application/json' } });
    if (!resp.ok) return null;
    const arr = await resp.json();
    return Array.isArray(arr) && arr.length ? arr[0] : null;
  }

  async function reverseGeocode(lon, lat){
    try {
      const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${encodeURIComponent(lat)}&lon=${encodeURIComponent(lon)}&zoom=18&addressdetails=1&accept-language=vi`;
      const resp = await fetch(url, { headers: { 'Accept':'application/json' } });
      if (!resp.ok) return null;
      return await resp.json();
    } catch(_){ return null; }
  }

  function latlngChanged(){
    const lat = parseFloat(getValue('adLat'));
    const lon = parseFloat(getValue('adLng'));
    if (Number.isFinite(lat) && Number.isFinite(lon)){
      addrMapSetView([lon, lat], 16);
      addrMapSetMarker([lon, lat]);
    }
  }

  async function useMyLocationProfile(){
    if (!('geolocation' in navigator)){
      Toast.error('Trình duyệt không hỗ trợ GPS');
      return;
    }
    Loading.show();
    navigator.geolocation.getCurrentPosition(async (pos)=>{
      try{
        const lat = pos.coords.latitude;
        const lon = pos.coords.longitude;
        setValue('adLat', Number(lat.toFixed(6)));
        setValue('adLng', Number(lon.toFixed(6)));
        addrMapSetView([lon, lat], 16);
        addrMapSetMarker([lon, lat]);
        const rev = await reverseGeocode(lon, lat);
        if (rev && rev.display_name){
          setValue('adLine', rev.display_name);
        }
      } finally { Loading.hide(); }
    }, (err)=>{
      Loading.hide();
      let msg = 'Không thể lấy vị trí. ';
      if (err && err.code === err.PERMISSION_DENIED) msg += 'Bạn đã từ chối quyền truy cập vị trí.';
      else if (err && err.code === err.POSITION_UNAVAILABLE) msg += 'Không có thông tin vị trí.';
      else if (err && err.code === err.TIMEOUT) msg += 'Quá thời gian lấy vị trí.';
      else msg += 'Vui lòng thử lại.';
      Toast.error(msg);
    }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 });
  }

  function debounce(fn, wait){
    let t; return (...args)=>{ clearTimeout(t); t = setTimeout(()=>fn.apply(null,args), wait); };
  }

  function getSelectedText(id){
    const el = document.getElementById(id);
    if (!el) return '';
    const opt = el.options?.[el.selectedIndex];
    return opt ? opt.text : (el.value || '');
  }

  // ==========================
  // VN Province/District/Ward
  // ==========================
  async function setupVNAddressSelects(){
    const citySel = document.getElementById('adCity');
    const distSel = document.getElementById('adDistrict');
    const wardSel = document.getElementById('adWard');
    if (!citySel || !distSel || !wardSel) return;

    const data = await getVNAdminData();
    if (!data || !Array.isArray(data)) return;

    // Populate provinces
    citySel.innerHTML = '<option value="">-- Chọn tỉnh/thành --</option>' + data.map(p=>`<option value="${escapeHtml(p.code)}">${escapeHtml(p.name)}</option>`).join('');
    citySel.disabled = false;
    distSel.disabled = true; wardSel.disabled = true;

    citySel.addEventListener('change', ()=>{
      const p = data.find(x=> String(x.code) === citySel.value);
      const dists = p?.districts || [];
      distSel.innerHTML = '<option value="">-- Chọn quận/huyện --</option>' + dists.map(d=>`<option value="${escapeHtml(d.code)}">${escapeHtml(d.name)}</option>`).join('');
      distSel.disabled = dists.length===0;
      wardSel.innerHTML = '<option value="">-- Chọn phường/xã --</option>';
      wardSel.disabled = true;
    });

    distSel.addEventListener('change', ()=>{
      const p = data.find(x=> String(x.code) === citySel.value);
      const d = (p?.districts || []).find(x=> String(x.code) === distSel.value);
      const wards = d?.wards || [];
      wardSel.innerHTML = '<option value="">-- Chọn phường/xã --</option>' + wards.map(w=>`<option value="${escapeHtml(w.code)}">${escapeHtml(w.name)}</option>`).join('');
      wardSel.disabled = wards.length===0;
    });
  }

  async function getVNAdminData(){
    const CACHE_KEY = 'VN_ADMIN_DATA_V1';
    try {
      const cached = localStorage.getItem(CACHE_KEY);
      if (cached) return JSON.parse(cached);
    } catch(_){ }
    try {
      const resp = await fetch('https://provinces.open-api.vn/api/?depth=3', { headers: { 'Accept': 'application/json' } });
      if (!resp.ok) throw new Error('fetch provinces failed');
      const data = await resp.json();
      try { localStorage.setItem(CACHE_KEY, JSON.stringify(data)); } catch(_){ }
      return data;
    } catch(e){
      console.warn('Failed to load provinces API', e);
      return null;
    }
  }

  function escapeHtml(s){
    return String(s).replace(/[&<>"]+/g, c=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;' }[c]));
  }
})();

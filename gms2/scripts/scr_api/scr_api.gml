// ── 서버 호출 ──
// 앱과 같은 서버 기능(/my/api/...)을 쓴다. 로그인 결과로 받은 열쇠(token)를 x-gmm-s 머리글로 보낸다(앱과 같은 방식).
// GML 함수는 바깥 지역 변수를 기억하지 못하므로, 응답을 받을 함수(cb)와 함께 필요한 값(ctx)을 넘긴다: cb(ok, data, code, ctx)

function api_init() {
	global.api_pending = {};
	global.api_token = "";
	ini_open("galmaegiman.ini");
	global.api_token = ini_read_string("auth", "token", "");
	ini_close();
}

function api_save_token(_t) {
	global.api_token = _t;
	ini_open("galmaegiman.ini");
	ini_write_string("auth", "token", _t);
	ini_close();
}

/// HTML5로 같은 서버에서 돌리면 상대 주소(로그인 쿠키·출처 문제 없음), 그 밖에는 운영 서버 주소
function api_base() {
	if (os_browser != browser_not_a_browser) return "";
	return SERVER_URL;
}

function api_request(_method, _path, _body, _cb, _ctx) {
	var _h = ds_map_create();
	ds_map_add(_h, "x-gmm", "1");
	ds_map_add(_h, "x-gmm-app", "1");
	// 앱과 같은 「현재 증강(current-v3)」 전투를 처리한다는 호환 표시(권한이 아님)
	ds_map_add(_h, "x-gmm-roster60-augment-capability", "current-v3");
	ds_map_add(_h, "x-gmm-roster60-augment-model", "roster60-current-v1");
	ds_map_add(_h, "x-gmm-craft-discovery", "1");
	if (global.api_token != "") ds_map_add(_h, "x-gmm-s", global.api_token);
	var _data = "";
	if (_method == "POST") {
		ds_map_add(_h, "Content-Type", "application/json");
		_data = json_stringify(is_struct(_body) ? _body : {});
	}
	var _id = http_request(api_base() + "/my/api/" + _path, _method, _h, _data);
	ds_map_destroy(_h);
	if (_id < 0) {
		_cb(false, undefined, 0, _ctx);
		return -1;
	}
	global.api_pending[$ string(_id)] = { cb: _cb, ctx: _ctx, path: _path };
	global.ui.busy += 1;
	return _id;
}

function api_get(_path, _cb, _ctx) { return api_request("GET", _path, undefined, _cb, _ctx); }
function api_post(_path, _body, _cb, _ctx) { return api_request("POST", _path, _body, _cb, _ctx); }
function r60(_p) { return "native/roster60/" + _p; }

/// obj_app의 비동기 HTTP 이벤트에서 부른다
function api_async_http() {
	var _key = string(async_load[? "id"]);
	var _p = global.api_pending[$ _key];
	if (is_undefined(_p)) return;
	var _status = async_load[? "status"];
	if (_status == 1) return;   // 아직 받는 중
	variable_struct_remove(global.api_pending, _key);
	global.ui.busy = max(0, global.ui.busy - 1);
	var _code = async_load[? "http_status"];
	if (is_undefined(_code)) _code = 0;
	var _res = async_load[? "result"], _data = undefined;
	if (is_string(_res) && _res != "") {
		try { _data = json_parse(_res); } catch (_e) { _data = undefined; }
	}
	var _ok = (_status == 0) && _code >= 200 && _code < 300;
	if (_code == 401) {   // 로그인이 풀렸으면 로그인 화면으로
		api_save_token("");
		ui_toast("다시 로그인해 주세요.");
		go("login");
		return;
	}
	var _cb = _p.cb;
	_cb(_ok, _data, _code, _p.ctx);
}

/// 실패면 알림을 띄우고 true(호출한 쪽은 바로 끝내면 됨)
function api_failed(_ok, _data, _code) {
	if (_ok) return false;
	ui_toast(error_text(_data, _code));
	return true;
}

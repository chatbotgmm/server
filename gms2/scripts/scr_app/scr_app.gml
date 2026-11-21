// ── 앱 흐름: 시작 · 로그인 · 화면 이동 · 그리기 순서 ──

function app_init() {
	randomise();
	ui_init();
	api_init();
	assets_init();
	global.me = { credits: "-", nickname: "" };
	global.screen = "";
	global.S = {};
	global.dialog = -1;
	global.prefs = prefs_load();
	if (os_type == os_windows || os_type == os_macosx || os_type == os_linux) {
		window_set_size(1280, 720);
		window_center();
	}
	go("boot");
}

/// 화면 이동. 화면마다 상태(global.S)를 새로 만든다
function go(_name, _arg = undefined) {
	global.screen = _name;
	global.S = { arg: _arg };
	ui_list_reset(_name);
	switch (_name) {
		case "boot": boot_enter(); break;
		case "village": village_enter(); break;
		case "recruit": recruit_enter(); break;
		case "recruit_result": recruit_result_enter(); break;
		case "units": units_enter(); break;
		case "story": story_enter(); break;
		case "scene": break;
		case "formation": formation_enter(); break;
		case "battle": battle_enter(); break;
		case "settle": settle_enter(); break;
	}
}

/// obj_app 「Draw GUI」
function app_draw() {
	var _u = global.ui;
	draw_set_alpha(1);
	draw_rectangle_colour(0, 0, _u.GW, _u.GH, C_BACKDROP, C_BACKDROP, C_BACKDROP, C_BACKDROP, false);
	matrix_set(matrix_world, matrix_build(_u.ox, _u.oy, 0, 0, 0, 0, 1, 1, 1));
	_u.block = !is_undefined(_u.confirm);
	switch (global.screen) {
		case "boot": boot_draw(); break;
		case "login": login_draw(); break;
		case "village": village_draw(); break;
		case "recruit": recruit_draw(); break;
		case "recruit_result": recruit_result_draw(); break;
		case "units": units_draw(); break;
		case "story": story_draw(); break;
		case "scene": scene_draw(); break;
		case "formation": formation_draw(); break;
		case "battle": battle_draw(); break;
		case "settle": settle_draw(); break;
	}
	ui_draw_overlays();
	matrix_set(matrix_world, matrix_build_identity());
}

/// 배경: 마을 파노라마를 화면에 꽉 채우고 어둡게
function app_backdrop(_dark = 0.72) {
	var _u = global.ui;
	draw_cover("village-identity-coast-panorama", 0, 0, _u.W, _u.H);
	ui_rect(0, 0, _u.W, _u.H, C_BACKDROP, _dark, 0);
}

// ── 설정 기억(정렬·묶기) ──
function prefs_load() {
	ini_open("galmaegiman.ini");
	var _p = { sort: ini_read_string("prefs", "sort", "level"), group: ini_read_real("prefs", "group", 1) == 1 };
	ini_close();
	return _p;
}
function prefs_save() {
	ini_open("galmaegiman.ini");
	ini_write_string("prefs", "sort", global.prefs.sort);
	ini_write_real("prefs", "group", global.prefs.group ? 1 : 0);
	ini_close();
}

// ── 시작: 로그인 확인 → 첫 갈매미(처음 한 번) → 마을 ──
function boot_enter() {
	global.S.error = "";
	if (global.api_token == "") { go("login"); return; }
	api_get("who", boot_who_cb, undefined);
}
function boot_who_cb(_ok, _data, _code, _ctx) {
	if (!_ok) { global.S.error = error_text(_data, _code); return; }
	global.me.credits = jv(_data, "credits", 0);
	global.me.nickname = jv(_data, "nickname", "");
	api_get(r60("state"), boot_state_cb, undefined);
}
function boot_state_cb(_ok, _data, _code, _ctx) {
	if (!_ok) { global.S.error = error_text(_data, _code); return; }
	if (jv(_data, "started", true) == false) api_post(r60("start"), {}, boot_start_cb, undefined);
	else go("village");
}
function boot_start_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code)) return;
	ui_toast("방범대에 온 것을 환영해요! 첫 동료 " + jv(jv(_data, "starter", {}), "name", "") + "가 합류했어요.");
	go("village");
}
function boot_draw() {
	var _u = global.ui;
	app_backdrop();
	if (global.S.error == "") { ui_text(_u.W / 2, _u.H / 2, "불러오는 중…", global.F18, C_MUTED, fa_center, fa_middle); return; }
	ui_panel(_u.W / 2 - 280, _u.H / 2 - 90, 560, 180);
	ui_text_wrap(_u.W / 2 - 256, _u.H / 2 - 70, global.S.error, global.F18, C_TEXT, 512);
	if (ui_button(_u.W / 2 - 100, _u.H / 2 + 20, 200, 52, "다시 시도", "primary")) go("boot");
}

// ── 로그인: 카톡 6자리 코드(앱의 「카톡 코드로 로그인」과 같음) ──
function login_draw() {
	var _u = global.ui;
	app_backdrop(0.55);
	var _w = 560, _h = 270, _x = (_u.W - _w) / 2, _y = (_u.H - _h) / 2;
	ui_panel(_x, _y, _w, _h);
	ui_text(_x + 28, _y + 26, "갈매미맨 방범대", global.F28, C_TEXT);
	ui_text_wrap(_x + 28, _y + 76, "앱과 같은 계정으로 이어서 해요. 카카오톡 「갈매미맨」 채널에 「웹 로그인」이라고 보내면 6자리 코드가 와요.", global.F16, C_MUTED, _w - 56);
	if (ui_button(_x + 28, _y + _h - 28 - 56, _w - 56, 56, "카톡 코드로 로그인", "primary")) {
		global.dialog = get_string_async("카카오톡에서 받은 6자리 코드를 적어 주세요.", "");
	}
}
/// obj_app 「비동기 - 대화창」
function app_async_dialog() {
	if (async_load[? "id"] != global.dialog) return;
	global.dialog = -1;
	if (!async_load[? "status"]) return;
	var _code = string_digits(string(async_load[? "result"]));
	if (string_length(_code) != 6) { ui_toast("6자리 숫자를 적어 주세요."); return; }
	api_post("login", { code: _code }, login_cb, undefined);
}
function login_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code)) return;
	var _t = jv(_data, "token", "");
	if (_t == "") { ui_toast("로그인 열쇠를 받지 못했어요. 다시 시도해 주세요."); return; }
	api_save_token(_t);
	go("boot");
}
function app_logout(_ctx) {
	api_save_token("");
	go("login");
}

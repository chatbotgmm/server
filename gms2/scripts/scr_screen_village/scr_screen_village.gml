// ── 마을(로비): 갈매미 건물이 메뉴 ──

function village_enter() {
	global.S.story = undefined;
	api_get(r60("story/state"), village_story_cb, undefined);
}
function village_story_cb(_ok, _data, _code, _ctx) {
	if (_ok && global.screen == "village") global.S.story = _data;
}

function village_draw() {
	var _u = global.ui, _S = global.S;
	// 하늘·바다 + 파노라마
	draw_rectangle_colour(0, 0, _u.W, _u.H, make_colour_rgb(90, 160, 216), make_colour_rgb(90, 160, 216), make_colour_rgb(217, 238, 248), make_colour_rgb(217, 238, 248), false);
	draw_art("village-identity-coast-panorama", -40, _u.H - 430, _u.W + 80, 430);
	ui_rect(0, _u.H - 150, _u.W, 150, C_BACKDROP, 0.6, 0);
	// 건물 4채(누르면 이동). 준비 중인 곳은 흐리게
	var _b = [
		{ art: "village-identity-hq", w: 250, title: "동료의 거리", sub: "모집 · 내 갈매미", to: "recruit", on: true },
		{ art: "village-identity-records", w: 230, title: "기억의 정원", sub: "도감 (다음 단계)", to: "", on: false },
		{ art: "village-identity-ship", w: 230, title: "푸른 출항길", sub: "스토리", to: "story", on: true },
		{ art: "village-identity-market", w: 230, title: "바람의 시장", sub: "시장 (다음 단계)", to: "", on: false }
	];
	var _n = array_length(_b), _gap = (_u.W - 940) / (_n + 1), _x = _gap, _by = _u.H - 92;
	for (var _i = 0; _i < _n; _i++) {
		var _e = _b[_i], _h = _e.w * 0.7;
		draw_art(_e.art, _x, _by - _h, _e.w, _h, _e.on ? c_white : c_gray);
		var _pw = 170, _px = _x + (_e.w - _pw) / 2;
		ui_rect(_px, _by - 8, _pw, 46, C_HEADER, 0.95);
		ui_outline(_px, _by - 8, _pw, 46, C_GOLD);
		ui_text(_px + _pw / 2, _by + 6, _e.title, global.F16, C_TEXT, fa_center, fa_middle);
		ui_text(_px + _pw / 2, _by + 26, _e.sub, global.F14, C_MUTED, fa_center, fa_middle);
		if (_e.on && global.ui.busy == 0 && ui_click(_x, _by - _h, _e.w, _h + 40)) go(_e.to);
		_x += _e.w + _gap;
	}
	// 위: 이름·방범대장 레벨·지원서·로그아웃
	var _cmd = jv(_S.story, "commander", undefined);
	ui_rect(8, 6, 220, 52, C_HEADER, 1); ui_outline(8, 6, 220, 52, C_EDGE);
	ui_text_fit(20, 14, global.me.nickname == "" ? "방범대장" : global.me.nickname, global.F18, C_TEXT, 196);
	ui_text(20, 38, is_undefined(_cmd) ? "방범대" : "방범대장 Lv." + string(jv(_cmd, "level", 1)), global.F14, C_MUTED);
	ui_rect(_u.W - 300, 12, 158, 40, C_SLOT, 1, 20); ui_outline(_u.W - 300, 12, 158, 40, C_EDGE, 20);
	draw_art("menu-currency-application", _u.W - 292, 18, 28, 28);
	ui_text(_u.W - 258, 32, "지원서 " + string(global.me.credits), global.F16, C_TEXT, fa_left, fa_middle);
	if (ui_button(_u.W - 132, 12, 120, 40, "로그아웃", "quiet")) ui_confirm("로그아웃할까요? 다시 들어올 때 카톡 코드가 필요해요.", app_logout, undefined);
	// 아래: 바로가기
	var _dock = [["스토리", "story", "menu-icon-explore", true], ["모집", "recruit", "menu-icon-draw", true], ["내 갈매미", "units", "menu-icon-team", true], ["조합", "", "menu-icon-combine", false], ["도감", "", "menu-icon-collection", false], ["시장", "", "menu-icon-shop", false]];
	var _dw = array_length(_dock) * 104 + 12, _dx = (_u.W - _dw) / 2, _dy = _u.H - 80;
	ui_panel(_dx, _dy, _dw, 72);
	for (var _j = 0; _j < array_length(_dock); _j++) {
		var _d = _dock[_j], _bx = _dx + 8 + _j * 104;
		var _hot = _d[3] && ui_hover(_bx, _dy + 6, 96, 60);
		ui_rect(_bx, _dy + 6, 96, 60, _hot ? C_HEADER : C_SLOT, _d[3] ? 1 : 0.5);
		ui_outline(_bx, _dy + 6, 96, 60, _hot ? C_GOLD : C_LINE);
		draw_art(_d[2], _bx + 32, _dy + 10, 32, 30, c_white, _d[3] ? 1 : 0.4);
		ui_text(_bx + 48, _dy + 52, _d[0], global.F14, _d[3] ? C_TEXT : C_MUTED, fa_center, fa_middle);
		if (_d[3] && global.ui.busy == 0 && ui_click(_bx, _dy + 6, 96, 60)) go(_d[1]);
	}
}

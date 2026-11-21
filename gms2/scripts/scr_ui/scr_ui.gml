// ── 화면 그리기·입력(즉시 그리기 방식: 그리면서 눌림을 확인한다) ──
// 좌표는 「내용 좌표」: 가로 W(960~1280) × 세로 540. 남는 화면은 가운데 맞춤 여백(배경색).

function ui_init() {
	global.ui = {
		GW: DESIGN_W_MIN, GH: DESIGN_H, W: DESIGN_W_MIN, H: DESIGN_H, ox: 0, oy: 0,
		mx: -1, my: -1, px: -1, py: -1, lastmy: 0,
		down: false, pressed: false, released: false, drag: false, dragArea: "",
		consumed: false, block: false, busy: 0,
		lists: {}, areas: {}, hover: "",
		toast: "", toastUntil: 0,
		confirm: undefined
	};
}

/// 매 프레임(Step): 화면 크기·입력 갱신
function ui_step() {
	var _u = global.ui;
	var _ww = max(1, window_get_width()), _wh = max(1, window_get_height());
	// 창 비율 그대로 GUI 크기를 정해 늘어나지 않게 한다. 4:3처럼 좁으면 세로를 늘린다
	var _gw, _gh;
	if (_ww / _wh < DESIGN_W_MIN / DESIGN_H) { _gw = DESIGN_W_MIN; _gh = round(DESIGN_W_MIN * _wh / _ww); }
	else { _gh = DESIGN_H; _gw = round(DESIGN_H * _ww / _wh); }
	if (_gw != _u.GW || _gh != _u.GH || display_get_gui_width() != _gw) { display_set_gui_size(_gw, _gh); _u.GW = _gw; _u.GH = _gh; }
	_u.W = min(_gw, DESIGN_W_MAX); _u.H = DESIGN_H;
	_u.ox = floor((_gw - _u.W) / 2); _u.oy = floor((_gh - _u.H) / 2);
	_u.mx = device_mouse_x_to_gui(0) - _u.ox;
	_u.my = device_mouse_y_to_gui(0) - _u.oy;
	_u.pressed = mouse_check_button_pressed(mb_left);
	_u.released = mouse_check_button_released(mb_left);
	_u.down = mouse_check_button(mb_left);
	_u.consumed = false;
	if (_u.pressed) {
		_u.px = _u.mx; _u.py = _u.my; _u.lastmy = _u.my; _u.drag = false;
		_u.dragArea = ui_area_at(_u.mx, _u.my);
	}
	// 목록 끌기: 한 칸 높이만큼 움직일 때마다 한 줄씩
	if (_u.down && _u.dragArea != "") {
		if (abs(_u.my - _u.py) > 10) _u.drag = true;
		var _l = _u.lists[$ _u.dragArea];
		if (!is_undefined(_l) && _u.drag) {
			var _d = _u.lastmy - _u.my;
			if (abs(_d) >= _l.rowH * 0.6) { _l.first = clamp(_l.first + sign(_d), 0, _l.maxFirst); _u.lastmy = _u.my; }
		}
	}
	if (!_u.down && !_u.released) _u.dragArea = "";
	// 마우스 휠
	var _h = ui_area_at(_u.mx, _u.my);
	if (_h != "") {
		var _l2 = _u.lists[$ _h];
		if (!is_undefined(_l2)) {
			if (mouse_wheel_down()) _l2.first = clamp(_l2.first + 1, 0, _l2.maxFirst);
			if (mouse_wheel_up()) _l2.first = clamp(_l2.first - 1, 0, _l2.maxFirst);
		}
	}
	_u.areas = {};   // 이번 프레임 그리기에서 다시 등록
}

function ui_area_at(_x, _y) {
	var _u = global.ui, _names = variable_struct_get_names(_u.areas);
	for (var _i = 0; _i < array_length(_names); _i++) {
		var _a = _u.areas[$ _names[_i]];
		if (_x >= _a[0] && _y >= _a[1] && _x < _a[0] + _a[2] && _y < _a[1] + _a[3]) return _names[_i];
	}
	// 이번 프레임에 아직 안 그렸으면 지난 프레임 기록
	return "";
}

function ui_in(_x, _y, _w, _h, _px, _py) {
	return _px >= _x && _py >= _y && _px < _x + _w && _py < _y + _h;
}

/// 눌렀다 뗀 곳이 모두 이 칸이면 true(한 프레임에 하나만)
function ui_click(_x, _y, _w, _h) {
	var _u = global.ui;
	if (!_u.released || _u.consumed || _u.block || _u.drag) return false;
	if (!ui_in(_x, _y, _w, _h, _u.mx, _u.my) || !ui_in(_x, _y, _w, _h, _u.px, _u.py)) return false;
	_u.consumed = true;
	return true;
}

function ui_hover(_x, _y, _w, _h) {
	var _u = global.ui;
	return !_u.block && ui_in(_x, _y, _w, _h, _u.mx, _u.my);
}

// ── 그리기 ──
function ui_rect(_x, _y, _w, _h, _col, _alpha = 1, _r = 6) {
	draw_set_alpha(_alpha);
	draw_roundrect_colour_ext(_x, _y, _x + _w, _y + _h, _r, _r, _col, _col, false);
	draw_set_alpha(1);
}
function ui_outline(_x, _y, _w, _h, _col, _r = 6) {
	draw_roundrect_colour_ext(_x, _y, _x + _w, _y + _h, _r, _r, _col, _col, true);
}
function ui_panel(_x, _y, _w, _h) {
	ui_rect(_x, _y, _w, _h, C_PANEL, 0.93);
	ui_outline(_x, _y, _w, _h, C_EDGE);
}

/// 글자. _ha: fa_left/fa_center/fa_right, _va: fa_top/fa_middle/fa_bottom
function ui_text(_x, _y, _s, _font, _col, _ha = fa_left, _va = fa_top) {
	draw_set_font(font_or_default(_font));
	draw_set_halign(_ha); draw_set_valign(_va); draw_set_colour(_col);
	draw_text(_x, _y, string(_s));
	draw_set_halign(fa_left); draw_set_valign(fa_top);
}
/// 너비를 넘으면 뒤를 줄이고 「…」
function ui_fit(_s, _font, _w) {
	draw_set_font(font_or_default(_font));
	_s = string(_s);
	if (string_width(_s) <= _w) return _s;
	var _n = string_length(_s);
	while (_n > 1 && string_width(string_copy(_s, 1, _n) + "…") > _w) _n--;
	return string_copy(_s, 1, _n) + "…";
}
function ui_text_fit(_x, _y, _s, _font, _col, _w, _ha = fa_left, _va = fa_top) {
	ui_text(_x, _y, ui_fit(_s, _font, _w), _font, _col, _ha, _va);
}
/// 여러 줄(띄어쓰기에서 줄바꿈). 높이를 돌려준다
function ui_text_wrap(_x, _y, _s, _font, _col, _w, _lineH = -1) {
	draw_set_font(font_or_default(_font)); draw_set_colour(_col);
	draw_set_halign(fa_left); draw_set_valign(fa_top);
	draw_text_ext(_x, _y, string(_s), _lineH, _w);
	return string_height_ext(string(_s), _lineH, _w);
}

/// 버튼. _style: "primary"(금색) · "normal"(남색) · "quiet"(어두운 칸) · "red"/"blue"/"green"/"gray"(전투 명령)
function ui_button(_x, _y, _w, _h, _label, _style = "normal", _enabled = true, _sub = "") {
	var _bg = C_HEADER, _fg = C_TEXT, _edge = C_GOLD;
	switch (_style) {
		case "primary": _bg = C_PRIMARY; _fg = C_INK; _edge = C_GOLD_EDGE; break;
		case "quiet": _bg = C_SLOT; _edge = C_LINE; break;
		case "red": _bg = C_RED; _edge = make_colour_rgb(200, 150, 63); break;
		case "blue": _bg = C_BLUE; _edge = make_colour_rgb(200, 150, 63); break;
		case "green": _bg = C_GREEN; _edge = make_colour_rgb(200, 150, 63); break;
		case "gray": _bg = C_GRAY; _edge = make_colour_rgb(200, 150, 63); break;
	}
	var _hot = _enabled && ui_hover(_x, _y, _w, _h);
	var _a = _enabled ? 1 : 0.45;
	ui_rect(_x, _y, _w, _h, _hot ? merge_colour(_bg, c_white, 0.12) : _bg, _a);
	draw_set_alpha(_a); ui_outline(_x, _y, _w, _h, _edge); draw_set_alpha(1);
	var _f = _h >= 56 ? global.F18 : global.F16;
	if (_sub == "") ui_text_fit(_x + _w / 2, _y + _h / 2, _label, _f, _fg, _w - 12, fa_center, fa_middle);
	else {
		ui_text_fit(_x + _w / 2, _y + _h / 2 - 9, _label, _f, _fg, _w - 12, fa_center, fa_middle);
		ui_text_fit(_x + _w / 2, _y + _h / 2 + 12, _sub, global.F14, _fg, _w - 12, fa_center, fa_middle);
	}
	return _enabled && global.ui.busy == 0 && ui_click(_x, _y, _w, _h);
}

/// 작은 표시 띠(칸 위 「출전」·「×3」 등)
function ui_tag(_x, _y, _s, _bg = C_SLOT, _fg = C_TEXT) {
	draw_set_font(font_or_default(global.F14));
	var _w = string_width(_s) + 14;
	ui_rect(_x, _y, _w, 22, _bg, 0.92, 10);
	ui_outline(_x, _y, _w, 22, C_EDGE, 10);
	ui_text(_x + _w / 2, _y + 11, _s, global.F14, _fg, fa_center, fa_middle);
	return _w;
}

function ui_bar(_x, _y, _w, _h, _ratio, _col) {
	ui_rect(_x, _y, _w, _h, C_SLOT, 1, 3);
	if (_ratio > 0) ui_rect(_x, _y, max(2, _w * clamp(_ratio, 0, 1)), _h, _col, 1, 3);
}

/// 줄 단위로 넘기는 목록 영역. 보이는 첫 줄 번호를 돌려준다(그 줄부터 _visibleRows줄을 그리면 된다)
function ui_list(_key, _x, _y, _w, _h, _rowH, _rows) {
	var _u = global.ui;
	var _l = _u.lists[$ _key];
	if (is_undefined(_l)) { _l = { first: 0, rowH: _rowH, maxFirst: 0 }; _u.lists[$ _key] = _l; }
	var _vis = max(1, floor(_h / _rowH));
	_l.rowH = _rowH; _l.maxFirst = max(0, _rows - _vis); _l.first = clamp(_l.first, 0, _l.maxFirst);
	_u.areas[$ _key] = [_x, _y, _w, _h];
	if (_l.maxFirst > 0) {   // 오른쪽 스크롤 표시
		var _th = max(24, _h * _vis / _rows), _ty = _y + (_h - _th) * (_l.first / _l.maxFirst);
		ui_rect(_x + _w - 5, _y, 4, _h, C_SLOT, 0.8, 2);
		ui_rect(_x + _w - 5, _ty, 4, _th, C_EDGE, 1, 2);
	}
	return _l.first;
}
function ui_list_reset(_key) {
	var _l = global.ui.lists[$ _key];
	if (!is_undefined(_l)) _l.first = 0;
}

function ui_toast(_s) {
	global.ui.toast = string(_s);
	global.ui.toastUntil = current_time + 2800;
}

/// 확인 창: 「예」를 누르면 _fn(_ctx)
function ui_confirm(_msg, _fn, _ctx) {
	global.ui.confirm = { msg: _msg, fn: _fn, ctx: _ctx };
}

/// 맨 위에 그리는 것들(확인 창·알림·통신 중)
function ui_draw_overlays() {
	var _u = global.ui;
	if (!is_undefined(_u.confirm)) {
		var _c = _u.confirm;
		_u.block = false;
		ui_rect(0, 0, _u.W, _u.H, C_BACKDROP, 0.7, 0);
		var _w = 560, _h = 220, _x = (_u.W - _w) / 2, _y = (_u.H - _h) / 2;
		ui_panel(_x, _y, _w, _h);
		ui_text_wrap(_x + 24, _y + 24, _c.msg, global.F18, C_TEXT, _w - 48);
		if (ui_button(_x + _w - 24 - 150, _y + _h - 24 - 52, 150, 52, "예", "primary")) { var _fn = _c.fn; _u.confirm = undefined; _fn(_c.ctx); }
		else if (ui_button(_x + _w - 24 - 310, _y + _h - 24 - 52, 150, 52, "아니오", "quiet")) _u.confirm = undefined;
	}
	if (_u.toast != "" && current_time < _u.toastUntil) {
		draw_set_font(font_or_default(global.F16));
		var _tw = min(_u.W - 80, string_width(_u.toast) + 40);
		ui_rect((_u.W - _tw) / 2, 64, _tw, 42, C_HEADER, 0.96, 8);
		ui_outline((_u.W - _tw) / 2, 64, _tw, 42, C_GOLD, 8);
		ui_text_fit(_u.W / 2, 85, _u.toast, global.F16, C_TEXT, _tw - 24, fa_center, fa_middle);
	}
	if (_u.busy > 0) ui_text(_u.W - 14, _u.H - 10, "통신 중…", global.F14, C_MUTED, fa_right, fa_bottom);
}

/// 공통 머리줄. _back: 돌아갈 화면 이름("" 이면 버튼 없음)
function ui_top(_title, _sub, _back = "village") {
	var _u = global.ui;
	ui_rect(8, 6, _u.W - 16, 52, C_HEADER, 1);
	ui_outline(8, 6, _u.W - 16, 52, C_EDGE);
	var _x = 18;
	if (_back != "") {
		if (ui_button(16, 12, 84, 40, "‹ 뒤로", "normal")) go(_back);
		_x = 112;
	}
	ui_text(_x, 32, _title, global.F22, C_TEXT, fa_left, fa_middle);
	draw_set_font(font_or_default(global.F22));
	var _tx = _x + string_width(_title) + 12;
	ui_text_fit(_tx, 32, _sub, global.F16, C_MUTED, _u.W - _tx - 200, fa_left, fa_middle);
	// 오른쪽: 지원서
	ui_rect(_u.W - 176, 14, 158, 36, C_SLOT, 1, 18);
	ui_outline(_u.W - 176, 14, 158, 36, C_EDGE, 18);
	draw_art("menu-currency-application", _u.W - 168, 18, 26, 28);
	ui_text(_u.W - 136, 32, "지원서 " + string(global.me.credits), global.F16, C_TEXT, fa_left, fa_middle);
}

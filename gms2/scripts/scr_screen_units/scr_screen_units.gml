// ── 내 갈매미: 정렬(레벨·종족·이름·등급) · 같은 갈매미 묶기 · 상세 ──

function cmp_units(_a, _b) {
	var _s = global.prefs.sort, _d = 0;
	switch (_s) {
		case "tribe": _d = tribe_rank(_a.speciesId) - tribe_rank(_b.speciesId); if (_d == 0) _d = str_cmp(_a.speciesId, _b.speciesId); if (_d == 0) _d = _b.level - _a.level; break;
		case "name": _d = str_cmp(_a.name, _b.name); if (_d == 0) _d = _b.level - _a.level; break;
		case "grade": _d = grade_rank(_a.rarity) - grade_rank(_b.rarity); if (_d == 0) _d = str_cmp(_a.name, _b.name); if (_d == 0) _d = _b.level - _a.level; break;
		default: _d = _b.level - _a.level; if (_d == 0) _d = grade_rank(_a.rarity) - grade_rank(_b.rarity); if (_d == 0) _d = str_cmp(_a.name, _b.name);
	}
	return _d;
}

/// 정렬하고, 묶기면 같은 종을 한 칸으로(대표는 레벨이 가장 높은 개체). [{rep, all}]
function arrange_units(_units, _grouped) {
	var _rows = [];
	if (!_grouped) {
		for (var _i = 0; _i < array_length(_units); _i++) array_push(_rows, { rep: _units[_i], items: [_units[_i]] });
	} else {
		var _map = {}, _order = [];
		for (var _j = 0; _j < array_length(_units); _j++) {
			var _u = _units[_j], _g = _map[$ _u.speciesId];
			if (is_undefined(_g)) { _g = { rep: _u, items: [] }; _map[$ _u.speciesId] = _g; array_push(_order, _g); }
			array_push(_g.items, _u);
			if (_u.level > _g.rep.level) _g.rep = _u;
		}
		for (var _k = 0; _k < array_length(_order); _k++) _order[_k].items = sort_by(_order[_k].items, function(_a, _b) { return _b.level - _a.level; });
		_rows = _order;
	}
	return sort_by(_rows, function(_x, _y) { return cmp_units(_x.rep, _y.rep); });
}

/// 정렬·묶기 도구 줄. 바뀌면 true
function units_toolbar(_x, _y, _listKey) {
	var _sorts = [["level", "레벨순"], ["tribe", "종족순"], ["name", "이름순"], ["grade", "등급순"]], _changed = false;
	for (var _i = 0; _i < 4; _i++) {
		if (ui_button(_x + _i * 84, _y, 80, 40, _sorts[_i][1], global.prefs.sort == _sorts[_i][0] ? "normal" : "quiet")) { global.prefs.sort = _sorts[_i][0]; _changed = true; }
	}
	if (ui_button(_x + 344, _y, 150, 40, (global.prefs.group ? "● " : "○ ") + "같은 갈매미 묶기", global.prefs.group ? "normal" : "quiet")) { global.prefs.group = !global.prefs.group; _changed = true; }
	if (_changed) { prefs_save(); ui_list_reset(_listKey); }
	return _changed;
}

/// 갈매미 칸 하나(그림·이름·등급·레벨·마릿수)
function unit_card(_x, _y, _w, _h, _u, _count, _on) {
	ui_rect(_x, _y, _w, _h, C_SLOT, 1);
	ui_outline(_x, _y, _w, _h, _on ? C_GOLD_EDGE : C_LINE);
	if (_on) ui_outline(_x + 1, _y + 1, _w - 2, _h - 2, C_GOLD);
	draw_art(art_front(_u.speciesId), _x + 4, _y + 4, _w - 8, _h - 52);
	ui_text_fit(_x + _w / 2, _y + _h - 44, _u.name, global.F16, C_TEXT, _w - 8, fa_center);
	ui_text_fit(_x + _w / 2, _y + _h - 22, grade_name(_u.rarity) + " · " + (_count > 1 ? "최고 " : "") + "Lv." + string(_u.level), global.F14, C_MUTED, _w - 8, fa_center);
	if (_count > 1) ui_tag(_x + _w - 44, _y + 6, "×" + string(_count), C_GOLD, C_INK);
}

function units_enter() {
	global.S.list = undefined; global.S.pick = jv(global.S, "arg", undefined); global.S.det = undefined; global.S.detFor = "";
	api_get(r60("owned"), units_cb, undefined);
}
function units_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code) || global.screen != "units") return;
	global.S.list = ja(jv(_data, "units", []));
}
function units_detail_cb(_ok, _data, _code, _ctx) {
	if (!_ok || global.screen != "units" || global.S.pick != _ctx.id) return;
	global.S.det = jv(_data, "unit", undefined);
}

function units_draw() {
	var _u = global.ui, _S = global.S;
	app_backdrop();
	if (is_undefined(_S.list)) { ui_top("내 갈매미", ""); ui_text(_u.W / 2, _u.H / 2, "불러오는 중…", global.F18, C_MUTED, fa_center, fa_middle); return; }
	var _list = _S.list, _key = global.prefs.sort + string(global.prefs.group) + string(array_length(_list));
	if (jv(_S, "rowsKey", "") != _key) { _S.rows = arrange_units(_list, global.prefs.group); _S.rowsKey = _key; }   // 정렬은 바뀔 때만
	var _rows = _S.rows;
	var _found = false;
	for (var _i = 0; _i < array_length(_list); _i++) if (_list[_i].ownedId == _S.pick) _found = true;
	if (!_found) _S.pick = array_length(_rows) ? _rows[0].rep.ownedId : undefined;
	var _kinds = {};
	for (var _k = 0; _k < array_length(_list); _k++) _kinds[$ _list[_k].speciesId] = true;
	ui_top("내 갈매미", string(array_length(_list)) + "마리 · " + string(array_length(variable_struct_get_names(_kinds))) + "종");
	// 왼쪽 목록
	var _lw = 560;
	ui_panel(8, 64, _lw, _u.H - 72);
	units_toolbar(18, 74, "units");
	var _cols = 4, _cw = (_lw - 28 - 6 * (_cols - 1)) / _cols, _ch = 150, _nrow = ceil(array_length(_rows) / _cols);
	var _first = ui_list("units", 18, 122, _lw - 20, _u.H - 140, _ch + 6, _nrow), _vis = floor((_u.H - 140) / (_ch + 6));
	for (var _r = _first; _r < min(_nrow, _first + _vis); _r++) for (var _c = 0; _c < _cols; _c++) {
		var _idx = _r * _cols + _c;
		if (_idx >= array_length(_rows)) break;
		var _row = _rows[_idx], _x = 18 + _c * (_cw + 6), _y = 122 + (_r - _first) * (_ch + 6), _on = false;
		for (var _a = 0; _a < array_length(_row.items); _a++) if (_row.items[_a].ownedId == _S.pick) _on = true;
		unit_card(_x, _y, _cw, _ch, _row.rep, array_length(_row.items), _on);
		if (ui_click(_x, _y, _cw, _ch)) _S.pick = _row.rep.ownedId;
	}
	if (array_length(_rows) == 0) ui_text(28, 140, "아직 갈매미가 없어요.", global.F16, C_MUTED);
	// 오른쪽 상세
	var _dx = _lw + 16, _dw = _u.W - _dx - 8;
	ui_panel(_dx, 64, _dw, _u.H - 72);
	if (is_undefined(_S.pick)) return;
	if (_S.detFor != _S.pick) { _S.detFor = _S.pick; _S.det = undefined; api_post(r60("owned/read"), { ownedId: _S.pick }, units_detail_cb, { id: _S.pick }); }
	var _d = _S.det;
	if (is_undefined(_d)) { ui_text(_dx + 16, 80, "불러오는 중…", global.F16, C_MUTED); return; }
	var _y2 = 76;
	ui_text_fit(_dx + 16, _y2, _d.name, global.F22, C_TEXT, _dw - 32); _y2 += 32;
	ui_text(_dx + 16, _y2, grade_name(_d.rarity) + " · Lv." + string(_d.level) + "/" + string(_d.maxLevel) + (jv(_d, "reincarnation", 0) ? " · 환생 " + string(_d.reincarnation) : "") + (tribe_of(_d.speciesId) != "" ? " · " + tribe_of(_d.speciesId) : ""), global.F16, C_GOLD); _y2 += 28;
	// 같은 갈매미 개체(레벨) 바꿔 보기
	var _sib = [];
	for (var _s = 0; _s < array_length(_list); _s++) if (_list[_s].speciesId == _d.speciesId) array_push(_sib, _list[_s]);
	if (array_length(_sib) > 1) {
		_sib = sort_by(_sib, function(_a, _b) { return _b.level - _a.level; });
		var _cx = _dx + 16;
		for (var _t = 0; _t < array_length(_sib) && _cx < _dx + _dw - 90; _t++) {
			if (ui_button(_cx, _y2, 76, 34, "Lv." + string(_sib[_t].level), _sib[_t].ownedId == _S.pick ? "normal" : "quiet")) _S.pick = _sib[_t].ownedId;
			_cx += 82;
		}
		_y2 += 42;
	}
	var _artH = _u.H - _y2 - 200;
	draw_art(art_front(_d.speciesId), _dx + 16, _y2, _dw - 32, _artH); _y2 += _artH + 4;
	var _g = jv(_d, "growth", {});
	ui_text(_dx + 16, _y2, jv(_g, "level", 0) >= jv(_g, "maxLevel", 60) ? "최고 레벨" : "다음 레벨까지 " + string(jv(_g, "xpToNext", "-")) + " XP", global.F14, C_MUTED); _y2 += 24;
	var _st = jv(_d, "stats", {}), _keys = [["hp", "HP"], ["mp", "MP"], ["durability", "내구"], ["statusResistance", "저항"], ["speed", "속도"]], _bw = (_dw - 32 - 16) / 5;
	for (var _m = 0; _m < 5; _m++) {
		var _bx = _dx + 16 + _m * (_bw + 4);
		ui_rect(_bx, _y2, _bw, 48, C_SLOT, 1); ui_outline(_bx, _y2, _bw, 48, C_LINE);
		ui_text(_bx + _bw / 2, _y2 + 4, _keys[_m][1], global.F14, C_MUTED, fa_center);
		ui_text(_bx + _bw / 2, _y2 + 22, string(jv(_st, _keys[_m][0], "-")), global.F18, C_TEXT, fa_center);
	}
	_y2 += 56;
	var _sk = jv(_d, "skills", {}), _all = [];
	if (!is_undefined(jv(_sk, "attack", undefined))) array_push(_all, _sk.attack);
	var _cur = ja(jv(_sk, "current", []));
	for (var _n = 0; _n < array_length(_cur); _n++) array_push(_all, _cur[_n]);
	for (var _q = 0; _q < array_length(_all) && _y2 < _u.H - 28; _q++) {
		var _k2 = _all[_q];
		ui_text_fit(_dx + 16, _y2, _k2.name + "  " + elem_name(_k2.element), global.F14, C_TEXT, _dw / 2);
		ui_text(_dx + _dw - 16, _y2, "위력 " + string(_k2.power) + " · MP " + string(_k2.mpCost), global.F14, C_MUTED, fa_right);
		_y2 += 22;
	}
}

// ── 모집: 하급·중급·고급(서버 모집 세션) → 결과(다시 뽑기·불합격·모두 합류) ──

function recruit_enter() {
	global.S.info = undefined;
	global.S.qty = { LOW: 1, MID: 1, HIGH: 1 };
	api_get(r60("recruit/session"), recruit_info_cb, undefined);
}
function recruit_info_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code) || global.screen != "recruit") return;
	global.me.credits = jv(_data, "credits", global.me.credits);
	var _pend = ja(jv(_data, "pendingSessions", []));
	if (array_length(_pend) > 0) { go("recruit_result", _pend); return; }
	global.S.info = _data;
}

/// 등급 확률 글자: "커먼 70% · 언커먼 28% · 레어 2%"(높은 확률 순)
function odds_text(_w) {
	var _names = variable_struct_get_names(_w), _rows = [];
	for (var _i = 0; _i < array_length(_names); _i++) array_push(_rows, { g: _names[_i], p: _w[$ _names[_i]] });
	_rows = sort_by(_rows, function(_a, _b) { return _b.p - _a.p; });
	var _s = "";
	for (var _j = 0; _j < array_length(_rows); _j++) _s += (_j ? " · " : "") + grade_name(_rows[_j].g) + " " + string(_rows[_j].p) + "%";
	return _s;
}

function recruit_draw() {
	var _u = global.ui, _S = global.S;
	app_backdrop();
	ui_top("모집", "지원서로 새 동료를 만나요");
	if (is_undefined(_S.info)) { ui_text(_u.W / 2, _u.H / 2, "불러오는 중…", global.F18, C_MUTED, fa_center, fa_middle); return; }
	ui_panel(8, 64, _u.W - 16, _u.H - 72);
	var _offers = ja(jv(_S.info, "offers", [])), _n = array_length(_offers);
	var _cw = (_u.W - 16 - 28 - 12 * (_n - 1)) / max(1, _n);
	for (var _i = 0; _i < _n; _i++) {
		var _o = _offers[_i], _k = _o.kind, _x = 22 + _i * (_cw + 12), _y = 78;
		ui_rect(_x, _y, _cw, 250, C_HEADER, 1); ui_outline(_x, _y, _cw, 250, C_EDGE);
		ui_text(_x + 16, _y + 14, jv(_o, "label", _k) + " 모집", global.F22, C_TEXT);
		ui_text(_x + 16, _y + 50, "지원서 " + string(_o.cost) + "장 · 다시 뽑기 " + string(jv(_o, "rerollCost", 0)) + "장", global.F16, C_MUTED);
		ui_text_wrap(_x + 16, _y + 78, odds_text(jv(_o, "weights", {})), global.F14, C_MUTED, _cw - 32);
		var _q = [1, 5, 10];
		for (var _j = 0; _j < 3; _j++) {
			var _bw = (_cw - 32 - 16) / 3, _bx = _x + 16 + _j * (_bw + 8);
			if (ui_button(_bx, _y + 126, _bw, 42, string(_q[_j]) + "회", _S.qty[$ _k] == _q[_j] ? "normal" : "quiet")) _S.qty[$ _k] = _q[_j];
		}
		var _times = _S.qty[$ _k], _cost = _o.cost * _times;
		var _can = jv(_S.info, "enabled", true) && is_real(global.me.credits) && global.me.credits >= _cost;
		if (ui_button(_x + 16, _y + 180, _cw - 32, 56, "모집하기", "primary", _can, "지원서 " + string(_cost) + "장")) {
			api_post(r60("recruit/session/start"), { requestId: rid(), kind: _k, catalogVersion: _S.info.catalogVersion, times: _times }, recruit_start_cb, undefined);
		}
	}
	ui_text_wrap(22, _u.H - 44, "같은 등급 안에서는 모두 같은 확률이에요. 모집 횟수에 따른 보장은 없어요.", global.F14, C_MUTED, _u.W - 44);
}
function recruit_start_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code)) return;
	global.me.credits = jv(_data, "credits", global.me.credits);
	var _list = ja(jv(_data, "pendingSessions", []));
	if (array_length(_list) == 0) _list = ja(jv(_data, "sessions", []));
	go("recruit_result", _list);
}

// ── 결과 ──
function recruit_result_enter() {
	global.S.list = ja(global.S.arg);
}
/// 서버가 돌려준 값으로 결과 카드 상태를 맞춘다
function recruit_sync(_r) {
	var _list = global.S.list;
	global.me.credits = jv(_r, "credits", global.me.credits);
	var _all = ja(jv(_r, "pendingSessions", []));
	if (!is_undefined(jv(_r, "sessionId", undefined))) array_push(_all, _r);
	for (var _i = 0; _i < array_length(_all); _i++) {
		var _src = _all[_i];
		for (var _j = 0; _j < array_length(_list); _j++) {
			var _dst = _list[_j];
			if (_dst.sessionId != _src.sessionId) continue;
			var _keys = ["status", "revision", "candidate", "rerolls", "canReject", "rejectRefund", "ownedSpecies"];
			for (var _k = 0; _k < array_length(_keys); _k++) { var _v = _src[$ _keys[_k]]; if (!is_undefined(_v)) _dst[$ _keys[_k]] = _v; }
		}
	}
}
function recruit_action_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code)) return;
	recruit_sync(_data);
	if (_ctx.action == "reject") ui_toast("지원서를 돌려받았어요.");
}
function recruit_claim_all_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code)) return;
	global.me.credits = jv(_data, "credits", global.me.credits);
	var _list = global.S.list;
	for (var _i = 0; _i < array_length(_list); _i++) if (_list[_i].status == "PENDING") _list[_i].status = "CLAIMED";
	ui_toast(string(_ctx.n) + "명이 방범대에 합류했어요.");
}

function recruit_result_draw() {
	var _u = global.ui, _list = global.S.list;
	var _pending = [], _seen = {}, _dup = {};
	for (var _i = 0; _i < array_length(_list); _i++) {
		var _s = _list[_i];
		if (_s.status != "PENDING") continue;
		array_push(_pending, _s);
		var _sp = jv(_s.candidate, "speciesId", "");
		if (jv(_s, "ownedSpecies", false) || !is_undefined(_seen[$ _sp])) _dup[$ _s.sessionId] = true;
		_seen[$ _sp] = true;
	}
	var _np = array_length(_pending);
	app_backdrop();
	ui_top("모집 결과", string(_np) + "명 확인 대기", _np > 0 ? "" : "village");
	ui_panel(8, 64, _u.W - 16, _u.H - 72);
	var _cols = 5, _cw = (_u.W - 16 - 24 - 8 * (_cols - 1)) / _cols, _ch = 200, _rows = ceil(array_length(_list) / _cols);
	var _first = ui_list("recruit_result", 20, 76, _u.W - 40, _u.H - 160, _ch + 8, _rows);
	var _vis = floor((_u.H - 160) / (_ch + 8));
	for (var _r = _first; _r < min(_rows, _first + _vis); _r++) {
		for (var _c = 0; _c < _cols; _c++) {
			var _idx = _r * _cols + _c;
			if (_idx >= array_length(_list)) break;
			var _s2 = _list[_idx], _cand = jv(_s2, "candidate", {}), _x = 20 + _c * (_cw + 8), _y = 76 + (_r - _first) * (_ch + 8);
			var _done = _s2.status != "PENDING";
			ui_rect(_x, _y, _cw, _ch, C_SLOT, 1); ui_outline(_x, _y, _cw, _ch, _done ? C_LINE : C_GOLD);
			draw_art(art_front(jv(_cand, "speciesId", "")), _x + 6, _y + 4, _cw - 12, 104);
			ui_text_fit(_x + _cw / 2, _y + 112, jv(_cand, "name", ""), global.F16, C_TEXT, _cw - 10, fa_center);
			ui_text(_x + _cw / 2, _y + 134, grade_name(jv(_cand, "rarity", "")) + " · Lv." + string(jv(_cand, "level", 1)), global.F14, C_MUTED, fa_center);
			if (_done) ui_tag(_x + 6, _y + 6, _s2.status == "CLAIMED" ? "합류" : (_s2.status == "REJECTED" ? "불합격" : string(_s2.status)));
			else {
				if (jv(_s2, "ownedSpecies", false)) ui_tag(_x + 6, _y + 6, "보유 중", C_SLOT, C_GOLD_EDGE);
				else if (!is_undefined(_dup[$ _s2.sessionId])) ui_tag(_x + 6, _y + 6, "중복", C_SLOT, C_GOLD_EDGE);
				// 다시 뽑기(같은 등급에서 한 번)
				if (ui_button(_x + _cw - 62, _y + 6, 56, 34, "다시", "normal", jv(_s2, "rerolls", 0) == 0, "")) {
					api_post(r60("recruit/session/action"), { requestId: rid(), sessionId: _s2.sessionId, revision: _s2.revision, action: "reroll" }, recruit_action_cb, { action: "reroll" });
				}
				if (jv(_s2, "canReject", false) && ui_button(_x + 6, _y + 156, _cw - 12, 38, "불합격 · 지원서 " + string(jv(_s2, "rejectRefund", 1)) + "장", "quiet")) {
					api_post(r60("recruit/session/action"), { requestId: rid(), sessionId: _s2.sessionId, revision: _s2.revision, action: "reject" }, recruit_action_cb, { action: "reject" });
				}
			}
		}
	}
	ui_text_wrap(22, _u.H - 70, "「다시」: 같은 등급에서 한 번 다시 뽑기(지원서 필요) · 불합격: 이미 가진 종만, 지원서 1장 돌려받기", global.F14, C_MUTED, _u.W - 420);
	if (ui_button(_u.W - 380, _u.H - 74, 150, 56, "내 갈매미", "normal")) go("units");
	if (_np > 0) {
		if (ui_button(_u.W - 220, _u.H - 74, 196, 56, "모두 합류 (" + string(_np) + ")", "primary")) {
			var _body = [];
			for (var _k = 0; _k < _np; _k++) array_push(_body, { sessionId: _pending[_k].sessionId, revision: _pending[_k].revision });
			api_post(r60("recruit/session/claim-all"), { requestId: rid(), sessions: _body }, recruit_claim_all_cb, { n: _np });
		}
	} else if (ui_button(_u.W - 220, _u.H - 74, 196, 56, "모집 계속", "primary")) go("recruit");
}

// ── 전투: 증강체 선택 · 공격 · 기술(상성 표시) · 집중 · 아이템 · 교체 · 도망 → 정산 ──

function battle_load_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code)) return;
	go("battle", { v: _data, q: _ctx.q });
}
function battle_enter() {
	global.S.v = global.S.arg.v; global.S.q = global.S.arg.q; global.S.mode = "main";
	if (jv(global.S.v, "status", "") == "FINISHED") go("settle", { v: global.S.v, q: global.S.q });
}
function battle_act(_action) {
	var _v = global.S.v;
	api_post(r60("battle/action"), { requestId: rid(), battleId: _v.battleId, revision: _v.revision, action: _action }, battle_action_cb, { q: global.S.q });
}
function battle_action_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code) || global.screen != "battle") return;
	global.S.v = _data; global.S.mode = "main";
	if (jv(_data, "status", "") == "FINISHED") go("settle", { v: _data, q: _ctx.q });
}
function battle_escape(_ctx) { battle_act({ kind: "escape" }); }

/// 상대 방어 상성(본 것만): ▲유리 ×1.5 · ▼불리 ×0.67
function defense_mark(_skill, _intel) {
	var _d = jv(jv(_intel, "defenses", {}), _skill.element, "unknown");
	if (_d == "weak") return ["▲유리 ×1.5", C_GOOD];
	if (_d == "resist") return ["▼불리 ×0.67", C_BAD];
	if (_d == "unknown") return ["상성 미확인", C_MUTED];
	return ["", C_MUTED];
}

/// 마지막 턴 기록 두 줄
function event_lines(_st) {
	var _turns = ja(jv(_st, "events", []));
	if (array_length(_turns) == 0) return "";
	var _evs = ja(jv(_turns[array_length(_turns) - 1], "events", [])), _out = [];
	for (var _i = 0; _i < array_length(_evs); _i++) {
		var _e = _evs[_i], _pres = jv(_e, "presentation", {}), _who = "";
		var _bef = ja(jv(_pres, "before", []));
		for (var _j = 0; _j < array_length(_bef); _j++) if (_bef[_j].id == jv(_e, "actor", "")) _who = _bef[_j].name;
		var _k = jv(_e, "kind", "");
		if (_k == "attack" || _k == "skill") {
			var _sn = jv(jv(_pres, "skill", {}), "name", _k == "attack" ? "공격" : "기술"), _df = jv(_e, "defense", "");
			array_push(_out, _who + "의 " + _sn + "! " + string(jv(_e, "damage", 0)) + " 피해" + (_df == "weak" ? " (효과가 굉장했다)" : (_df == "resist" ? " (효과가 별로)" : "")));
		} else if (_k == "switch") array_push(_out, _who + " 교체");
		else if (_k == "focus") array_push(_out, _who + " 집중");
		else if (_k == "item") array_push(_out, _who + " 아이템 사용");
	}
	var _s = "", _from = max(0, array_length(_out) - 2);
	for (var _m = _from; _m < array_length(_out); _m++) _s += (_m > _from ? "\n" : "") + _out[_m];
	return _s;
}

function fighter_bar(_x, _y, _w, _label, _f) {
	ui_rect(_x, _y, _w, 76, C_PANEL, 0.95); ui_outline(_x, _y, _w, 76, C_GOLD);
	ui_text_fit(_x + 12, _y + 8, _label + " · " + jv(_f, "name", "") + " Lv" + string(jv(_f, "level", 1)), global.F16, C_TEXT, _w - 24);
	var _st = jv(_f, "stats", {}), _hp = jv(_f, "hp", 0), _mhp = max(1, jv(_st, "hp", 1)), _mp = jv(_f, "mp", 0), _mmp = max(1, jv(_st, "mp", 1));
	ui_text(_x + 12, _y + 36, "HP", global.F14, C_MUTED); ui_bar(_x + 44, _y + 40, _w - 140, 9, _hp / _mhp, C_HP); ui_text(_x + _w - 12, _y + 34, string(_hp) + "/" + string(_mhp), global.F14, C_TEXT, fa_right);
	ui_text(_x + 12, _y + 54, "MP", global.F14, C_MUTED); ui_bar(_x + 44, _y + 58, _w - 140, 9, _mp / _mmp, C_MP); ui_text(_x + _w - 12, _y + 52, string(_mp) + "/" + string(_mmp), global.F14, C_TEXT, fa_right);
}

function battle_draw() {
	var _u = global.ui, _S = global.S, _v = _S.v, _st = jv(_v, "state", {});
	var _fs = ja(jv(_st, "fighters", []));
	if (array_length(_fs) < 2) return;
	var _my = _fs[0], _foe = _fs[1], _ac = jv(_st, "augmentChoice", undefined), _intel = jv(_st, "opponentIntel", {});
	var _forced = jv(_my, "hp", 1) <= 0, _off = jv(_v, "disabledCommands", {});
	var _res = [], _rr = ja(jv(_st, "reserves", []));
	for (var _i = 0; _i < array_length(_rr); _i++) if (!is_undefined(jv(_rr[_i], "ownedId", undefined))) array_push(_res, _rr[_i]);
	var _aug = is_struct(_ac) && jv(_ac, "phase", "") == "choose" && is_undefined(jv(_ac, "selected", undefined)) && !_forced;
	global.ui.block = global.ui.block || _aug;   // 증강체를 고르는 동안 아래 화면은 누를 수 없음
	app_backdrop();
	// 머리줄
	ui_rect(8, 6, _u.W - 16, 52, C_HEADER, 1); ui_outline(8, 6, _u.W - 16, 52, C_EDGE);
	if (ui_button(16, 12, 100, 40, "‹ 스토리", "normal")) go("story");
	ui_text(128, 32, "턴 " + string(jv(_st, "turn", 0) + 1), global.F22, C_TEXT, fa_left, fa_middle);
	ui_text(210, 32, _forced ? "쓰러진 갈매미를 교체해 주세요" : "행동을 선택하세요", global.F16, C_MUTED, fa_left, fa_middle);
	if (is_struct(_S.q)) ui_text_fit(_u.W - 20, 32, _S.q.title, global.F14, C_MUTED, 300, fa_right, fa_middle);
	// 싸움터
	var _fw = _u.W - 360, _fx = 8, _fy = 64, _fh = _u.H - 72;
	ui_rect(_fx, _fy, _fw, _fh, make_colour_rgb(40, 90, 130), 1, 6);
	draw_art("ui-bg-harbor", _fx, _fy, _fw, _fh);
	ui_outline(_fx, _fy, _fw, _fh, C_EDGE);
	draw_art(art_foe(_foe), _fx + _fw - 300, _fy + 40, 260, 230);
	draw_art(art_rear(jv(_my, "speciesId", "u01")), _fx + 30, _fy + _fh - 250, 260, 250);
	fighter_bar(_fx + 10, _fy + 10, 300, "상대", _foe);
	fighter_bar(_fx + _fw - 310, _fy + _fh - 86, 300, "출전", _my);
	var _log = event_lines(_st);
	if (_log != "") { ui_rect(_fx + _fw / 2 - 200, _fy + _fh - 160, 400, 56, C_BACKDROP, 0.85); ui_text_wrap(_fx + _fw / 2 - 190, _fy + _fh - 154, _log, global.F14, C_TEXT, 380); }
	// 명령판
	var _cx = _u.W - 344, _cw = 336, _cy = 64, _ch = _u.H - 72;
	ui_panel(_cx, _cy, _cw, _ch);
	var _mode = _forced ? "switch" : _S.mode;
	if (_mode == "main") {
		ui_text(_cx + 12, _cy + 10, "행동 선택", global.F18, C_TEXT);
		var _bw = (_cw - 32) / 2, _bh = (_ch - 60) / 3, _bx = _cx + 12, _by = _cy + 44;
		if (ui_button(_bx, _by, _bw, _bh - 8, "공격", "red")) battle_act({ kind: "attack" });
		if (ui_button(_bx + _bw + 8, _by, _bw, _bh - 8, "기술", "red")) _S.mode = "skill";
		if (ui_button(_bx, _by + _bh, _bw, _bh - 8, "집중", "blue")) battle_act({ kind: "focus" });
		if (ui_button(_bx + _bw + 8, _by + _bh, _bw, _bh - 8, "아이템", "green", jv(_off, "item", "") == "")) _S.mode = "item";
		var _canSw = false; for (var _r = 0; _r < array_length(_res); _r++) if (jv(_res[_r], "hp", 0) > 0) _canSw = true;
		if (ui_button(_bx, _by + _bh * 2, _bw, _bh - 8, "교체", "blue", jv(_off, "switch", "") == "" && _canSw)) _S.mode = "switch";
		if (ui_button(_bx + _bw + 8, _by + _bh * 2, _bw, _bh - 8, "도망치기", "gray", jv(_off, "escape", "") == "")) ui_confirm("도망칠까요? 이번 모험에서 받지 않은 보상을 잃을 수 있어요.", battle_escape, undefined);
	} else {
		var _title = _mode == "skill" ? "기술 선택" : (_mode == "item" ? "아이템" : (_forced ? "다음에 나갈 갈매미" : "교체"));
		ui_text(_cx + 12, _cy + 10, _title, global.F18, C_TEXT);
		var _oy = _cy + 44, _oh = 62;
		if (_mode == "skill") {
			var _sk = ja(jv(_my, "skills", []));
			for (var _k = 0; _k < array_length(_sk) && _oy < _cy + _ch - 130; _k++) {
				var _s = _sk[_k], _mk = defense_mark(_s, _intel);
				if (ui_button(_cx + 12, _oy, _cw - 24, _oh - 6, "", "quiet", _s.mpCost <= jv(_my, "mp", 0))) battle_act({ kind: "skill", skillId: _s.id });
				ui_text_fit(_cx + 24, _oy + 8, _s.name, global.F16, C_TEXT, 170);
				ui_text(_cx + 24, _oy + 32, elem_name(_s.element) + " · 위력 " + string(_s.power) + " · MP " + string(_s.mpCost), global.F14, C_MUTED);
				ui_text(_cx + _cw - 24, _oy + 20, _mk[0], global.F14, _mk[1], fa_right);
				_oy += _oh;
			}
		} else if (_mode == "item") {
			var _inv = ja(jv(_st, "inventory", [])), _defs = jv(_v, "itemDefinitions", {});
			if (array_length(_inv) == 0) ui_text(_cx + 16, _oy + 8, "가진 아이템이 없어요.", global.F16, C_MUTED);
			for (var _t = 0; _t < array_length(_inv) && _oy < _cy + _ch - 130; _t++) {
				var _it = _inv[_t], _iid = jv(_it, "itemId", jv(_it, "id", "")), _d = jv(_defs, _iid, {});
				if (ui_button(_cx + 12, _oy, _cw - 24, _oh - 6, "", "quiet", jv(_it, "count", 1) > 0)) battle_act({ kind: "item", itemId: _iid });
				ui_text_fit(_cx + 24, _oy + 8, jv(_d, "name", _iid) + " ×" + string(jv(_it, "count", 1)), global.F16, C_TEXT, _cw - 48);
				ui_text_fit(_cx + 24, _oy + 32, jv(_d, "desc", ""), global.F14, C_MUTED, _cw - 48);
				_oy += _oh;
			}
		} else {
			if (array_length(_res) == 0) ui_text(_cx + 16, _oy + 8, "예비 갈매미가 없어요.", global.F16, C_MUTED);
			for (var _q = 0; _q < array_length(_res); _q++) {
				var _f = _res[_q], _fst = jv(_f, "stats", {});
				if (ui_button(_cx + 12, _oy, _cw - 24, _oh - 6, "", "quiet", jv(_f, "hp", 0) > 0)) battle_act({ kind: "switch", targetOwnedId: _f.ownedId });
				ui_text_fit(_cx + 24, _oy + 8, _f.name, global.F16, C_TEXT, _cw - 110);
				ui_text(_cx + 24, _oy + 32, "Lv." + string(_f.level) + " · HP " + string(_f.hp) + "/" + string(jv(_fst, "hp", 0)), global.F14, C_MUTED);
				draw_art(art_front(_f.speciesId), _cx + _cw - 76, _oy + 4, 48, 48);
				_oy += _oh;
			}
		}
		if (!_forced && ui_button(_cx + 12, _cy + _ch - 64, _cw - 24, 52, "돌아가기", "quiet")) _S.mode = "main";
	}
	// 증강체 선택(맨 위)
	if (_aug) {
		global.ui.block = !is_undefined(global.ui.confirm);
		ui_rect(0, 0, _u.W, _u.H, C_BACKDROP, 0.72, 0);
		var _sw = min(_u.W - 80, 840), _sx = (_u.W - _sw) / 2, _sy = 70, _sh = 400;
		ui_panel(_sx, _sy, _sw, _sh);
		ui_text(_sx + 20, _sy + 16, "증강체 선택 · " + string(jv(_ac, "round", 1)) + "라운드", global.F22, C_TEXT);
		ui_text(_sx + 20, _sy + 50, "하나를 골라요. 30초 안에 고르지 않으면 무작위로 정해져요.", global.F14, C_MUTED);
		var _offers = ja(jv(_ac, "offers", [])), _rer = ja(jv(_ac, "rerolled", [])), _ow = (_sw - 40 - 20) / 3;
		for (var _o = 0; _o < array_length(_offers); _o++) {
			var _of = _offers[_o], _ox = _sx + 20 + _o * (_ow + 10), _oyy = _sy + 80;
			var _tier = jv(_of, "tier", ""), _tn = _tier == "gold" ? "골드" : (_tier == "silver" ? "실버" : (_tier == "prismatic" ? "프리즘" : _tier));
			if (ui_button(_ox, _oyy, _ow, 230, "", "normal")) battle_act({ kind: "augment", augmentId: _of.id, phaseId: _ac.phaseId });
			ui_text(_ox + 14, _oyy + 12, _tn, global.F14, C_MUTED);
			ui_text_fit(_ox + 14, _oyy + 34, _of.name, global.F18, C_GOLD_EDGE, _ow - 28);
			ui_text_wrap(_ox + 14, _oyy + 66, jv(_of, "desc", ""), global.F14, C_TEXT, _ow - 28);
			var _used = _o < array_length(_rer) && _rer[_o];
			if (ui_button(_ox, _oyy + 240, _ow, 44, "이 칸 다시 뽑기", "quiet", !_used)) battle_act({ kind: "augment-reroll", slot: _o, phaseId: _ac.phaseId });
		}
	}
}

// ── 정산 ──
function settle_enter() {
	var _v = global.S.arg.v;
	global.S.v = _v; global.S.q = global.S.arg.q; global.S.set = jv(_v, "settlement", undefined);
	if (is_undefined(global.S.set)) api_post(r60("battle/settle"), { requestId: rid(), battleId: _v.battleId, revision: _v.revision }, settle_cb, undefined);
}
function settle_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code) || global.screen != "settle") return;
	global.S.v = _data; global.S.set = jv(_data, "settlement", {});
}
function settle_draw() {
	var _u = global.ui, _S = global.S, _set = _S.set;
	app_backdrop(0.8);
	if (is_undefined(_set)) { ui_text(_u.W / 2, _u.H / 2, "정산하는 중…", global.F18, C_MUTED, fa_center, fa_middle); return; }
	var _win = jv(_set, "outcome", "") == "win";
	var _w = min(_u.W - 80, 820), _x = (_u.W - _w) / 2, _y = 110, _h = 300;
	ui_panel(_x, _y, _w, _h);
	ui_text(_x + 24, _y + 20, _win ? "승리!" : (jv(_set, "outcome", "") == "escape" ? "도망쳤어요" : "패배"), global.F28, C_TEXT);
	var _pending = jv(jv(_set, "unitXp", {}), "delivery", "") == "pending-return";
	ui_text(_x + 24, _y + 66, _win ? (_pending ? "경험치는 모험에서 정상 귀환하면 받아요." : "경험치를 받았어요.") : "이번 전투에서는 경험치를 받지 못했어요.", global.F16, C_MUTED);
	if (_win) {
		// 이름은 전투에 나온 갈매미에서 찾는다
		var _st = jv(_S.v, "state", {}), _pool = [], _fs = ja(jv(_st, "fighters", [])), _rs = ja(jv(_st, "reserves", []));
		for (var _i = 0; _i < array_length(_fs); _i++) array_push(_pool, _fs[_i]);
		for (var _j = 0; _j < array_length(_rs); _j++) array_push(_pool, _rs[_j]);
		var _xp = jv(_set, "xpByOwnedId", {}), _ids = variable_struct_get_names(_xp), _bw = (_w - 48 - 20) / 3;
		for (var _k = 0; _k < min(3, array_length(_ids)); _k++) {
			var _f = undefined;
			for (var _m = 0; _m < array_length(_pool); _m++) if (jv(_pool[_m], "ownedId", "") == _ids[_k]) _f = _pool[_m];
			var _bx = _x + 24 + _k * (_bw + 10);
			ui_rect(_bx, _y + 106, _bw, 90, C_SLOT, 1); ui_outline(_bx, _y + 106, _bw, 90, C_LINE);
			if (is_struct(_f)) { draw_art(art_front(_f.speciesId), _bx + 8, _y + 114, 70, 74); ui_text_fit(_bx + 86, _y + 124, _f.name, global.F16, C_TEXT, _bw - 96); }
			ui_text(_bx + 86, _y + 152, "+" + string(_xp[$ _ids[_k]]) + " XP", global.F18, C_GOLD_EDGE);
		}
	}
	if (ui_button(_x + _w - 24 - 180, _y + _h - 24 - 56, 180, 56, "스토리로", "primary")) go("story");
}

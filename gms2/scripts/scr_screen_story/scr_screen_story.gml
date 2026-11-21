// ── 스토리: 의뢰 → 대화 장면 → 편성 → 전투 → 귀환 ──

function place_of(_bg) {
	switch (_bg) {
		case "final_statue": return ["village-identity-statue", "석상 앞"];
		case "human_records": return ["village-identity-records", "인간의 기록 장소"];
		case "hideout": return ["village-identity-hq", "은신처"];
		case "memory_workshop": return ["village-identity-records", "기억 속 작업장"];
		case "defense_village": return ["village-identity-hq", "마을 방어 준비"];
	}
	return ["village-identity-statue", "마을 석상 터"];
}

function story_enter() {
	global.S.st = undefined; global.S.adv = undefined;
	api_get(r60("story/state"), story_cb, undefined);
}
function story_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code) || global.screen != "story") return;
	if (is_struct(jv(_data, "scene", undefined))) { go("scene", _data); return; }
	global.S.st = _data;
	var _q = jv(_data, "currentQuest", undefined);
	if (is_struct(_q) && _q.status == "ACTIVE" && !is_undefined(jv(_q, "adventureId", undefined))) api_get(r60("adventure/active"), story_adv_cb, { id: _q.adventureId });
}
function story_adv_cb(_ok, _data, _code, _ctx) {
	if (!_ok || global.screen != "story") return;
	var _list = ja(jv(_data, "adventures", []));
	for (var _i = 0; _i < array_length(_list); _i++) if (_list[_i].adventureId == _ctx.id) global.S.adv = _list[_i];
}
function story_open_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code)) return;
	go("scene", _data);
}
function story_return_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code)) return;
	ui_toast("모험에서 귀환했어요. 모은 보상을 받았어요.");
	go("story");
}

function story_draw() {
	var _u = global.ui, _S = global.S;
	app_backdrop();
	var _st = _S.st, _q = jv(_st, "currentQuest", undefined);
	ui_top("스토리", is_struct(_q) ? "챕터 " + string(_q.chapter) : "");
	if (is_undefined(_st)) { ui_text(_u.W / 2, _u.H / 2, "불러오는 중…", global.F18, C_MUTED, fa_center, fa_middle); return; }
	var _lw = 520;
	ui_panel(16, 66, _lw, _u.H - 82);
	var _y = 80;
	if (!is_struct(_q)) {
		ui_text_wrap(36, _y, jv(_st, "nextStoryHint", "지금 들을 수 있는 이야기를 모두 들었어요."), global.F18, C_TEXT, _lw - 40);
	} else {
		ui_tag(36, _y, _q.kind == "main" ? "본편" : "이야기"); _y += 32;
		ui_text_fit(36, _y, _q.title, global.F22, C_TEXT, _lw - 40); _y += 36;
		var _obj = jv(_q, "objective", {}), _wins = jv(_obj, "wins", 0), _req = jv(_obj, "requiredWins", 1), _adv = _S.adv;
		var _goal = "짧은 이야기를 듣고 의뢰를 수락해 주세요.";
		if (_q.status == "READY") _goal = "마을에 돌아왔어요. 후속 이야기를 들을 수 있어요.";
		else if (_q.status == "ACTIVE") _goal = "현재 의뢰 · 현장 확인 " + string(_wins) + "/" + string(_req) + (is_struct(_adv) ? " · 모험 진행 중" : "");
		var _lv = ja(jv(_q, "recommendedLevel", []));
		if (array_length(_lv) == 2) _goal += "\n권장 레벨 " + string(_lv[0]) + "~" + string(_lv[1]) + " · 입장 제한 아님";
		_y += ui_text_wrap(36, _y, _goal, global.F16, C_TEXT, _lw - 40) + 10;
		// 의뢰 목록(지금 의뢰가 보이게)
		var _qs = ja(jv(_st, "quests", [])), _rowH = 30, _lh = _u.H - 90 - _y - 70;
		var _first = ui_list("story", 36, _y, _lw - 40, _lh, _rowH, array_length(_qs)), _vis = floor(_lh / _rowH);
		if (!jv(_S, "scrolled", false)) { _S.scrolled = true; for (var _f = 0; _f < array_length(_qs); _f++) if (_qs[_f].id == _q.id) global.ui.lists[$ "story"].first = clamp(_f - floor(_vis / 2), 0, max(0, array_length(_qs) - _vis)); }
		for (var _i = _first; _i < min(array_length(_qs), _first + _vis); _i++) {
			var _e = _qs[_i], _ry = _y + (_i - _first) * _rowH, _now = _e.id == _q.id;
			var _stt = _e.status == "COMPLETED" ? "완료" : (_e.status == "ACTIVE" ? "진행 중" : (_e.status == "READY" ? "귀환 대기" : "열림"));
			ui_text(36, _ry + 4, string(_e.chapter) + "장", global.F14, C_GOLD);
			ui_text_fit(84, _ry + 4, _e.title, global.F14, _now ? C_TEXT : C_MUTED, _lw - 200);
			ui_text(_lw - 8, _ry + 4, _stt, global.F14, _now ? C_TEXT : C_MUTED, fa_right);
		}
		// 아래 행동 버튼
		var _bx = 36, _by = _u.H - 32 - 60, _bw = _lw - 40;
		if (_q.status == "READY") {
			if (ui_button(_bx, _by, _bw, 60, "귀환 이야기 듣기", "primary")) api_post(r60("story/open"), { requestId: rid(), revision: _st.revision, questId: _q.id, phase: "return" }, story_open_cb, undefined);
		} else if (_q.status == "ACTIVE") {
			if (is_struct(_adv) && !is_undefined(jv(_adv, "activeBattleId", undefined))) {
				if (ui_button(_bx, _by, _bw, 60, "전투 이어가기", "primary")) api_post(r60("battle/read"), { battleId: _adv.activeBattleId }, battle_load_cb, { q: _q });
			} else if (is_struct(_adv) && _wins >= _req) {
				if (ui_button(_bx, _by, _bw, 60, "마을로 귀환", "primary", true, "모은 보상을 받아요")) api_post(r60("adventure/return"), { requestId: rid(), adventureId: _adv.adventureId, revision: _adv.revision }, story_return_cb, undefined);
			} else if (ui_button(_bx, _by, _bw, 60, "편성하고 출발", "primary")) go("formation", { q: _q, adv: _adv });
		} else if (ui_button(_bx, _by, _bw, 60, "대화 이어가기", "primary")) api_post(r60("story/open"), { requestId: rid(), revision: _st.revision, questId: _q.id, phase: "intro" }, story_open_cb, undefined);
	}
	// 오른쪽: 석상
	var _rx = _lw + 28, _rw = _u.W - _rx - 16;
	ui_panel(_rx, 66, _rw, _u.H - 82);
	draw_art("village-identity-statue", _rx + 20, 80, _rw - 40, _u.H - 220);
	var _sta = jv(_st, "statue", {}), _cmd = jv(_st, "commander", {});
	ui_text(_rx + _rw / 2, _u.H - 128, "석상 복원 " + string(jv(_sta, "stage", 1)) + "단계" + (jv(_sta, "hatched", false) ? " · 본편 완료" : ""), global.F16, C_MUTED, fa_center);
	ui_text(_rx + _rw / 2, _u.H - 100, "방범대장 Lv." + string(jv(_cmd, "level", 1)) + " · 다음 레벨까지 " + string(jv(_cmd, "xpToNext", "-")) + " XP", global.F14, C_MUTED, fa_center);
}

// ── 대화 장면 ──
function scene_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code)) return;
	if (_ctx.kind == "accept") { ui_toast("의뢰를 수락했어요. 편성하고 출발해 주세요."); go("story"); return; }
	if (_ctx.kind == "close") { go("story"); return; }
	if (is_struct(jv(_data, "scene", undefined))) { go("scene", _data); return; }
	if (_ctx.phase == "return") ui_toast("의뢰를 마쳤어요!");
	go("story");
}
function scene_draw() {
	var _u = global.ui, _st = global.S.arg, _sc = jv(_st, "scene", {}), _l = jv(_sc, "line", {});
	var _pl = place_of(jv(_sc, "background", ""));
	draw_cover("village-identity-coast-panorama", 0, 0, _u.W, _u.H);
	ui_rect(0, 0, _u.W, _u.H, C_BACKDROP, 0.55, 0);
	draw_art(_pl[0], _u.W - 600, 50, 560, 330);
	if (string_pos("hero", string(jv(_sc, "camera", ""))) > 0) draw_art(art_rear("u01"), 60, 140, 260, 260);
	ui_rect(_u.W / 2 - 110, 12, 220, 52, C_PANEL, 0.95); ui_outline(_u.W / 2 - 110, 12, 220, 52, C_EDGE);
	ui_text(_u.W / 2, 28, _pl[1], global.F16, C_TEXT, fa_center, fa_middle);
	var _phase = jv(_sc, "phase", "intro");
	ui_text(_u.W / 2, 50, _phase == "return" ? "귀환 이야기" : (_phase == "replay" ? "다시 보기" : "이야기"), global.F14, C_MUTED, fa_center, fa_middle);
	if (ui_button(14, 12, 100, 44, "나가기", "normal")) api_post(r60("story/close"), { requestId: rid(), revision: _st.revision }, scene_cb, { kind: "close" });
	// 대화 상자
	var _bx = 16, _by = _u.H - 140, _bw = _u.W - 32, _bh = 128;
	ui_rect(_bx, _by, _bw, _bh, C_PANEL, 0.96, 8); ui_outline(_bx, _by, _bw, _bh, C_GOLD, 8);
	var _narr = jv(_l, "speakerId", "") == "narrator" || jv(_l, "speakerName", "") == "";
	if (!_narr) ui_text(_bx + 20, _by + 14, _l.speakerName, global.F16, C_GOLD);
	ui_text_wrap(_bx + 20, _by + 40, jv(_l, "text", ""), global.F18, _narr ? C_MUTED : C_TEXT, _bw - 230);
	var _last = jv(_sc, "last", false);
	var _label = !_last ? "다음" : (_phase == "intro" ? "의뢰 수락" : (_phase == "return" ? "이야기 완료" : "닫기"));
	if (ui_button(_bx + _bw - 190, _by + _bh - 72, 170, 56, _label, "primary")) {
		if (_last && _phase == "intro") api_post(r60("story/accept"), { requestId: rid(), revision: _st.revision, questId: _sc.questId }, scene_cb, { kind: "accept" });
		else api_post(r60("story/advance"), { requestId: rid(), revision: _st.revision, cursor: _sc.cursor }, scene_cb, { kind: "advance", phase: _phase });
	}
}

// ── 편성: 1~3마리, 누른 순서대로(첫 번째가 출전) ──
function formation_enter() {
	global.S.units = undefined; global.S.picked = [];
	api_get(r60("owned"), formation_cb, undefined);
}
function formation_cb(_ok, _data, _code, _ctx) {
	if (api_failed(_ok, _data, _code) || global.screen != "formation") return;
	var _all = ja(jv(_data, "units", [])), _av = [];
	for (var _i = 0; _i < array_length(_all); _i++) if (_all[_i].status == "AVAILABLE") array_push(_av, _all[_i]);
	global.S.units = _av;
	if (array_length(_av)) { var _best = _av[0]; for (var _j = 1; _j < array_length(_av); _j++) if (_av[_j].level > _best.level) _best = _av[_j]; global.S.picked = [_best.ownedId]; }
}
function picked_index(_id) {
	var _p = global.S.picked;
	for (var _i = 0; _i < array_length(_p); _i++) if (_p[_i] == _id) return _i;
	return -1;
}
function formation_draw() {
	var _u = global.ui, _S = global.S, _q = _S.arg.q;
	app_backdrop();
	ui_top("전투 편성", "스토리 · 출전 1 + 예비 2", "story");
	if (is_undefined(_S.units)) { ui_text(_u.W / 2, _u.H / 2, "불러오는 중…", global.F18, C_MUTED, fa_center, fa_middle); return; }
	var _MAX = 3, _units = _S.units, _key = global.prefs.sort + string(global.prefs.group) + string(array_length(_units));
	if (jv(_S, "rowsKey", "") != _key) { _S.rows = arrange_units(_units, global.prefs.group); _S.rowsKey = _key; }
	var _rows = _S.rows, _lw = 640;
	ui_panel(8, 64, _lw, _u.H - 72);
	units_toolbar(18, 74, "formation");
	var _cols = 5, _cw = (_lw - 28 - 6 * (_cols - 1)) / _cols, _ch = 140, _nrow = ceil(array_length(_rows) / _cols);
	var _first = ui_list("formation", 18, 122, _lw - 20, _u.H - 140, _ch + 6, _nrow), _vis = floor((_u.H - 140) / (_ch + 6));
	for (var _r = _first; _r < min(_nrow, _first + _vis); _r++) for (var _c = 0; _c < _cols; _c++) {
		var _idx = _r * _cols + _c;
		if (_idx >= array_length(_rows)) break;
		var _row = _rows[_idx], _x = 18 + _c * (_cw + 6), _y = 122 + (_r - _first) * (_ch + 6), _n = 0, _role = -1;
		for (var _a = 0; _a < array_length(_row.items); _a++) { var _pi = picked_index(_row.items[_a].ownedId); if (_pi >= 0) { _n++; _role = _pi; } }
		unit_card(_x, _y, _cw, _ch, _row.rep, array_length(_row.items), _n > 0);
		if (_n > 0) ui_tag(_x + 6, _y + 6, _n > 1 ? "편성 " + string(_n) : (_role == 0 ? "출전" : "예비 " + string(_role)));
		if (ui_click(_x, _y, _cw, _ch)) {
			// 묶인 칸: 아직 안 고른 개체 중 레벨 높은 것부터. 모두 골랐으면 마지막에 넣은 것을 뺀다
			var _free = undefined;
			for (var _b = 0; _b < array_length(_row.items); _b++) if (picked_index(_row.items[_b].ownedId) < 0) { _free = _row.items[_b]; break; }
			if (array_length(_row.items) == 1 && picked_index(_row.rep.ownedId) >= 0) array_delete(_S.picked, picked_index(_row.rep.ownedId), 1);
			else if (!is_undefined(_free) && array_length(_S.picked) < _MAX) array_push(_S.picked, _free.ownedId);
			else if (is_undefined(_free)) { for (var _z = array_length(_S.picked) - 1; _z >= 0; _z--) { var _hit = false; for (var _w = 0; _w < array_length(_row.items); _w++) if (_row.items[_w].ownedId == _S.picked[_z]) _hit = true; if (_hit) { array_delete(_S.picked, _z, 1); break; } } }
			else ui_toast("최대 " + string(_MAX) + "마리까지 편성할 수 있어요.");
		}
	}
	// 오른쪽: 고른 갈매미(누르면 빠짐)
	var _rx = _lw + 16, _rw = _u.W - _rx - 8;
	ui_panel(_rx, 64, _rw, _u.H - 72);
	ui_text(_rx + 14, 78, "출전 " + string(array_length(_S.picked)) + "/" + string(_MAX), global.F18, C_TEXT);
	for (var _p = 0; _p < array_length(_S.picked); _p++) {
		var _un = undefined;
		for (var _v = 0; _v < array_length(_units); _v++) if (_units[_v].ownedId == _S.picked[_p]) _un = _units[_v];
		if (is_undefined(_un)) continue;
		var _py = 110 + _p * 62;
		if (ui_button(_rx + 12, _py, _rw - 24, 56, "", "quiet")) { array_delete(_S.picked, _p, 1); break; }
		ui_text_fit(_rx + 24, _py + 8, _un.name, global.F16, C_TEXT, _rw - 110);
		ui_text(_rx + 24, _py + 32, (_p == 0 ? "출전" : "예비 " + string(_p)) + " · Lv." + string(_un.level) + " · 눌러서 빼기", global.F14, C_MUTED);
		draw_art(art_front(_un.speciesId), _rx + _rw - 70, _py + 4, 48, 48);
	}
	ui_text_fit(_rx + 14, _u.H - 110, _q.title, global.F14, C_MUTED, _rw - 28);
	if (ui_button(_rx + 12, _u.H - 82, _rw - 24, 60, "편성 확정 · 전투 시작", "primary", array_length(_S.picked) > 0)) {
		var _body = { requestId: rid(), questId: _q.id, ownedIds: _S.picked };
		if (is_struct(_S.arg.adv)) _body.adventureId = _S.arg.adv.adventureId;
		api_post(r60("story/battle/create"), _body, battle_load_cb, { q: _q });
	}
}

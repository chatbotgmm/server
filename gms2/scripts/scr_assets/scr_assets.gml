// ── 그림·글꼴 ──
// 그림은 포함 파일(datafiles)의 PNG를 처음 쓸 때 불러온다(sprite_add). HTML5에서는 비동기로 불러와 잠깐 빈 칸일 수 있다.

function assets_init() {
	global.spr = {};
	// 앱 글꼴(Noto 기반, OFL). 파일 글꼴은 모든 글자를 쓸 때 불러오고, 한 프레임에 새 글자 약 200자까지(설명서)
	global.F14 = font_add("font-workbench.otf", 14, false, false, 32, 128);
	global.F16 = font_add("font-workbench.otf", 16, false, false, 32, 128);
	global.F18 = font_add("font-workbench.otf", 18, false, false, 32, 128);
	global.F22 = font_add("font-workbench.otf", 22, false, false, 32, 128);
	global.F28 = font_add("font-workbench.otf", 28, false, false, 32, 128);
}

function font_or_default(_f) {
	return (is_undefined(_f) || !font_exists(_f)) ? -1 : _f;
}

/// "u01-front" 같은 이름(확장자 없음) → 스프라이트. 없으면 -1
function spr(_name) {
	var _s = global.spr[$ _name];
	if (is_undefined(_s)) {
		_s = sprite_add(working_directory + _name + ".png", 1, false, false, 0, 0);
		global.spr[$ _name] = _s;
	}
	return _s;
}

/// 앱과 같은 임시 빌런 그림 별칭(StoryBattleArt)
function art_alias(_sp) {
	switch (_sp) {
		case "story_record": return "u01";
		case "story_guide": return "u02";
		case "story_engineer": return "u22";
		case "story_escort": return "u03";
		case "story_dark_first": return "u31";
	}
	return _sp;
}
function art_front(_sp) { return art_alias(_sp) + "-front"; }
function art_rear(_sp) { return art_alias(_sp) + "-rear"; }
/// 적(흑화) 그림: 흑화 그림이 있는 8종이면 흑화, 아니면 앞모습
function art_foe(_f) {
	var _sp = jv(_f, "speciesId", ""), _base = art_alias(_sp);
	var _black = (_base == "u01" || _base == "u02" || _base == "u06" || _base == "u07" || _base == "u14" || _base == "u23" || _base == "u28" || _base == "u31");
	if (_black && (jv(_f, "artVariant", "") == "black" || _base != _sp)) return "black-" + _base;
	return art_front(_sp);
}

/// 칸 안에 비율을 지켜 맞춰 그린다(아래 가운데 정렬). _alpha·_col 선택
function draw_art(_name, _x, _y, _w, _h, _col = c_white, _alpha = 1) {
	var _s = spr(_name);
	if (_s < 0 || !sprite_exists(_s)) return;
	var _sw = sprite_get_width(_s), _sh = sprite_get_height(_s);
	if (_sw <= 1 || _sh <= 1) return;   // 아직 불러오는 중
	var _k = min(_w / _sw, _h / _sh);
	draw_sprite_ext(_s, 0, _x + (_w - _sw * _k) / 2, _y + _h - _sh * _k, _k, _k, 0, _col, _alpha);
}

/// 칸을 가득 채우게(넘치는 부분은 칸 밖으로 나감 — 배경용, 화면 전체에만 쓴다)
function draw_cover(_name, _x, _y, _w, _h, _alpha = 1) {
	var _s = spr(_name);
	if (_s < 0 || !sprite_exists(_s)) return;
	var _sw = sprite_get_width(_s), _sh = sprite_get_height(_s);
	if (_sw <= 1 || _sh <= 1) return;
	var _k = max(_w / _sw, _h / _sh);
	draw_sprite_ext(_s, 0, _x + (_w - _sw * _k) / 2, _y + (_h - _sh * _k) / 2, _k, _k, 0, c_white, _alpha);
}

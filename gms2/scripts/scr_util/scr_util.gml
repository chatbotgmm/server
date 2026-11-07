// ── 작은 도구 ──

/// JSON에서 읽은 구조체의 값을 안전하게 꺼낸다(없거나 null이면 기본값)
function jv(_s, _key, _def) {
	if (!is_struct(_s)) return _def;
	var _v = _s[$ _key];
	if (is_undefined(_v)) return _def;
	return _v;
}

/// 배열이면 그대로, 아니면 빈 배열
function ja(_v) {
	return is_array(_v) ? _v : [];
}

/// 서버에 보낼 요청 번호(중복 요청 방지용, 32자)
function rid() {
	var _hex = "0123456789abcdef", _s = "";
	for (var _i = 0; _i < 32; _i++) _s += string_char_at(_hex, irandom(15) + 1);
	return "gm" + _s;
}

function grade_name(_g) {
	switch (_g) {
		case "COMMON": return "커먼";
		case "UNCOMMON": return "언커먼";
		case "RARE": return "레어";
		case "EPIC": return "에픽";
		case "LEGEND": return "전설";
		case "HIDDEN": return "히든";
		case "LIMITED": return "제한";
		case "TRANSCEND": return "초월";
		case "IMMORTAL": return "불멸";
		case "ETERNAL": return "영원";
	}
	return string(_g);
}

function grade_rank(_g) {
	switch (_g) {
		case "ETERNAL": return 0;
		case "IMMORTAL": return 1;
		case "TRANSCEND": return 2;
		case "LIMITED": return 3;
		case "HIDDEN": return 4;
		case "LEGEND": return 5;
		case "EPIC": return 6;
		case "RARE": return 7;
		case "UNCOMMON": return 8;
		case "COMMON": return 9;
	}
	return 99;
}

function elem_name(_e) {
	switch (_e) {
		case "NORMAL": return "일반";
		case "FIRE": return "불";
		case "WATER": return "물";
		case "LIGHTNING": return "번개";
		case "ICE": return "얼음";
		case "NATURE": return "자연";
		case "HOLY": return "성";
		case "DARK": return "어둠";
	}
	return string(_e);
}

/// 서버 오류 코드를 사람이 읽는 문장으로
function error_text(_data, _code) {
	var _c = jv(_data, "code", jv(_data, "error", ""));
	var _m = jv(_data, "message", "");
	if (_m != "") return _m;
	switch (_c) {
		case "INSUFFICIENT_CREDITS": return "지원서가 부족해요.";
		case "FORCED_SWITCH_REQUIRED": return "쓰러진 갈매미를 먼저 교체해 주세요.";
		case "STORY_SCENE_STALE": return "이야기 진행이 바뀌었어요. 다시 불러왔어요.";
		case "BATTLE_NOT_FINISHED": return "전투가 아직 끝나지 않았어요.";
		case "login": return "로그인이 필요해요.";
	}
	if (_code == 0) return "서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.";
	if (_code == 429) return "요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요.";
	if (is_string(_c) && _c != "") return "요청을 처리하지 못했어요 (" + _c + ").";
	return "요청을 처리하지 못했어요 (" + string(_code) + ").";
}

// 종족: 서버 목록에 없어 방범대 등록증 v10 자료의 종족을 쓴다(검토안, 전투 시너지 확정 아님)
function tribe_of(_sp) {
	static _map = {
		u01:"태초",u02:"태초",u03:"거인",u04:"수목",u05:"태초",u06:"정령",u07:"정령",u08:"망령",u09:"태초",u10:"정령",
		u11:"태초",u12:"해인",u13:"태초",u14:"정령",u15:"요정",u16:"정령",u17:"태초",u18:"요정",u19:"거인",u20:"해인",
		u21:"거인",u22:"야수",u23:"태초",u24:"용족",u25:"태초",u26:"수목",u27:"야수",u28:"해인",u29:"태초",u30:"야수",
		u31:"망령",u32:"용족",u33:"태초",u34:"수목",u35:"해인",u36:"야수",u37:"야수",u38:"천익",u39:"태초",u40:"요정",
		u41:"정령",u42:"망령",u43:"태초",u44:"수목",u45:"거인",u46:"야수",u47:"거인",u48:"정령",u49:"천익",u50:"야수",
		u51:"수목",u52:"망령",u53:"천익",u54:"정령",u55:"용족",u56:"태초",u57:"천익",u58:"거인",u59:"망령",u60:"용족"
	};
	return jv(_map, _sp, "");
}

function tribe_rank(_sp) {
	static _order = ["태초", "거인", "수목", "정령", "망령", "해인", "요정", "야수", "용족", "천익"];
	var _t = tribe_of(_sp);
	for (var _i = 0; _i < array_length(_order); _i++) if (_order[_i] == _t) return _i;
	return 99;
}

/// 간단한 삽입 정렬(목록이 수백 개 이하라 충분). _cmp(a, b)가 0보다 작으면 a가 앞
function sort_by(_arr, _cmp) {
	var _n = array_length(_arr);
	for (var _i = 1; _i < _n; _i++) {
		var _x = _arr[_i], _j = _i - 1;
		while (_j >= 0 && _cmp(_arr[_j], _x) > 0) { _arr[_j + 1] = _arr[_j]; _j--; }
		_arr[_j + 1] = _x;
	}
	return _arr;
}

/// 글자 비교(유니코드 번호 순: 한글은 가나다순). a가 앞이면 음수
function str_cmp(_a, _b) {
	var _la = string_length(_a), _lb = string_length(_b), _n = min(_la, _lb);
	for (var _i = 1; _i <= _n; _i++) {
		var _d = ord(string_char_at(_a, _i)) - ord(string_char_at(_b, _i));
		if (_d != 0) return _d;
	}
	return _la - _lb;
}

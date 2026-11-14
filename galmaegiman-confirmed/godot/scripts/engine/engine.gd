# 갈매미맨 랜덤 디펜스: 게임 규칙(화면과 분리). 웹 판 public/defense/engine.js를 GDScript로 옮긴 것.
# - 수치·표는 godot/data/game_data.json(웹 판에서 tools/export_data.mjs로 뽑음)에서 읽는다. 여기에는 규칙(계산)만 있다.
# - 같은 시드·같은 행동이면 웹 판과 결과가 똑같아야 한다(scripts/test/compare_test.gd로 비교).
#   그래서 JS와 계산 순서를 그대로 두고, Math.round·Math.hypot·32비트 난수·안정 정렬·객체 키 순서를 JS와 같게 흉내 낸다.
# - 판 상태(g)·갈매미(u)·적(e)은 Dictionary. 키 이름은 웹 판과 같다(이어하기 저장을 맞추기 쉽게).
extends RefCounted

const DATA_PATH := "res://data/game_data.json"
const ELEM := {"불": "FIRE", "물": "WATER", "얼음": "ICE", "번개": "LIGHTNING", "자연": "NATURE", "성": "HOLY", "성술": "HOLY", "어둠": "DARK"}
const AURA_HOLD := 0.3
const INHERIT_CAP := {"slow": 0.6, "stun": 0.3, "astun": 0.2}
const UPG_GROUP := [0, 0, 1, 2, 3, 3, 4, 5, 6, 7]

static var D: Dictionary = {}
static var RULES: Dictionary = {}
static var UNITS: Array = []
static var RECIPES: Array = []
static var UNIT: Dictionary = {}
static var GRADES: Array = []
static var BY_GRADE: Dictionary = {}
static var REGIONS: Array = []
static var LOOP: Dictionary = {}
static var SPAWN: Dictionary = {}
static var INNER: Dictionary = {}
static var COMMON_SPOTS: Array = []
static var TOP_TIER := 6
static var TOP_POWER_TIER := 7
static var SPECIAL_ROLE := ""
static var LW := 0.0
static var LH := 0.0
static var PERIM := 0.0
static var _kit_cache: Dictionary = {}


static func _static_init() -> void:
	load_data(DATA_PATH)


static func load_data(path: String) -> void:
	var text := FileAccess.get_file_as_string(path)
	var parsed = JSON.parse_string(text)
	if typeof(parsed) != TYPE_DICTIONARY:
		push_error("게임 데이터를 읽지 못했어요: %s" % path)
		return
	D = parsed
	RULES = D.RULES
	UNITS = D.UNITS
	RECIPES = D.RECIPES
	GRADES = D.GRADES
	REGIONS = D.REGIONS
	LOOP = D.LOOP
	SPAWN = D.SPAWN
	INNER = D.INNER
	COMMON_SPOTS = D.COMMON_SPOTS
	TOP_TIER = int(D.TOP_TIER)
	TOP_POWER_TIER = int(D.TOP_POWER_TIER)
	SPECIAL_ROLE = D.SPECIAL_ROLE
	UNIT = {}
	for u in UNITS:
		UNIT[u.id] = u
	BY_GRADE = {}
	for gr in GRADES:
		var ids := []
		for u in UNITS:
			if u.grade == gr:
				ids.append(u.id)
		BY_GRADE[gr] = ids
	LW = LOOP.x1 - LOOP.x0
	LH = LOOP.y1 - LOOP.y0
	PERIM = 2.0 * (LW + LH)
	_kit_cache.clear()


# 규칙 값(RULES)을 바꾼 뒤에는 꼭 부른다(웹 판의 kitKey 비교 대신)
static func clear_kit_cache() -> void:
	_kit_cache.clear()


# ── JS와 같은 계산 도우미 ──

# Math.round: .5는 위로(음수도 +∞ 쪽)
static func jround(x: float) -> float:
	var r := floorf(x)
	return r + 1.0 if x - r >= 0.5 else r


# Math.hypot(V8 구현: 가장 큰 값으로 나눈 뒤 카한 합산)
static func hypot(a: float, b: float) -> float:
	a = absf(a)
	b = absf(b)
	var mx := maxf(a, b)
	if mx == 0.0:
		return 0.0
	if is_inf(mx):
		return INF
	var s := 0.0
	var comp := 0.0
	for v in [a, b]:
		var n: float = v / mx
		var summand := n * n - comp
		var pre := s + summand
		comp = (pre - s) - summand
		s = pre
	return sqrt(s) * mx


static func _imul_u32(a: int, b: int) -> int:
	var lo := (a & 0xFFFF) * b
	var hi := ((a >> 16) * b) & 0xFFFF
	return (lo + (hi << 16)) & 0xFFFFFFFF


# 웹 판 rng(seed)와 같은 수열(mulberry32). 상태는 g.rs(0~2^32−1 정수)
static func rand(g: Dictionary) -> float:
	var s: int = (int(g.rs) + 0x6D2B79F5) & 0xFFFFFFFF
	g.rs = s
	var t := s
	t = _imul_u32(t ^ (t >> 15), t | 1)
	t = t ^ ((t + _imul_u32(t ^ (t >> 7), t | 61)) & 0xFFFFFFFF)
	return float((t ^ (t >> 14)) & 0xFFFFFFFF) / 4294967296.0


# Array.prototype.sort처럼 같은 값이면 원래 순서를 지키는 정렬. key_fn(x) → 숫자
static func stable_sort(arr: Array, key_fn: Callable) -> Array:
	var dec := []
	for i in arr.size():
		dec.append([key_fn.call(arr[i]), i, arr[i]])
	dec.sort_custom(func(p, q): return p[0] < q[0] or (p[0] == q[0] and p[1] < q[1]))
	var out := []
	for d in dec:
		out.append(d[2])
	return out


# JS 객체의 for…in 순서: 정수 키는 작은 것부터, 그다음 글자 키는 넣은 순서
static func js_keys(m: Dictionary) -> Array:
	var ints := []
	var strs := []
	for key in m.keys():
		if typeof(key) == TYPE_INT:
			ints.append(key)
		else:
			strs.append(key)
	ints.sort()
	return ints + strs


static func truthy(v) -> bool:
	match typeof(v):
		TYPE_NIL:
			return false
		TYPE_BOOL:
			return v
		TYPE_INT, TYPE_FLOAT:
			return v != 0
		TYPE_STRING:
			return v != ""
	return true


static func num(d: Dictionary, key: String, def: float = 0.0) -> float:
	var v = d.get(key)
	return def if v == null else float(v)


static func rule(key: String, def = null):
	var v = RULES.get(key)
	return def if v == null else v


# ── 기본 정보 ──
static func grade_tier(gr: String) -> int:
	return GRADES.find(gr)


static func tier_of(id: String) -> int:
	return grade_tier(UNIT[id].grade)


static func units_of_grade(gr: String) -> Array:
	return BY_GRADE.get(gr, [])


# 위치는 Vector2(32비트 실수)가 아니라 64비트 실수 사전 {x, y}로 계산한다(웹 판과 결과를 똑같이 맞추려고 — Vector2로 하면 좌표가 조금씩 달라짐)
static func path_pos(s: float) -> Dictionary:   # 반시계 방향(왼쪽 위에서 아래로 → 오른쪽 → 위 → 왼쪽)
	var d := fmod(fmod(s, PERIM) + PERIM, PERIM)
	if d < LH:
		return {"x": float(LOOP.x0), "y": LOOP.y0 + d}
	d -= LH
	if d < LW:
		return {"x": LOOP.x0 + d, "y": float(LOOP.y1)}
	d -= LW
	if d < LH:
		return {"x": float(LOOP.x1), "y": LOOP.y1 - d}
	d -= LH
	return {"x": LOOP.x1 - d, "y": float(LOOP.y0)}


static func clamp_field(x: float, y: float) -> Dictionary:
	return {"x": maxf(INNER.x0, minf(INNER.x1, x)), "y": maxf(INNER.y0, minf(INNER.y1, y))}


# ── 유틸 세기(웹 판과 같은 식) ──
static func SHRED(t: int) -> float:
	return [2, 4, 7, 30, 80, 100, 130, 160, 190, 220][t]


static func AMP(t: int) -> float:
	return [0.02, 0.03, 0.05][t] if t < 3 else 0.15 + 0.05 * (t - 3)


static func SLOW(t: int) -> float:
	return [0.06, 0.08, 0.12][t] if t < 3 else minf(0.5, 0.25 + 0.04 * (t - 3))


static func STUN(t: int) -> float:
	return [0.03, 0.05, 0.08][t] if t < 3 else minf(0.35, 0.18 + 0.03 * (t - 3))


static func ASTUN(t: int) -> float:
	if t < 4:
		return 0.0
	if t >= 7:
		return 0.2
	return [0.12, 0.14, 0.16][t - 4]


static func BUFF(t: int) -> float:
	if t < 3:
		return 0.0
	if t == 3:
		return 0.08
	if t == 4:
		return 0.12
	if t == 5:
		return 0.15
	return 0.2


static func slow_mul(total: float) -> float:
	return 1.0 - D.SLOW_EFFECT * minf(D.SLOW_MAX, total)


static func stun_perks(k: Dictionary, t: int) -> Array:
	var big := jround(SHRED(t) * 0.6)
	var out := []
	if truthy(k.get("shred")): out.append("shred")
	if num(k, "mshred") >= big: out.append("mshred")
	if truthy(k.get("amp")): out.append("amp")
	if truthy(k.get("slow")): out.append("slow")
	if truthy(k.get("area")): out.append("area")
	if num(k, "splash") >= 0.3: out.append("splash")
	if truthy(k.get("chain")): out.append("chain")
	if truthy(k.get("poison")): out.append("poison")
	if truthy(k.get("holy")): out.append("holy")
	if truthy(k.get("wisp")): out.append("wisp")
	return out


static func stun_time_of(k: Dictionary, t: int) -> float:
	var base := 2.25 if k.job == "보조" else 1.75
	return minf(D.STUN_MAX, maxf(D.STUN_MIN, base - 0.25 * stun_perks(k, t).size()))


# ── 고유 기술 설명 ──
static func sig_text(id: String) -> String:
	var s = D.SIGNATURE.get(id)
	if s == null:
		return ""
	var desc := ""
	match s.kind:
		"smash": desc = "대상에게 ×%s 강타" % fmt(s.mul)
		"execute": desc = "체력 30%% 이하 적은 바로 쓰러뜨림(보스 제외), 아니면 ×%s" % fmt(s.mul)
		"pierce": desc = "대상 쪽 직선 위 적 모두 ×%s" % fmt(s.mul)
		"bounce": desc = "가까운 적 %s마리에게 튕기며 ×%s" % [fmt(s.n), fmt(s.mul)]
		"nova": desc = "대상 둘레 적 모두 ×%s" % fmt(s.mul)
		"storm": desc = "적 %s마리에게 낙뢰 ×%s" % [fmt(s.n), fmt(s.mul)]
		"bind": desc = "둘레 적 모두 ×%s + %s초 묶기" % [fmt(s.mul), fmt(s.sec)]
		"field":
			var sl := ("·이감 %s%%" % fmt(jround(s.slow * 100))) if truthy(s.get("slow")) else ""
			desc = "%s초 장판(0.5초마다 ×%s%s)" % [fmt(s.sec), fmt(s.mul), sl]
	return "고유 기술 「%s」: 공격 %s번마다 %s" % [s.name, fmt(s.every), desc]


# 숫자를 JS 글자처럼(정수면 소수점 없이)
static func fmt(v) -> String:
	var f := float(v)
	if f == floorf(f) and absf(f) < 1e15:
		return str(int(f))
	return str(f)


# ── 갈매미 한 마리의 공격 방식(규칙이 같으면 결과가 같으므로 한 번 계산해 둠). 돌려준 사전은 고치지 않는다 ──
static func kit(id: String) -> Dictionary:
	var k = _kit_cache.get(id)
	if k == null:
		k = _kit_raw(id)
		_kit_cache[id] = k
	return k


static func _kit_raw(id: String) -> Dictionary:
	var u: Dictionary = UNIT[id]
	if u.role == SPECIAL_ROLE:
		return {"job": "재료", "type": "phys", "elem": "NORMAL", "tier": tier_of(id), "dmg": 0.0, "gap": 99.0, "range": 0.0, "mul": 0.0, "sell": 0.0, "wisp": null, "explore": 0.0, "special": true}
	var tt := tier_of(id)
	var t := TOP_POWER_TIER if tt >= TOP_TIER else tt
	var role: String = u.role
	var elem: String = ELEM.get(role.split(" ")[0], "NORMAL")
	var tank := role.begins_with("고체력")
	var ambush := role.contains("기습")
	var mage := role.ends_with("술사")
	var type := "mag" if mage else "phys"
	var k := {}
	if tank:
		k = {"job": "보조", "gap": 1.1, "mul": 0.35, "range": 150.0, "stun": STUN(t), "shred": SHRED(t)}
		if ASTUN(t) != 0.0:
			k.astun = ASTUN(t)
	elif ambush and mage:
		k = {"job": "보조", "gap": 0.6, "mul": 0.3, "range": 170.0, "mshred": SHRED(t), "amp": AMP(t)}
	elif ambush:
		k = {"job": "보조", "gap": 0.5, "mul": 0.3, "range": 160.0, "shred": SHRED(t), "slow": SLOW(t)}
	elif role.ends_with("근접술사"):
		k = {"job": "주력", "gap": 0.9, "mul": 0.75, "range": 140.0, "area": 70.0}
	elif mage:
		k = {"job": "주력", "gap": 1.1, "mul": 1.4, "range": 230.0}
	else:
		k = {"job": "주력", "gap": 0.8, "mul": 1.0, "range": 170.0}
	if k.job == "주력":
		if mage and not k.has("area"):
			k.splash = 0.1 if t < 2 else (0.2 if t < 3 else 0.3)
			k.splashR = 45.0
		if elem == "FIRE":
			k.splash = 0.4
			k.splashR = 50.0
		if elem == "LIGHTNING":
			k.chain = 2.0
		if elem == "ICE":
			k.slow = SLOW(t) * 0.8
		if elem == "WATER":
			k.slow = SLOW(t) * 0.5
		if elem == "NATURE":
			k.poison = 0.25
		if elem == "DARK":
			if mage:
				k.mshred = SHRED(t)
			else:
				k.shred = SHRED(t)
		if elem == "HOLY":
			k.holy = 1.5
	var ux = D.UTIL_EXTRA.get(id)
	if ux != null:
		if truthy(ux.get("slow")): k.slow = maxf(num(k, "slow"), SLOW(t) * ux.slow)
		if truthy(ux.get("stun")): k.stun = maxf(num(k, "stun"), STUN(t) * ux.stun)
		if truthy(ux.get("astun")): k.astun = maxf(num(k, "astun"), maxf(ASTUN(t), 0.12) * ux.astun)
		if truthy(ux.get("mshred")): k.mshred = maxf(num(k, "mshred"), jround(SHRED(t) * ux.mshred))
		if truthy(ux.get("shred")): k.shred = maxf(num(k, "shred"), jround(SHRED(t) * ux.shred))
	if D.UTIL_BUFF.has(id):
		k[D.UTIL_BUFF[id]] = BUFF(t)
	if D.UTIL_WEAKEN.has(id):
		k.weaken = 0.12 if t >= 6 else 0.1
	if not truthy(k.get("mshred")) and (t == 4 or t == 5 or (mage and k.job == "주력")):
		k.mshred = maxf(1.0, jround(SHRED(t) * float(rule("mshredAll", 0.35))))
	if k.job == "주력":
		k.line = truthy(k.get("area")) or truthy(k.get("chain")) or num(k, "splash") >= 0.3
		k.bossMul = float(RULES.lineBossMul) if k.line else float(RULES.singleBossMul)
		if not k.line and t >= 4:
			k.mul *= float(rule("singleMul", 1.0))
	var AS := float(rule("atkSpeed", 1.0))
	if truthy(k.get("stun")): k.stun /= AS
	if truthy(k.get("astun")): k.astun /= AS
	if truthy(k.get("poison")): k.poison *= AS
	if tt < 3:
		for key in ["shred", "mshred", "amp", "slow", "stun", "astun", "poison", "haste", "might", "weaken"]:
			k.erase(key)
	var out := k.duplicate()
	var sg := sig_text(id)
	if sg != "":
		out.sig = sg
	out.gap = k.gap / AS
	out.type = type
	out.elem = elem
	out.tier = tt
	out.dmg = jround(float(RULES.dmg[t]) * k.mul / AS)
	out.sell = float(RULES.sell[t])
	out.wisp = D.WISP_SKILL.get(id)
	out.explore = float(D.EXPLORE.get(id, 0.0))
	if tt >= TOP_TIER:
		_inherit_util(id, out)
		var m := 0.0
		var r = recipe_for(id)
		if r != null:
			for ing in r.in:
				m = maxf(m, float(D.EXPLORE.get(ing, 0.0)))
		if m > 1.0:
			var v: float = 1.0 + (m - 1.0) * float(D.EXPLORE_INHERIT)
			if v > num(out, "explore"):
				out.explore = v
				out.exploreFrom = true
		if num(out, "explore") < float(D.EXPLORE_TOP):
			out.explore = float(D.EXPLORE_TOP)
			out.exploreTop = true
			out.erase("exploreFrom")
		out.exDmg = jround(float(RULES.dmg[t]) * maxf(k.mul, 1.0) / AS)
	if truthy(out.get("shred")) and truthy(out.get("mshred")):
		if out.type == "phys":
			out.erase("mshred")
		else:
			out.erase("shred")
	if truthy(out.get("stun")) or truthy(out.get("astun")):
		out.stunTime = stun_time_of(out, t)
	return out


static func _inherit_util(id: String, k: Dictionary) -> void:
	var r = recipe_for(id)
	if r == null:
		return
	var keys := ["slow", "stun", "astun"] + (["shred"] if k.type == "phys" else ["mshred", "amp"])
	k.inherited = []
	for key in keys:
		var best := 0.0
		for ing in r.in:
			var v := num(kit(ing), key)
			if v > best:
				best = v
		if best == 0.0:
			continue
		var v: float = best if INHERIT_CAP.has(key) else best * float(D.INHERIT)
		if INHERIT_CAP.has(key):
			v = minf(INHERIT_CAP[key], v)
		if key == "shred" or key == "mshred":
			v = jround(v)
		if v > num(k, key):
			k[key] = v
			k.inherited.append(key)


# 화면용 짧은 설명
static func kit_tips(k: Dictionary) -> Array:
	var out := []
	if truthy(k.get("special")):
		return ["공격하지 않음", "초월 등급 조합 재료(쓰면 사라짐)", "외부탐사 6장 보상"]
	if truthy(k.get("shred")): out.append("방깍 %s" % fmt(jround(k.shred)))
	if truthy(k.get("mshred")): out.append("주변 마방깍 %s" % fmt(jround(k.mshred)))
	if truthy(k.get("amp")): out.append("마딜증폭 %s%%" % fmt(jround(k.amp * 100)))
	if truthy(k.get("stun")): out.append("스턴 %s%%(%s초)" % [fmt(jround(k.stun * 100)), fmt(k.get("stunTime", 1))])
	if truthy(k.get("astun")): out.append("범위 스턴 %s%%(%s초)" % [fmt(jround(k.astun * 100)), fmt(k.get("stunTime", 1))])
	if k.job == "주력":
		out.append(("라인 정리형(보스 피해 ×%s)" % fmt(RULES.lineBossMul)) if k.line else ("단일 공격형(보스 피해 ×%s)" % fmt(RULES.singleBossMul)))
	if truthy(k.get("explore")):
		if truthy(k.get("exploreTop")):
			out.append("최상위 탐사: 외부탐사 피해 ×%s(역할 감점 없음)" % fmt(k.explore))
		else:
			out.append("탐사 특기%s: 외부탐사 피해 ×%s%s" % ["(재료에게 물려받음)" if truthy(k.get("exploreFrom")) else "", fmt(k.explore), "(역할 감점 없음)" if truthy(k.get("exDmg")) else ""])
	if truthy(k.get("slow")): out.append("주변 이속감소 %s%%(겹침)" % fmt(jround(k.slow * 100)))
	if truthy(k.get("area")): out.append("범위 공격(반경 %s)" % fmt(k.area))
	if truthy(k.get("splash")): out.append("스플래시 %s%%(반경 %s)" % [fmt(jround(k.splash * 100)), fmt(k.get("splashR", 50))])
	if truthy(k.get("chain")): out.append("번개 연쇄")
	if truthy(k.get("poison")): out.append("독")
	if truthy(k.get("holy")): out.append("보스 추가 피해")
	if truthy(k.get("wisp")): out.append("위습 찾기")
	if k.get("inherited", []).size() > 0: out.append("재료 유틸 물려받음")
	var head := []
	if truthy(k.get("sig")): head.append(k.sig)
	if truthy(k.get("weaken")): head.append("패널티: 주변 공격력 −%s%%(반경 %s)" % [fmt(jround(k.weaken * 100)), fmt(D.WEAKEN_R)])
	if truthy(k.get("haste")): head.append("주변 공격속도 +%s%%(반경 %s)" % [fmt(jround(k.haste * 100)), fmt(D.BUFF_R)])
	if truthy(k.get("might")): head.append("주변 공격력 +%s%%(반경 %s)" % [fmt(jround(k.might * 100)), fmt(D.BUFF_R)])
	return head + out


# ── 웨이브·난이도 ──
static func def_at(w: int) -> float:
	return float(RULES.defBase) + float(RULES.defPer) * mini(w, int(RULES.defLateFrom)) + float(RULES.defLatePer) * maxi(0, w - int(RULES.defLateFrom))


static func def_mul(left: float) -> float:
	return pow(100.0 / (100.0 + maxf(0.0, left)), float(RULES.armorPow))


static func wave_info(w: int) -> Dictionary:
	var boss := w % int(RULES.bossEvery) == 0
	var nE := int(rule("hpEarlyN", 0))
	var eW := mini(w - 1, nE)
	var lW := maxi(0, w - 1 - nE)
	var hp := jround(float(RULES.hpBase) * pow(float(rule("hpEarly", RULES.hpGrowth)), eW) * pow(float(RULES.hpGrowth), lW) * (1.0 + w / float(RULES.hpLinear)))
	var A = RULES.get("hpAnchors")
	if A != null and A.size() > 0:
		var k := -1
		for i in A.size():
			if A[i][0] >= w:
				k = i
				break
		if k < 0:
			hp = jround(A[A.size() - 1][1])
		elif k == 0 or A[k][0] == w:
			hp = jround(A[k][1])
		else:
			var w0: float = A[k - 1][0]
			var h0: float = A[k - 1][1]
			var w1: float = A[k][0]
			var h1: float = A[k][1]
			hp = jround(h0 * pow(h1 / h0, (w - w0) / (w1 - w0)))
	var df := def_at(w)
	var armor := jround(df * (float(RULES.defBossMul) if boss else 1.0))
	var look := ""
	if boss:
		look = D.BOSS_LOOK[mini(D.BOSS_LOOK.size(), int(ceilf(w / 10.0))) - 1]
	else:
		for band in D.ENEMY_LOOK:
			if w <= band[0]:
				look = band[1][w % band[1].size()]
				break
	var late := maxi(0, w - int(rule("lateFrom", 999)))
	var hp_out := 0.0
	if boss:
		hp_out = hp * float(RULES.bossMul.get(str(w), 2))
	else:
		hp_out = jround(hp * (1.0 + float(rule("lateHpPer", 0)) * late))
	var speed := 34.0 if boss else (56.0 + mini(30, w)) * (1.0 + float(rule("lateSpdPer", 0)) * late)
	var gold := jround((30.0 if boss else 1.0) * (float(RULES.killGold) + float(RULES.killGoldGrow) * w + float(rule("killGoldSq", 0)) * w * w))
	return {"boss": boss, "kind": "boss" if boss else "일반", "armor": armor, "mr": armor, "look": look, "baseHp": hp, "hp": hp_out, "speed": speed, "gold": gold}


static func region_guard(k: int) -> Dictionary:
	var r: Dictionary = REGIONS[k]
	var wi := wave_info(int(r.level) + 2)
	var gh = RULES.get("guardHp")
	var hp: float = float(gh[k]) if gh != null and k < gh.size() else jround(wi.baseHp * float(RULES.guardMul))
	return {"hp": hp, "armor": jround(wi.armor * 1.3), "mr": jround(wi.mr * 1.3)}


static func mode_of(g: Dictionary) -> Dictionary:
	return D.MODES.get(g.mode, D.MODES.normal)


static func diff_of(g: Dictionary) -> Dictionary:
	return D.DIFFS.get(g.diff, D.DIFFS.master)


static func armor_at(g: Dictionary, w: int) -> float:
	return jround(wave_info(w).armor * float(diff_of(g).armor))


static func mr_at(g: Dictionary, w: int) -> float:
	return jround(wave_info(w).mr * float(diff_of(g).mr))


static func rift_at(w: int) -> float:
	var rf := int(rule("riftFrom", 999))
	return minf(float(rule("riftMax", 0)), float(rule("riftPer", 0)) * (w - rf)) if w > rf else 0.0


static func rest_wave(w: int) -> bool:
	return truthy(RULES.get("restEvery")) and w % int(RULES.restEvery) == 0 and w < int(RULES.waves)


static func wave_len_of(w: int) -> float:
	return float(RULES.waveLen) + (float(RULES.restLen) if rest_wave(w) else 0.0)


static func resting(g: Dictionary) -> bool:
	return rest_wave(g.wave) and g.waveT >= float(RULES.waveLen)


static func next_wave_in(g: Dictionary) -> float:
	return maxf(0.0, wave_len_of(g.wave) - g.waveT)


# ── 새 판 ──
static func new_game(seed: int, mode: String = "normal", diff: String = "master") -> Dictionary:
	var picks := {"COMMON": 0, "UNCOMMON": 0, "RARE": 0, "EPIC": 0}
	for key in RULES.startPick:
		picks[key] = RULES.startPick[key]
	var upg := []
	upg.resize(8)
	upg.fill(0)
	return {
		"seed": seed, "mode": mode if D.MODES.has(mode) else "normal", "diff": diff if D.DIFFS.has(diff) else "master", "rs": seed & 0xFFFFFFFF,
		"dmg": {}, "dmgWave": {}, "tickets": 0.0, "rerolls": 0, "t": 0.0, "gold": float(rule("startGold", 0)), "kills": 0, "upg": upg,
		"wave": 0, "waveT": 0.0, "spawnLeft": 0, "spawnT": 0.0, "warn": 0, "warnAcc": 0.0,
		"wisps": {"COMMON": int(RULES.startWisps), "UNCOMMON": int(rule("startUncommon", 0)), "RARE": 0, "EPIC": 0, "LEGEND": 0},
		"picks": picks, "enemies": [], "units": [], "nextId": 1, "nextUid": 1,
		"goals": [], "mission": null, "missionDone": 0, "autoStore": false, "autoGrades": {}, "missionDmg": {}, "peerDmg": null,
		"over": null, "reason": "", "fx": [], "news": [], "zones": [],
	}


static func news(g: Dictionary, msg: String) -> void:
	g.news.append({"msg": msg, "t": g.t})
	if g.news.size() > 6:
		g.news.pop_front()


static func add_wisps(g: Dictionary, bag: Dictionary) -> void:
	for key in bag:
		g.wisps[key] = int(g.wisps.get(key, 0)) + int(bag[key])


static func wisp_text(bag: Dictionary) -> String:
	var parts := []
	for key in bag:
		parts.append("%s 위습 %s개" % [D.GRADE_NAME[key], fmt(bag[key])])
	return " · ".join(parts)


static func pick_text(bag: Dictionary) -> String:
	var parts := []
	for key in bag:
		parts.append("%s 선택 위습 %s개" % [D.GRADE_NAME[key], fmt(bag[key])])
	return " · ".join(parts)


# ── 갈매미 ──
static func field_units(g: Dictionary) -> Array:
	return g.units.filter(func(u): return not u.away and not u.get("stored", false))


static func stored_units(g: Dictionary) -> Array:
	return g.units.filter(func(u): return u.get("stored", false))


static func away_units(g: Dictionary) -> Array:
	return g.units.filter(func(u): return u.away)


static func unit_by_id(g: Dictionary, uid: int):
	for u in g.units:
		if u.uid == uid:
			return u
	return null


static func _spot_of(id: String) -> Dictionary:
	var gr = UNIT.get(id, {}).get("grade")
	var i: int = BY_GRADE.get(gr, []).find(id)
	return COMMON_SPOTS[(0 if i < 0 else i) % COMMON_SPOTS.size()]


static func _near_spawn(g: Dictionary, u: Dictionary) -> void:
	var a := rand(g) * PI * 2.0
	var r := 10.0 + rand(g) * 40.0
	var p := clamp_field(SPAWN.x + cos(a) * r, SPAWN.y + sin(a) * r)
	u.x = p.x
	u.tx = p.x
	u.y = p.y
	u.ty = p.y


static func _add_unit(g: Dictionary, id: String, x: float, y: float) -> Dictionary:
	var p := clamp_field(x, y)
	var u := {"uid": g.nextUid, "id": id, "x": p.x, "y": p.y, "tx": p.x, "ty": p.y, "cd": 0.2 + rand(g) * 0.3, "born": g.t, "away": false}
	g.nextUid += 1
	g.units.append(u)
	return u


static func _group_of(gr: String):
	for q in D.STORE_GROUPS:
		if q.grades.has(gr):
			return q
	return null


static func _auto_store_new(g: Dictionary, u: Dictionary) -> void:
	var q = _group_of(UNIT[u.id].grade)
	if q != null and (truthy(g.autoGrades.get(q.key)) or (q.key == "COMMON" and g.autoStore)):
		u.stored = true


static func summon(g: Dictionary, grade: String = "COMMON") -> Dictionary:
	if g.over != null:
		return {"ok": false, "why": ""}
	if not (int(g.wisps.get(grade, 0)) > 0):
		return {"ok": false, "why": "%s 위습이 없어요." % D.GRADE_NAME[grade]}
	g.wisps[grade] -= 1
	var pool: Array = BY_GRADE[grade]
	var id: String = pool[int(floorf(rand(g) * pool.size()))]
	var a := rand(g) * PI * 2.0
	var r := 10.0 + rand(g) * 34.0
	var u: Dictionary
	if grade == "COMMON":
		var cs := _spot_of(id)
		u = _add_unit(g, id, cs.x, cs.y)
	else:
		u = _add_unit(g, id, SPAWN.x + cos(a) * r, SPAWN.y + sin(a) * r)
	u.got = true
	_auto_store_new(g, u)
	g.fx.append({"kind": "combine", "x": u.x, "y": u.y, "t": g.t})
	return {"ok": true, "unit": u}


static func summon_pick(g: Dictionary, id: String) -> Dictionary:
	var grade = UNIT.get(id, {}).get("grade")
	if g.over != null or grade == null:
		return {"ok": false, "why": ""}
	if not (int(g.picks.get(grade, 0)) > 0):
		return {"ok": false, "why": "%s 선택 위습이 없어요." % D.GRADE_NAME[grade]}
	g.picks[grade] -= 1
	var a := rand(g) * PI * 2.0
	var r := 10.0 + rand(g) * 34.0
	var u: Dictionary
	if grade == "COMMON":
		var cs := _spot_of(id)
		u = _add_unit(g, id, cs.x, cs.y)
	else:
		u = _add_unit(g, id, SPAWN.x + cos(a) * r, SPAWN.y + sin(a) * r)
	u.got = true
	_auto_store_new(g, u)
	g.fx.append({"kind": "combine", "x": u.x, "y": u.y, "t": g.t})
	return {"ok": true, "unit": u}


static func sell(g: Dictionary, uid: int):
	var k := -1
	for i in g.units.size():
		if g.units[i].uid == uid:
			k = i
			break
	if k < 0 or g.over != null:
		return false
	var id: String = g.units[k].id
	if UNIT[id].role == SPECIAL_ROLE or tier_of(id) >= TOP_TIER:
		return false
	var grade: String = UNIT[id].grade
	g.gold += kit(id).sell
	g.units.remove_at(k)
	var got := ""
	if grade == "UNCOMMON" and rand(g) < 0.3:
		g.tickets += 1
		got = "모집권 1"
	elif grade == "RARE":
		g.tickets += 1
		got = "모집권 1"
	elif grade == "EPIC":
		g.tickets += 1
		add_wisps(g, {"COMMON": 2})
		got = "모집권 1 · 커먼 위습 2"
	if got != "":
		news(g, "판매 보너스: %s" % got)
		return got
	return true


static func recruit(g: Dictionary) -> Dictionary:
	if g.over != null:
		return {"ok": false, "why": ""}
	if g.tickets < float(RULES.recruitCost):
		return {"ok": false, "why": "모집권이 모자라요(%s개 필요)." % fmt(RULES.recruitCost)}
	g.tickets -= float(RULES.recruitCost)
	var r := rand(g)
	var grade = null
	for gr in RULES.recruitOdds:
		r -= float(RULES.recruitOdds[gr])
		if r < 0:
			grade = gr
			break
	if grade == null:
		return {"ok": true, "fail": true}
	g.wisps[grade] = int(g.wisps.get(grade, 0)) + 1
	var res := summon(g, grade)
	return {"ok": true, "unit": res.get("unit"), "grade": grade}


static func can_reroll(g: Dictionary, u) -> bool:
	return u != null and u.get("got", false) and tier_of(u.id) >= 3 and UNIT[u.id].role != SPECIAL_ROLE and not u.away and int(g.rerolls) > 0 and g.over == null


static func reroll_unit(g: Dictionary, uid: int):
	var u = unit_by_id(g, uid)
	if not can_reroll(g, u):
		return null
	var pool: Array = BY_GRADE.get(UNIT[u.id].grade, []).filter(func(id): return id != u.id and UNIT[id].role != SPECIAL_ROLE)
	if pool.is_empty():
		return null
	var was: String = u.id
	u.id = pool[int(floorf(rand(g) * pool.size()))]
	u.cd = 0.3
	u.sigN = 0
	g.rerolls -= 1
	g.fx.append({"kind": "combine", "x": u.x, "y": u.y, "t": g.t})
	news(g, "리롤: %s → %s" % [UNIT[was].name, UNIT[u.id].name])
	return u


static func move_unit(g: Dictionary, uid: int, x: float, y: float, now: bool = false) -> bool:
	var u = unit_by_id(g, uid)
	if u == null or u.away or u.get("lock", false):
		return false
	var p := clamp_field(x, y)
	u.tx = p.x
	u.ty = p.y
	if now:
		u.x = p.x
		u.y = p.y
	return true


static func arrange_uncommon(g: Dictionary) -> int:
	var n := 0
	for u in field_units(g):
		if UNIT[u.id].grade == "UNCOMMON" and not u.get("lock", false):
			var s := _spot_of(u.id)
			if move_unit(g, u.uid, s.x, s.y, true):
				n += 1
	return n


static func store(g: Dictionary, uids: Array) -> int:
	var n := 0
	for uid in uids:
		var u = unit_by_id(g, uid)
		if u != null and not u.away and not u.get("stored", false):
			u.stored = true
			n += 1
	return n


static func unstore(g: Dictionary, uids: Array) -> int:
	var n := 0
	for uid in uids:
		var u = unit_by_id(g, uid)
		if u != null and u.get("stored", false):
			u.stored = false
			_near_spawn(g, u)
			u.cd = 0.3
			n += 1
	return n


static func store_group_units(g: Dictionary, key: String) -> Array:
	return field_units(g).filter(func(u):
		var q = _group_of(UNIT[u.id].grade)
		return not u.get("lock", false) and q != null and q.key == key)


static func store_group(g: Dictionary, key: String) -> int:
	var n := store(g, store_group_units(g, key).map(func(u): return u.uid))
	if key == "COMMON":
		arrange_uncommon(g)
	return n


static func set_auto_grade(g: Dictionary, key: String, on: bool) -> int:
	g.autoGrades[key] = on
	if key == "COMMON":
		g.autoStore = on
	return store_group(g, key) if on else 0


static func gather(g: Dictionary, uid: int) -> Array:
	var t = unit_by_id(g, uid)
	if t == null or t.away or t.get("stored", false):
		return []
	var list := field_units(g).filter(func(u): return u.id == t.id)
	for u in list:
		if not u.get("lock", false) or is_same(u, t):
			u.tx = t.tx
			u.ty = t.ty
	return list.map(func(u): return u.uid)


static func set_lock(g: Dictionary, uids: Array, on: bool) -> int:
	var n := 0
	for uid in uids:
		var u = unit_by_id(g, uid)
		if u != null and not u.away and not u.get("stored", false):
			u.lock = on
			n += 1
	return n


# ── 강화 ──
static func upg_group(tier: int) -> int:
	return UPG_GROUP[tier]


static func upgrade_cost(g: Dictionary, gi: int) -> float:
	var gr = RULES.get("upgGrowth")
	return jround(float(RULES.upgBase[gi]) * pow(float(gr if gr != null else 1), g.upg[gi]) + (0.0 if truthy(gr) else float(RULES.upgStep[gi]) * g.upg[gi]))


static func upg_mul(g: Dictionary, tier: int) -> float:
	return 1.0 + float(RULES.upgPct) * g.upg[upg_group(tier)]


static func upg_spd_mul(g: Dictionary, tier: int) -> float:
	return 1.0 + float(rule("upgSpd", 0)) * g.upg[upg_group(tier)]


static func gap_of(g: Dictionary, k: Dictionary) -> float:
	return k.gap / upg_spd_mul(g, k.tier)


static func upg_maxed(g: Dictionary, gi: int) -> bool:
	return g.upg[gi] >= int(RULES.upgMax)


static func upgrade(g: Dictionary, gi: int) -> Dictionary:
	var cost := upgrade_cost(g, gi)
	if g.over != null:
		return {"ok": false, "why": ""}
	if truthy(mode_of(g).get("noUpg")):
		return {"ok": false, "why": "강화 금지 모드예요."}
	if upg_maxed(g, gi):
		return {"ok": false, "why": "이미 최대 단계(풀강)예요."}
	if g.gold < cost:
		return {"ok": false, "why": "새우가 모자라요(%s 필요)." % fmt(cost)}
	g.gold -= cost
	g.upg[gi] += 1
	return {"ok": true}


# ── 조합 ──
static func recipe_for(id: String):
	for r in RECIPES:
		if r.out == id:
			return r
	return null


static func recipes_using(id: String) -> Array:
	return RECIPES.filter(func(r): return r.in.has(id))


static func recipe_units(g: Dictionary, r: Dictionary, keep_uid: int = 0):
	var avail := stable_sort(g.units, func(u): return 1 if u.away else 0)
	var used := {}
	var out := []
	var need: Array = r.in.duplicate()
	if keep_uid != 0:
		var ku = null
		for u in avail:
			if u.uid == keep_uid:
				ku = u
				break
		var k: int = need.find(ku.id) if ku != null else -1
		if k < 0:
			return null
		need.remove_at(k)
		used[ku.uid] = true
		out.append(ku.uid)
	for id in need:
		var f = null
		for u in avail:
			if not used.has(u.uid) and u.id == id:
				f = u
				break
		if f == null:
			return null
		used[f.uid] = true
		out.append(f.uid)
	return out


static func top_count(g: Dictionary) -> int:
	return g.units.filter(func(u): return tier_of(u.id) >= TOP_TIER).size()


static func top_blocked(g: Dictionary, r: Dictionary) -> bool:
	return float(RULES.topCap) > 0 and tier_of(r.out) >= TOP_TIER and top_count(g) >= int(RULES.topCap)


static func combine(g: Dictionary, r: Dictionary, keep_uid: int = 0):
	if g.over != null or top_blocked(g, r):
		return null
	var uids = recipe_units(g, r, keep_uid)
	if uids == null:
		return null
	var a := rand(g) * PI * 2.0
	var rr := 10.0 + rand(g) * 30.0
	var x: float = SPAWN.x + cos(a) * rr
	var y: float = SPAWN.y + sin(a) * rr
	g.units = g.units.filter(func(u): return not uids.has(u.uid))
	var nu := _add_unit(g, r.out, x, y)
	g.fx.append({"kind": "combine", "x": x, "y": y, "t": g.t})
	return nu


static func count_on_field(g: Dictionary) -> Dictionary:
	var c := {}
	for u in g.units:
		c[u.id] = int(c.get(u.id, 0)) + 1
	return c


static func toggle_goal(g: Dictionary, out: String) -> bool:
	var k: int = g.goals.find(out)
	if k >= 0:
		g.goals.remove_at(k)
		return false
	if g.goals.size() >= int(D.GOAL_MAX):
		g.goals.pop_front()
	g.goals.append(out)
	return true


# ── 외부탐사 ──
static func choice_text(c: Dictionary) -> String:
	if c.has("unit"):
		return UNIT[c.unit].name
	if c.has("wisps"):
		return wisp_text(c.wisps)
	if c.has("picks"):
		return pick_text(c.picks)
	return ""


static func choose_reward(g: Dictionary, i: int) -> bool:
	if g.get("choice") == null:
		return false
	var r: Dictionary = REGIONS[g.choice.k]
	var ch: Array = r.get("choice", [])
	if i < 0 or i >= ch.size():
		return false
	var c: Dictionary = ch[i]
	if c.has("unit"):
		var nu := _add_unit(g, c.unit, SPAWN.x, SPAWN.y)
		g.fx.append({"kind": "combine", "x": nu.x, "y": nu.y, "t": g.t})
	if c.has("wisps"):
		add_wisps(g, c.wisps)
	if c.has("picks"):
		for key in c.picks:
			g.picks[key] = int(g.picks.get(key, 0)) + int(c.picks[key])
	g.choice = null
	news(g, "%s장 보상: %s을(를) 받았어요" % [fmt(r.ch), choice_text(c)])
	return true


static func mission_open(g: Dictionary) -> bool:
	return g.missionDone < REGIONS.size() and not truthy(mode_of(g).get("noMission"))


static func story_left(g: Dictionary) -> bool:
	return not truthy(mode_of(g).get("noMission")) and g.missionDone < REGIONS.size()


static func _ensure_mission(g: Dictionary):
	if not mission_open(g):
		return null
	if g.mission == null or g.mission.k != g.missionDone:
		var gd := region_guard(g.missionDone)
		var mhp := jround(gd.hp * float(mode_of(g).get("hpMul", 1)) * float(diff_of(g).boss))
		g.mission = {"k": g.missionDone, "hp": mhp, "max": mhp, "armor": jround(gd.armor * float(diff_of(g).armor)), "mr": jround(gd.mr * float(diff_of(g).mr)), "boss": true,
			"shred": 0.0, "shredUntil": 0.0, "mshred": 0.0, "mshredUntil": 0.0, "amp": 0.0, "ampUntil": 0.0, "x": 0.0, "y": 0.0}
	return g.mission


static func mission_target(g: Dictionary):
	return _ensure_mission(g)


static func set_peer_mission(g: Dictionary, arr) -> void:
	if typeof(arr) == TYPE_ARRAY:
		g.peerDmg = arr.map(func(v): return maxf(0.0, float(v) if (typeof(v) == TYPE_INT or typeof(v) == TYPE_FLOAT) else 0.0))


static func _peer_dmg(g: Dictionary, k: int) -> float:
	var p = g.get("peerDmg")
	if p == null or k >= p.size() or p[k] == null:
		return 0.0
	return float(p[k])


static func mission_share(g: Dictionary) -> Dictionary:
	var k: int = g.missionDone
	return {"mine": float(g.missionDmg.get(k, 0.0)), "peer": _peer_dmg(g, k)}


static func send_away(g: Dictionary, uids: Array) -> int:
	if _ensure_mission(g) == null:
		return 0
	var n := 0
	for uid in uids:
		var u = unit_by_id(g, uid)
		if u != null and not u.away and not u.get("stored", false) and UNIT[u.id].role != SPECIAL_ROLE:
			u.away = true
			u.cd = 0.3
			n += 1
	return n


static func recall(g: Dictionary, uids = null) -> int:
	var n := 0
	for u in g.units:
		if u.away and (uids == null or uids.has(u.uid)):
			u.away = false
			var a := rand(g) * PI * 2.0
			var r := 10.0 + rand(g) * 40.0
			var p := clamp_field(SPAWN.x + cos(a) * r, SPAWN.y + sin(a) * r)
			u.x = p.x
			u.tx = p.x
			u.y = p.y
			u.ty = p.y
			n += 1
	return n


# ── 싸움 ──
# 방깍·마방깍·이감: 갈매미마다 따로 걸려 모두 더한다. 키는 갈매미 번호(정수) 또는 장판 'z'+번호(글자)
static func _eff_sum(e: Dictionary, key: String, now: float) -> float:
	var m = e.get(key)
	if m == null:
		return 0.0
	var s := 0.0
	for id in js_keys(m):
		if now >= m[id].until:
			m.erase(id)
		else:
			s += m[id].v
	return s


static func shred_now(e: Dictionary, now: float) -> Dictionary:
	return {"shred": _eff_sum(e, "shreds", now), "mshred": _eff_sum(e, "mshreds", now)}


static func _hurt(g: Dictionary, e: Dictionary, raw: float, k: Dictionary, uid: int) -> float:
	if e.hp <= 0:
		return 0.0
	e.hitT = g.t
	var now: float = g.t
	var m: float
	if k.type == "phys":
		m = def_mul(e.armor - _eff_sum(e, "shreds", now)) * upg_mul(g, k.tier)
	else:
		var ms := 0.0 if truthy(RULES.get("simNoMshred")) else _eff_sum(e, "mshreds", now)
		m = def_mul(e.mr - ms) * (1.0 + (e.amp if now < e.ampUntil else 0.0)) * upg_mul(g, k.tier)
	if truthy(e.get("boss")):
		if truthy(k.get("holy")):
			m *= k.holy
		m *= float(k.get("bossMul", 1.0))
	if truthy(e.get("rift")) and k.tier < TOP_TIER:
		m *= 1.0 - e.rift
	var d := minf(maxf(0.0, e.hp), raw * m)
	e.hp -= raw * m
	e.by = uid
	if uid != 0:
		g.dmg[uid] = float(g.dmg.get(uid, 0.0)) + d
		g.dmgWave[uid] = float(g.dmgWave.get(uid, 0.0)) + d
	return d


static func hurt_for_test(g: Dictionary, e: Dictionary, raw: float, k: Dictionary) -> float:
	return _hurt(g, e, raw, k, 0)


static func _debuff(g: Dictionary, e: Dictionary, k: Dictionary, uid: int = 0, aura: bool = false) -> void:
	var now: float = g.t
	var spd := truthy(e.get("speed"))
	if truthy(k.get("shred")):
		if not e.has("shreds"): e.shreds = {}
		e.shreds[uid] = {"v": k.shred, "until": now + 3}
	if truthy(k.get("mshred")) and not aura:
		if not e.has("mshreds"): e.mshreds = {}
		e.mshreds[uid] = {"v": k.mshred, "until": now + 3}
	if truthy(k.get("amp")):
		e.amp = maxf(e.amp, k.amp) if now < e.ampUntil else k.amp
		e.ampUntil = now + 3
	if truthy(k.get("slow")) and spd and not aura:
		if not e.has("slows"): e.slows = {}
		e.slows[uid] = {"v": k.slow, "until": now + 1.5}
		e.slowUntil = now + 1.5
	var st := float(k.get("stunTime", 1.0))
	if truthy(k.get("stun")) and spd and rand(g) < k.stun:
		e.stunUntil = maxf(e.stunUntil, now + st * (float(D.BOSS_STUN) if truthy(e.get("boss")) else 1.0))
	if truthy(k.get("astun")) and spd and rand(g) < k.astun:
		for o in g.enemies:
			if o.hp > 0 and hypot(o.x - e.x, o.y - e.y) <= 70:
				o.stunUntil = maxf(o.stunUntil, now + st * (float(D.BOSS_STUN) if truthy(o.get("boss")) else 1.0))
	if truthy(k.get("poison")):
		var pu := num(e, "poisonUntil")
		e.poison = maxf(num(e, "poison") if now < pu else 0.0, k.dmg * k.poison)
		e.poisonK = k
		e.poisonUntil = now + 2


static func debuff_for_test(g: Dictionary, e: Dictionary, k: Dictionary, uid: int) -> void:
	_debuff(g, e, k, uid)


static func _apply_auras(g: Dictionary) -> void:
	var now: float = g.t
	for u in g.units:
		if u.away or u.get("stored", false):
			continue
		var k := kit(u.id)
		var has_slow := truthy(k.get("slow"))
		var has_ms := truthy(k.get("mshred"))
		if not has_slow and not has_ms:
			continue
		var r2: float = k.range * k.range
		for e in g.enemies:
			if e.hp <= 0:
				continue
			var dx: float = e.x - u.x
			var dy: float = e.y - u.y
			if dx * dx + dy * dy > r2:
				continue
			if has_ms:
				if not e.has("mshreds"): e.mshreds = {}
				e.mshreds[u.uid] = {"v": k.mshred, "until": now + AURA_HOLD}
			if has_slow and truthy(e.get("speed")):
				if not e.has("slows"): e.slows = {}
				e.slows[u.uid] = {"v": k.slow, "until": now + AURA_HOLD}
				e.slowUntil = now + AURA_HOLD


static func buffs_of(g: Dictionary) -> Dictionary:
	var f := field_units(g)
	var src := f.filter(func(u):
		var k := kit(u.id)
		return truthy(k.get("haste")) or truthy(k.get("might")) or truthy(k.get("weaken")))
	var out := {}
	if src.is_empty():
		return out
	for u in f:
		var mi := 0.0
		var ha := 0.0
		var wk := 0.0
		for b in src:
			if is_same(b, u):
				continue
			var k := kit(b.id)
			var d := hypot(b.x - u.x, b.y - u.y)
			if truthy(k.get("might")) and d <= D.BUFF_R: mi += k.might
			if truthy(k.get("haste")) and d <= D.BUFF_R: ha += k.haste
			if truthy(k.get("weaken")) and d <= D.WEAKEN_R: wk += k.weaken
		if mi != 0.0 or ha != 0.0 or wk != 0.0:
			out[u.uid] = [1.0 + minf(D.BUFF_MAX, mi) - minf(D.WEAKEN_MAX, wk), 1.0 + minf(D.BUFF_MAX, ha)]
	return out


static func _near(g: Dictionary, x: float, y: float, r: float) -> Array:
	return g.enemies.filter(func(e): return e.hp > 0 and hypot(e.x - x, e.y - y) <= r)


static func _signature(g: Dictionary, u: Dictionary, k: Dictionary, best: Dictionary, s: Dictionary) -> void:
	var fx := {"kind": "sig", "sk": s.kind, "uid": u.uid, "name": s.name, "x0": u.x, "y0": u.y, "x1": best.x, "y1": best.y, "elem": k.elem, "type": k.type, "t": g.t}
	match s.kind:
		"smash":
			_hurt(g, best, k.dmg * s.mul, k, u.uid)
		"execute":
			if not truthy(best.get("boss")) and best.hp <= best.max * 0.3:
				best.by = u.uid
				best.hp = 0.0
			else:
				_hurt(g, best, k.dmg * s.mul, k, u.uid)
		"pierce":
			var dx: float = best.x - u.x
			var dy: float = best.y - u.y
			var L := hypot(dx, dy)
			if L == 0.0:
				L = 1.0
			var uxv := dx / L
			var uyv := dy / L
			fx.x1 = u.x + uxv * s.len
			fx.y1 = u.y + uyv * s.len
			for e in g.enemies:
				if e.hp <= 0:
					continue
				var px: float = e.x - u.x
				var py: float = e.y - u.y
				var along := px * uxv + py * uyv
				if along < 0 or along > s.len:
					continue
				if absf(px * uyv - py * uxv) <= 26:
					_hurt(g, e, k.dmg * s.mul, k, u.uid)
		"bounce":
			var pts := [[best.x, best.y]]
			var done := {best.id: true}
			var cur: Dictionary = best
			_hurt(g, best, k.dmg * s.mul, k, u.uid)
			for i in int(s.n):
				var c := cur
				var cand := _near(g, c.x, c.y, 130).filter(func(e): return not done.has(e.id))
				cand = stable_sort(cand, func(e): return hypot(e.x - c.x, e.y - c.y))
				if cand.is_empty():
					break
				var nx: Dictionary = cand[0]
				done[nx.id] = true
				_hurt(g, nx, k.dmg * s.mul, k, u.uid)
				pts.append([nx.x, nx.y])
				cur = nx
			fx.pts = pts
		"nova":
			for e in _near(g, best.x, best.y, s.r):
				_hurt(g, e, k.dmg * s.mul, k, u.uid)
			fx.r = s.r
		"storm":
			var pool := _near(g, u.x, u.y, k.range + 60)
			var pts := []
			var i := 0
			while i < int(s.n) and pool.size() > 0:
				var e: Dictionary = pool.pop_at(int(floorf(rand(g) * pool.size())))
				_hurt(g, e, k.dmg * s.mul, k, u.uid)
				pts.append([e.x, e.y])
				i += 1
			fx.pts = pts
		"bind":
			for e in _near(g, best.x, best.y, s.r):
				_hurt(g, e, k.dmg * s.mul, k, u.uid)
				if truthy(e.get("speed")):
					e.stunUntil = maxf(e.stunUntil, g.t + s.sec * (float(D.BOSS_STUN) if truthy(e.get("boss")) else 1.0))
			fx.r = s.r
		"field":
			g.zones.append({"x": best.x, "y": best.y, "r": s.r, "id": u.id, "uid": u.uid, "mul": s.mul, "slow": float(s.get("slow", 0.0)), "next": g.t, "until": g.t + s.sec, "elem": k.elem})
			fx.r = s.r
	g.fx.append(fx)


static func _tick_zones(g: Dictionary) -> void:
	if g.zones.is_empty():
		return
	for z in g.zones:
		if g.t < z.next:
			continue
		z.next += 0.5
		var k := kit(z.id)
		for e in g.enemies:
			if e.hp <= 0 or hypot(e.x - z.x, e.y - z.y) > z.r:
				continue
			_hurt(g, e, k.dmg * z.mul, k, z.uid)
			if truthy(z.slow) and truthy(e.get("speed")):
				if not e.has("slows"): e.slows = {}
				e.slows["z%d" % z.uid] = {"v": z.slow, "until": g.t + 0.6}
				e.slowUntil = maxf(num(e, "slowUntil"), g.t + 0.6)
	g.zones = g.zones.filter(func(z): return g.t < z.until)


static func _attack(g: Dictionary, u: Dictionary, k: Dictionary) -> bool:
	var best = null
	var best_d := INF
	var rr: float = k.range * k.range
	for e in g.enemies:
		if e.hp <= 0:
			continue
		var dx: float = e.x - u.x
		var dy: float = e.y - u.y
		var d2 := dx * dx + dy * dy
		if d2 > rr or d2 >= best_d:
			continue
		best = e
		best_d = d2
	if best == null:
		return false
	var hits := [best]
	var has_area := k.has("area")
	if truthy(k.get("area")) or truthy(k.get("splash")):
		var r: float = k.area if has_area else float(k.get("splashR", 50.0))
		for e in g.enemies:
			if not is_same(e, best) and e.hp > 0 and hypot(e.x - best.x, e.y - best.y) <= r:
				hits.append(e)
		g.fx.append({"kind": "boom", "x": best.x, "y": best.y, "r": r, "area": has_area, "type": k.type, "elem": k.elem, "t": g.t})
	var d0 := 0.0
	for i in hits.size():
		var e: Dictionary = hits[i]
		var raw: float = k.dmg if (i == 0 or truthy(k.get("area"))) else k.dmg * k.splash
		var d := _hurt(g, e, raw, k, u.uid)
		if i == 0:
			d0 = d
		_debuff(g, e, k, u.uid, true)
	if truthy(best.get("boss")) and k.tier >= 4 and d0 > 0 and g.fx.filter(func(f): return f.kind == "num").size() < 4:
		g.numSeq = (int(g.get("numSeq", 0)) + 1) % 1000
		g.fx.append({"kind": "num", "x": best.x, "y": best.y, "v": d0, "tier": k.tier, "n": g.numSeq, "t": g.t})
	if truthy(k.get("chain")):
		var near := []
		for e in g.enemies:
			if not is_same(e, best) and e.hp > 0:
				var d := hypot(e.x - best.x, e.y - best.y)
				if d <= 90:
					near.append([e, d])
		near = stable_sort(near, func(p): return p[1]).slice(0, int(k.chain))
		for p in near:
			_hurt(g, p[0], k.dmg * 0.5, k, u.uid)
			g.fx.append({"kind": "zap", "x0": best.x, "y0": best.y, "x1": p[0].x, "y1": p[0].y, "t": g.t})
	var sg = D.SIGNATURE.get(u.id)
	if sg != null:
		u.sigN = int(u.get("sigN", 0)) + 1
		if u.sigN >= int(sg.every):
			u.sigN = 0
			_signature(g, u, k, best, sg)
	g.fx.append({"kind": "shot", "x0": u.x, "y0": u.y, "x1": best.x, "y1": best.y, "type": k.type, "job": k.job, "elem": k.elem, "uid": u.uid, "tier": k.tier,
		"near": k.range <= 150 or has_area, "boss": truthy(best.get("boss")), "t": g.t})
	return true


static func _start_wave(g: Dictionary) -> void:
	if g.wave >= int(RULES.waves):
		return
	var left: int = int(RULES.storyDeadline) - g.wave
	if story_left(g) and (left == 10 or left == 5):
		news(g, "스토리 %d장 마감까지 %d웨이브! (%s웨이브까지 못 깨면 패배)" % [REGIONS.size(), left, fmt(RULES.storyDeadline)])
	if g.wave > 0:
		add_wisps(g, {"COMMON": RULES.wispPerWave})
		if RULES.get("wispPerWaveUp") != null:
			add_wisps(g, RULES.wispPerWaveUp)
	g.wave += 1
	g.waveT = 0.0
	g.dmgWave = {}
	if g.wave == 40 and truthy(diff_of(g).get("legend40")):
		add_wisps(g, {"LEGEND": 1})
		news(g, "40웨이브! %s 보상: 전설 위습 1개" % diff_of(g).name)
	if g.wave == int(rule("riftFrom", 999)) + 1:
		news(g, "균열 저항 시작! 이제 적이 전설·히든 이하 공격을 웨이브마다 %s%%씩 덜 받아요(최대 %s%%). 초불영제 공격은 그대로" % [fmt(jround(float(rule("riftPer", 0)) * 100)), fmt(jround(float(rule("riftMax", 0)) * 100))])
	var wi := wave_info(g.wave)
	g.spawnLeft = 1 if wi.boss else int(RULES.perWave)
	g.spawnT = 0.0


static func _spawn(g: Dictionary) -> void:
	var wi := wave_info(g.wave)
	var df := diff_of(g)
	var hp: float = wi.hp * float(mode_of(g).get("hpMul", 1)) * (float(df.boss) if wi.boss else float(df.line))
	var rift := rift_at(g.wave) if (not wi.boss and g.wave > int(rule("riftFrom", 999))) else 0.0
	g.enemies.append({"id": g.nextId, "hp": hp, "max": hp, "s": 0.0, "speed": wi.speed, "boss": wi.boss, "look": wi.look, "gold": wi.gold,
		"armor": jround(wi.armor * float(df.armor)), "mr": jround(wi.mr * float(df.mr)), "rift": rift,
		"shred": 0.0, "shredUntil": 0.0, "mshred": 0.0, "mshredUntil": 0.0, "amp": 0.0, "ampUntil": 0.0, "slow": 0.0, "slowUntil": 0.0, "stunUntil": 0.0, "poison": 0.0, "poisonUntil": 0.0,
		"x": LOOP.x0, "y": LOOP.y0, "by": 0})
	g.nextId += 1


static func _roll_wisp(g: Dictionary, skill: Dictionary):
	if rand(g) >= float(skill.p):
		return null
	var r := rand(g)
	var last = null
	for grade in skill.table:
		last = grade
		r -= float(skill.table[grade])
		if r < 0:
			return grade
	return last


# 한 걸음(dt초)
static func step(g: Dictionary, dt: float) -> void:
	if g.over != null:
		return
	g.t += dt
	if g.wave == 0:
		_start_wave(g)
	var wi := wave_info(g.wave)
	if g.spawnLeft > 0:
		g.spawnT -= dt
		if g.spawnT <= 0:
			_spawn(g)
			g.spawnLeft -= 1
			g.spawnT = float(RULES.spawnGap)
	_apply_auras(g)
	for e in g.enemies:
		if e.hp <= 0:
			continue
		e.slow = 0.0
		if e.has("slows"):
			var sls: Dictionary = e.slows
			for id in js_keys(sls):
				var sl: Dictionary = sls[id]
				if g.t >= sl.until:
					sls.erase(id)
				else:
					e.slow += sl.v
		e.slow = minf(D.SLOW_MAX, e.slow)
		var v: float = 0.0 if g.t < e.stunUntil else e.speed * slow_mul(e.slow)
		e.s += v * dt
		var p := path_pos(e.s)
		e.x = p.x
		e.y = p.y
		if g.t < e.poisonUntil:
			_hurt(g, e, e.poison * dt, e.poisonK, e.by)
	var by_uid := {}
	for u in g.units:
		by_uid[u.uid] = u
	if g.peerDmg != null:
		_ensure_mission(g)
	var ms = g.mission if (g.mission != null and mission_open(g) and g.mission.k == g.missionDone) else null
	var buffs := buffs_of(g)
	for u in g.units:
		if u.get("stored", false):
			continue
		if not u.away and (u.x != u.tx or u.y != u.ty):
			var dx: float = u.tx - u.x
			var dy: float = u.ty - u.y
			var d := hypot(dx, dy)
			var m: float = float(D.WALK) * dt
			if d <= m:
				u.x = u.tx
				u.y = u.ty
			else:
				u.x += dx / d * m
				u.y += dy / d * m
		u.cd -= dt
		if u.cd > 0:
			continue
		if UNIT[u.id].role == SPECIAL_ROLE:
			u.cd = 5.0
			continue
		var k := kit(u.id)
		if u.away:
			if ms == null:
				u.cd = 0.2
				continue
			var before: float = ms.hp
			var ek := k
			if truthy(k.get("exDmg")) and float(k.get("bossMul", 1.0)) < 1:
				ek = k.duplicate()
				ek.bossMul = 1.0
			var base: float = k.exDmg if truthy(k.get("exDmg")) else k.dmg
			var explore: float = k.explore if truthy(k.get("explore")) else 1.0
			_hurt(g, ms, base * (1.2 if truthy(k.get("area")) else 1.0) * explore, ek, u.uid)
			_debuff(g, ms, k, u.uid)
			g.missionDmg[ms.k] = float(g.missionDmg.get(ms.k, 0.0)) + maxf(0.0, before - ms.hp)
			u.cd = maxf(-0.05, u.cd) + gap_of(g, k)
			continue
		var bf = buffs.get(u.uid)
		var kk := k
		if bf != null and bf[0] != 1.0:
			kk = k.duplicate()
			kk.dmg = k.dmg * bf[0]
		if _attack(g, u, kk):
			u.cd = maxf(-0.05, u.cd) + gap_of(g, k) / (bf[1] if bf != null else 1.0)
		else:
			u.cd = 0.1
	_tick_zones(g)
	if ms != null:
		ms.hp = ms.max - float(g.missionDmg.get(ms.k, 0.0)) - _peer_dmg(g, ms.k)
	# 처치
	var boss_down := false
	var alive := []
	for e in g.enemies:
		if e.hp > 0:
			alive.append(e)
			continue
		g.gold += e.gold
		g.kills += 1
		if e.boss:
			boss_down = true
		if e.by != 0:
			g.fx.append({"kind": "kill", "x": e.x, "y": e.y, "uid": e.by, "t": g.t})
		var killer = by_uid.get(e.by)
		var sk = D.WISP_SKILL.get(killer.id) if killer != null else null
		if sk != null:
			var w = _roll_wisp(g, sk)
			if w != null:
				add_wisps(g, {w: 1})
				news(g, "%s이(가) %s 위습을 찾았어요!" % [UNIT[killer.id].name, D.GRADE_NAME[w]])
				g.fx.append({"kind": "wisp", "x": e.x, "y": e.y, "grade": w, "t": g.t})
	g.enemies = alive
	# 외부탐사 대장 쓰러뜨림
	if ms != null and ms.hp <= 0:
		var r: Dictionary = REGIONS[ms.k]
		add_wisps(g, r.reward)
		if truthy(r.get("gold")):
			g.gold += float(r.gold)
		if truthy(r.get("ticket")):
			g.tickets += float(r.ticket)
		if r.has("pick"):
			for key in r.pick:
				g.picks[key] = int(g.picks.get(key, 0)) + int(r.pick[key])
		if r.has("choice"):
			g.choice = {"k": ms.k}
		elif r.has("unit"):
			var nu := _add_unit(g, r.unit, SPAWN.x, SPAWN.y)
			g.fx.append({"kind": "combine", "x": nu.x, "y": nu.y, "t": g.t})
		var msg := "%s외부탐사 「%s」 성공! %s" % ["친구와 함께 " if g.peerDmg != null else "", r.name, wisp_text(r.reward)]
		if r.has("pick"): msg += " · " + pick_text(r.pick)
		if truthy(r.get("ticket")): msg += " · 모집권 %s" % fmt(r.ticket)
		if truthy(r.get("gold")): msg += " · 새우 %s" % fmt(r.gold)
		if r.has("choice"): msg += " · 보상 하나를 골라요"
		elif r.has("unit"): msg += " · " + UNIT[r.unit].name
		if ms.k + 1 < REGIONS.size(): msg += " · %d장이 열렸어요" % (ms.k + 2)
		news(g, msg)
		if ms.k + 1 >= REGIONS.size():
			recall(g)
		g.missionDone += 1
		g.mission = null
	g.fx = g.fx.filter(func(f): return g.t - f.t < 0.8)
	# 경고: 70마리를 넘은 동안 1초마다 1회(누적)
	if g.enemies.size() > int(RULES.limit):
		g.warnAcc += dt
		while g.warnAcc >= 1:
			g.warnAcc -= 1
			g.warn += 1
		if g.warn > int(RULES.warnMax):
			g.over = "lose"
			g.reason = "경고가 %s회를 넘었어요(길 위의 적 %s마리 초과)." % [fmt(RULES.warnMax), fmt(RULES.limit)]
			return
	else:
		g.warnAcc = 0.0
	# 보스·웨이브 진행
	if wi.boss and boss_down:
		if g.wave >= int(RULES.waves):
			if g.get("finaleAt") == null:
				g.finaleAt = g.t
			g.bossDone = g.wave
			news(g, "%s웨이브 보스 처치! 남은 적을 %s초 안에 모두 잡으면 완벽 클리어" % [fmt(RULES.waves), fmt(RULES.finaleLen)])
		else:
			var rw = RULES.bossReward.get(str(g.wave))
			if rw != null:
				add_wisps(g, rw)
			var rp := {}
			for key in rule("bossPickEach", {}):
				rp[key] = RULES.bossPickEach[key]
			var bp = RULES.bossPick.get(str(g.wave), {})
			for key in bp:
				rp[key] = int(rp.get(key, 0)) + int(bp[key])
			for key in rp:
				g.picks[key] = int(g.picks.get(key, 0)) + int(rp[key])
			var tk := 0.0
			if g.wave >= int(rule("bossTicketLateFrom", 999)):
				tk = float(rule("bossTicketLate", RULES.bossTicket))
			elif truthy(RULES.bossTicket) and g.wave % int(rule("bossTicketEvery", RULES.bossEvery)) == 0:
				tk = float(RULES.bossTicket)
			if tk != 0.0:
				g.tickets += tk
			var rr := 1 if (truthy(RULES.get("bossRerollEvery")) and g.wave % int(RULES.bossRerollEvery) == 0) else 0
			if rr != 0:
				g.rerolls += rr
			var parts := []
			if rw != null: parts.append(wisp_text(rw))
			if rp.size() > 0: parts.append(pick_text(rp))
			if tk != 0.0: parts.append("모집권 %s" % fmt(tk))
			if rr != 0: parts.append("리롤권 %d" % rr)
			news(g, "보스 처치! " + " · ".join(parts))
			g.bossDone = g.wave
	# 마지막 보스 뒤: 남은 적을 다 잡거나 finaleLen초가 지나면 끝
	if g.get("finaleAt") != null:
		var left: int = g.enemies.size() + int(g.get("spawnLeft", 0))
		if left == 0 or g.t - g.finaleAt >= float(RULES.finaleLen):
			g.leftover = left
			g.over = "win"
			g.reason = ("%s웨이브 보스까지 막았어요! 남은 적 %d마리" % [fmt(RULES.waves), left]) if left else ("완벽 클리어! %s웨이브 보스와 남은 적까지 모두 잡았어요" % fmt(RULES.waves))
		return
	g.waveT += dt
	if g.waveT >= float(RULES.waveLen) and wi.boss and g.get("bossDone") != g.wave:
		g.over = "lose"
		g.reason = "%d웨이브 보스를 라운드 안에 잡지 못했어요." % g.wave
		return
	if g.waveT >= wave_len_of(g.wave):
		if g.wave >= int(RULES.waves):
			return
		if g.wave == int(RULES.storyDeadline) and story_left(g):
			g.over = "lose"
			g.reason = "스토리 %d장을 %s웨이브까지 끝내지 못했어요." % [REGIONS.size(), fmt(RULES.storyDeadline)]
			return
		_start_wave(g)


# ── 이어하기: 웹 판 saveGame/loadGame과 같은 모양({v, g}, g.rand = 난수 상태) ──
static func save_game(g: Dictionary) -> String:
	var c := g.duplicate(true)
	c.rand = c.rs
	c.erase("rs")
	c.fx = []
	var md := []
	for k in c.missionDmg:
		while md.size() <= int(k):
			md.append(null)
		md[int(k)] = c.missionDmg[k]
	c.missionDmg = md
	return JSON.stringify({"v": int(D.SAVE_VER), "g": c})


static func load_game(text: String):
	var o = JSON.parse_string(text)
	if typeof(o) != TYPE_DICTIONARY or not (o.get("v") == float(D.SAVE_VER) or o.get("v") == 1.0) or typeof(o.get("g")) != TYPE_DICTIONARY or typeof(o.g.get("units")) != TYPE_ARRAY:
		return null
	var g: Dictionary = o.g
	if o.v == 1.0 and float(g.get("missionDone", 0)) >= float(D.OLD_STORY_LEN):
		g.missionDone = REGIONS.size()
		g.mission = null
	g.rs = int(g.rand) & 0xFFFFFFFF
	g.erase("rand")
	# JSON은 숫자를 모두 실수로 읽으므로 정수여야 하는 것을 되돌림(사전 키 비교가 정수/실수를 구분함)
	for key in ["wave", "spawnLeft", "warn", "nextId", "nextUid", "missionDone", "kills", "rerolls", "seed", "bossDone"]:
		if g.get(key) != null:
			g[key] = int(g[key])
	g.upg = g.upg.map(func(v): return int(v))
	for wk in ["wisps", "picks"]:
		for key in g[wk]:
			g[wk][key] = int(g[wk][key])
	for u in g.units:
		u.uid = int(u.uid)
		if u.has("sigN"):
			u.sigN = int(u.sigN)
	for e in g.enemies:
		e.id = int(e.id)
		e.by = int(e.get("by", 0))
		for mk in ["shreds", "mshreds", "slows"]:
			if e.has(mk):
				var m2 := {}
				for key in e[mk]:
					m2[int(key) if str(key).is_valid_int() else key] = e[mk][key]
				e[mk] = m2
	var md := {}
	var arr = g.get("missionDmg", [])
	if typeof(arr) == TYPE_ARRAY:
		for i in arr.size():
			if arr[i] != null:
				md[i] = float(arr[i])
	g.missionDmg = md
	if g.get("mission") != null:
		g.mission.k = int(g.mission.k)
	for z in g.get("zones", []):
		z.uid = int(z.uid)
	g.fx = []
	for key in ["dmg", "dmgWave", "autoGrades"]:
		if g.get(key) == null:
			g[key] = {}
	if g.get("tickets") == null: g.tickets = 0.0
	if g.get("diff") == null: g.diff = "master"
	if g.get("zones") == null: g.zones = []
	if g.get("rerolls") == null: g.rerolls = 0
	if g.get("news") == null: g.news = []
	return g

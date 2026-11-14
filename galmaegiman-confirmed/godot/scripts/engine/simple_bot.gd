# 아주 단순한 자동 봇(웹 판 godot/tools/ref_bot.mjs의 bot()과 한 줄씩 같아야 함 — 바꾸면 둘 다 바꿀 것).
# 비교 시험(scripts/test/compare_test.gd)과 미리보기 화면(scripts/preview/main.gd)이 같이 쓴다.
# 위습 모두 소환 → 조합법 순서대로 되는 것 조합 → 모집 → 강화 → 선택 위습·리롤·판매·창고·옮기기·모으기·고정·불러오기 → 외부탐사 2마리까지
extends RefCounted

const E = preload("res://scripts/engine/engine.gd")


static func act(g: Dictionary, tops: bool = false) -> void:
	if g.get("choice") != null:
		E.choose_reward(g, 0)
	for gr in E.D.WISP_GRADES:
		while int(g.wisps.get(gr, 0)) > 0:
			E.summon(g, gr)
	for n in 60:
		var made := false
		for r in E.RECIPES:
			if E.recipe_units(g, r) != null:
				E.combine(g, r)
				made = true
				break
		if not made:
			break
	while g.tickets >= float(E.RULES.recruitCost):
		E.recruit(g)
	for gi in 8:
		if not E.upg_maxed(g, gi) and g.gold >= E.upgrade_cost(g, gi):
			E.upgrade(g, gi)
	for gr in E.D.PICK_GRADES:
		while int(g.picks.get(gr, 0)) > 0:
			var lst := E.units_of_grade(gr)
			E.summon_pick(g, lst[g.wave % lst.size()])
	if g.rerolls > 0:
		for x in g.units:
			if E.can_reroll(g, x):
				E.reroll_unit(g, x.uid)
				break
	var f := E.field_units(g)
	if f.size() > 40:
		for x in f:
			if E.tier_of(x.id) <= 1:
				E.sell(g, x.uid)
				break
	var step := int(E.jround(g.t * 2))
	if step % 40 == 0:
		E.store_group(g, "COMMON")
	if step % 40 == 20:
		E.unstore(g, E.stored_units(g).slice(0, 3).map(func(u): return u.uid))
	if step % 30 == 5 and f.size() > 0:
		var u: Dictionary = f[step % f.size()]
		E.move_unit(g, u.uid, 100 + (step % 300), 200 + (step % 350), step % 2 == 0)
	if step % 50 == 7 and f.size() > 0:
		E.gather(g, f[0].uid)
	if step % 70 == 9 and f.size() > 1:
		E.set_lock(g, [f[1].uid], step % 140 == 9)
	if step % 90 == 11 and E.away_units(g).size() > 0:
		E.recall(g, [E.away_units(g)[0].uid])
	if tops and g.wave >= 3 and step % 20 == 0 and E.top_count(g) < 12:
		var ids: Array = E.D.SIGNATURE.keys()
		var id: String = ids[(step / 20) % ids.size()]
		var gr: String = E.UNIT[id].grade
		g.picks[gr] = int(g.picks.get(gr, 0)) + 1
		E.summon_pick(g, id)
	if E.mission_open(g) and E.away_units(g).size() < 2:
		var best = null
		for u in E.field_units(g):
			var t := E.tier_of(u.id)
			if t >= 3 and E.UNIT[u.id].role != E.SPECIAL_ROLE and (best == null or t > E.tier_of(best.id)):
				best = u
		if best != null:
			E.send_away(g, [best.uid])

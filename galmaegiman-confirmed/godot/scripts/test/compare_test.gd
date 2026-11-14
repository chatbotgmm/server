# 웹 판과 같은 결과가 나오는지 비교: tools/ref_bot.mjs가 쓴 기준 기록과 같은 봇·같은 시드로 돌린 고도 판 상태를 한 줄씩 비교한다.
# 사용(godot 폴더에서): godot --headless --script res://scripts/test/compare_test.gd -- <시드> <난이도> <최대 초> <기준 기록 파일>
extends SceneTree

const E = preload("res://scripts/engine/engine.gd")
const Bot = preload("res://scripts/engine/simple_bot.gd")


func bot(g: Dictionary) -> void:
	Bot.act(g, OS.get_environment("BOT_TOPS") == "1")


func snap(g: Dictionary) -> Dictionary:
	var ehp := 0.0
	for e in g.enemies:
		ehp += e.hp
	var ids := []
	for u in g.units:
		ids.append(u.id)
	var upg := []
	for v in g.upg:
		upg.append(str(v))
	var wl := []
	for key in g.wisps:
		wl.append(str(g.wisps[key]))
	var pl := []
	for key in g.picks:
		pl.append(str(g.picks[key]))
	return {"t": E.jround(g.t * 100) / 100, "wave": g.wave, "gold": bits(g.gold), "kills": g.kills, "warn": g.warn, "en": g.enemies.size(), "ehp": bits(ehp), "units": ",".join(ids),
		"md": g.missionDone, "mhp": bits(g.mission.hp if g.mission != null else -1.0), "rs": g.rs, "tickets": g.tickets, "upg": ",".join(upg), "wisps": ",".join(wl), "picks": ",".join(pl), "over": g.over if g.over != null else ""}


func bits(x: float) -> String:   # 실수 64비트를 16진수 글자로(웹 판 Buffer.writeDoubleLE와 같은 순서)
	var pb := PackedByteArray()
	pb.resize(8)
	pb.encode_double(0, x)
	return pb.hex_encode()


func same(a, b) -> bool:
	if typeof(a) == TYPE_STRING or typeof(b) == TYPE_STRING:
		return str(a) == str(b)
	var x := float(a)
	var y := float(b)
	return x == y or absf(x - y) <= 1e-9 * maxf(1.0, maxf(absf(x), absf(y)))


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	var seed := int(args[0])
	var diff := args[1]
	var max_t := float(args[2])
	var ref := FileAccess.get_file_as_string(args[3]).strip_edges().split("\n")
	if args.size() > 4:   # 시험용 난이도 바꾸기(ref_bot.mjs의 6번째 인자와 같게)
		var dx = JSON.parse_string(args[4])
		for key in dx:
			E.D.DIFFS[diff][key] = dx[key]
	var g := E.new_game(seed, "normal", diff)
	var lines := []
	var i := 1
	var t0 := Time.get_ticks_msec()
	var save_at := float(OS.get_environment("SAVE_AT")) if OS.get_environment("SAVE_AT") != "" else -1.0
	var saved := 0
	while g.over == null and g.t < max_t:
		E.step(g, 0.05)
		if save_at > 0 and absf(g.t - save_at) < 0.025:
			var txt := E.save_game(g)
			g = E.load_game(txt)
			saved = txt.length()
		if i % 10 == 0:
			bot(g)
		if i % 100 == 0:
			lines.append(snap(g))
		i += 1
	lines.append(snap(g))
	var ms := Time.get_ticks_msec() - t0
	var bad := 0
	var exact := 0
	for n in maxi(lines.size(), ref.size()):
		if n >= lines.size() or n >= ref.size():
			print("줄 수가 달라요: 고도 %d · 웹 %d" % [lines.size(), ref.size()])
			bad += 1
			break
		var r: Dictionary = JSON.parse_string(ref[n])
		var mine: Dictionary = lines[n]
		var diffs := []
		var all_exact := true
		for key in r:
			if not same(mine.get(key), r[key]):
				diffs.append("%s 고도=%s 웹=%s" % [key, str(mine.get(key)), str(r[key])])
			elif typeof(r[key]) != TYPE_STRING and float(mine.get(key)) != float(r[key]):
				all_exact = false
				if OS.get_environment("CMP_VERBOSE") == "1":
					print("  근소 차이 %d번째 %s: 차이=%s 고도=%s (웹 글자 %s)" % [n + 1, key, str(float(mine.get(key)) - float(r[key])), String.num(float(mine.get(key)), 17), ref[n].split("\"%s\":" % key)[1].split(",")[0]])
		if all_exact and diffs.is_empty():
			exact += 1
		if not diffs.is_empty():
			print("다름 %d번째(%s초): %s" % [n + 1, str(r.t), " | ".join(diffs)])
			bad += 1
			if bad >= 5:
				break
	if saved:
		print("  (%s초에 저장→되살리기 함, 저장 글자 %d자)" % [str(save_at), saved])
	print("결과: 시드 %d %s · %d줄 비교 · 다름 %d · 완전히 같은 줄 %d · 끝 %s 웨이브 %d · 고도 %.1f초 걸림" % [seed, diff, mini(lines.size(), ref.size()), bad, exact, str(g.over), g.wave, ms / 1000.0])
	quit(1 if bad else 0)

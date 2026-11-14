# 고도 판 1단계 미리보기: 옮긴 규칙 엔진이 실제로 돌아가는지 눈으로 확인하는 화면(실제 게임 화면은 다음 단계).
# 왼쪽: 판(도형) · 오른쪽: 상태·버튼·누른 갈매미 설명. 자동 봇을 켜면 비교 시험과 같은 봇이 대신 둔다.
extends Control

const E = preload("res://scripts/engine/engine.gd")
const Bot = preload("res://scripts/engine/simple_bot.gd")
const BoardView = preload("res://scripts/preview/board_view.gd")
const FONT_PATH := "res://assets/fonts/workbench-noto-regular.otf"
const C_BG := Color("#07131c")
const C_PANEL := Color(0.063, 0.141, 0.2, 0.95)
const C_EDGE := Color("#917a4c")
const C_TEXT := Color("#f5ead0")
const C_MUTED := Color("#b0bec4")
const C_GOLD := Color("#d9bb7f")

var g: Dictionary = {}
var speed := 1
var auto := true
var acc := 0.0
var steps := 0
var board: Control
var status: Label
var info: Label
var news_label: Label
var diff_pick: OptionButton
var auto_btn: Button
var speed_btn: Button
var selected_uid := 0


func _ready() -> void:
	var th := Theme.new()
	var font = load(FONT_PATH)
	if font != null:
		th.default_font = font
	th.default_font_size = 18
	theme = th
	var bg := ColorRect.new()
	bg.color = C_BG
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var row := HBoxContainer.new()
	row.set_anchors_preset(Control.PRESET_FULL_RECT)
	row.add_theme_constant_override("separation", 12)
	add_child(row)
	board = BoardView.new()
	board.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	board.size_flags_stretch_ratio = 1.15
	board.unit_picked.connect(_on_unit_picked)
	row.add_child(board)
	var side := PanelContainer.new()
	side.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var sb := StyleBoxFlat.new()
	sb.bg_color = C_PANEL
	sb.border_color = C_EDGE
	sb.set_border_width_all(2)
	sb.set_corner_radius_all(12)
	sb.set_content_margin_all(14)
	side.add_theme_stylebox_override("panel", sb)
	row.add_child(side)
	var col := VBoxContainer.new()
	col.add_theme_constant_override("separation", 8)
	side.add_child(col)
	var title := Label.new()
	title.text = "고도 판 미리보기 · 규칙 엔진 1단계"
	title.add_theme_color_override("font_color", C_GOLD)
	title.add_theme_font_size_override("font_size", 20)
	col.add_child(title)
	var btns := HBoxContainer.new()
	btns.add_theme_constant_override("separation", 6)
	col.add_child(btns)
	diff_pick = OptionButton.new()
	for key in E.D.DIFF_ORDER:
		diff_pick.add_item(E.D.DIFFS[key].name)
		diff_pick.set_item_metadata(diff_pick.item_count - 1, key)
	diff_pick.select(E.D.DIFF_ORDER.find("intro"))
	btns.add_child(diff_pick)
	btns.add_child(_button("새 판", _new_game))
	auto_btn = _button("", _toggle_auto)
	btns.add_child(auto_btn)
	speed_btn = _button("", _toggle_speed)
	btns.add_child(speed_btn)
	var btns2 := HBoxContainer.new()
	btns2.add_theme_constant_override("separation", 6)
	col.add_child(btns2)
	btns2.add_child(_button("커먼 위습 소환", func(): E.summon(g, "COMMON")))
	btns2.add_child(_button("되는 조합 하나", _combine_one))
	btns2.add_child(_button("저장→되살리기", _save_load))
	status = _label(C_TEXT, 17)
	col.add_child(status)
	info = _label(C_MUTED, 15)
	info.text = "판의 갈매미를 누르면 설명이 나와요."
	col.add_child(info)
	news_label = _label(C_MUTED, 14)
	news_label.size_flags_vertical = Control.SIZE_EXPAND_FILL
	col.add_child(news_label)
	_new_game()


func _button(text: String, cb: Callable) -> Button:
	var b := Button.new()
	b.text = text
	b.pressed.connect(cb)
	return b


func _label(c: Color, fs: int) -> Label:
	var l := Label.new()
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.add_theme_color_override("font_color", c)
	l.add_theme_font_size_override("font_size", fs)
	return l


func _new_game() -> void:
	var key: String = diff_pick.get_item_metadata(diff_pick.selected)
	g = E.new_game(int(Time.get_unix_time_from_system()) & 0xFFFFFFFF, "normal", key)
	steps = 0
	acc = 0.0
	selected_uid = 0
	_refresh_buttons()


func _toggle_auto() -> void:
	auto = not auto
	_refresh_buttons()


func _toggle_speed() -> void:
	speed = {1: 2, 2: 4, 4: 8, 8: 1}[speed]
	_refresh_buttons()


func _refresh_buttons() -> void:
	auto_btn.text = "자동 봇 켬" if auto else "자동 봇 끔"
	speed_btn.text = "속도 ×%d" % speed


func _combine_one() -> void:
	for r in E.RECIPES:
		if E.recipe_units(g, r) != null:
			E.combine(g, r)
			return


func _save_load() -> void:
	var txt := E.save_game(g)
	var back = E.load_game(txt)
	if back != null:
		g = back
		E.news(g, "저장→되살리기 했어요(%d자)" % txt.length())


func _on_unit_picked(uid: int) -> void:
	selected_uid = uid


func _process(delta: float) -> void:
	if g.is_empty():
		return
	if g.over == null:
		acc += minf(delta, 0.1) * speed
		while acc >= 0.05:
			E.step(g, 0.05)
			steps += 1
			if auto and steps % 10 == 0:
				Bot.act(g)
			acc -= 0.05
	board.g = g
	board.selected = selected_uid
	board.queue_redraw()
	_refresh_status()


func _refresh_status() -> void:
	var w: Dictionary = g.wisps
	var lines := []
	lines.append("웨이브 %d/%s · %s · 다음까지 %d초" % [g.wave, E.fmt(E.RULES.waves), E.diff_of(g).name, int(E.next_wave_in(g))])
	lines.append("길 위의 적 %d/%s · 경고 %d/%s" % [g.enemies.size(), E.fmt(E.RULES.limit), g.warn, E.fmt(E.RULES.warnMax)])
	lines.append("새우 %s · 처치 %d · 모집권 %s · 리롤권 %d" % [E.fmt(int(g.gold)), g.kills, E.fmt(g.tickets), g.rerolls])
	lines.append("위습 커먼 %d · 언커먼 %d · 레어 %d · 에픽 %d · 전설 %d" % [w.COMMON, w.UNCOMMON, w.RARE, w.EPIC, w.LEGEND])
	lines.append("갈매미 %d마리(판 %d · 탐사 %d · 창고 %d) · 외부탐사 %d/%d장" % [g.units.size(), E.field_units(g).size(), E.away_units(g).size(), E.stored_units(g).size(), g.missionDone, E.REGIONS.size()])
	if g.over != null:
		lines.append("끝: %s" % g.reason)
	status.text = "\n".join(lines)
	var u = E.unit_by_id(g, selected_uid) if selected_uid else null
	if u != null:
		var unit: Dictionary = E.UNIT[u.id]
		var k := E.kit(u.id)
		info.text = "%s · %s · %s\n공격 %s · 사거리 %s\n%s" % [unit.name, E.D.GRADE_NAME[unit.grade], unit.role, E.fmt(int(k.dmg * E.upg_mul(g, k.tier))), E.fmt(k.range), " · ".join(E.kit_tips(k))]
	var nl := []
	for n in g.news:
		nl.push_front(n.msg)
	news_label.text = "\n".join(nl)

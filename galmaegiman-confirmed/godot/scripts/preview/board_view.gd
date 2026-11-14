# 미리보기 판: 엔진 상태(g)를 도형으로 그린다(그림 파일은 다음 단계에서). 판 논리 좌표는 웹 판과 같은 540×960 중 길 둘레만 보여 줌.
extends Control

signal unit_picked(uid: int)

const E = preload("res://scripts/engine/engine.gd")
const VIEW := Rect2(14, 100, 512, 564)   # 웹 판 FIELD
const GRADE_COLOR := {"COMMON": Color("#9aa5ad"), "UNCOMMON": Color("#62c370"), "RARE": Color("#4d9de0"), "EPIC": Color("#a77be8"), "LEGEND": Color("#f0a03c"),
	"HIDDEN": Color("#3fd0c0"), "LIMITED": Color("#ef5a5a"), "TRANSCEND": Color("#ff8fc7"), "IMMORTAL": Color("#f3d36b"), "ETERNAL": Color("#ffffff")}
const C_LANE := Color(0.84, 0.745, 0.55, 0.22)
const C_LANE_EDGE := Color(0.953, 0.847, 0.62, 0.35)
const C_TEXT := Color("#f5ead0")

var g: Dictionary = {}
var selected := 0


func _scale() -> float:
	return minf(size.x / VIEW.size.x, size.y / VIEW.size.y)


func _origin() -> Vector2:
	var s := _scale()
	return (size - VIEW.size * s) / 2.0 - VIEW.position * s


func to_screen(x: float, y: float) -> Vector2:
	return _origin() + Vector2(x, y) * _scale()


func _draw() -> void:
	draw_rect(Rect2(Vector2.ZERO, size), Color("#0b1e2a"))
	if g.is_empty():
		return
	var s := _scale()
	var L: Dictionary = E.LOOP
	var lane := Rect2(to_screen(L.x0, L.y0), Vector2(L.x1 - L.x0, L.y1 - L.y0) * s)
	draw_rect(lane, C_LANE, false, 40.0 * s)
	draw_rect(lane.grow(20.0 * s), C_LANE_EDGE, false, 1.5)
	draw_rect(lane.grow(-20.0 * s), C_LANE_EDGE, false, 1.5)
	var font := get_theme_default_font()
	for e in g.enemies:
		var p := to_screen(e.x, e.y)
		var r := (20.0 if e.boss else 11.0) * s
		var stunned: bool = g.t < e.stunUntil
		draw_circle(p, r, Color("#ff6b6b") if e.boss else (Color("#b9a7ff") if stunned else Color("#c98b6b")))
		var bw := (70.0 if e.boss else 30.0) * s
		var top := p + Vector2(-bw / 2.0, -r - 7.0 * s)
		draw_rect(Rect2(top, Vector2(bw, 4.0 * s)), Color(0, 0, 0, 0.6))
		draw_rect(Rect2(top, Vector2(bw * clampf(e.hp / e.max, 0.0, 1.0), 4.0 * s)), Color("#ff6b6b") if e.boss else Color("#6fd08a"))
	for z in g.zones:
		draw_circle(to_screen(z.x, z.y), z.r * s, Color(0.6, 0.9, 0.5, 0.15))
	for u in g.units:
		if u.away or u.get("stored", false):
			continue
		var p := to_screen(u.x, u.y)
		var unit: Dictionary = E.UNIT[u.id]
		var col: Color = GRADE_COLOR.get(unit.grade, Color.WHITE)
		var r := 15.0 * s
		draw_circle(p, r, Color(0.04, 0.12, 0.17, 0.95))
		draw_arc(p, r, 0, TAU, 24, col, 2.5 if u.uid != selected else 4.5)
		if u.get("lock", false):
			draw_arc(p, r + 4.0 * s, 0, TAU, 24, Color("#f3d89e"), 1.5)
		var ch: String = unit.name.substr(0, 1)
		var fs := int(maxf(10.0, 16.0 * s))
		draw_string(font, p + Vector2(-fs / 2.0, fs / 3.0), ch, HORIZONTAL_ALIGNMENT_CENTER, fs, fs, col)
	for f in g.fx:
		if f.kind == "sig":
			draw_line(to_screen(f.x0, f.y0), to_screen(f.x1, f.y1), Color(1, 0.85, 0.5, 0.8), 3.0)
			if f.has("r"):
				draw_arc(to_screen(f.x1, f.y1), f.r * s, 0, TAU, 32, Color(1, 0.85, 0.5, 0.7), 2.0)


func _gui_input(ev: InputEvent) -> void:
	if g.is_empty():
		return
	var pos := Vector2.INF
	if ev is InputEventMouseButton and ev.pressed and ev.button_index == MOUSE_BUTTON_LEFT:
		pos = ev.position
	elif ev is InputEventScreenTouch and ev.pressed:
		pos = ev.position
	if pos == Vector2.INF:
		return
	var best := 0
	var best_d := 26.0 * _scale()
	for u in g.units:
		if u.away or u.get("stored", false):
			continue
		var d := to_screen(u.x, u.y).distance_to(pos)
		if d < best_d:
			best_d = d
			best = u.uid
	selected = best
	unit_picked.emit(best)
	queue_redraw()

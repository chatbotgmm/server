# 화면 캡처(디자인 검토용): 미리보기 장면을 띄워 몇 초 돌린 뒤 PNG로 저장.
# 사용: xvfb-run -a godot --path . --rendering-driver opengl3 --resolution 915x412 --script res://scripts/test/shot.gd -- <출력.png> <초>
extends SceneTree

var out := ""
var wait := 3.0
var t := 0.0


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	out = args[0]
	wait = float(args[1]) if args.size() > 1 else 3.0
	var scene: PackedScene = load("res://scenes/main.tscn")
	root.add_child(scene.instantiate())


func _process(delta: float) -> bool:
	t += delta
	if t >= wait:
		var main = root.get_child(0)
		var img := root.get_texture().get_image()
		img.save_png(out)
		print("캡처: %s (%dx%d) 웨이브 %d" % [out, img.get_width(), img.get_height(), main.g.wave])
		return true
	return false

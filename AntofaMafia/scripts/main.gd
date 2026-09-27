extends Node3D

var world: AntofaWorld
var player: AMPlayer
var car: AMCar
var hud: AMHud
var mission_complete := false
var port_pos := AntofaWorld.PORT_POS

func _ready() -> void:
	world = AntofaWorld.new()
	world.name = "AntofagastaWorld"
	add_child(world)

	player = AMPlayer.new()
	player.name = "Player"
	player.global_position = AntofaWorld.START_POS
	add_child(player)

	car = AMCar.new()
	car.name = "PlayerCar"
	car.global_position = AntofaWorld.CAR_POS
	car.rotation.y = deg_to_rad(90.0)
	add_child(car)

	_spawn_npcs()

	hud = AMHud.new()
	hud.name = "HUD"
	add_child(hud)

func _spawn_npcs() -> void:
	var spawn_points := [
		Vector3(-200, 1, 120), Vector3(-280, 1, 40), Vector3(-40, 1, 200),
		Vector3(120, 1, 40), Vector3(200, 1, -120), Vector3(360, 1, 120),
		Vector3(-360, 1, -120), Vector3(-440, 1, 200), Vector3(40, 1, -280),
		Vector3(280, 1, 280), Vector3(-120, 1, -360), Vector3(440, 1, -40),
		Vector3(-360, 1, 360), Vector3(120, 1, 360), Vector3(360, 1, -360),
		Vector3(-415, 1, -160), Vector3(-385, 1, -205), Vector3(-315, 1, -115),
		Vector3(-225, 1, -190), Vector3(-155, 1, -70), Vector3(-95, 1, 70),
		Vector3(-285, 1, 215), Vector3(-430, 1, 120), Vector3(55, 1, 155),
		Vector3(185, 1, 245)
	]
	for p in spawn_points:
		var npc := AMNpc.new()
		npc.global_position = p
		add_child(npc)

func _process(_delta: float) -> void:
	if player == null or hud == null or car == null:
		return
	var controlled_pos := car.global_position if car.active else player.global_position
	hud.update_runtime(controlled_pos, port_pos, car.active, car.speed)

	if not mission_complete:
		var d := Vector2(controlled_pos.x, controlled_pos.z).distance_to(Vector2(port_pos.x, port_pos.z))
		if d < 28.0:
			mission_complete = true
			hud.set_mission_complete()

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
		if Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
			Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
		else:
			Input.mouse_mode = Input.MOUSE_MODE_CAPTURED

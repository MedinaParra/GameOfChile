class_name AntofaWorld
extends Node3D

const PORT_POS := Vector3(-510.0,1.0,245.0)
const START_POS := Vector3(-250.0,1.1,-20.0)
const CAR_POS := Vector3(-230.0,0.7,-20.0)

var asphalt: StandardMaterial3D
var sidewalk: StandardMaterial3D
var sand: StandardMaterial3D
var ocean: StandardMaterial3D
var rng := RandomNumberGenerator.new()
var streets: Array = []

func _ready() -> void:
	rng.seed = 260927
	asphalt = _mat(Color("#30343a"),0.95)
	sidewalk = _mat(Color("#a7a49c"),0.9)
	sand = _mat(Color("#b58c58"),0.97)
	ocean = _mat(Color("#0c627e"),0.3)
	_environment()
	_ground()
	_make_streets()
	_city()
	_landmarks()

func _mat(c: Color, rough := 0.85) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = rough
	return m

func _environment() -> void:
	var env := Environment.new()
	var sky := Sky.new()
	var sky_mat := ProceduralSkyMaterial.new()
	sky_mat.sky_top_color = Color("#4d79a1")
	sky_mat.sky_horizon_color = Color("#d8b088")
	sky_mat.ground_horizon_color = Color("#c59b6d")
	sky.sky_material = sky_mat
	env.background_mode = Environment.BG_SKY
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color("#dce5ee")
	env.ambient_light_energy = 0.8
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-52,-28,0)
	sun.light_energy = 1.25
	sun.shadow_enabled = true
	add_child(sun)

func _ground() -> void:
	_box(Vector3(1250,0.5,1250),Vector3(20,-0.25,0),sand,true)
	var sea := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(1200,1500)
	sea.mesh = pm
	sea.material_override = ocean
	sea.position = Vector3(-1170,0.02,0)
	add_child(sea)

func _make_streets() -> void:
	var longitudinal := [
		[Vector2(-550,-560),Vector2(-500,540),25.0],
		[Vector2(-405,-560),Vector2(-335,535),18.0],
		[Vector2(-305,-560),Vector2(-225,535),17.0],
		[Vector2(-205,-560),Vector2(-112,535),18.0],
		[Vector2(-105,-560),Vector2(5,535),16.0],
		[Vector2(170,-575),Vector2(315,545),27.0],
		[Vector2(390,-565),Vector2(555,555),23.0]
	]
	var transverse := [
		[Vector2(-530,-330),Vector2(220,-225),15.0],
		[Vector2(-525,-235),Vector2(225,-125),15.0],
		[Vector2(-520,-135),Vector2(235,-18),16.0],
		[Vector2(-515,-35),Vector2(245,95),17.0],
		[Vector2(-510,80),Vector2(260,220),15.0],
		[Vector2(-505,185),Vector2(275,335),15.0],
		[Vector2(-500,295),Vector2(290,460),15.0]
	]
	for s in longitudinal + transverse:
		_road(s[0],s[1],s[2])

func _road(a: Vector2, b: Vector2, width: float) -> void:
	streets.append({"a":a,"b":b,"w":width})
	_strip(a,b,width+8.0,sidewalk,0.035)
	_strip(a,b,width,asphalt,0.085)

func _strip(a: Vector2, b: Vector2, width: float, mat: Material, y: float) -> void:
	var mid := (a+b)*0.5
	var length := a.distance_to(b)
	var angle := atan2(b.x-a.x,b.y-a.y)
	_box(Vector3(width,0.07,length),Vector3(mid.x,y,mid.y),mat,false,angle)

func _city() -> void:
	var palette := [Color("#c8b89f"),Color("#d6c9b8"),Color("#ad9c89"),Color("#b86e58"),Color("#828b93")]
	for z in range(-510,511,52):
		for x in range(-455,351,56):
			var p := Vector2(float(x)+rng.randf_range(-7,7),float(z)+rng.randf_range(-7,7))
			if _road_distance(p) < 25.0:
				continue
			if p.distance_to(Vector2(-370,-170)) < 56:
				continue
			var h := rng.randf_range(9,20)
			if p.distance_to(Vector2(-210,0)) < 300:
				h += rng.randf_range(5,38)
			var mat := _mat(palette[rng.randi_range(0,palette.size()-1)],0.87)
			_box(Vector3(rng.randf_range(25,40),h,rng.randf_range(23,38)),Vector3(p.x,h*0.5,p.y),mat,true,deg_to_rad(-6))

func _road_distance(p: Vector2) -> float:
	var best := 99999.0
	for s in streets:
		var a: Vector2 = s["a"]
		var b: Vector2 = s["b"]
		var ab := b-a
		var t := clampf((p-a).dot(ab)/maxf(ab.length_squared(),0.001),0,1)
		best = minf(best,p.distance_to(a+ab*t)-float(s["w"])*0.5)
	return best

func _landmarks() -> void:
	var park := _mat(Color("#4e7549"),0.95)
	_box(Vector3(65,0.08,72),Vector3(-370,0.10,-170),park,false)
	for p in [Vector3(-390,0,-190),Vector3(-350,0,-190),Vector3(-390,0,-150),Vector3(-350,0,-150)]:
		_tree(p)
	var port_mat := _mat(Color("#686e73"),0.8)
	_box(Vector3(145,0.3,250),Vector3(-545,0.13,300),port_mat,true)
	for i in range(3):
		_box(Vector3(190,0.5,18),Vector3(-660,0.3,220+i*70),port_mat,true)
	var mark := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius=5
	cm.bottom_radius=5
	cm.height=0.3
	mark.mesh=cm
	var mm := _mat(Color("#ffd13d"),0.4)
	mm.emission_enabled=true
	mm.emission=Color("#ffd13d")
	mark.material_override=mm
	mark.position=PORT_POS
	add_child(mark)

func _tree(pos: Vector3) -> void:
	_box(Vector3(0.6,4,0.6),pos+Vector3(0,2,0),_mat(Color("#654b35"),1),false)
	var mi := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius=2.2
	sm.height=4.4
	mi.mesh=sm
	mi.material_override=_mat(Color("#345d38"),0.95)
	mi.position=pos+Vector3(0,5,0)
	add_child(mi)

func _box(size: Vector3, pos: Vector3, mat: Material, collide := false, rot := 0.0) -> Node3D:
	var root := Node3D.new()
	root.position=pos
	root.rotation.y=rot
	add_child(root)
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size=size
	mi.mesh=bm
	mi.material_override=mat
	root.add_child(mi)
	if collide:
		var body := StaticBody3D.new()
		var cs := CollisionShape3D.new()
		var bs := BoxShape3D.new()
		bs.size=size
		cs.shape=bs
		body.add_child(cs)
		root.add_child(body)
	return root

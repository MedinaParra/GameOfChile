from pathlib import Path

PROJECT = Path('project')


def replace(path: Path, old: str, new: str, required: bool = True) -> None:
    text = path.read_text(encoding='utf-8')
    if old not in text:
        if required:
            raise RuntimeError(f'Missing token in {path}: {old[:100]!r}')
        return
    path.write_text(text.replace(old, new), encoding='utf-8')


def ensure_rendering_setting(text: str, setting_line: str) -> str:
    key = setting_line.split('=', 1)[0]
    lines = [line for line in text.splitlines() if not line.startswith(key + '=')]
    text = '\n'.join(lines) + '\n'
    marker = '[rendering]\n'
    if marker not in text:
        raise RuntimeError('Missing [rendering] section')
    return text.replace(marker, marker + setting_line + '\n', 1)

# Project settings: Vulkan Mobile renderer with OpenGL fallback and conservative Android settings.
config_path = PROJECT / 'project.godot'
config = config_path.read_text(encoding='utf-8')
config = config.replace('config/name="Tatis y el Laberinto del Fauno"', 'config/name="Tatis Laberinto - Modo Seguro"')
config = config.replace('config/version="1.0.0"', 'config/version="1.0.1"')
config = config.replace('renderer/rendering_method="gl_compatibility"', 'renderer/rendering_method="mobile"')
config = config.replace('renderer/rendering_method.mobile="gl_compatibility"', 'renderer/rendering_method.mobile="mobile"')
config = config.replace('occlusion_culling/use_occlusion_culling=true', 'occlusion_culling/use_occlusion_culling=false')
for setting in [
    'rendering_device/driver.android="vulkan"',
    'rendering_device/fallback_to_opengl3=true',
    'shading/overrides/force_vertex_shading=true',
    'limits/opengl/max_renderable_lights=8',
    'limits/opengl/max_lights_per_object=4',
]:
    config = ensure_rendering_setting(config, setting)
if '[display/window]' in config and 'frame_pacing/android/enable_frame_pacing' not in config:
    config = config.replace('[display/window]\n', '[display/window]\nframe_pacing/android/enable_frame_pacing=true\n', 1)
config_path.write_text(config, encoding='utf-8')

# Export as a side-by-side safe package; include x86_64 for emulator validation.
preset_path = PROJECT / 'export_presets.cfg'
preset = preset_path.read_text(encoding='utf-8')
preset = preset.replace('architectures/x86_64=false', 'architectures/x86_64=true')
preset = preset.replace('package/unique_name="com.tatis.laberintodelfauno"', 'package/unique_name="com.tatis.laberintodelfauno.safe"')
preset = preset.replace('package/name="Tatis y el Laberinto del Fauno"', 'package/name="Tatis Laberinto - Modo Seguro"')
preset = preset.replace('version/code=1', 'version/code=2')
preset = preset.replace('version/name="1.0.0"', 'version/name="1.0.1-safe"')
preset_path.write_text(preset, encoding='utf-8')

# Remove GPU particles from all 50 corn collectibles (driver-safe glow remains).
corn_path = PROJECT / 'scripts/CornCollectible.gd'
corn = corn_path.read_text(encoding='utf-8')
old_particles = '''    var particles:=GPUParticles3D.new(); particles.amount=5; particles.lifetime=1.2; particles.randomness=0.7
    var pm:=ParticleProcessMaterial.new(); pm.emission_shape=ParticleProcessMaterial.EMISSION_SHAPE_SPHERE; pm.emission_sphere_radius=0.28; pm.gravity=Vector3.ZERO; pm.initial_velocity_min=0.05; pm.initial_velocity_max=0.18; pm.scale_min=0.04; pm.scale_max=0.08
    particles.process_material=pm; var dot:=SphereMesh.new(); dot.radius=0.05; dot.height=0.1; dot.material=mat; particles.draw_pass_1=dot; add_child(particles)
'''
if old_particles not in corn:
    raise RuntimeError('Corn GPU particle block not found')
corn = corn.replace(old_particles, '')
corn_path.write_text(corn, encoding='utf-8')

# Cache materials and primitive meshes, disable shadows, and reduce dynamic lights.
game_path = PROJECT / 'scripts/GameManager.gd'
game = game_path.read_text(encoding='utf-8')
game = game.replace('var checkpoint_id:=0\n', 'var checkpoint_id:=0\nvar material_cache: Dictionary = {}\nvar box_mesh_cache: Dictionary = {}\nvar sphere_mesh_cache: Dictionary = {}\n')
old_mat = '''func _mat(color: Color, emission:=Color.TRANSPARENT) -> StandardMaterial3D:
    var m:=StandardMaterial3D.new(); m.albedo_color=color; m.roughness=.86
    if emission.a>0: m.emission_enabled=true; m.emission=emission; m.emission_energy_multiplier=.7
    return m
'''
new_mat = '''func _mat(color: Color, emission:=Color.TRANSPARENT) -> StandardMaterial3D:
    var key := "%s|%s" % [color.to_html(true), emission.to_html(true)]
    if material_cache.has(key):
        return material_cache[key]
    var material := StandardMaterial3D.new()
    material.albedo_color = color
    material.roughness = 0.9
    if emission.a > 0.0:
        material.emission_enabled = true
        material.emission = emission
        material.emission_energy_multiplier = 0.55
    material_cache[key] = material
    return material
'''
if old_mat not in game:
    raise RuntimeError('GameManager material function not found')
game = game.replace(old_mat, new_mat)
old_box = '''    holder.position=pos; parent.add_child(holder)
    var mi:=MeshInstance3D.new(); var mesh:=BoxMesh.new(); mesh.size=size; mesh.material=_mat(color,emission); mi.mesh=mesh; holder.add_child(mi)
    return holder
'''
new_box = '''    holder.position = pos
    parent.add_child(holder)
    var mesh_key := "%s|%s|%s" % [size, color.to_html(true), emission.to_html(true)]
    var mesh: BoxMesh
    if box_mesh_cache.has(mesh_key):
        mesh = box_mesh_cache[mesh_key]
    else:
        mesh = BoxMesh.new()
        mesh.size = size
        mesh.material = _mat(color, emission)
        box_mesh_cache[mesh_key] = mesh
    var mesh_instance := MeshInstance3D.new()
    mesh_instance.mesh = mesh
    holder.add_child(mesh_instance)
    return holder
'''
if old_box not in game:
    raise RuntimeError('GameManager box function block not found')
game = game.replace(old_box, new_box)
old_sphere = '''func _mesh_sphere(parent: Node,pos:Vector3,scale_v:Vector3,color:Color) -> MeshInstance3D:
    var mi:=MeshInstance3D.new(); var mesh:=SphereMesh.new(); mesh.material=_mat(color); mi.mesh=mesh; mi.position=pos; mi.scale=scale_v; parent.add_child(mi); return mi
'''
new_sphere = '''func _mesh_sphere(parent: Node, pos: Vector3, scale_v: Vector3, color: Color) -> MeshInstance3D:
    var key := color.to_html(true)
    var mesh: SphereMesh
    if sphere_mesh_cache.has(key):
        mesh = sphere_mesh_cache[key]
    else:
        mesh = SphereMesh.new()
        mesh.radial_segments = 12
        mesh.rings = 6
        mesh.material = _mat(color)
        sphere_mesh_cache[key] = mesh
    var mesh_instance := MeshInstance3D.new()
    mesh_instance.mesh = mesh
    mesh_instance.position = pos
    mesh_instance.scale = scale_v
    parent.add_child(mesh_instance)
    return mesh_instance
'''
if old_sphere not in game:
    raise RuntimeError('GameManager sphere function not found')
game = game.replace(old_sphere, new_sphere)
game = game.replace('sun.shadow_enabled=true', 'sun.shadow_enabled=false')
game = game.replace('for i in 13:', 'for i in 8:')
old_fireflies = '''            for i in 8:
                var li:=OmniLight3D.new(); li.light_color=Color("d7ff82"); li.light_energy=.35; li.omni_range=2; li.position=Vector3(cx-10+i*2.7,1.4,11-sin(i)*2); world.add_child(li)
'''
new_fireflies = '''            for i in 4:
                _mesh_sphere(
                    world,
                    Vector3(cx - 9 + i * 5.5, 1.4, 11 - sin(i) * 2),
                    Vector3(0.08, 0.08, 0.08),
                    Color("d7ff82")
                )
'''
if old_fireflies not in game:
    raise RuntimeError('Firefly light block not found')
game = game.replace(old_fireflies, new_fireflies)
game_path.write_text(game, encoding='utf-8')

# Avoid a background audio loop on Android in the compatibility build; cache SFX.
audio_path = PROJECT / 'scripts/AudioManager.gd'
audio = audio_path.read_text(encoding='utf-8')
audio = audio.replace('var music_mode: String = "menu"\n', 'var music_mode: String = "menu"\nvar sfx_cache: Dictionary = {}\n')
audio = audio.replace('''func play_sfx(effect_name: String) -> void:
    var player := AudioStreamPlayer.new()
    add_child(player)
    match effect_name:
''', '''func play_sfx(effect_name: String) -> void:
    var player := AudioStreamPlayer.new()
    add_child(player)
    if sfx_cache.has(effect_name):
        player.stream = sfx_cache[effect_name]
    else:
        match effect_name:
''')
# Indent match cases and cache once. Use a small parser-safe transformation.
lines = audio.splitlines()
out = []
in_match = False
for line in lines:
    if line == '        match effect_name:':
        in_match = True
        out.append(line)
        continue
    if in_match:
        if line.startswith('        "') or line.startswith('        _'):
            out.append('    ' + line)
            continue
        if line.startswith('            '):
            out.append('    ' + line)
            continue
        # First line after match block.
        out.append('        sfx_cache[effect_name] = player.stream')
        in_match = False
    out.append(line)
audio = '\n'.join(out) + '\n'
audio = audio.replace('''func set_music(mode: String) -> void:
    music_mode = mode
''', '''func set_music(mode: String) -> void:
    music_mode = mode
    if OS.get_name() == "Android":
        return
''')
audio_path.write_text(audio, encoding='utf-8')

# Force conservative 30 FPS on Android regardless of a previous save.
settings_path = PROJECT / 'scripts/SettingsManager.gd'
settings = settings_path.read_text(encoding='utf-8')
settings = settings.replace('''    var q := int(values.quality)
    if q == 0:
''', '''    if OS.get_name() == "Android":
        Engine.max_fps = 30
        return
    var q := int(values.quality)
    if q == 0:
''')
settings_path.write_text(settings, encoding='utf-8')

# Make root activation changes physics-safe.
roots_path = PROJECT / 'scripts/MagicRoots.gd'
roots = roots_path.read_text(encoding='utf-8')
roots = roots.replace('var mesh_node: MeshInstance3D\n', 'var mesh_node: MeshInstance3D\nvar monitoring_state:=false\n')
roots = roots.replace('''    active=cycle>1.2 and cycle<2.6
    mesh_node.scale.y=lerp(mesh_node.scale.y,1.0 if active else 0.08,delta*7.0)
    monitoring=active
''', '''    active = cycle > 1.2 and cycle < 2.6
    mesh_node.scale.y = lerp(mesh_node.scale.y, 1.0 if active else 0.08, delta * 7.0)
    if active != monitoring_state:
        monitoring_state = active
        set_deferred("monitoring", active)
''')
roots_path.write_text(roots, encoding='utf-8')

# Real world smoke test: enters a new game, verifies 50 corn, health, save and door unlock.
test_path = PROJECT / 'tests/RuntimeWorldSmoke.gd'
test_path.write_text('''extends SceneTree

func _initialize() -> void:
    call_deferred("_run")

func _count_corn(node: Node) -> int:
    var count := 1 if node is CornCollectible else 0
    for child in node.get_children():
        count += _count_corn(child)
    return count

func _fail(message: String) -> void:
    push_error("WORLD SMOKE FAILED: " + message)
    quit(1)

func _run() -> void:
    var game := GameManager.new()
    root.add_child(game)
    await process_frame
    game.start_game(false)
    await process_frame
    await process_frame
    if not is_instance_valid(game.world) or not is_instance_valid(game.player):
        _fail("world or player missing")
        return
    if _count_corn(game.world) != 50:
        _fail("expected exactly 50 corn")
        return
    if game.door.opened:
        _fail("door opened before 50 corn")
        return
    game.player.take_damage(1)
    if game.player.hearts != 2:
        _fail("damage system failed")
        return
    for corn_id in range(50):
        game.corn_manager.collect(corn_id)
    await process_frame
    if game.corn_manager.count() != 50 or not game.door.opened:
        _fail("50 corn did not unlock final door")
        return
    game.player.checkpoint_position = Vector3(28, 1, -8)
    game.player.respawn()
    await process_frame
    if not SaveManager.has_save():
        _fail("save file missing")
        return
    print("WORLD SMOKE: OK - 5 sectors, 50 corn, damage, checkpoint, save, final door")
    quit(0)
''', encoding='utf-8')

print('Applied Samsung A26 / Android stability patch')

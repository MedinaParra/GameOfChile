from pathlib import Path

path = Path('project/project.godot')
text = path.read_text(encoding='utf-8')
wrong = '''
[textures]
vram_compression/import_etc2_astc=true
'''
text = text.replace(wrong, '')
setting = 'textures/vram_compression/import_etc2_astc=true'
if setting not in text:
    marker = '[rendering]\n'
    if marker not in text:
        raise SystemExit('Missing [rendering] section in project.godot')
    text = text.replace(marker, marker + setting + '\n', 1)
path.write_text(text, encoding='utf-8')
print('Enabled rendering/textures/vram_compression/import_etc2_astc')

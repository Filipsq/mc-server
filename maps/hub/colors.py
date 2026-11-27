import hashlib
COL = {
 "deepslate_tiles": (54, 54, 58), "polished_deepslate": (72, 72, 76), "sea_lantern": (200, 225, 215),
 "lime_stained_glass": (120, 230, 60), "polished_blackstone_bricks": (48, 42, 50), "smooth_quartz": (235, 230, 222),
 "deepslate_bricks": (68, 68, 72), "verdant_froglight": (190, 240, 160), "polished_blackstone": (52, 46, 56),
 "lime_concrete": (94, 168, 24), "water": (50, 90, 220), "quartz_pillar": (236, 232, 224), "light_blue_stained_glass": (100, 170, 230),
 "red_stained_glass": (190, 50, 50), "purple_stained_glass": (130, 60, 190), "orange_stained_glass": (230, 130, 40),
 "crimson_hyphae": (130, 25, 35), "gray_stained_glass": (90, 90, 90), "gold_block": (245, 205, 60), "cyan_stained_glass": (40, 160, 170),
 "lantern": (250, 200, 100), "black_concrete": (12, 12, 16), "spruce_stairs": (110, 80, 50), "cherry_log": (60, 30, 40),
 "cherry_leaves": (240, 170, 200), "stone": (125, 125, 125), "dirt": (134, 96, 67), "grass_block": (98, 160, 58),
 "white_terracotta": (209, 178, 161), "light_gray_terracotta": (135, 106, 97), "lime_terracotta": (103, 117, 52),
 "green_terracotta": (76, 83, 42), "cyan_terracotta": (87, 91, 91), "terracotta": (152, 94, 67), "light_blue_terracotta": (113, 108, 137),
 "snow_block": (245, 250, 250), "gravel": (130, 125, 122), "mossy_cobblestone": (100, 115, 90), "polished_andesite": (132, 134, 133),
 "andesite": (136, 136, 136), "spruce_planks": (114, 84, 48), "spruce_fence": (114, 84, 48), "dirt_path": (148, 122, 65),
 "stone_bricks": (122, 121, 122), "spruce_log": (58, 37, 16), "spruce_leaves": (50, 90, 50), "poppy": (200, 40, 30),
 "dandelion": (240, 220, 40), "cornflower": (70, 100, 220), "allium": (180, 110, 220), "oxeye_daisy": (240, 240, 230),
 "azure_bluet": (230, 240, 250), "short_grass": (98, 160, 58), "fern": (80, 140, 50), "deepslate_tiles": (54, 54, 58),
}
def col(name):
    n = name.split(":")[1].split("[")[0]
    return COL.get(n) or tuple(int(hashlib.md5(n.encode()).hexdigest()[i:i+2],16) for i in (0,2,4))

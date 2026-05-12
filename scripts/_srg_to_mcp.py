"""
_srg_to_mcp.py — SRG (obfuscated) to MCP (mapped) name translator for Forge 1.12.x.

When vineflower decompiles a mod JAR that was compiled against obfuscated Minecraft,
the decompiled source contains SRG names like `func_71410_x()` and `field_71439_g`
instead of proper MCP-mapped names like `getMinecraft()` and `player`.

This module provides a mapping table to convert SRG names back to their proper
MCP-mapped equivalents so the AI sees usable source code.

EXTENDING THIS MODULE:
When you encounter a new SRG name in a build log error, add it to the
SRG_MCP_MAP dictionary with its correct MCP-mapped name.
"""

from __future__ import annotations

import re

# ── SRG → MCP Mapping ──────────────────────────────────────────────────────
# Format: {srg_name: mcp_name}
# These are Forge 1.12.x MCP mappings (mcpSnapshot_20171003 for 1.12.2)
# Source: DIF entries, build error logs, and Forge MCPBot SRG dumps

SRG_MCP_MAP: dict[str, str] = {
    # ── Minecraft class static/singleton methods ─────────────────────────
    "func_71410_x": "getMinecraft",

    # ── Minecraft instance fields ────────────────────────────────────────
    "field_71439_g": "player",
    "field_71441_e": "world",
    "field_71474_y": "gameSettings",
    "field_71442_b": "playerController",

    # ── GameSettings fields ──────────────────────────────────────────────
    "field_74313_G": "keyBindUseItem",

    # ── GameSettings input check method ──────────────────────────────────
    "func_151470_d": "isKeyDown",

    # ── EntityPlayer / EntityLivingBase methods ──────────────────────────
    "func_184614_ca": "getHeldItemMainhand",
    "func_184592_cb": "getHeldItemOffhand",
    "func_184609_a": "swingItem",
    "func_70005_c_": "getName",
    "func_70638_az": "getHealth",
    "func_110143_aJ": "getMaxHealth",
    "func_184601_bH": "getActiveHand",

    # ── ItemStack methods ─────────────────────────────────────────────────
    "func_190926_b": "isEmpty",
    "func_77973_b": "getItem",
    "func_77952_i": "getItemDamage",
    "func_190916_E": "getCount",
    "func_77951_h": "hasTagCompound",
    "func_77978_p": "getTagCompound",
    "func_77960_j": "getMetadata",
    "func_77962_a": "getTooltip",
    "func_77955_b": "copy",
    "func_190920_e": "setCount",

    # ── Items class fields ────────────────────────────────────────────────
    "field_151062_by": "EXPERIENCE_BOTTLE",
    "field_151153_an": "DIAMOND_SWORD",
    "field_151071_au": "BOW",
    "field_151068_bn": "ARROW",
    "field_151133_ar": "GOLDEN_APPLE",
    "field_151079_az": "POTION",
    "field_151149_bK": "BLAZE_ROD",
    "field_151142_bU": "ENDER_PEARL",
    "field_151129_at": "ENCHANTED_BOOK",
    "field_151113_aN": "FISHING_ROD",
    "field_151065_bR": "COOKED_BEEF",
    "field_151085_bO": "BREAD",

    # ── World class methods ───────────────────────────────────────────────
    "func_72838_d": "spawnEntity",
    "func_72875_e": "getEntityByID",
    "func_72986_a": "getWorldInfo",
    "func_72897_h": "getTotalWorldTime",
    "func_72820_D": "getWorldTime",
    "func_72866_a": "loadedEntityList",
    "func_147461_a": "loadedTileEntityList",
    "func_72912_b": "getGameRules",
    "func_175642_b": "getClosestPlayer",
    "func_72826_c": "playSound",
    "func_184148_a": "playEvent",

    # ── PlayerControllerMP methods ────────────────────────────────────────
    "func_187101_a": "processRightClick",
    "func_78761_a": "clickBlock",
    "func_78764_a": "sendUseItem",
    "func_78769_a": "syncCurrentPlayItem",
    "func_78768_b": "windowClick",

    # ── WorldClient specific methods ──────────────────────────────────────
    "func_72977_a": "getWorldVec3Pool",
    "func_82736_a": "getCollidingBoundingBoxes",

    # ── Entity fields ─────────────────────────────────────────────────────
    "field_70165_t": "posX",
    "field_70163_u": "posY",
    "field_70161_v": "posZ",
    "field_70177_z": "rotationYaw",
    "field_70125_A": "rotationPitch",
    "field_70159_w": "motionX",
    "field_70181_x": "motionY",
    "field_70179_y": "motionZ",
    "field_70170_p": "world",
    "field_70173_aa": "ticksExisted",
    "field_70131_O": "height",
    "field_70130_N": "width",
    "field_70147_ah": "stepHeight",
    "field_70718_bc": "deathTime",
    "field_70717_bb": "hurtTime",

    # ── EntityPlayer specific fields ──────────────────────────────────────
    "field_71071_by": "inventory",
    "field_71075_bZ": "capabilities",
    "field_71076_b": "experienceLevel",
    "field_71077_c": "experienceTotal",
    "field_71078_a": "experience",
    "field_71100_bB_": "experienceLevel",
    "field_71079_bU": "sleeping",
    "field_71091_bM": "fishEntity",

    # ── EntityLivingBase fields ───────────────────────────────────────────
    "field_70706_ct": "activeHand",
    "field_70704_cr": "activeItemStack",
    "field_70705_cs": "activeItemStackUseCount",
    "field_70708_cq": "isHandActive",
    "field_70709_cp": "isPlayerSleeping",
    "field_110151_bq": "recentlyHit",
    "field_94063_bp": "attackingPlayer",
    "field_70717_bb": "hurtTime",
    "field_70718_bc": "deathTime",
    "field_70719_bd": "attackTime",

    # ── Minecraft object methods ──────────────────────────────────────────
    "func_147114_u": "getIntegratedServer",
    "func_147104_a": "getMinecraft",
    "func_147108_a": "displayGuiScreen",
    "func_71381_b": "isGuiEnabled",
    "func_71352_k": "launchIntegratedServer",
    "func_71403_a": "loadWorld",
    "func_71404_a": "displayInGameMenu",
    "func_71405_c": "quit",
    "func_71407_l": "setIngameFocus",
    "func_71411_J": "startGame",
    "func_71422_O": "player",
    "func_147113_a": "getRenderManager",
    "func_175606_aa": "getRenderViewEntity",
    "func_175607_ar": "setRenderViewEntity",
    "func_71415_G": "getTextureManager",
    "func_110434_K": "getResourceManager",
    "func_152344_a": "addScheduledTask",

    # ── EnumHand ──────────────────────────────────────────────────────────
    "field_184553_a": "MAIN_HAND",
    "field_184554_b": "OFF_HAND",
    "field_184555_c": "values",

    # ── EnumActionResult ──────────────────────────────────────────────────
    "field_178366_a": "SUCCESS",
    "field_178364_b": "PASS",
    "field_178365_c": "FAIL",

    # ── Minecraft.getMinecraft().player.swingItem() ────────────────────────
    # (func_184609_a → swingItem already in entity section above)

    # ── Util / Misc ──────────────────────────────────────────────────────
    "func_110139_bj": "getSoundHandler",
    "func_147118_V": "getSoundHandler",
    "func_178877_a": "getDefaultResourcePack",
}


def translate_srg_to_mcp(source_text: str) -> str:
    """
    Translate SRG/obfuscated names in source code to MCP-mapped names.

    Scans for `func_XXXXX_XXX` and `field_XXXXX_XXX` patterns and replaces
    them with their proper MCP-mapped equivalents when known.

    Unknown SRG names are left unchanged so the AI can still reference them
    in context (they just won't compile — the DIF system will catch those).
    """
    if not source_text:
        return source_text

    result = source_text

    # Apply known SRG → MCP mappings
    for srg_name, mcp_name in SRG_MCP_MAP.items():
        # Match as word boundary to avoid partial matches
        pattern = re.compile(r'\b' + re.escape(srg_name) + r'\b')
        result = pattern.sub(mcp_name, result)

    return result


def translate_srg_in_files(source_files: dict[str, str]) -> dict[str, str]:
    """
    Apply SRG→MCP translation to all source files.
    Each key is a filepath, each value is the file content.
    Returns a new dict with translated content.
    """
    return {
        path: translate_srg_to_mcp(content)
        for path, content in source_files.items()
    }

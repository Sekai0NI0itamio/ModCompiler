s/double renderX = mc.getRenderManager().renderPosX;/Entity rv = mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : player; double renderX = rv.lastTickPosX + (rv.posX - rv.lastTickPosX) * partialTicks;/g
s/double renderY = mc.getRenderManager().renderPosY;/double renderY = rv.lastTickPosY + (rv.posY - rv.lastTickPosY) * partialTicks;/g
s/double renderZ = mc.getRenderManager().renderPosZ;/double renderZ = rv.lastTickPosZ + (rv.posZ - rv.lastTickPosZ) * partialTicks;/g

package com.argos.argos_weaponry;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class ArgWeaponry {

    public ArgWeaponry(FMLJavaModLoadingContext context) {

        context.registerConfig(
                ModConfig.Type.COMMON,
                Config.SPEC
        );

        ContentPackInstaller.install();
    }
}
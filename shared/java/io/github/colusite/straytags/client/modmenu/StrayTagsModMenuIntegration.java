package io.github.colusite.straytags.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

public class StrayTagsModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            return StrayTagsConfigScreenBuilder::create;
        }
        return parent -> null;
    }
}
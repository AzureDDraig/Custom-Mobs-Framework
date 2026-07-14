package ddraig.net.custommobs.mixin;

import ddraig.net.custommobs.client.renderer.DynamicMobPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Inject(method = "discoverAvailable", at = @At("RETURN"), cancellable = true)
    private void injectDynamicPack(CallbackInfoReturnable<Map<String, Pack>> cir) {
        Map<String, Pack> original = cir.getReturnValue();
        if (original.containsKey("custom_mobs_dynamic")) {
            return;
        }

        Pack pack = Pack.readMetaAndCreate(
            "custom_mobs_dynamic",
            net.minecraft.network.chat.Component.literal("Custom Mobs Dynamic Resources"),
            true,
            id -> new DynamicMobPackResources(),
            net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
            Pack.Position.TOP,
            net.minecraft.server.packs.repository.PackSource.BUILT_IN
        );

        if (pack != null) {
            Map<String, Pack> modified = new HashMap<>(original);
            modified.put("custom_mobs_dynamic", pack);
            cir.setReturnValue(Map.copyOf(modified));
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "setSelected",
        at = @At("HEAD"),
        argsOnly = true
    )
    private java.util.Collection<String> modifySelected(java.util.Collection<String> selected) {
        if (selected != null && !selected.contains("custom_mobs_dynamic")) {
            java.util.List<String> list = new java.util.ArrayList<>(selected);
            list.add("custom_mobs_dynamic");
            return list;
        }
        return selected;
    }
}

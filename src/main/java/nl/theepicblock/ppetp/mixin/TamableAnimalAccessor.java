package nl.theepicblock.ppetp.mixin;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TamableAnimal.class)
public interface TamableAnimalAccessor {
    @Invoker
    boolean invokeCanTeleportTo(BlockPos pos);
}

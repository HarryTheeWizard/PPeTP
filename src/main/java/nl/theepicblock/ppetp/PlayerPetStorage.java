package nl.theepicblock.ppetp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.core.BlockPos;
import nl.theepicblock.ppetp.mixin.EntityAccessor;
import nl.theepicblock.ppetp.mixin.TamableAnimalAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static nl.theepicblock.ppetp.PPeTP.LOGGER;

public class PlayerPetStorage {
    public static final String KEY = "PPeTP";
    public static final Codec<List<PetEntry>> CODEC = Codec.list(PetEntry.CODEC);

    private List<PetData> entitydatas = new ArrayList<>();
    private boolean verified = false;

    public void tick(ServerPlayer owner) {
        if (owner.level() == null) return;

        var world = (ServerLevel) owner.level();
        if (!verified && world.getServer() != null) {
            this.entitydatas.replaceAll(d -> new PetData(d.entity(), d.entry().verified(world.getServer())));
            this.verified = true;
        }

        var iter = entitydatas.iterator();
        while (iter.hasNext()) {
            var d = iter.next();
            if (!canExtractPet(owner, d.entry())) {
                continue;
            }
            Predicate<BlockPos> spotValidator;

            var e = d.entity();
            if (e != null) {
                ((EntityAccessor) e).invokeSetLevel(world);
                spotValidator = (pos) -> ((TamableAnimalAccessor) e).invokeCanTeleportTo(pos);
            } else {
                spotValidator = (pos) -> world.getBlockState(pos).isAir() &&
                        !world.getBlockState(pos.below()).getCollisionShape(world, pos.below()).isEmpty();
            }
            var spot = SpotFinder.findSpot(owner, spotValidator);
            if (spot != null) {
                if (dropEntityInWorld(d.entry().data(), world, spot)) {
                    iter.remove();
                }
            }
        }
    }

    private boolean canExtractPet(ServerPlayer owner, PetEntry e) {
        if (owner.isSpectator()) {
            return false;
        }

        var gameRules = owner.level().getGameRules();
        if (!gameRules.get(PPeTP.SHOULD_TP_CROSS_DIMENSIONAL)) {
            if (e.sourceDimension().isPresent() && !Objects.equals(owner.level().dimension().identifier(), e.sourceDimension().get())) {
                return false;
            }
        }

        return true;
    }

    private boolean dropEntityInWorld(CompoundTag data, ServerLevel world, BlockPos pos) {
        var dataReadView = TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), data);
        var optionalEntity = EntityType.create(dataReadView, world, EntitySpawnReason.LOAD);
        if (optionalEntity.isEmpty()) {
            return false;
        }
        var entity = optionalEntity.get();
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return world.addFreshEntity(entity);
    }

    private Optional<Entity> readData(CompoundTag data, ServerLevel world) {
        var dataReadView = TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), data);
        return EntityType.create(dataReadView, world, EntitySpawnReason.LOAD);
    }

    public boolean insert(TamableAnimal entity) {
        TagValueOutput nbtWriteView = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        var success = entity.save(nbtWriteView);
        if (!success) return false;

        var world = entity.level();
        Identifier dimensionId = world == null ? null : world.dimension().identifier();

        var petEntry = new PetEntry(Optional.ofNullable(dimensionId), nbtWriteView.buildResult());
        entitydatas.add(new PetData(entity, petEntry));
        return true;
    }

    public void writePlayerData(ValueOutput view) {
        var list = new ArrayList<PetEntry>(this.entitydatas.size());
        for (var d : this.entitydatas) {
            list.add(d.entry());
        }
        view.store(KEY, CODEC, list);
    }

    public void readPlayerData(ValueInput view, ServerPlayer player) {
        var optList = view.read(KEY, CODEC);
        optList.ifPresent(list -> {
            this.entitydatas = new ArrayList<>(list.size());
            this.verified = false;
            var world = (ServerLevel) player.level();

            if (world == null) {
                list.forEach(e -> entitydatas.add(new PetData(null, e)));
                return;
            }

            list.forEach(e -> entitydatas.add(new PetData(
                    readData(e.data(), world).orElse(null) instanceof TamableAnimal te ? te : null,
                    e
            )));
        });
    }

    public int getNumberOfStoredPets() {
        return this.entitydatas.size();
    }

    private record PetData(@Nullable TamableAnimal entity, PetEntry entry) {}

    private record PetEntry(Optional<Identifier> sourceDimension, CompoundTag data) {
        public static final Codec<PetEntry> CODEC = RecordCodecBuilder.create(i ->
                i.group(
                        Identifier.CODEC.optionalFieldOf("sourceDimension").forGetter(PetEntry::sourceDimension),
                        CompoundTag.CODEC.fieldOf("data").forGetter(PetEntry::data)
                ).apply(i, PetEntry::new));

        private PetEntry verified(MinecraftServer server) {
            if (sourceDimension.isPresent() && server.getLevel(ResourceKey.create(Level.OVERWORLD.registryKey(), sourceDimension.get())) == null) {
                return new PetEntry(Optional.empty(), this.data);
            } else {
                return this;
            }
        }
    }
}

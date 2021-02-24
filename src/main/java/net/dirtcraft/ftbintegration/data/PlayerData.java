package net.dirtcraft.ftbintegration.data;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.math.ChunkDimPos;
import com.feed_the_beast.ftbutilities.FTBUtilitiesPermissions;
import com.feed_the_beast.ftbutilities.data.ClaimedChunk;
import com.feed_the_beast.ftbutilities.data.ClaimedChunks;
import com.mojang.authlib.GameProfile;
import net.dirtcraft.ftbintegration.core.mixins.badges.FTBUtilitiesUniverseDataAccessor;
import net.dirtcraft.ftbintegration.core.mixins.generic.AccessorFinalIDObject;
import net.dirtcraft.ftbintegration.data.sponge.PlayerSettings;
import net.dirtcraft.ftbintegration.utility.Permission;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.server.permission.PermissionAPI;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.common.SpongeImpl;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    private final User user;
    private final GameProfile gameProfile;
    private ForgePlayer fPlayer;

    private ForgeTeam claimStandingIn;
    private ClaimedChunk lastInteractClaim;
    private boolean lastInteractResult;
    private int lastInteractTick;

    private boolean bypassClaims;
    private boolean debugClaims;

    private ChunkDimPos primaryChunkPos;
    private ChunkDimPos secondaryChunkPos;
    private List<ChunkDimPos> selection;

    public static PlayerData getOrCreate(User user){
        return PlayerDataManager.INSTANCE.getOrCreate(user);
    }

    public static PlayerData get(User user){
        return PlayerDataManager.INSTANCE.get(user);
    }

    public PlayerData(User user){
        this.gameProfile = (GameProfile) user.getProfile();
        this.user = user;
        this.bypassClaims = user.get(PlayerSettings.CAN_BYPASS).orElse(false) && user.hasPermission(Permission.BYPASS);
        this.debugClaims = user.get(PlayerSettings.IS_DEBUG).orElse(false) && user.hasPermission(Permission.DEBUG);
        if (getBadge() != null) FTBUtilitiesUniverseDataAccessor.getBADGE_CACHE().put(user.getUniqueId(), getBadge());
    }

    public void setLastInteractData(ClaimedChunk claim) {
        this.lastInteractResult = true;
        this.lastInteractClaim = claim;
        this.lastInteractTick = SpongeImpl.getServer().getTickCounter();
    }

    public boolean checkLastInteraction(ClaimedChunk claim, User user) {
        if (this.lastInteractResult && user != null && ((SpongeImpl.getServer().getTickCounter() - this.lastInteractTick) <= 2)) {
            return claim == null || lastInteractClaim != null && claim.getTeam().equals(this.lastInteractClaim.getTeam());
        }
        return false;
    }

    public String getClaimStandingIn(){
        return formatTeam(claimStandingIn);
    }

    public void setClaimStandingIn(ForgeTeam team){
        claimStandingIn = team;
    }

    public void setPrimaryChunkPos(Location<World> location, Player player) {
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        int d = ((EntityPlayerMP)player).dimension;
        this.primaryChunkPos = new ChunkDimPos(x, z, d);
        calcSelectedRegion();
        String message = String.format("&aSet primary selection %d, %d - %d total chunks)", x, z, selection.size());
        Text response = TextSerializers.FORMATTING_CODE.deserialize(message);
        player.sendMessage(response);
    }

    public void setSecondaryChunkPos(Location<World> location, Player player) {
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        int d = ((EntityPlayerMP)player).dimension;
        this.secondaryChunkPos = new ChunkDimPos(x, z, d);
        calcSelectedRegion();
        String message = String.format("&aSet secondary selection %d, %d - %d total chunks)", x, z, selection.size());
        Text response = TextSerializers.FORMATTING_CODE.deserialize(message);
        player.sendMessage(response);
    }

    public List<ChunkDimPos> getSelectedRegion(){
        if (selection == null) selection = new ArrayList<>();
        return selection;
    }

    public void calcSelectedRegion(){
        if (primaryChunkPos == null || secondaryChunkPos == null || primaryChunkPos.dim != secondaryChunkPos.dim) {
            this.selection = new ArrayList<>();
            return;
        }
        List<ChunkDimPos> posList = new ArrayList<>();
        int dim = primaryChunkPos.dim;
        int xMin = Math.min(primaryChunkPos.posX, secondaryChunkPos.posX);
        int xMax = Math.max(primaryChunkPos.posX, secondaryChunkPos.posX);
        int zMin = Math.min(primaryChunkPos.posZ, secondaryChunkPos.posZ);
        int zMax = Math.max(primaryChunkPos.posZ, secondaryChunkPos.posZ);
        for (int x = xMin; x <= xMax; x++){
            for (int z = zMin; z <= zMax; z++){
                ChunkDimPos pos = new ChunkDimPos(x, z, dim);
                posList.add(pos);
            }
        }
        this.selection = posList;

    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasBlockEditingPermission(Block block, ClaimedChunk chunk) {
        return canBypassClaims() || PermissionAPI.hasPermission(gameProfile, "ftbutilities.claims.block.edit." + formatId(block) + "." + formatClaim(chunk), null);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasBlockInteractionPermission(Block block, ClaimedChunk chunk) {
        return canBypassClaims() || PermissionAPI.hasPermission(gameProfile, "ftbutilities.claims.block.interact." + formatId(block) + "." + formatClaim(chunk), null);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasItemUsePermission(Item block, ClaimedChunk chunk) {
        return canBypassClaims() || PermissionAPI.hasPermission(gameProfile, "ftbutilities.claims.item." + formatId(block) + "." + formatClaim(chunk), null);
    }

    public boolean hasAnimalAttackPermission(ClaimedChunk chunk) {
        return canBypassClaims() || PermissionAPI.hasPermission(gameProfile, FTBUtilitiesPermissions.CLAIMS_ATTACK_ANIMALS + "." + formatClaim(chunk), null);
    }

    public ForgePlayer getForgePlayer(){
        if (fPlayer == null) fPlayer = ClaimedChunks.instance.universe.getPlayer(gameProfile);
        return fPlayer;
    }

    public ForgeTeam getForgeTeam(){
        return getForgePlayer().team;
    }

    public User getUser(){
        return user;
    }

    public GameProfile getGameProfile(){
        return gameProfile;
    }

    public boolean isOnline(){
        return user.isOnline();
    }

    public boolean toggleBypassClaims(){
        bypassClaims = !bypassClaims;
        user.offer(PlayerSettings.CAN_BYPASS, bypassClaims);
        return bypassClaims;
    }

    public boolean canBypassClaims(){
        return bypassClaims;
    }

    public boolean toggleDebugClaims(){
        debugClaims = !debugClaims;
        user.offer(PlayerSettings.IS_DEBUG, debugClaims);
        return debugClaims;
    }

    public boolean canDebugClaims(){
        return debugClaims;
    }

    public @Nullable String getBadge(){
        if (!user.hasPermission(Permission.STAFF_BADGE)) return null;
        return "https://i.imgur.com/G0pEx1j.png";
    }

    private String formatId(@Nullable IForgeRegistryEntry<?> item) {
        return item != null && item.getRegistryName() != null ? item.getRegistryName().toString().toLowerCase().replace(':', '.') : "minecraft.air";
    }

    private String formatClaim(ClaimedChunk chunk){
        return formatTeam(chunk == null? null: chunk.getTeam());
    }

    private String formatTeam(ForgeTeam team){
        if (team == null) return "wilderness";
        else if (team.owner == null) return "server";
        else return ((AccessorFinalIDObject)team).getTeamIdString();
    }
}
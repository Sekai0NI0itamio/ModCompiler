package com.mojang.realmsclient.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RegionSelectionPreferenceDto extends ValueObject implements ReflectionBasedSerialization {
    public static final RegionSelectionPreferenceDto DEFAULT = new RegionSelectionPreferenceDto(RegionSelectionPreference.AUTOMATIC_OWNER, null);
    private static final Logger LOGGER = LogUtils.getLogger();
    @SerializedName("regionSelectionPreference")
    @JsonAdapter(RegionSelectionPreference.RegionSelectionPreferenceJsonAdapter.class)
    public RegionSelectionPreference regionSelectionPreference;
    @SerializedName("preferredRegion")
    @JsonAdapter(RealmsRegion.RealmsRegionJsonAdapter.class)
    @Nullable
    public RealmsRegion preferredRegion;

    public RegionSelectionPreferenceDto(RegionSelectionPreference p_408403_, @Nullable RealmsRegion p_410468_) {
        this.regionSelectionPreference = p_408403_;
        this.preferredRegion = p_410468_;
    }

    private RegionSelectionPreferenceDto() {
    }

    public static RegionSelectionPreferenceDto parse(GuardedSerializer p_408548_, String p_406855_) {
        try {
            RegionSelectionPreferenceDto regionselectionpreferencedto = p_408548_.fromJson(p_406855_, RegionSelectionPreferenceDto.class);
            if (regionselectionpreferencedto == null) {
                LOGGER.error("Could not parse RegionSelectionPreference: {}", p_406855_);
                return new RegionSelectionPreferenceDto();
            } else {
                return regionselectionpreferencedto;
            }
        } catch (Exception exception) {
            LOGGER.error("Could not parse RegionSelectionPreference: {}", exception.getMessage());
            return new RegionSelectionPreferenceDto();
        }
    }

    public RegionSelectionPreferenceDto clone() {
        return new RegionSelectionPreferenceDto(this.regionSelectionPreference, this.preferredRegion);
    }
}

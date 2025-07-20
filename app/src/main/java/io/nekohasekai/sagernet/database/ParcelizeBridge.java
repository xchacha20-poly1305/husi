package io.nekohasekai.sagernet.database;

import android.os.Parcel;

/**
 * see: <a href="https://youtrack.jetbrains.com/issue/KT-19853">KT-19853</a>
 */
public class ParcelizeBridge {

    public static RuleEntity createRule(Parcel parcel) {
        return (RuleEntity) RuleEntity.CREATOR.createFromParcel(parcel);
    }

    public static AssetEntity createAsset(Parcel parcel) {
        return (AssetEntity) AssetEntity.CREATOR.createFromParcel(parcel);
    }
}

package io.nekohasekai.sagernet.fmt.v2ray;

import androidx.annotation.NonNull;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import moe.matsuri.nb4a.utils.JavaUtil;
import org.jetbrains.annotations.NotNull;

public class VMessBean extends StandardV2RayBean {

    public static final Creator<VMessBean> CREATOR = new CREATOR<VMessBean>() {
        @NonNull
        @Override
        public VMessBean newInstance() {
            return new VMessBean();
        }

        @Override
        public VMessBean[] newArray(int size) {
            return new VMessBean[size];
        }
    };
    public Integer alterId; // alterID == -1 --> VLESS

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        alterId = alterId != null ? alterId : 0;
        if (!JavaUtil.isNotBlank(encryption))  {
            encryption = isVLESS() ? "" : "auto";
        }
    }

    @NotNull
    @Override
    public VMessBean clone() {
        return KryoConverters.deserialize(new VMessBean(), KryoConverters.serialize(this));
    }
}

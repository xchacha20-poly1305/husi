package io.nekohasekai.sagernet.fmt.v2ray;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.SingBoxOptions;

public class VLESSBean extends StandardV2RayBean {
    public static final Creator<VLESSBean> CREATOR = new CREATOR<VLESSBean>() {
        @NonNull
        @Override
        public VLESSBean newInstance() {
            return new VLESSBean();
        }

        @Override
        public VLESSBean[] newArray(int size) {
            return new VLESSBean[size];
        }
    };

    public String flow;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (flow == null) flow = "";
        if (encryption == null) encryption = "";
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof VLESSBean)) return;
        VLESSBean bean = ((VLESSBean) other);
        bean.flow = flow;
    }

    @NotNull
    @Override
    public VLESSBean clone() {
        return KryoConverters.deserialize(new VLESSBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() throws Throwable {
        return SingBoxOptions.TYPE_VLESS;
    }
}

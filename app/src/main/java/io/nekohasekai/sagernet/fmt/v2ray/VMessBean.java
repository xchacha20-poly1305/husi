package io.nekohasekai.sagernet.fmt.v2ray;

import androidx.annotation.NonNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.SingBoxOptions;

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
    public Integer alterId;
    public Boolean authenticatedLength;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (alterId == null) alterId = 0;
        if (encryption == null || encryption.contains("xtls")) {
            encryption = "auto";
        }
        if (authenticatedLength == null) authenticatedLength = false;
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof VMessBean)) return;
        VMessBean bean = ((VMessBean) other);
        if (authenticatedLength) {
            bean.authenticatedLength = true;
        }
    }

    @NotNull
    @Override
    public VMessBean clone() {
        return KryoConverters.deserialize(new VMessBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() throws Throwable {
        return SingBoxOptions.TYPE_VMESS;
    }
}

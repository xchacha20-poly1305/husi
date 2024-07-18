package io.nekohasekai.sagernet.fmt.shadowsocks;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import moe.matsuri.nb4a.utils.JavaUtil;
import org.jetbrains.annotations.NotNull;

public class ShadowsocksBean extends AbstractBean {

    public static final Creator<ShadowsocksBean> CREATOR = new CREATOR<ShadowsocksBean>() {
        @NonNull
        @Override
        public ShadowsocksBean newInstance() {
            return new ShadowsocksBean();
        }

        @Override
        public ShadowsocksBean[] newArray(int size) {
            return new ShadowsocksBean[size];
        }
    };
    public String method;
    public String password;
    public String plugin;
    public Boolean sUoT;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (JavaUtil.isNullOrBlank(method)) method = "aes-256-gcm";
        if (method == null) method = "";
        if (password == null) password = "";
        if (plugin == null) plugin = "";
        if (sUoT == null) sUoT = false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(2);
        super.serialize(output);
        output.writeString(method);
        output.writeString(password);
        output.writeString(plugin);
        output.writeBoolean(sUoT);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        method = input.readString();
        password = input.readString();
        plugin = input.readString();
        sUoT = input.readBoolean();
        if (version < 2) {
            int ignored = input.readInt();
            return;
        }
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof ShadowsocksBean)) return;
        ShadowsocksBean bean = ((ShadowsocksBean) other);
        bean.sUoT = sUoT;
    }

    @Override
    public boolean canBrutal() {
        return true;
    }

    @NotNull
    @Override
    public ShadowsocksBean clone() {
        return KryoConverters.deserialize(new ShadowsocksBean(), KryoConverters.serialize(this));
    }
}

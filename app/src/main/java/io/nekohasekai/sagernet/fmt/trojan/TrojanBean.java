package io.nekohasekai.sagernet.fmt.trojan;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean;
import moe.matsuri.nb4a.SingBoxOptions;

import org.jetbrains.annotations.NotNull;

public class TrojanBean extends StandardV2RayBean {

    public static final Creator<TrojanBean> CREATOR = new CREATOR<TrojanBean>() {
        @NonNull
        @Override
        public TrojanBean newInstance() {
            return new TrojanBean();
        }

        @Override
        public TrojanBean[] newArray(int size) {
            return new TrojanBean[size];
        }
    };
    public String password;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (security == null || security.isEmpty()) security = "tls";
        if (password == null) password = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(password);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input); // StandardV2RayBean
        password = input.readString();
    }

    @NotNull
    @Override
    public TrojanBean clone() {
        return KryoConverters.deserialize(new TrojanBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() throws Throwable {
        return SingBoxOptions.TYPE_TROJAN;
    }
}

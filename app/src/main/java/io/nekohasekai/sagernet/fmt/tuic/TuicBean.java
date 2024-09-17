package io.nekohasekai.sagernet.fmt.tuic;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import moe.matsuri.nb4a.SingBoxOptions;

import org.jetbrains.annotations.NotNull;

public class TuicBean extends AbstractBean {

    public static final Creator<TuicBean> CREATOR = new CREATOR<TuicBean>() {
        @NonNull
        @Override
        public TuicBean newInstance() {
            return new TuicBean();
        }

        @Override
        public TuicBean[] newArray(int size) {
            return new TuicBean[size];
        }
    };
    public String token;
    public String caText;
    public String udpRelayMode;
    public String congestionController;
    public String alpn;
    public Boolean disableSNI;
    public Boolean reduceRTT;
    public Integer mtu;

    // TUIC zep
    public String sni;

    public Boolean allowInsecure;
    public String customJSON;
    public String uuid;

    // ECH
    public Boolean ech;
    public String echCfg;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (token == null) token = "";
        if (caText == null) caText = "";
        if (udpRelayMode == null) udpRelayMode = "native";
        if (congestionController == null) congestionController = "cubic";
        if (alpn == null) alpn = "";
        if (disableSNI == null) disableSNI = false;
        if (reduceRTT == null) reduceRTT = false;
        if (mtu == null) mtu = 1400;
        if (sni == null) sni = "";
        if (allowInsecure == null) allowInsecure = false;
        if (customJSON == null) customJSON = "";
        if (uuid == null) uuid = "";
        if (ech == null) ech = false;
        if (echCfg == null) echCfg = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(token);
        output.writeString(caText);
        output.writeString(udpRelayMode);
        output.writeString(congestionController);
        output.writeString(alpn);
        output.writeBoolean(disableSNI);
        output.writeBoolean(reduceRTT);
        output.writeInt(mtu);
        output.writeString(sni);
        output.writeBoolean(allowInsecure);
        output.writeString(customJSON);
        output.writeString(uuid);
        output.writeBoolean(ech);
        output.writeString(echCfg);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        token = input.readString();
        caText = input.readString();
        udpRelayMode = input.readString();
        congestionController = input.readString();
        alpn = input.readString();
        disableSNI = input.readBoolean();
        reduceRTT = input.readBoolean();
        mtu = input.readInt();
        sni = input.readString();
        allowInsecure = input.readBoolean();
        customJSON = input.readString();
        uuid = input.readString();

        ech = input.readBoolean();
        echCfg = input.readString();
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof TuicBean)) return;
        TuicBean bean = ((TuicBean) other);
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @NotNull
    @Override
    public TuicBean clone() {
        return KryoConverters.deserialize(new TuicBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() throws Throwable {
        return SingBoxOptions.TYPE_TUIC;
    }
}

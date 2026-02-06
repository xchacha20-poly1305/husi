package io.nekohasekai.sagernet.fmt.shadowquic;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class ShadowQUICBean extends AbstractBean {
    public static final Creator<ShadowQUICBean> CREATOR = new CREATOR<ShadowQUICBean>() {
        @NonNull
        @Override
        public ShadowQUICBean newInstance() {
            return new ShadowQUICBean();
        }

        @Override
        public ShadowQUICBean[] newArray(int size) {
            return new ShadowQUICBean[size];
        }
    };

    public String username; // JLS IV
    public String password; // JLS password
    public String sni;
    public String alpn;
    public Integer initialMTU;
    public Integer minimumMTU;
    public String congestionControl;
    public Boolean zeroRTT;
    public Boolean udpOverStream;
    public Boolean gso;
    public Integer subProtocol;

    public static final int SUB_PROTOCOL_SHADOW_QUIC = 0;
    public static final int SUB_PROTOCOL_SUNNY_QUIC = 1;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (password == null) password = "";
        if (username == null) username = "";
        if (sni == null) sni = "";
        if (alpn == null) alpn = "h3";
        if (initialMTU == null) initialMTU = 1300;
        if (minimumMTU == null) minimumMTU = 1290;
        if (congestionControl == null) congestionControl = "bbr";
        if (zeroRTT == null) zeroRTT = false;
        if (udpOverStream == null) udpOverStream = false;
        if (gso == null) gso = false;
        if (subProtocol == null) subProtocol = SUB_PROTOCOL_SHADOW_QUIC;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(password);
        output.writeString(username);
        output.writeString(sni);
        output.writeString(alpn);
        output.writeInt(initialMTU);
        output.writeInt(minimumMTU);
        output.writeString(congestionControl);
        output.writeBoolean(zeroRTT);
        output.writeBoolean(udpOverStream);
        output.writeBoolean(gso);
        output.writeInt(subProtocol);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        password = input.readString();
        username = input.readString();
        sni = input.readString();
        alpn = input.readString();
        initialMTU = input.readInt();
        minimumMTU = input.readInt();
        congestionControl = input.readString();
        zeroRTT = input.readBoolean();
        udpOverStream = input.readBoolean();

        if (version >= 1) {
            gso = input.readBoolean();
            subProtocol = input.readInt();
        }
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof ShadowQUICBean)) return;
        ShadowQUICBean bean = (ShadowQUICBean) other;
        bean.password = password;
        bean.username = username;
        bean.sni = sni;
        bean.alpn = alpn;
        bean.initialMTU = initialMTU;
        bean.minimumMTU = minimumMTU;
        bean.congestionControl = congestionControl;
        bean.zeroRTT = zeroRTT;
        bean.udpOverStream = udpOverStream;
    }

    @Override
    public @NotNull AbstractBean clone() {
        return KryoConverters.deserialize(new ShadowQUICBean(), KryoConverters.serialize(this));
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    public String displayType() {
        if (subProtocol == SUB_PROTOCOL_SHADOW_QUIC) {
            return "ShadowQUIC";
        } else {
            return "SunnyQUIC";
        }
    }
}
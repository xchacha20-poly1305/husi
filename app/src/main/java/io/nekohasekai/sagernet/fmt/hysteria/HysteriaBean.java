package io.nekohasekai.sagernet.fmt.hysteria;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.ktx.NetsKt;
import org.jetbrains.annotations.NotNull;

public class HysteriaBean extends AbstractBean {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_STRING = 1;

    public static final int TYPE_BASE64 = 2;
    public static final int PROTOCOL_UDP = 0;

    public static final int PROTOCOL_FAKETCP = 1;
    public static final int PROTOCOL_WECHAT_VIDEO = 2;
    public static final Creator<HysteriaBean> CREATOR = new CREATOR<HysteriaBean>() {
        @NonNull
        @Override
        public HysteriaBean newInstance() {
            return new HysteriaBean();
        }

        @Override
        public HysteriaBean[] newArray(int size) {
            return new HysteriaBean[size];
        }
    };
    public Integer protocolVersion;
    // Use serverPorts instead of serverPort
    public String serverPorts;
    public Boolean ech;
    public String echCfg;
    public String authPayload;
    public String obfuscation;
    public String sni;
    public String caText;

    // HY1
    public Boolean allowInsecure;
    public Integer streamReceiveWindow;
    public Integer connectionReceiveWindow;
    public Boolean disableMtuDiscovery;
    public Integer hopInterval;
    public String alpn;
    public Integer authPayloadType;
    public Integer protocol;

    @Override
    public boolean canMapping() {
        return protocol != PROTOCOL_FAKETCP;
    }

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (protocolVersion == null) protocolVersion = 2;

        if (authPayloadType == null) authPayloadType = TYPE_NONE;
        if (authPayload == null) authPayload = "";
        if (protocol == null) protocol = PROTOCOL_UDP;
        if (obfuscation == null) obfuscation = "";
        if (sni == null) sni = "";
        if (alpn == null) alpn = "";
        if (caText == null) caText = "";
        if (allowInsecure == null) allowInsecure = false;


        if (streamReceiveWindow == null) streamReceiveWindow = 0;
        if (connectionReceiveWindow == null) connectionReceiveWindow = 0;
        if (disableMtuDiscovery == null) disableMtuDiscovery = false;
        if (hopInterval == null) hopInterval = 10;
        if (serverPorts == null) serverPorts = "443";

        if (ech == null) ech = false;
        if (echCfg == null) echCfg = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);

        output.writeInt(protocolVersion);

        output.writeInt(authPayloadType);
        output.writeString(authPayload);
        output.writeInt(protocol);
        output.writeString(obfuscation);
        output.writeString(sni);
        output.writeString(alpn);

        output.writeBoolean(allowInsecure);

        output.writeString(caText);
        output.writeInt(streamReceiveWindow);
        output.writeInt(connectionReceiveWindow);
        output.writeBoolean(disableMtuDiscovery);
        output.writeInt(hopInterval);
        output.writeString(serverPorts);

        output.writeBoolean(ech);
        output.writeString(echCfg);

    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        protocolVersion = input.readInt();

        authPayloadType = input.readInt();
        authPayload = input.readString();
        protocol = input.readInt();
        obfuscation = input.readString();
        sni = input.readString();
        alpn = input.readString();
        allowInsecure = input.readBoolean();
        caText = input.readString();
        streamReceiveWindow = input.readInt();
        connectionReceiveWindow = input.readInt();
        disableMtuDiscovery = input.readBoolean(); // note: skip 4
        hopInterval = input.readInt();
        serverPorts = input.readString();


        ech = input.readBoolean();
        echCfg = input.readString();
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof HysteriaBean)) return;
        HysteriaBean bean = ((HysteriaBean) other);
        bean.allowInsecure = allowInsecure;
        bean.disableMtuDiscovery = disableMtuDiscovery;
        bean.hopInterval = hopInterval;
        bean.ech = ech;
        bean.echCfg = echCfg;
    }

    @Override
    public boolean canTCPing() {
        return switch (protocolVersion) {
            case 1 -> protocol == PROTOCOL_FAKETCP;
            default -> false;
        };
    }

    @NotNull
    @Override
    public HysteriaBean clone() {
        return KryoConverters.deserialize(new HysteriaBean(), KryoConverters.serialize(this));
    }

}

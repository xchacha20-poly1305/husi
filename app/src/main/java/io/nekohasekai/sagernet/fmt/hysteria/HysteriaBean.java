package io.nekohasekai.sagernet.fmt.hysteria;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.ktx.NetsKt;
import io.nekohasekai.sagernet.fmt.SingBoxOptions;

import org.jetbrains.annotations.NotNull;

public class HysteriaBean extends AbstractBean {
    public static final int PROTOCOL_VERSION_1 = 1;
    public static final int PROTOCOL_VERSION_2 = 2;

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
    public String echConfig;
    public String authPayload;
    public String obfuscation;
    public String sni;
    public String certificates;
    public Boolean disableSNI;

    // HY1
    public Boolean allowInsecure;
    public Integer streamReceiveWindow;
    public Integer connectionReceiveWindow;
    public Boolean disableMtuDiscovery;
    // Since serialize version 1, hopInterval change to string.
    public String hopInterval;
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
        if (protocolVersion == null) protocolVersion = PROTOCOL_VERSION_2;

        if (authPayloadType == null) authPayloadType = TYPE_NONE;
        if (authPayload == null) authPayload = "";
        if (protocol == null) protocol = PROTOCOL_UDP;
        if (obfuscation == null) obfuscation = "";
        if (sni == null) sni = "";
        if (alpn == null) alpn = "";
        if (certificates == null) certificates = "";
        if (allowInsecure == null) allowInsecure = false;
        if (disableSNI == null) disableSNI = false;

        if (streamReceiveWindow == null) streamReceiveWindow = 0;
        if (connectionReceiveWindow == null) connectionReceiveWindow = 0;
        if (disableMtuDiscovery == null) disableMtuDiscovery = false;
        if (hopInterval == null) hopInterval = "10s";
        if (serverPorts == null) serverPorts = "443";

        if (ech == null) ech = false;
        if (echConfig == null) echConfig = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(2);
        super.serialize(output);

        output.writeInt(protocolVersion);

        output.writeInt(authPayloadType);
        output.writeString(authPayload);
        output.writeInt(protocol);
        output.writeString(obfuscation);
        output.writeString(sni);
        output.writeString(alpn);

        output.writeBoolean(allowInsecure);

        output.writeString(certificates);
        output.writeInt(streamReceiveWindow);
        output.writeInt(connectionReceiveWindow);
        output.writeBoolean(disableMtuDiscovery);
        output.writeString(hopInterval);
        output.writeString(serverPorts);

        output.writeBoolean(ech);
        output.writeString(echConfig);

        output.writeBoolean(disableSNI);
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
        certificates = input.readString();
        streamReceiveWindow = input.readInt();
        connectionReceiveWindow = input.readInt();
        disableMtuDiscovery = input.readBoolean();
        if (version < 1) {
            hopInterval = input.readInt() + "s";
        } else {
            hopInterval = input.readString();
        }
        serverPorts = input.readString();


        ech = input.readBoolean();
        echConfig = input.readString();

        if (version >= 2) {
            disableSNI = input.readBoolean();
        }
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof HysteriaBean)) return;
        HysteriaBean bean = ((HysteriaBean) other);
        bean.allowInsecure = allowInsecure;
        bean.disableSNI = disableSNI;
        bean.disableMtuDiscovery = disableMtuDiscovery;
        bean.hopInterval = hopInterval;
        bean.ech = ech;
        bean.echConfig = echConfig;
    }

    @Override
    public boolean canTCPing() {
        return switch (protocolVersion) {
            case PROTOCOL_VERSION_1 -> protocol == PROTOCOL_FAKETCP;
            default -> false;
        };
    }

    @Override
    public String displayAddress() {
        return NetsKt.wrapIPV6Host(serverAddress) + ":" + serverPorts;
    }

    @NotNull
    @Override
    public HysteriaBean clone() {
        return KryoConverters.deserialize(new HysteriaBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() throws Throwable {
        return switch (protocolVersion) {
            case PROTOCOL_VERSION_1 -> SingBoxOptions.TYPE_HYSTERIA;
            case PROTOCOL_VERSION_2 -> SingBoxOptions.TYPE_HYSTERIA2;
            default -> throw unknownVersion();
        };
    }

    Throwable unknownVersion() {
        return new IllegalArgumentException("Unknown version: " + protocolVersion.toString());
    }

}

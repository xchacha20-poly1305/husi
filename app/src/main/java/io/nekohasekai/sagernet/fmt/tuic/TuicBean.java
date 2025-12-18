package io.nekohasekai.sagernet.fmt.tuic;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.SingBoxOptions;

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
    public String certificates;
    public String certPublicKeySha256;
    public String udpRelayMode;
    public String congestionController;
    public String alpn;
    public Boolean disableSNI;
    public Boolean zeroRTT;
    public Integer mtu;

    // TUIC zep
    public String sni;

    public Boolean allowInsecure;
    public String customJSON;
    public String uuid;

    // ECH
    public Boolean ech;
    public String echConfig;
    public String echQueryServerName;

    // mTLS
    public String clientCert;
    public String clientKey;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (token == null) token = "";
        if (certificates == null) certificates = "";
        if (certPublicKeySha256 == null) certPublicKeySha256 = "";
        if (udpRelayMode == null) udpRelayMode = "native";
        if (congestionController == null) congestionController = "cubic";
        if (alpn == null) alpn = "";
        if (disableSNI == null) disableSNI = false;
        if (zeroRTT == null) zeroRTT = false;
        if (mtu == null) mtu = 1400;
        if (sni == null) sni = "";
        if (allowInsecure == null) allowInsecure = false;
        if (customJSON == null) customJSON = "";
        if (uuid == null) uuid = "";
        if (ech == null) ech = false;
        if (echConfig == null) echConfig = "";
        if (echQueryServerName == null) echQueryServerName = "";
        if (clientCert == null) clientCert = "";
        if (clientKey == null) clientKey = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(3);

        // version 0
        super.serialize(output);
        output.writeString(token);
        output.writeString(certificates);
        output.writeString(udpRelayMode);
        output.writeString(congestionController);
        output.writeString(alpn);
        output.writeBoolean(disableSNI);
        output.writeBoolean(zeroRTT);
        output.writeInt(mtu);
        output.writeString(sni);
        output.writeBoolean(allowInsecure);
        output.writeString(customJSON);
        output.writeString(uuid);
        output.writeBoolean(ech);
        output.writeString(echConfig);

        // version 1
        output.writeString(certPublicKeySha256);

        // version 2
        output.writeString(clientCert);
        output.writeString(clientKey);

        // version 3
        output.writeString(echQueryServerName);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        token = input.readString();
        certificates = input.readString();
        udpRelayMode = input.readString();
        congestionController = input.readString();
        alpn = input.readString();
        disableSNI = input.readBoolean();
        zeroRTT = input.readBoolean();
        mtu = input.readInt();
        sni = input.readString();
        allowInsecure = input.readBoolean();
        customJSON = input.readString();
        uuid = input.readString();

        ech = input.readBoolean();
        echConfig = input.readString();

        if (version >= 1) {
            certPublicKeySha256 = input.readString();
        }

        if (version >= 2) {
            clientCert = input.readString();
            clientKey = input.readString();
        }

        if (version >= 3) {
            echQueryServerName = input.readString();
        }
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

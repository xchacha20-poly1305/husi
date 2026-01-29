package io.nekohasekai.sagernet.fmt.trusttunnel;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.SingBoxOptions;

public class TrustTunnelBean extends AbstractBean {
    public static final Creator<TrustTunnelBean> CREATOR = new CREATOR<TrustTunnelBean>() {
        @NonNull
        @Override
        public TrustTunnelBean newInstance() {
            return new TrustTunnelBean();
        }

        @Override
        public TrustTunnelBean[] newArray(int size) {
            return new TrustTunnelBean[size];
        }
    };

    public String username;
    public String password;
    public Boolean healthCheck;
    public Boolean quic;
    public String quicCongestionControl;

    public String serverName;
    public String alpn;
    public String certificates;
    public String certPublicKeySha256;
    public String utlsFingerprint;
    public Boolean allowInsecure;
    public Boolean tlsFragment;
    public String tlsFragmentFallbackDelay;
    public Boolean tlsRecordFragment;
    public Boolean ech;
    public String echConfig;
    public String echQueryServerName;
    public String clientCert;
    public String clientKey;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (username == null) username = "";
        if (password == null) password = "";
        if (healthCheck == null) healthCheck = false;
        if (quic == null) quic = false;
        if (quicCongestionControl == null) quicCongestionControl = "bbr";
        if (serverName == null) serverName = "";
        if (alpn == null) alpn = "";
        if (certificates == null) certificates = "";
        if (certPublicKeySha256 == null) certPublicKeySha256 = "";
        if (utlsFingerprint == null) utlsFingerprint = "";
        if (allowInsecure == null) allowInsecure = false;
        if (tlsFragment == null) tlsFragment = false;
        if (tlsFragmentFallbackDelay == null) tlsFragmentFallbackDelay = "0s";
        if (tlsRecordFragment == null) tlsRecordFragment = false;
        if (ech == null) ech = false;
        if (echConfig == null) echConfig = "";
        if (echQueryServerName == null) echQueryServerName = "";
        if (clientCert == null) clientCert = "";
        if (clientKey == null) clientKey = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(username);
        output.writeString(password);
        output.writeBoolean(healthCheck);
        output.writeBoolean(quic);
        output.writeString(quicCongestionControl);
        output.writeString(serverName);
        output.writeString(alpn);
        output.writeString(certificates);
        output.writeString(certPublicKeySha256);
        output.writeString(utlsFingerprint);
        output.writeBoolean(allowInsecure);
        output.writeBoolean(tlsFragment);
        output.writeString(tlsFragmentFallbackDelay);
        output.writeBoolean(tlsRecordFragment);
        output.writeBoolean(ech);
        output.writeString(echConfig);
        output.writeString(echQueryServerName);
        output.writeString(clientCert);
        output.writeString(clientKey);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        username = input.readString();
        password = input.readString();
        healthCheck = input.readBoolean();
        quic = input.readBoolean();
        quicCongestionControl = input.readString();
        serverName = input.readString();
        alpn = input.readString();
        certificates = input.readString();
        certPublicKeySha256 = input.readString();
        utlsFingerprint = input.readString();
        allowInsecure = input.readBoolean();
        tlsFragment = input.readBoolean();
        tlsFragmentFallbackDelay = input.readString();
        tlsRecordFragment = input.readBoolean();
        ech = input.readBoolean();
        echConfig = input.readString();
        echQueryServerName = input.readString();
        clientCert = input.readString();
        clientKey = input.readString();
    }

    @NotNull
    @Override
    public TrustTunnelBean clone() {
        return KryoConverters.deserialize(new TrustTunnelBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() throws Exception {
        return SingBoxOptions.TYPE_TRUST_TUNNEL;
    }
}

package io.nekohasekai.sagernet.fmt.anytls;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.SingBoxOptions;

public class AnyTLSBean extends AbstractBean {

    public static final Creator<AnyTLSBean> CREATOR = new CREATOR<AnyTLSBean>() {
        @NonNull
        @Override
        public AnyTLSBean newInstance() {
            return new AnyTLSBean();
        }

        @Override
        public AnyTLSBean[] newArray(int size) {
            return new AnyTLSBean[size];
        }
    };
    public String password;
    public String idleSessionCheckInterval;
    public String idleSessionTimeout;
    public Integer minIdleSession;
    public String serverName;
    public String alpn;
    public String certificates;
    public String certPublicKeySha256;
    public String utlsFingerprint;
    public Boolean allowInsecure;
    public Boolean disableSNI;
    public Boolean tlsFragment;
    public String tlsFragmentFallbackDelay;
    public Boolean tlsRecordFragment;
    // In sing-box, this seemed can be used with REALITY.
    // But even mihomo appended many options, it still not provide REALITY.
    // Their developer said that unless the original author allow, they will never add it. We follow it.
    // https://github.com/anytls/anytls-go/blob/4636d90462fa21a510420512d7706a9acf69c7b9/docs/faq.md?plain=1#L25-L37

    public Boolean ech;
    public String echConfig;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (password == null) password = "";
        if (idleSessionCheckInterval == null) idleSessionCheckInterval = "30s";
        if (idleSessionTimeout == null) idleSessionTimeout = "30s";
        if (minIdleSession == null) minIdleSession = 0;
        if (serverName == null) serverName = "";
        if (alpn == null) alpn = "";
        if (certificates == null) certificates = "";
        if (certPublicKeySha256 == null) certPublicKeySha256 = "";
        if (utlsFingerprint == null) utlsFingerprint = "";
        if (allowInsecure == null) allowInsecure = false;
        if (disableSNI == null) disableSNI = false;
        if (tlsFragment == null) tlsFragment = false;
        if (tlsFragmentFallbackDelay == null) tlsFragmentFallbackDelay = "500ms";
        if (tlsRecordFragment == null) tlsRecordFragment = false;
        if (ech == null) ech = false;
        if (echConfig == null) echConfig = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(5);

        // version 0
        super.serialize(output);
        output.writeString(password);
        output.writeString(serverName);
        output.writeString(alpn);
        output.writeString(certificates);
        output.writeString(utlsFingerprint);
        output.writeBoolean(allowInsecure);
        output.writeString(echConfig);

        // version 1
        output.writeString(idleSessionCheckInterval);
        output.writeString(idleSessionTimeout);
        output.writeInt(minIdleSession);

        // version 2
        output.writeBoolean(ech);

        // version 3
        output.writeBoolean(tlsFragment);
        output.writeString(tlsFragmentFallbackDelay);
        output.writeBoolean(tlsRecordFragment);

        // version 4
        output.writeBoolean(disableSNI);

        // version 5
        output.writeString(certPublicKeySha256);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        password = input.readString();
        serverName = input.readString();
        alpn = input.readString();
        certificates = input.readString();
        utlsFingerprint = input.readString();
        allowInsecure = input.readBoolean();
        echConfig = input.readString();

        if (version >= 1) {
            idleSessionCheckInterval = input.readString();
            idleSessionTimeout = input.readString();
            minIdleSession = input.readInt();
        }

        if (version >= 2) {
            ech = input.readBoolean();
        }

        if (version >= 3) {
            tlsFragment = input.readBoolean();
            tlsFragmentFallbackDelay = input.readString();
            tlsRecordFragment = input.readBoolean();
        }

        if (version >= 4) {
            disableSNI = input.readBoolean();
        }

        if (version >= 5) {
            certPublicKeySha256 = input.readString();
        }
    }

    @NotNull
    @Override
    public AnyTLSBean clone() {
        return KryoConverters.deserialize(new AnyTLSBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() {
        return SingBoxOptions.TYPE_ANYTLS;
    }
}

package io.nekohasekai.sagernet.fmt.v2ray;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.fmt.AbstractBean;

public abstract class StandardV2RayBean extends AbstractBean {

    public String uuid;
    public String encryption;

    /// ///// End of VMess & VLESS ////////

    // "V2Ray Transport" tcp/http/ws/quic/grpc/httpupgrade
    public String v2rayTransport;

    public String host;

    public String path;

    public String headers;

    // --------------------------------------- tls?

    public String security;

    public String sni;

    public String alpn;

    public String utlsFingerprint;

    public Boolean allowInsecure;

    public Boolean disableSNI;

    public Boolean fragment;

    public String fragmentFallbackDelay;

    public Boolean recordFragment;

    public String certificates;

    public String certPublicKeySha256;

    public String clientCert;

    public String clientKey;

    // --------------------------------------- reality


    public String realityPublicKey;

    public String realityShortID;

    // --------------------------------------- ECH

    public Boolean ech;

    public String echConfig;

    public String echQueryServerName;


    // --------------------------------------- //

    public Integer wsMaxEarlyData;
    public String earlyDataHeaderName;

    // --------------------------------------- //

    public Integer packetEncoding; // 1:packetaddr 2:xudp

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (uuid == null) uuid = "";

        if (v2rayTransport == null) v2rayTransport = "";
        else if ("tcp".equals(v2rayTransport)) v2rayTransport = "";
        else if ("h2".equals(v2rayTransport)) v2rayTransport = "http";

        v2rayTransport = v2rayTransport.toLowerCase();

        if (host == null) host = "";
        if (path == null) path = "";
        if (headers == null) headers = "";

        if (security == null) security = "";
        if (sni == null) sni = "";
        if (alpn == null) alpn = "";

        if (certificates == null) certificates = "";
        if (certPublicKeySha256 == null) certPublicKeySha256 = "";
        if (clientCert == null) clientCert = "";
        if (clientKey == null) clientKey = "";
        if (earlyDataHeaderName == null) earlyDataHeaderName = "";
        if (utlsFingerprint == null) utlsFingerprint = "";

        if (fragment == null) fragment = false;
        if (fragmentFallbackDelay == null) fragmentFallbackDelay = "500ms";
        if (recordFragment == null) recordFragment = false;

        if (wsMaxEarlyData == null) wsMaxEarlyData = 0;
        if (allowInsecure == null) allowInsecure = false;
        if (disableSNI == null) disableSNI = false;
        if (packetEncoding == null) packetEncoding = 0;

        if (realityPublicKey == null) realityPublicKey = "";
        if (realityShortID == null) realityShortID = "";

        if (ech == null) ech = false;
        if (echConfig == null) echConfig = "";
        if (echQueryServerName == null) echQueryServerName = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(11);
        super.serialize(output);

        output.writeString(uuid);
        output.writeString(encryption);
        switch (this) {
            case VMessBean ignored -> output.writeInt(((VMessBean) this).alterId);
            case VLESSBean ignored -> output.writeString(((VLESSBean) this).flow);
            default -> {}
        }

        output.writeString(v2rayTransport);
        switch (v2rayTransport) {
            case "", "tcp":
            case "quic": {
                break;
            }
            case "ws": {
                output.writeString(host);
                output.writeString(path);
                output.writeInt(wsMaxEarlyData);
                output.writeString(earlyDataHeaderName);
                output.writeString(headers);
                break;
            }
            case "http": {
                output.writeString(host);
                output.writeString(path);
                output.writeString(headers);
                break;
            }
            case "grpc": {
                output.writeString(path);
            }
            case "httpupgrade": {
                output.writeString(host);
                output.writeString(path);
                output.writeString(headers);
            }
        }

        output.writeString(security);
        if ("tls".equals(security)) {
            output.writeString(sni);
            output.writeString(alpn);
            output.writeString(certificates);
            output.writeBoolean(allowInsecure);
            output.writeString(utlsFingerprint);
            output.writeString(realityPublicKey);
            output.writeString(realityShortID);
            output.writeBoolean(ech);
            output.writeString(echConfig);
            output.writeBoolean(fragment);
            output.writeString(fragmentFallbackDelay);
            output.writeBoolean(recordFragment);
            output.writeBoolean(disableSNI);

            output.writeString(certPublicKeySha256);

            output.writeString(clientCert);
            output.writeString(clientKey);

            output.writeString(echQueryServerName);
        }

        output.writeInt(packetEncoding);

        if (this instanceof VMessBean) {
            output.writeBoolean(((VMessBean) this).authenticatedLength);
        }
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        encryption = input.readString();
        if (this instanceof VMessBean) {
            ((VMessBean) this).alterId = input.readInt();
        }
        if (version >= 9 && this instanceof VLESSBean) {
            ((VLESSBean) this).flow = input.readString();
        }

        v2rayTransport = input.readString();
        switch (v2rayTransport) {
            case "":
            case "tcp":
                v2rayTransport = "";
            case "quic": {
                break;
            }
            case "ws": {
                host = input.readString();
                path = input.readString();
                wsMaxEarlyData = input.readInt();
                earlyDataHeaderName = input.readString();
                if (version >= 5) headers = input.readString();
                break;
            }
            case "http": {
                host = input.readString();
                path = input.readString();
                if (version >= 5) headers = input.readString();
                break;
            }
            case "grpc": {
                path = input.readString();
            }
            case "httpupgrade": {
                host = input.readString();
                path = input.readString();
                if (version >= 5) headers = input.readString();
            }
        }

        security = input.readString();
        if ("tls".equals(security)) {
            sni = input.readString();
            alpn = input.readString();
            certificates = input.readString();
            allowInsecure = input.readBoolean();
            utlsFingerprint = input.readString();
            // https://github.com/SagerNet/sing-box/commit/9f0d61a44011db196b5b302d96efc6376e211acf
            if (version < 4 && utlsFingerprint.startsWith("chrome")) {
                utlsFingerprint = "chrome";
            }
            realityPublicKey = input.readString();
            realityShortID = input.readString();
            ech = input.readBoolean();
            echConfig = input.readString();

            if (version >= 6) {
                fragment = input.readBoolean();
                fragmentFallbackDelay = input.readString();
                recordFragment = input.readBoolean();
            }

            if (version >= 7) {
                disableSNI = input.readBoolean();
            }

            if (version >= 8) {
                certPublicKeySha256 = input.readString();
            }

            if (version >= 10) {
                clientCert = input.readString();
                clientKey = input.readString();
            }

            if (version >= 11) {
                echQueryServerName = input.readString();
            }
        }

        packetEncoding = input.readInt();

        if (this instanceof VMessBean) {
            if (version >= 1) ((VMessBean) this).authenticatedLength = input.readBoolean();
        }

        if (version < 3) {
            int ignored = input.readInt();
            return;
        }
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof StandardV2RayBean)) return;
        StandardV2RayBean bean = ((StandardV2RayBean) other);
        bean.allowInsecure = allowInsecure;
        bean.disableSNI = disableSNI;
        bean.utlsFingerprint = utlsFingerprint;
        bean.packetEncoding = packetEncoding;
        bean.ech = ech;
        bean.echConfig = echConfig;
        bean.fragment = fragment;
        bean.fragmentFallbackDelay = fragmentFallbackDelay;
        bean.recordFragment = recordFragment;
    }

    @Override
    public boolean canTCPing() {
        return !v2rayTransport.equals("quic");
    }

}

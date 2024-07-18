package io.nekohasekai.sagernet.fmt.v2ray;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean;
import moe.matsuri.nb4a.utils.JavaUtil;

public abstract class StandardV2RayBean extends AbstractBean {

    public String uuid;
    public String encryption; // or VLESS flow

    //////// End of VMess & VLESS ////////

    // "V2Ray Transport" tcp/http/ws/quic/grpc/httpUpgrade
    public String type;

    public String host;

    public String path;

    // --------------------------------------- tls?

    public String security;

    public String sni;

    public String alpn;

    public String utlsFingerprint;

    public Boolean allowInsecure;

    // --------------------------------------- reality


    public String realityPubKey;

    public String realityShortId;

    // --------------------------------------- ECH

    public Boolean ech;

    public String echCfg;


    // --------------------------------------- //

    public Integer wsMaxEarlyData;
    public String earlyDataHeaderName;

    public String certificates;

    // --------------------------------------- //

    public Integer packetEncoding; // 1:packetaddr 2:xudp

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (JavaUtil.isNullOrBlank(uuid)) uuid = "";

        if (JavaUtil.isNullOrBlank(type)) type = "tcp";
        else if ("h2".equals(type)) type = "http";

        type = type.toLowerCase();

        if (JavaUtil.isNullOrBlank(host)) host = "";
        if (JavaUtil.isNullOrBlank(path)) path = "";

        if (JavaUtil.isNullOrBlank(security)) {
            if (this instanceof TrojanBean || isVLESS()) {
                security = "tls";
            } else {
                security = "none";
            }
        }
        if (JavaUtil.isNullOrBlank(sni)) sni = "";
        if (JavaUtil.isNullOrBlank(alpn)) alpn = "";

        if (JavaUtil.isNullOrBlank(certificates)) certificates = "";
        if (JavaUtil.isNullOrBlank(earlyDataHeaderName)) earlyDataHeaderName = "";
        if (JavaUtil.isNullOrBlank(utlsFingerprint)) utlsFingerprint = "";

        if (wsMaxEarlyData == null) wsMaxEarlyData = 0;
        if (allowInsecure == null) allowInsecure = false;
        if (packetEncoding == null) packetEncoding = 0;

        if (realityPubKey == null) realityPubKey = "";
        if (realityShortId == null) realityShortId = "";

        if (ech == null) ech = false;
        if (echCfg == null) echCfg = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(3);
        super.serialize(output);

        output.writeString(uuid);
        output.writeString(encryption);
        if (this instanceof VMessBean) {
            output.writeInt(((VMessBean) this).alterId);
        }

        output.writeString(type);
        switch (type) {
            case "tcp":
            case "quic": {
                break;
            }
            case "ws": {
                output.writeString(host);
                output.writeString(path);
                output.writeInt(wsMaxEarlyData);
                output.writeString(earlyDataHeaderName);
                break;
            }
            case "http": {
                output.writeString(host);
                output.writeString(path);
                break;
            }
            case "grpc": {
                output.writeString(path);
            }
            case "httpupgrade": {
                output.writeString(host);
                output.writeString(path);

            }
        }

        output.writeString(security);
        if ("tls".equals(security)) {
            output.writeString(sni);
            output.writeString(alpn);
            output.writeString(certificates);
            output.writeBoolean(allowInsecure);
            output.writeString(utlsFingerprint);
            output.writeString(realityPubKey);
            output.writeString(realityShortId);
            output.writeBoolean(ech);
            output.writeString(echCfg);
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

        type = input.readString();
        switch (type) {
            case "tcp":
            case "quic": {
                break;
            }
            case "ws": {
                host = input.readString();
                path = input.readString();
                wsMaxEarlyData = input.readInt();
                earlyDataHeaderName = input.readString();
                break;
            }
            case "http": {
                host = input.readString();
                path = input.readString();
                break;
            }
            case "grpc": {
                path = input.readString();
            }
            case "httpupgrade": {
                host = input.readString();
                path = input.readString();
            }
        }

        security = input.readString();
        if ("tls".equals(security)) {
            sni = input.readString();
            alpn = input.readString();
            certificates = input.readString();
            allowInsecure = input.readBoolean();
            utlsFingerprint = input.readString();
            realityPubKey = input.readString();
            realityShortId = input.readString();
            ech = input.readBoolean();
            echCfg = input.readString();
        }

        packetEncoding = input.readInt();

        if (this instanceof VMessBean) {
            if (version >= 1) ((VMessBean) this).authenticatedLength = input.readBoolean();
        }

        if (version < 2) {
            int ignored = input.readInt();
            return;
        }
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof StandardV2RayBean)) return;
        StandardV2RayBean bean = ((StandardV2RayBean) other);
        bean.allowInsecure = allowInsecure;
        bean.utlsFingerprint = utlsFingerprint;
        bean.packetEncoding = packetEncoding;
        bean.ech = ech;
        bean.echCfg = echCfg;
    }

    public boolean isVLESS() {
        if (this instanceof VMessBean) {
            Integer aid = ((VMessBean) this).alterId;
            return aid != null && aid == -1;
        }
        return false;
    }

    @Override
    public boolean canTCPing() {
        return !type.equals("quic");
    }

    @Override
    public boolean canBrutal() {
        return switch (type) {
            case "quic", "grpc" -> false;
            // If using TLS, http will upgrade to http2, which not supports mux.
            case "http" -> !security.equals("tls");
            default -> true;
        };
    }

}

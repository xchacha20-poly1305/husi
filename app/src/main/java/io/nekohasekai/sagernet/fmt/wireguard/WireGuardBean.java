package io.nekohasekai.sagernet.fmt.wireguard;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import moe.matsuri.nb4a.SingBoxOptions;

import org.jetbrains.annotations.NotNull;

public class WireGuardBean extends AbstractBean {

    public static final Creator<WireGuardBean> CREATOR = new CREATOR<WireGuardBean>() {
        @NonNull
        @Override
        public WireGuardBean newInstance() {
            return new WireGuardBean();
        }

        @Override
        public WireGuardBean[] newArray(int size) {
            return new WireGuardBean[size];
        }
    };
    public String localAddress;
    public String privateKey;
    public String publicKey;
    public String preSharedKey;
    public Integer mtu;
    public String reserved;
    /**
     * Enable listen if it > 0
     */
    public Integer listenPort;
    public Integer persistentKeepaliveInterval;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (localAddress == null) localAddress = "";
        if (privateKey == null) privateKey = "";
        if (publicKey == null) publicKey = "";
        if (preSharedKey == null) preSharedKey = "";
        if (mtu == null || mtu < 1000 || mtu > 65535) mtu = 1420;
        if (reserved == null) reserved = "";
        if (listenPort == null) listenPort = 0;
        if (persistentKeepaliveInterval == null) persistentKeepaliveInterval = 0;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(2);
        super.serialize(output);
        output.writeString(localAddress);
        output.writeString(privateKey);
        output.writeString(publicKey);
        output.writeString(preSharedKey);
        output.writeInt(mtu);
        output.writeString(reserved);
        output.writeInt(listenPort);
        output.writeInt(persistentKeepaliveInterval);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        localAddress = input.readString();
        privateKey = input.readString();
        publicKey = input.readString();
        preSharedKey = input.readString();
        mtu = input.readInt();
        reserved = input.readString();
        if (version >= 1) {
            listenPort = input.readInt();
        }
        if (version >= 2) {
            persistentKeepaliveInterval = input.readInt();
        }
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @NotNull
    @Override
    public WireGuardBean clone() {
        return KryoConverters.deserialize(new WireGuardBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() throws Throwable {
        return SingBoxOptions.TYPE_WIREGUARD;
    }
}

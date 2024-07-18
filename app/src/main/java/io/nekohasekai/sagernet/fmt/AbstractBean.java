package io.nekohasekai.sagernet.fmt;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import io.nekohasekai.sagernet.ktx.NetsKt;
import moe.matsuri.nb4a.utils.JavaUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public abstract class AbstractBean extends Serializable {

    public String serverAddress;
    public Integer serverPort;
    public String name;
    public String customOutboundJson;
    public String customConfigJson;

    public Boolean serverMux;
    public Boolean serverBrutal;
    public Integer serverMuxType;
    public Integer serverMuxConcurrency;
    public Boolean serverMuxPadding;

    public transient String finalAddress;
    public transient int finalPort;
    private transient boolean serializeWithoutName;

    public String displayName() {
        if (JavaUtil.isNotBlank(name)) {
            return name;
        } else {
            return displayAddress();
        }
    }

    public String displayAddress() {
        return NetsKt.wrapIPV6Host(serverAddress) + ":" + serverPort;
    }

    public String network() {
        return "tcp,udp";
    }

    public boolean canBrutal() {
        return false;
    }

    public boolean canICMPing() {
        return true;
    }

    public boolean canTCPing() {
        return true;
    }

    public boolean canMapping() {
        return true;
    }

    @Override
    public void initializeDefaultValues() {
        if (JavaUtil.isNullOrBlank(serverAddress)) {
            serverAddress = "127.0.0.1";
        } else if (serverAddress.startsWith("[") && serverAddress.endsWith("]")) {
            serverAddress = NetsKt.unwrapIPV6Host(serverAddress);
        }
        if (serverPort == null) {
            serverPort = 1080;
        }
        if (name == null) name = "";

        finalAddress = serverAddress;
        finalPort = serverPort;

        if (customOutboundJson == null) customOutboundJson = "";
        if (customConfigJson == null) customConfigJson = "";

        if (serverMux == null) serverMux = false;
        if (serverBrutal == null) serverBrutal = false;
        if (serverMuxType == null) serverMuxType = 0;
        if (serverMuxConcurrency == null) serverMuxConcurrency = 8;
        if (serverMuxPadding == null) serverMuxPadding = false;
    }

    @Override
    public void serializeToBuffer(@NonNull ByteBufferOutput output) {
        serialize(output);

        output.writeInt(3);
        if (!serializeWithoutName) {
            output.writeString(name);
        }
        output.writeString(customOutboundJson);
        output.writeString(customConfigJson);

        output.writeBoolean(serverBrutal);
        output.writeBoolean(serverMux);
        output.writeInt(serverMuxType);
        output.writeInt(serverMuxConcurrency);
        output.writeBoolean(serverMuxPadding);
    }

    @Override
    public void deserializeFromBuffer(@NonNull ByteBufferInput input) {
        deserialize(input);

        int extraVersion = input.readInt();

        name = input.readString();
        customOutboundJson = input.readString();
        customConfigJson = input.readString();

        if (extraVersion >= 2) serverBrutal = input.readBoolean();
        if (extraVersion < 3) return;
        serverMux = input.readBoolean();
        serverMuxType = input.readInt();
        serverMuxConcurrency = input.readInt();
        serverMuxPadding = input.readBoolean();
    }

    public void serialize(ByteBufferOutput output) {
        output.writeString(serverAddress);
        output.writeInt(serverPort);
    }

    public void deserialize(ByteBufferInput input) {
        serverAddress = input.readString();
        serverPort = input.readInt();
    }

    @NotNull
    @Override
    public abstract AbstractBean clone();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        try {
            serializeWithoutName = true;
            ((AbstractBean) o).serializeWithoutName = true;
            return Arrays.equals(KryoConverters.serialize(this), KryoConverters.serialize((AbstractBean) o));
        } finally {
            serializeWithoutName = false;
            ((AbstractBean) o).serializeWithoutName = false;
        }
    }

    @Override
    public int hashCode() {
        try {
            serializeWithoutName = true;
            return Arrays.hashCode(KryoConverters.serialize(this));
        } finally {
            serializeWithoutName = false;
        }
    }

    @NotNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + JavaUtil.gson.toJson(this);
    }

    public void applyFeatureSettings(AbstractBean other) {
    }

}

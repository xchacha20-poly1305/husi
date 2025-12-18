package io.nekohasekai.sagernet.fmt.naive;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.SingBoxOptions;

import org.jetbrains.annotations.NotNull;

public class NaiveBean extends AbstractBean {

    public static final Creator<NaiveBean> CREATOR = new CREATOR<NaiveBean>() {
        @NonNull
        @Override
        public NaiveBean newInstance() {
            return new NaiveBean();
        }

        @Override
        public NaiveBean[] newArray(int size) {
            return new NaiveBean[size];
        }
    };
    /**
     * Available proto: https, quic.
     */
    public String proto;
    public String username;
    public String password;
    public String extraHeaders;
    public String sni;
    public Integer insecureConcurrency;
    // sing-box socks + sing-box server
    public Boolean udpOverTcp;
    // https://github.com/klzgrad/naiveproxy/blob/76e7bbed0fdd349fb8a8890cd082e90072dab734/USAGE.txt#L110
    // https://tldr.fail/
    public Boolean noPostQuantum;

    public Boolean enableEch;
    public String echConfig;
    public String echQueryServerName;

    @Override
    public void initializeDefaultValues() {
        if (serverPort == null) serverPort = 443;
        super.initializeDefaultValues();
        if (proto == null) proto = "https";
        if (username == null) username = "";
        if (password == null) password = "";
        if (extraHeaders == null) extraHeaders = "";
        if (sni == null) sni = "";
        if (insecureConcurrency == null) insecureConcurrency = 0;
        if (udpOverTcp == null) udpOverTcp = false;
        if (noPostQuantum == null) noPostQuantum = false;
        if (enableEch == null) enableEch = false;
        if (echConfig == null) echConfig = "";
        if (echQueryServerName == null) echQueryServerName = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(2);
        super.serialize(output);

        // version 0
        output.writeString(proto);
        output.writeString(username);
        output.writeString(password);
        output.writeString(extraHeaders);
        output.writeString(sni);
        output.writeInt(insecureConcurrency);
        output.writeBoolean(udpOverTcp);

        // version 1
        output.writeBoolean(noPostQuantum);

        // version 2
        output.writeBoolean(enableEch);
        output.writeString(echConfig);
        output.writeString(echQueryServerName);
    }

    @Override
    public boolean canTCPing() {
        return !proto.equals("quic");
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        proto = input.readString();
        username = input.readString();
        password = input.readString();
        extraHeaders = input.readString();
        sni = input.readString();
        insecureConcurrency = input.readInt();
        udpOverTcp = input.readBoolean();

        if (version >= 1) {
            noPostQuantum = input.readBoolean();
        }

        if (version >= 2) {
            enableEch = input.readBoolean();
            echConfig = input.readString();
            echQueryServerName = input.readString();
        }
    }

    @NotNull
    @Override
    public NaiveBean clone() {
        return KryoConverters.deserialize(new NaiveBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() throws Throwable {
        return SingBoxOptions.TYPE_NAIVE;
    }

    @Override
    public boolean needUDPOverTCP() {
        return udpOverTcp;
    }
}

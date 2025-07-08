package io.nekohasekai.sagernet.fmt.shadowquic;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class ShadowQUICBean extends AbstractBean {
    public static final Creator<ShadowQUICBean> CREATOR = new CREATOR<ShadowQUICBean>() {
        @NonNull
        @Override
        public ShadowQUICBean newInstance() {
            return new ShadowQUICBean();
        }

        @Override
        public ShadowQUICBean[] newArray(int size) {
            return new ShadowQUICBean[size];
        }
    };

    public String jlsPassword;
    public String jlsIv;
    public String sni;
    public String alpn;
    public Integer initialMTU;
    public Integer minimumMTU;
    public String congestionControl;
    public Boolean zeroRTT;
    public Boolean udpOverStream;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (jlsPassword == null) jlsPassword = "";
        if (jlsIv == null) jlsIv = "";
        if (sni == null) sni = "";
        if (alpn == null) alpn = "h3";
        if (initialMTU == null) initialMTU = 1300;
        if (minimumMTU == null) minimumMTU = 1290;
        if (congestionControl == null) congestionControl = "bbr";
        if (zeroRTT == null) zeroRTT = false;
        if (udpOverStream == null) udpOverStream = false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(jlsPassword);
        output.writeString(jlsIv);
        output.writeString(sni);
        output.writeString(alpn);
        output.writeInt(initialMTU);
        output.writeInt(minimumMTU);
        output.writeString(congestionControl);
        output.writeBoolean(zeroRTT);
        output.writeBoolean(udpOverStream);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        jlsPassword = input.readString();
        jlsIv = input.readString();
        sni = input.readString();
        alpn = input.readString();
        initialMTU = input.readInt();
        minimumMTU = input.readInt();
        congestionControl = input.readString();
        zeroRTT = input.readBoolean();
        udpOverStream = input.readBoolean();
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof ShadowQUICBean)) return;
        ShadowQUICBean bean = (ShadowQUICBean) other;
        bean.jlsPassword = jlsPassword;
        bean.jlsIv = jlsIv;
        bean.sni = sni;
        bean.alpn = alpn;
        bean.initialMTU = initialMTU;
        bean.minimumMTU = minimumMTU;
        bean.congestionControl = congestionControl;
        bean.zeroRTT = zeroRTT;
        bean.udpOverStream = udpOverStream;
    }

    @Override
    public @NotNull AbstractBean clone() {
        return KryoConverters.deserialize(new ShadowQUICBean(), KryoConverters.serialize(this));
    }

    @Override
    public boolean canTCPing() {
        return false;
    }
}
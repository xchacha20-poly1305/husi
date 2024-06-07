package io.nekohasekai.sagernet.fmt.juicity;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.tuic.TuicBean;

public class JuicityBean extends AbstractBean {
    public static final Creator<JuicityBean> CREATOR = new CREATOR<JuicityBean>() {
        @NonNull
        @Override
        public JuicityBean newInstance() {
            return new JuicityBean();
        }

        @Override
        public JuicityBean[] newArray(int size) {
            return new JuicityBean[size];
        }
    };

    public String uuid;
    public String password;
    public String sni;
    public Boolean allowInsecure;
    // Just BBR???
    // https://github.com/daeuniverse/softwind/blob/6daa40f6b7a5cb9a0c44ea252e86fcb3440a7a0e/protocol/tuic/common/congestion.go#L15
    // public String congestionControl;
    public String pinSHA256;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (uuid == null) uuid = "";
        if (password == null) password = "";
        if (sni == null) sni = "";
        if (allowInsecure == null) allowInsecure = false;
        if (pinSHA256 == null) pinSHA256 = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(uuid);
        output.writeString(password);
        output.writeString(sni);
        output.writeBoolean(allowInsecure);
        output.writeString(pinSHA256);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        password = input.readString();
        sni = input.readString();
        allowInsecure = input.readBoolean();
        pinSHA256 = input.readString();
    }


    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof JuicityBean)) return;
        JuicityBean bean = ((JuicityBean) other);
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @NotNull
    @Override
    public AbstractBean clone() {
        return KryoConverters.deserialize(new TuicBean(), KryoConverters.serialize(this));
    }
}

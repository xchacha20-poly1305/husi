package io.nekohasekai.sagernet.fmt.direct;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.SingBoxOptions;

/**
 * @implNote {@link #serverAddress} & {@link #serverPort} as overrideAddress & overridePort
 */
public class DirectBean extends AbstractBean {
    public static final Creator<DirectBean> CREATOR = new CREATOR<DirectBean>() {
        @NonNull
        @Override
        public DirectBean newInstance() {
            return new DirectBean();
        }

        @Override
        public DirectBean[] newArray(int size) {
            return new DirectBean[size];
        }
    };

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
    }

    @NotNull
    @Override
    public DirectBean clone() {
        return KryoConverters.deserialize(new DirectBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() {
        return SingBoxOptions.TYPE_DIRECT;
    }

    @Override
    public String displayName() {
        if (name == null || name.isEmpty()) {
            return SingBoxOptions.TYPE_DIRECT;
        }
        return name;
    }

    @Override
    public String displayAddress() {
        return "0.0.0.0";
    }

    @Override
    public boolean canICMPing() {
        return false;
    }

    @Override
    public boolean canTCPing() {
        return false;
    }
}

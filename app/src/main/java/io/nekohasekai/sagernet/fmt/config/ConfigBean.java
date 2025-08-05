package io.nekohasekai.sagernet.fmt.config;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.internal.InternalBean;

/**
 * Custom config
 */
public class ConfigBean extends InternalBean {

    public static final Creator<ConfigBean> CREATOR = new CREATOR<ConfigBean>() {
        @NonNull
        @Override
        public ConfigBean newInstance() {
            return new ConfigBean();
        }

        @Override
        public ConfigBean[] newArray(int size) {
            return new ConfigBean[size];
        }
    };

    public static final int TYPE_CONFIG = 0;
    public static final int TYPE_OUTBOUND = 1;
    public Integer type;

    public String config;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (type == null) type = TYPE_CONFIG;
        if (config == null) config = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeInt(type);
        output.writeString(config);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        type = input.readInt();
        config = input.readString();
    }

    @Override
    public String displayName() {
        if (TextUtils.isEmpty(name)) {
            return "Custom " + Math.abs(hashCode());
        }
        return name;
    }

    public String displayType() {
        return type == TYPE_CONFIG ? "sing-box config" : "sing-box outbound";
    }

    @NotNull
    @Override
    public ConfigBean clone() {
        return KryoConverters.deserialize(new ConfigBean(), KryoConverters.serialize(this));
    }
}
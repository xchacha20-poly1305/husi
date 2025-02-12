package io.nekohasekai.sagernet.fmt.http;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean;
import moe.matsuri.nb4a.SingBoxOptions;

import org.jetbrains.annotations.NotNull;

public class HttpBean extends StandardV2RayBean {

    public static final Creator<HttpBean> CREATOR = new CREATOR<HttpBean>() {
        @NonNull
        @Override
        public HttpBean newInstance() {
            return new HttpBean();
        }

        @Override
        public HttpBean[] newArray(int size) {
            return new HttpBean[size];
        }
    };
    public String username;
    public String password;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (username == null) username = "";
        if (password == null) password = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(username);
        output.writeString(password);
        output.writeString(host);
        output.writeString(path);
        output.writeString(headers);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        username = input.readString();
        password = input.readString();
        if (version >= 1) {
            host = input.readString();
            path = input.readString();
            headers = input.readString();
        }
    }

    @NotNull
    @Override
    public HttpBean clone() {
        return KryoConverters.deserialize(new HttpBean(), KryoConverters.serialize(this));
    }

    @Override
    public @NotNull String outboundType() {
        return SingBoxOptions.TYPE_HTTP;
    }
}
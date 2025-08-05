/******************************************************************************Add commentMore actions
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.internal;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import java.util.ArrayList;
import java.util.List;

import io.nekohasekai.sagernet.ConstantsKt;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class ProxySetBean extends InternalBean {

    public static final int MANAGEMENT_SELECTOR = 0;
    public static final int MANAGEMENT_URLTEST = 1;
    public Integer management;

    public static final int TYPE_LIST = 0;
    public static final int TYPE_GROUP = 1;
    public Integer type;

    public List<Long> proxies;
    public Long groupId;
    public String groupFilterNotRegex;

    // Selector + URLTest
    public Boolean interruptExistConnections;

    // URLTest
    public String testURL;
    public String testInterval;
    public String testIdleTimeout;
    public Integer testTolerance;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (management == null) management = MANAGEMENT_SELECTOR;
        if (name == null) name = "";
        if (type == null) type = TYPE_LIST;
        if (proxies == null) proxies = new ArrayList<>();
        if (groupId == null) groupId = 0L;
        if (groupFilterNotRegex == null) groupFilterNotRegex = "";
        if (interruptExistConnections == null) interruptExistConnections = false;
        if (testURL == null) testURL = ConstantsKt.CONNECTION_TEST_URL;
        if (testInterval == null) testInterval = "3m";
        if (testIdleTimeout == null) testIdleTimeout = "3m";
        if (testTolerance == null) testTolerance = 50;
    }

    @Override
    public String displayName() {
        if (TextUtils.isEmpty(name)) {
            int hash = Math.abs(hashCode());
            return switch (management) {
                case MANAGEMENT_SELECTOR -> "Selector " + hash;
                case MANAGEMENT_URLTEST -> "URLTest " + hash;
                default -> "Unknown " + hash;
            };
        }
        return name;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        output.writeInt(management);
        output.writeBoolean(interruptExistConnections);
        output.writeString(testURL);
        output.writeString(testInterval);
        output.writeString(testIdleTimeout);
        output.writeInt(testTolerance);

        output.writeInt(type);
        switch (type) {
            case TYPE_LIST: {
                int length = proxies.size();
                output.writeInt(length);
                for (Long proxy : proxies) {
                    output.writeLong(proxy);
                }
                break;
            }
            case TYPE_GROUP: {
                output.writeLong(groupId);
                output.writeString(groupFilterNotRegex);
                break;
            }
        }
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        management = input.readInt();
        interruptExistConnections = input.readBoolean();
        testURL = input.readString();
        testInterval = input.readString();
        testIdleTimeout = input.readString();
        testTolerance = input.readInt();

        type = input.readInt();
        switch (type) {
            case TYPE_LIST: {
                int length = input.readInt();
                proxies = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    proxies.add(input.readLong());
                }
                break;
            }
            case TYPE_GROUP: {
                groupId = input.readLong();
                if (version >= 1) {
                    groupFilterNotRegex = input.readString();
                }
                break;
            }
        }
    }

    @NonNull
    @Override
    public ProxySetBean clone() {
        return KryoConverters.deserialize(new ProxySetBean(), KryoConverters.serialize(this));
    }

    public static final Creator<ProxySetBean> CREATOR = new CREATOR<ProxySetBean>() {
        @NonNull
        @Override
        public ProxySetBean newInstance() {
            return new ProxySetBean();
        }

        @Override
        public ProxySetBean[] newArray(int size) {
            return new ProxySetBean[size];
        }
    };

    public String displayType() {
        return switch (management) {
            case MANAGEMENT_SELECTOR -> "Selector";
            case MANAGEMENT_URLTEST -> "URLTest";
            default -> "Unknown";
        };
    }
}
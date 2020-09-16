/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.jvm.values;

import org.ballerinalang.jvm.IteratorUtils;
import org.ballerinalang.jvm.api.values.BLink;
import org.ballerinalang.jvm.api.values.BObject;
import org.ballerinalang.jvm.api.values.BStream;
import org.ballerinalang.jvm.types.BStreamType;
import org.ballerinalang.jvm.types.BType;

import java.util.Map;
import java.util.UUID;

/**
 * <p>
 * The {@link StreamValue} represents a stream in Ballerina.
 * </p>
 * <p>
 * <i>Note: This is an internal API and may change in future versions.</i>
 * </p>
 *
 * @since 1.2.0
 */
public class StreamValue implements RefValue, BStream {

    private BType type;
    private BType constraintType;
    private BType iteratorNextReturnType;
    private BObject iteratorObj;


    /**
     * The name of the underlying broker topic representing the stream object.
     */
    public String streamId;

    @Deprecated
    public StreamValue(BType type) {
        this.constraintType = ((BStreamType) type).getConstrainedType();
        this.type = new BStreamType(constraintType);
        this.streamId = UUID.randomUUID().toString();
        this.iteratorObj = null;
    }

    public StreamValue(BType type, BObject iteratorObj) {
        this.constraintType = ((BStreamType) type).getConstrainedType();
        this.type = new BStreamType(constraintType);
        this.streamId = UUID.randomUUID().toString();
        this.iteratorObj = iteratorObj;
    }

    public String getStreamId() {
        return streamId;
    }

    public BObject getIteratorObj() {
        return iteratorObj;
    }

    public BType getIteratorNextReturnType() {
        if (iteratorNextReturnType == null) {
            iteratorNextReturnType = IteratorUtils.createIteratorNextReturnType(constraintType);
        }

        return iteratorNextReturnType;
    }

    /**
     * {@inheritDoc}
     * @param parent The link to the parent node
     */
    public String stringValue(BLink parent) {
        return "stream <" + getType().toString() + ">";
    }

    /**
     * {@inheritDoc}
     * @param parent The link to the parent node
     */
    public String toBalString(BLink parent) {
        return stringValue(parent);
    }

    @Override
    public BType getType() {
        return this.type;
    }

    @Override
    public Object copy(Map<Object, Object> refs) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public Object frozenCopy(Map<Object, Object> refs) {
        throw new UnsupportedOperationException();
    }

    public BType getConstraintType() {
        return constraintType;
    }

    @Override
    public String toString() {
        return stringValue(null);
    }
}

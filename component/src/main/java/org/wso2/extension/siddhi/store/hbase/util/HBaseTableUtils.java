/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.extension.siddhi.store.hbase.util;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.wso2.extension.siddhi.store.hbase.exception.HBaseTableException;
import org.wso2.siddhi.core.exception.OperationNotSupportedException;
import org.wso2.siddhi.query.api.annotation.Annotation;
import org.wso2.siddhi.query.api.annotation.Element;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.wso2.extension.siddhi.store.hbase.util.HBaseEventTableConstants.KEY_SEPARATOR;

public class HBaseTableUtils {

    /**
     * Utility method which can be used to check if a given string instance is null or empty.
     *
     * @param field the string instance to be checked.
     * @return true if the field is null or empty.
     */
    public static boolean isEmpty(String field) {
        return (field == null || field.trim().length() == 0);
    }

    public static String generatePrimaryKeyValue(Object[] record, List<Attribute> schema, Integer[] keyOrdinals) {
        if (keyOrdinals.length == 0) {
            return UUID.randomUUID().toString();
        }
        StringBuilder keyString = new StringBuilder();
        for (Integer key : keyOrdinals) {
            keyString.append(stringifyCell(schema.get(key).getType(), record[key]));
            if (key != keyOrdinals.length - 1) {
                keyString.append(KEY_SEPARATOR);
            }

        }
        return keyString.toString();
    }

    public static Integer[] inferPrimaryKeyOrdinals(List<Attribute> schema, Annotation primaryKeys) {
        if (primaryKeys == null) {
            return new Integer[]{};
        }
        List<String> elements = schema.stream().map(Attribute::getName).collect(Collectors.toList());
        return primaryKeys.getElements().stream().map(Element::getKey).map(elements::indexOf).toArray(Integer[]::new);
    }

    public static Object[] constructRecord(String rowID, String columnFamily, Result result, List<Attribute> schema) {
        List<byte[]> columns = new ArrayList<>();
        schema.forEach(attribute -> {
            Cell dataCell = result.getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes(attribute.getName()));
            if (dataCell == null) {
                throw new HBaseTableException("Data found on row '" + rowID + "' is corrupted, and cannot be decoded.");
            }
            columns.add(CellUtil.cloneValue(dataCell));
        });
        if (columns.size() != schema.size()) {
            throw new HBaseTableException("Data found on row '" + rowID + "' does not match the schema, and cannot be " +
                    "decoded.");
        }
        return columns.stream().map(column -> decodeCell(column, schema.get(columns.indexOf(column)).getType(), rowID))
                .toArray();
    }

    private static String stringifyCell(Attribute.Type type, Object value) {
        String output;
        switch (type) {
            case BOOL:
                output = Boolean.toString((boolean) value);
                break;
            case DOUBLE:
                output = Double.toString((double) value);
                break;
            case FLOAT:
                output = Float.toString((float) value);
                break;
            case INT:
                output = Integer.toString((int) value);
                break;
            case LONG:
                output = Long.toString((long) value);
                break;
            case STRING:
                output = (String) value;
                break;
            default:
                throw new OperationNotSupportedException("Unsupported column type found as primary key: " + type +
                        "Please check your query and try again.");
        }
        return output;
    }

    public static byte[] encodeCell(Attribute.Type type, Object value, String row) {
        byte[] output = null;
        switch (type) {
            case BOOL:
                output = Bytes.toBytes((boolean) value);
                break;
            case DOUBLE:
                output = Bytes.toBytes((double) value);
                break;
            case FLOAT:
                output = Bytes.toBytes((float) value);
                break;
            case INT:
                output = Bytes.toBytes((int) value);
                break;
            case LONG:
                output = Bytes.toBytes((long) value);
                break;
            case OBJECT:
                output = encodeBinaryData(value, row);
                break;
            case STRING:
                output = Bytes.toBytes((String) value);
                break;
        }
        return output;
    }

    private static byte[] encodeBinaryData(Object object, String row) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new HBaseTableException("Error converting data for row '" + row + "' : " + e.getMessage(), e);
        }
    }

    private static Object decodeCell(byte[] column, Attribute.Type type, String row) {
        Object output = null;
        switch (type) {
            case BOOL:
                output = Bytes.toBoolean(column);
                break;
            case DOUBLE:
                output = Bytes.toDouble(column);
                break;
            case FLOAT:
                output = Bytes.toFloat(column);
                break;
            case INT:
                output = Bytes.toInt(column);
                break;
            case LONG:
                output = Bytes.toLong(column);
                break;
            case OBJECT:
                output = decodeBinaryData(column, row);
                break;
            case STRING:
                output = Bytes.toString(column);
                break;
        }
        return output;
    }

    private static Object decodeBinaryData(byte[] bytes, String row) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInput in = new ObjectInputStream(bis)) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new HBaseTableException("Error converting data from row '" + row + "' : " + e.getMessage(), e);
        }
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ignore) {
            /* ignore */
        }
    }

}

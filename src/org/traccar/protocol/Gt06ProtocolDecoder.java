/*
 * Copyright 2012 - 2013 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import java.util.Calendar;
import java.util.TimeZone;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.Crc;
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class Gt06ProtocolDecoder extends BaseProtocolDecoder {

    private Long deviceId;
    private String deviceImei;

    public Gt06ProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    private String readImei(ChannelBuffer buf) {
        int b = buf.readUnsignedByte();
        StringBuilder imei = new StringBuilder();
        imei.append(b & 0x0F);
        for (int i = 0; i < 7; i++) {
            b = buf.readUnsignedByte();
            imei.append((b & 0xF0) >> 4);
            imei.append(b & 0x0F);
        }
        return imei.toString();
    }

    private static final int MSG_LOGIN = 0x01;
    private static final int MSG_GPS = 0x10;
    private static final int MSG_LBS = 0x11;
    private static final int MSG_GPS_LBS = 0x12;
    private static final int MSG_STATUS = 0x13;
    private static final int MSG_SATELLITE = 0x14;
    private static final int MSG_STRING = 0x15;
    private static final int MSG_GPS_LBS_STATUS = 0x16;
    private static final int MSG_LBS_PHONE = 0x17;
    private static final int MSG_LBS_EXTEND = 0x18;
    private static final int MSG_LBS_STATUS = 0x19;
    private static final int MSG_GPS_PHONE = 0x1A;

    private static void sendResponse(Channel channel, int type, int index) {
        if (channel != null) {
            ChannelBuffer response = ChannelBuffers.directBuffer(10);
            response.writeByte(0x78); response.writeByte(0x78); // header
            response.writeByte(0x05); // size
            response.writeByte(type);
            response.writeShort(index);
            response.writeShort(Crc.crc16Ccitt(response.toByteBuffer(2, 4)));
            response.writeByte(0x0D); response.writeByte(0x0A); // ending
            channel.write(response);
        }
    }

    private static void sendBadResponse(Channel channel, int type) {
        if (channel != null) {
            ChannelBuffer response = ChannelBuffers.directBuffer(10);
            response.writeByte(0x00); response.writeByte(0x00); // header
            response.writeByte(0x00); // size
            response.writeByte(type);
            response.writeShort(0);
            response.writeShort(0);
            response.writeByte(0x00); response.writeByte(0x00); // ending
            channel.write(response);
        }
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        ChannelBuffer buf = (ChannelBuffer) msg;

        buf.skipBytes(2); // header
        int length = buf.readByte(); // size
        int dataLength = length - 5;

        int type = buf.readUnsignedByte();
        
        if (type == MSG_LOGIN) {
            String imei = readImei(buf);
            try {
                deviceImei = imei;
                deviceId = getDataManager().getDeviceByImei(imei).getId();

                Log.debug("My: message type - LOGIN, deviceID: " + String.valueOf(deviceId));
                getDataManager().addSig("id:"+String.valueOf(deviceId), -1, null, -1, "login", null, -1, -1, null, null, null, -1, -1, -1, null, null, null, null, -1);
                buf.skipBytes(dataLength - 8);
                sendResponse(channel, type, buf.readUnsignedShort());
            } catch(Exception error) {
                Log.debug("Unknown device - " + imei);
                Log.error(error.getMessage());
            }
        }

        else if (type == MSG_GPS ||
                 type == MSG_GPS_LBS ||
                 type == MSG_GPS_LBS_STATUS ||
                 type == MSG_GPS_PHONE) {

            Object gps, charge, acc, voltage, signal;

            Log.debug("My: message type - MSG_GPS_LBS, deviceID: " + String.valueOf(deviceId));


            // Create new position
            Position position = new Position();
            position.setDeviceId(deviceId);
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("gt06");


            if (type == MSG_GPS)
                extendedInfo.set("type", "MSG_GPS");
            else if (type == MSG_GPS_LBS)
                extendedInfo.set("type", "MSG_GPS_LBS");
            else if (type == MSG_GPS_LBS_STATUS)
                extendedInfo.set("type", "MSG_GPS_LBS_STATUS");
            else if (type == MSG_GPS_PHONE)
                extendedInfo.set("type", "MSG_GPS_PHONE");

            // Date and time
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            time.clear();
            time.set(Calendar.YEAR, 2000 + buf.readUnsignedByte());
            time.set(Calendar.MONTH, buf.readUnsignedByte() - 1);
            time.set(Calendar.DAY_OF_MONTH, buf.readUnsignedByte());
            time.set(Calendar.HOUR, buf.readUnsignedByte());
            time.set(Calendar.MINUTE, buf.readUnsignedByte());
            time.set(Calendar.SECOND, buf.readUnsignedByte());
            position.setTime(time.getTime());

//            Log.info("Device: "+deviceId+", "
//                    +"timestamp: "+position.getTime().getTime()+"");

            // GPS length and Satellites count
            int gpsLength = buf.readUnsignedByte();
            extendedInfo.set("satellites", gpsLength & 0xf);
            gpsLength >>= 4;

            // Latitude
            double latitude = buf.readUnsignedInt() / (60.0 * 30000.0);

            // Longitude
            double longitude = buf.readUnsignedInt() / (60.0 * 30000.0);

            // Speed
            position.setSpeed(buf.readUnsignedByte() * 0.539957);

            // Course and flags
            int union = buf.readUnsignedShort();
            position.setCourse((double) (union & 0x03FF));
            position.setValid((union & 0x1000) != 0);
            if ((union & 0x0400) == 0) latitude = -latitude;
            if ((union & 0x0800) != 0) longitude = -longitude;

            gps = (union & 0x1000) >> 12;
            extendedInfo.set("gpsrealtime", (union & 0x2000) >> 13);
            extendedInfo.set("gpshavepos", (union & 0x1000) >> 12);


            position.setLatitude(latitude);
            position.setLongitude(longitude);
            position.setAltitude(0.0);

            buf.skipBytes(gpsLength - 12); // skip reserved

            if (type == MSG_GPS_LBS || type == MSG_GPS_LBS_STATUS) {

                int lbsLength = 0;
                if (type == MSG_GPS_LBS_STATUS) {
                    lbsLength = buf.readUnsignedByte();
                }

                // Cell information
                extendedInfo.set("mcc", buf.readUnsignedShort());
                extendedInfo.set("mnc", buf.readUnsignedByte());
                extendedInfo.set("lac", buf.readUnsignedShort());
                buf.readUnsignedByte();
                extendedInfo.set("cell", buf.readUnsignedShort());
                buf.skipBytes(lbsLength - 9);

                // Status
                if (type == MSG_GPS_LBS_STATUS) {
                    int flags = buf.readUnsignedByte(); // TODO parse flags

                    gps = (flags & 0x40) >> 6;
                    extendedInfo.set("gps", (flags & 0x40) >> 6);
                    charge = (flags & 0x04) >> 2;
                    extendedInfo.set("charge", (flags & 0x04) >> 2);
                    acc = (flags & 0x02) >> 1;
                    extendedInfo.set("acc", (flags & 0x02) >> 1);
                    extendedInfo.set("infoalarm", (flags & 0x38) >> 3);
                    //infoalarm
                    //100 SOS
                    //011 Low Battery
                    //010 Power cut
                    //001 shock
                    //000 normal

                    // Voltage
                    position.setPower((double) buf.readUnsignedByte());

                    // GSM signal
                    extendedInfo.set("gsm", buf.readUnsignedByte());

                    int alarm = buf.readUnsignedShort(); //alarm/language
                    extendedInfo.set("alarm", alarm & 0xff00);

                }
            }

            // Index
            if (buf.readableBytes() > 6) {
                buf.skipBytes(buf.readableBytes() - 6);
            }
            int index = buf.readUnsignedShort();
            extendedInfo.set("index", index);

            getDataManager().addSig("id:"+String.valueOf(deviceId), -1, null, -1, extendedInfo.toString(), gps.toString(), -1, -1, null, null, null, -1, -1, -1, null, null, null, null, -1);

            sendResponse(channel, type, index);

            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }
        else if(deviceId != null) {
            Object gps, charge, acc, voltage, signal;

            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("gt06");

            int infos = buf.readUnsignedByte();

            extendedInfo.set("info", infos);
            gps = (infos & 0x40) >> 6;
            extendedInfo.set("gps", (infos & 0x40) >> 6);
            charge = (infos & 0x04) >> 2;
            extendedInfo.set("charge", (infos & 0x04) >> 2);
            acc = (infos & 0x02) >> 1;
            extendedInfo.set("acc", (infos & 0x02) >> 1);
            extendedInfo.set("infoalarm", (infos & 0x38) >> 3);
            //infoalarm
            //100 SOS
            //011 Low Battery
            //010 Power cut
            //001 shock
            //000 normal

            voltage = buf.readUnsignedByte();
            extendedInfo.set("voltage", voltage);
            signal = buf.readUnsignedByte();
            extendedInfo.set("signal", signal);

            int alarm = buf.readUnsignedShort(); //alarm/language
            extendedInfo.set("alarm", alarm & 0xff00);

            //buf.skipBytes(dataLength);

            getDataManager().addSig("id:"+String.valueOf(deviceId), -1, null, -1, extendedInfo.toString(), gps.toString(), -1, -1, charge.toString(), acc.toString(), voltage.toString(), -1, -1, -1, null, null, signal.toString(), null, -1);

            Log.debug("My: message type - MSG_STATUS HEARTBEAT, deviceID: " + String.valueOf(deviceId));
            sendResponse(channel, type, buf.readUnsignedShort());


        }else {

            getDataManager().addSig("id:"+String.valueOf(deviceId), -1, null, -1, "Unknow signal. Type:"+String.valueOf(type), null, -1, -1, null, null, null, -1, -1, -1, null, null, null, null, -1);
            Log.debug("My: Unknow signal. Type:"+String.valueOf(type)+", deviceID: " + String.valueOf(deviceId));

            buf.skipBytes(dataLength);
            sendResponse(channel, type, buf.readUnsignedShort());
        }

        return null;
    }




}

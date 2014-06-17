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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

import java.text.ParseException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.google.protobuf.*;
import com.example.signalus_terminal.TerminalProtos;

public class SignalusProtocolDecoder extends BaseProtocolDecoder {

    private Long deviceId = null;
    private Long deviceImei;
    private Long deviceImei2;

    public SignalusProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        String sentence = /*decompress*/((String)msg);

        //LOGIN PACKET
        try {
            TerminalProtos.LoginPackage loginPacket = TerminalProtos.LoginPackage.parseFrom(hexStringToByteArray(sentence));

            if (!loginPacket.isInitialized() || loginPacket.getType() != TerminalProtos.PackageType.LOGIN)
                throw new Exception();

            Log.info("LOGIN PACKET");

            deviceImei = loginPacket.getImei();
            deviceImei2 = loginPacket.getImei2();

            try {
                deviceId = getDataManager().getDeviceByImei(String.valueOf(deviceImei)).getId();
            }catch (Exception e) {}

            if (deviceId == null){
                try {
                    deviceId = getDataManager().getDeviceByImei(String.valueOf(deviceImei2)).getId();
                }catch (Exception e) {}
            }

            TerminalProtos.DataResponcePackage.Builder responseLoginPacket = TerminalProtos.DataResponcePackage.newBuilder();
            responseLoginPacket.setType(TerminalProtos.PackageType.LOGIN);
            responseLoginPacket.setIndex(loginPacket.getIndex());

            if (deviceId != null){
                responseLoginPacket.setStatus(TerminalProtos.DataResponcePackage.StatusType.NO_ERROR);
            }else{
                responseLoginPacket.setStatus(TerminalProtos.DataResponcePackage.StatusType.INVALID_PACKET);
            }
            channel.write(/*compress*/(bytArrayToHex(responseLoginPacket.build().toByteArray()))+"\n");

            loginPacket = null;
        }catch (Exception e){}

        //DATA PACKET
        try {
            TerminalProtos.DataPackage dataPacket = TerminalProtos.DataPackage.parseFrom(hexStringToByteArray(sentence));

            if (!dataPacket.isInitialized() || dataPacket.getType() != TerminalProtos.PackageType.DATA)
                throw new Exception();

            Log.info("DATA PACKET, device: "+deviceId);

            TerminalProtos.DataResponcePackage.Builder responseLoginPacket = TerminalProtos.DataResponcePackage.newBuilder();
            responseLoginPacket.setIndex(dataPacket.getIndex());
            responseLoginPacket.setType(TerminalProtos.PackageType.LOGIN);

            if (deviceId != null && dataPacket.getSatellitesInFix() > 2) {

                Position position = new Position();
                position.setDeviceId(deviceId);
                ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("signalus");
                extendedInfo.set("index", dataPacket.getIndex());

                Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                time.clear();
                time.setTimeInMillis(dataPacket.getPosition().getTimestamp());
                position.setTime(time.getTime());

                extendedInfo.set("satellites", dataPacket.getSatellitesInFix());
                extendedInfo.set("satellitesAll", dataPacket.getSatellites());
                position.setSpeed((double)dataPacket.getPosition().getSpeed() /1000 * 3600 /1.609344);

                position.setCourse((double)dataPacket.getPosition().getCourse());
                position.setValid(true);


                position.setLatitude(dataPacket.getPosition().getLatitude());
                position.setLongitude(dataPacket.getPosition().getLongitude());
                position.setAltitude(dataPacket.getPosition().getAltitude());


                extendedInfo.set("mcc", dataPacket.getCell().getMcc());
                extendedInfo.set("mnc", dataPacket.getCell().getMnc());
                extendedInfo.set("lac", dataPacket.getCell().getLac());
                extendedInfo.set("cell", dataPacket.getCell().getCell());

                extendedInfo.set("cellLatitude", dataPacket.getCell().getPosition().getLatitude());
                extendedInfo.set("cellLongitude", dataPacket.getCell().getPosition().getLongitude());

                extendedInfo.set("cellstrength", dataPacket.getCell().getStrength());


                position.setExtendedInfo(extendedInfo.toString());

                Log.info("Device: "+deviceId+", "
                            +"index: "+dataPacket.getIndex()+", "
                            +"position: "+dataPacket.getPosition().getLatitude()+","+dataPacket.getPosition().getLongitude()+", "
                            +"timestamp: "+dataPacket.getPosition().getTimestamp()+", "
                            +"speed: "+dataPacket.getPosition().getSpeed()+" km/h, "
                            +"Altitude: "+dataPacket.getPosition().getAltitude()+" m, "
                            +"Course: "+dataPacket.getPosition().getCourse()+", "
                            +"CellID: "+dataPacket.getCell().getMcc()+"." +dataPacket.getCell().getMnc()+"."+dataPacket.getCell().getLac()+"."+dataPacket.getCell().getCell()+", "
                            +"timestamp: "+dataPacket.getCell().getPosition().getTimestamp()+", "
                            +"Cell strength: "+dataPacket.getCell().getStrength()+", "
                            +"Cell position: "+dataPacket.getCell().getPosition().getLatitude()+","+dataPacket.getCell().getPosition().getLongitude()+", "
                            +"Satellites: "+dataPacket.getSatellites()+", "
                            +"Satellites in fix: "+dataPacket.getSatellitesInFix()+""
                        );
                responseLoginPacket.setStatus(TerminalProtos.DataResponcePackage.StatusType.NO_ERROR);

                channel.write(/*compress*/(bytArrayToHex(responseLoginPacket.build().toByteArray()))+"\n");
                return position;

            }else{
                responseLoginPacket.setStatus(TerminalProtos.DataResponcePackage.StatusType.NO_ERROR);
                responseLoginPacket.setMsg("< 3 sattelites");

                Log.info("Device: "+deviceId+", "
                        +"index: "+dataPacket.getIndex()+", "
                        +"position: "+dataPacket.getPosition().getLatitude()+","+dataPacket.getPosition().getLongitude()+", "
                        +"timestamp: "+dataPacket.getPosition().getTimestamp()+", "
                        +"speed: "+dataPacket.getPosition().getSpeed()+" km/h, "
                        +"Altitude: "+dataPacket.getPosition().getAltitude()+" m, "
                        +"Course: "+dataPacket.getPosition().getCourse()+", "
                        +"CellID: "+dataPacket.getCell().getMcc()+"." +dataPacket.getCell().getMnc()+"."+dataPacket.getCell().getLac()+"."+dataPacket.getCell().getCell()+", "
                        +"timestamp: "+dataPacket.getCell().getPosition().getTimestamp()+", "
                        +"Cell strength: "+dataPacket.getCell().getStrength()+", "
                        +"Cell position: "+dataPacket.getCell().getPosition().getLatitude()+","+dataPacket.getCell().getPosition().getLongitude()+", "
                        +"Satellites: "+dataPacket.getSatellites()+", "
                        +"Satellites in fix: "+dataPacket.getSatellitesInFix()+""
                );
            }
            channel.write(/*compress*/(bytArrayToHex(responseLoginPacket.build().toByteArray()))+"\n");

            dataPacket = null;



        }catch (Exception e){}

        return null;
    }

    public byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    String bytArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for(byte b: a)
            sb.append(String.format("%02x", b&0xff));
        return sb.toString();
    }

    public String compress(String str) throws IOException {
        if (str == null || str.length() == 0) {
            return str;
        }
        System.out.println("String length : " + str.length());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(str.getBytes());
        gzip.close();
        String outStr = out.toString("ISO-8859-1");
        System.out.println("Output String lenght : " + outStr.length());
        return outStr;
    }

    public String decompress(String str) throws IOException {
        if (str == null || str.length() == 0) {
            return str;
        }
        System.out.println("Input String length : " + str.length());
        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(str.getBytes("ISO-8859-1")));
        BufferedReader bf = new BufferedReader(new InputStreamReader(gis, "ISO-8859-1"));
        String outStr = "";
        String line;
        while ((line=bf.readLine())!=null) {
            outStr += line;
        }
        System.out.println("Output String lenght : " + outStr.length());
        return outStr;
    }

}

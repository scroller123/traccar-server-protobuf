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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.Log;
import org.traccar.model.Device;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;
import org.traccar.model.BluetoothDevice;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


import java.text.ParseException;
import java.util.ArrayList;
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

import javax.swing.*;

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

        ChannelBuffer buf = (ChannelBuffer) msg;
        int length = buf.readShort(); // size
        byte[] sentence = new byte[length];
        buf.readBytes(sentence);

        //LOGIN PACKET
        try {
            TerminalProtos.LoginPackage loginPacket = TerminalProtos.LoginPackage.parseFrom(sentence);

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

            TerminalProtos.DataResponcePackage.Builder responsePacket = TerminalProtos.DataResponcePackage.newBuilder();
            responsePacket.setType(TerminalProtos.PackageType.LOGIN);
            responsePacket.setIndex(loginPacket.getIndex());

            if (deviceId != null){
                responsePacket.setStatus(TerminalProtos.DataResponcePackage.StatusType.NO_ERROR);
            }else{
                responsePacket.setStatus(TerminalProtos.DataResponcePackage.StatusType.INVALID_PACKET);
            }

            byte[] packetBytes = responsePacket.build().toByteArray();
            byte[] lengthBytes = shortToByteArray((short)packetBytes.length);
            ChannelBuffer message = ChannelBuffers.directBuffer(packetBytes.length + lengthBytes.length);
            message.writeBytes(lengthBytes);
            message.writeBytes(packetBytes);
            channel.write(message);
//            channel.write(/*compress*/(bytArrayToHex(responseLoginPacket.build().toByteArray()))+"\n");

            loginPacket = null;
        }catch (Exception e){}

        //DATA PACKET
        try {
            TerminalProtos.DataPackage dataPacket = TerminalProtos.DataPackage.parseFrom(sentence);

            if (!dataPacket.isInitialized() || dataPacket.getType() != TerminalProtos.PackageType.DATA)
                throw new Exception();

            Log.info("DATA PACKET, device: "+deviceId);


            if (dataPacket.getBluetoothDeviceCount()>0) {
                for(int i=0; i<dataPacket.getBluetoothDeviceCount(); i++) {
                    getDataManager().insertBluetoothSearchResult(deviceId, dataPacket.getBluetoothDevice(i).getName(), dataPacket.getBluetoothDevice(i).getMac());
                }
            }


            TerminalProtos.DataResponcePackage.Builder responsePacket = TerminalProtos.DataResponcePackage.newBuilder();
            responsePacket.setType(TerminalProtos.PackageType.DATA);
            responsePacket.setIndex(dataPacket.getIndex());

            Position position = null;

            if (deviceId != null && dataPacket.getSatellitesInFix() > 2) {

                position = new Position();
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
                position.setAltitude((double)dataPacket.getPosition().getAltitude());


                extendedInfo.set("mcc1", dataPacket.getCell(0).getMcc());
                extendedInfo.set("mnc1", dataPacket.getCell(0).getMnc());
                extendedInfo.set("lac1", dataPacket.getCell(0).getLac());
                extendedInfo.set("cell1", dataPacket.getCell(0).getCell());

                extendedInfo.set("mcc2", dataPacket.getCell(1).getMcc());
                extendedInfo.set("mnc2", dataPacket.getCell(1).getMnc());
                extendedInfo.set("lac2", dataPacket.getCell(1).getLac());
                extendedInfo.set("cell2", dataPacket.getCell(1).getCell());

                extendedInfo.set("cell1Latitude", dataPacket.getCell(0).getPosition().getLatitude());
                extendedInfo.set("cell1Longitude", dataPacket.getCell(0).getPosition().getLongitude());

                extendedInfo.set("cell1strength", dataPacket.getCell(0).getStrength());
                extendedInfo.set("cell2strength", dataPacket.getCell(1).getStrength());


                position.setExtendedInfo(extendedInfo.toString());

                Log.info("Device: "+deviceId+", "
                            +"index: "+dataPacket.getIndex()+", "
                            +"position: "+dataPacket.getPosition().getLatitude()+","+dataPacket.getPosition().getLongitude()+", "
                            +"timestamp: "+dataPacket.getPosition().getTimestamp()+", "
                            +"speed: "+dataPacket.getPosition().getSpeed()+" km/h, "
                            +"Altitude: "+dataPacket.getPosition().getAltitude()+" m, "
                            +"Course: "+dataPacket.getPosition().getCourse()+", "
                            +"Cell1ID: "+dataPacket.getCell(0).getMcc()+"." +dataPacket.getCell(0).getMnc()+"."+dataPacket.getCell(0).getLac()+"."+dataPacket.getCell(0).getCell()+", "
                            +"Cell2ID: "+dataPacket.getCell(1).getMcc()+"." +dataPacket.getCell(1).getMnc()+"."+dataPacket.getCell(1).getLac()+"."+dataPacket.getCell(1).getCell()+", "
                            +"Cell1 timestamp: "+dataPacket.getCell(0).getPosition().getTimestamp()+", "
                            +"Cell1 strength: "+dataPacket.getCell(0).getStrength()+", "
                            +"Cell2 strength: "+dataPacket.getCell(1).getStrength()+", "
                            +"Cell1 position: "+dataPacket.getCell(0).getPosition().getLatitude()+","+dataPacket.getCell(0).getPosition().getLongitude()+", "
                            +"Satellites: "+dataPacket.getSatellites()+", "
                            +"Satellites in fix: "+dataPacket.getSatellitesInFix()+""
                        );
                responsePacket.setStatus(TerminalProtos.DataResponcePackage.StatusType.NO_ERROR);



            }else{
                responsePacket.setStatus(TerminalProtos.DataResponcePackage.StatusType.NO_ERROR);
                responsePacket.setMsg("< 3 sattelites");

                Log.info("Device: "+deviceId+", "
                        +"index: "+dataPacket.getIndex()+", "
                        +"position: "+dataPacket.getPosition().getLatitude()+","+dataPacket.getPosition().getLongitude()+", "
                        +"timestamp: "+dataPacket.getPosition().getTimestamp()+", "
                        +"speed: "+dataPacket.getPosition().getSpeed()+" km/h, "
                        +"Altitude: "+dataPacket.getPosition().getAltitude()+" m, "
                        +"Course: "+dataPacket.getPosition().getCourse()+", "
                        +"Cell1ID: "+dataPacket.getCell(0).getMcc()+"." +dataPacket.getCell(0).getMnc()+"."+dataPacket.getCell(0).getLac()+"."+dataPacket.getCell(0).getCell()+", "
                        +"Cell2ID: "+dataPacket.getCell(1).getMcc()+"." +dataPacket.getCell(1).getMnc()+"."+dataPacket.getCell(1).getLac()+"."+dataPacket.getCell(1).getCell()+", "
                        +"Cell1 timestamp: "+dataPacket.getCell(0).getPosition().getTimestamp()+", "
                        +"Cell1 strength: "+dataPacket.getCell(0).getStrength()+", "
                        +"Cell2 strength: "+dataPacket.getCell(1).getStrength()+", "
                        +"Cell1 position: "+dataPacket.getCell(0).getPosition().getLatitude()+","+dataPacket.getCell(0).getPosition().getLongitude()+", "
                        +"Satellites: "+dataPacket.getSatellites()+", "
                        +"Satellites in fix: "+dataPacket.getSatellitesInFix()+""
                );


            }

            // Neighhooding Cells
            if (dataPacket.getNeighboringcellCount() >= 3) {
                StringBuilder url = new StringBuilder();
                url.append("http://www.signalus.ru/outer/setLBSPosition?deviceID="+deviceId);
                for(TerminalProtos.DataPackage.NeighboringCell nhCell : dataPacket.getNeighboringcellList()) {
                    url.append("&cellinfo[]="+dataPacket.getCell(0).getMcc()+":"+nhCell.getMnc()+":"+nhCell.getLac()+":"+nhCell.getCell()+"&cellrssi[]="+nhCell.getStrength());
                }
                SendLBSPositionProcess process = new SendLBSPositionProcess(url.toString());
                try {
                    process.execute();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }




            Device device = getDataManager().getDeviceByID(deviceId);

            // bluetooth searching
            if (device.getDoSearchingBluetooth()==1){
                getDataManager().deleteBluetoothSearchResult(deviceId);
                getDataManager().setDoSearchingBluetoothValue(deviceId, 0);
                responsePacket.setDoSearchingBluetooth(1);
            }

            // bluetooth binded
            if (device.getDoBindingBluetooth()==1){
                getDataManager().setDoBindingBluetoothValue(deviceId, 0);
                ArrayList<BluetoothDevice> btDevices = getDataManager().selectBluetoothBinded(deviceId);
                for (BluetoothDevice btDevice : btDevices) {
                    responsePacket.addBluetoothDevice(TerminalProtos.DataResponcePackage.BluetoothDevice.newBuilder()
                                                        .setMac(btDevice.getMac())
                                                        .setName(btDevice.getName()));
                }
            }

            byte[] packetBytes = responsePacket.build().toByteArray();
            byte[] lengthBytes = shortToByteArray((short)packetBytes.length);
            ChannelBuffer message = ChannelBuffers.directBuffer(packetBytes.length + lengthBytes.length);
            message.writeBytes(lengthBytes);
            message.writeBytes(packetBytes);
            channel.write(message);

            dataPacket = null;

            if (position!=null)
                return position;


        }catch (Exception e){}

        return null;
    }

    public class SendLBSPositionProcess extends SwingWorker {
        String url;

        public SendLBSPositionProcess (String url){
            this.url = url;
        }
        /**
         * @throws Exception
         */
        protected Object doInBackground() throws Exception {
            final String USER_AGENT = "Mozilla/5.0";

            try{
                URL obj = new URL(url);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setRequestMethod("GET");
                con.setRequestProperty("User-Agent", USER_AGENT);
                int responseCode = con.getResponseCode();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                in.close();

            }catch (Exception e){
                Log.error("HTTP GET Exception: "+e.getMessage()+", "+e.getCause().getMessage());
            }

            return null;
        }
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

    public static final byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    public static final byte[] shortToByteArray(short value) {
        return new byte[] {
                (byte)(value >>> 8),
                (byte)value};
    }

}

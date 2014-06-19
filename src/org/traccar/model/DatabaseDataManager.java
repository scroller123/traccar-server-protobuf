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
package org.traccar.model;

import java.io.File;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.traccar.helper.AdvancedConnection;
import org.traccar.helper.DriverDelegate;
import org.traccar.helper.Log;
import org.traccar.helper.NamedParameterStatement;
import org.xml.sax.InputSource;

/**
 * Database abstraction class
 */
public class DatabaseDataManager implements DataManager {

    public DatabaseDataManager(Properties properties) throws Exception {
        initDatabase(properties);
    }

    /**
     * Database statements
     */
    private NamedParameterStatement queryGetDevices;
    private NamedParameterStatement querySelectBluetoothBinded;
    private NamedParameterStatement querySetDoSearchingBluetootValue;
    private NamedParameterStatement querySetDoBindingBluetootValue;
    private NamedParameterStatement queryDeleteBluetoothSearchResult;
    private NamedParameterStatement queryInsertBluetoothSearchResult;
    private NamedParameterStatement queryAddPosition;
    private NamedParameterStatement queryUpdateLatestPosition;
    private NamedParameterStatement queryAddSig;

    /**
     * Initialize database
     */
    private void initDatabase(Properties properties) throws Exception {

        // Load driver
        String driver = properties.getProperty("database.driver");
        if (driver != null) {
            String driverFile = properties.getProperty("database.driverFile");

            if (driverFile != null) {
                URL url = new URL("jar:file:" + new File(driverFile).getAbsolutePath() + "!/");
                URLClassLoader cl = new URLClassLoader(new URL[] { url });
                Driver d = (Driver) Class.forName(driver, true, cl).newInstance();
                DriverManager.registerDriver(new DriverDelegate(d));
            } else {
                Class.forName(driver);
            }
        }

        // Refresh delay
        String refreshDelay = properties.getProperty("database.refreshDelay");
        if (refreshDelay != null) {
            devicesRefreshDelay = Long.valueOf(refreshDelay) * 1000;
        } else {
            devicesRefreshDelay = new Long(300) * 1000; // Magic number
        }

        // Connect database
        String url = properties.getProperty("database.url");
        String user = properties.getProperty("database.user");
        String password = properties.getProperty("database.password");
        AdvancedConnection connection = new AdvancedConnection(url, user, password);

        // Load statements from configuration
        String query;

        query = properties.getProperty("database.selectDevice");
        if (query != null) {
            queryGetDevices = new NamedParameterStatement(connection, query);
        }


        query = properties.getProperty("database.selectBluetoothBinded");
        if (query != null) {
            querySelectBluetoothBinded = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.setDoSearchingBluetootValue");
        if (query != null) {
            querySetDoSearchingBluetootValue = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.setDoBindingBluetootValue");
        if (query != null) {
            querySetDoBindingBluetootValue = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.deleteBluetoothSearchResult");
        if (query != null) {
            queryDeleteBluetoothSearchResult = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.insertBluetoothSearchResult");
        if (query != null) {
            queryInsertBluetoothSearchResult = new NamedParameterStatement(connection, query);
        }



        query = properties.getProperty("database.insertPosition");
        if (query != null) {
            queryAddPosition = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.updateLatestPosition");
        if (query != null) {
            queryUpdateLatestPosition = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.insertSig");
        if (query != null) {
            queryAddSig = new NamedParameterStatement(connection, query);
        }
    }

    @Override
    public synchronized List<Device> getDevices() throws SQLException {

        List<Device> deviceList = new LinkedList<Device>();

        if (queryGetDevices != null) {
            queryGetDevices.prepare();
            ResultSet result = queryGetDevices.executeQuery();
            while (result.next()) {
                Device device = new Device();
                device.setId(result.getLong("id"));
                device.setImei(result.getString("imei"));
                device.setDoSearchingBluetooth(result.getString("do_searching_bluetooth"));
                device.setDoBindingBluetooth(result.getString("do_binding_bluetooth"));
                deviceList.add(device);
            }
        }

        return deviceList;
    }

    /**
     * Devices cache
     */
    private Map<String, Device> devices;
    private Map<Long, Device> devicesIDs;
    private Calendar devicesLastUpdate;
    private Long devicesRefreshDelay;

    @Override
    public Device getDeviceByImei(String imei) throws SQLException {

        if ((devices == null) || (Calendar.getInstance().getTimeInMillis() - devicesLastUpdate.getTimeInMillis() > devicesRefreshDelay)) {
            refreshDevices();
        }

        return devices.get(imei);
    }

    @Override
    public Device getDeviceByID(Long id) throws SQLException {

        if ((devicesIDs == null) || (Calendar.getInstance().getTimeInMillis() - devicesLastUpdate.getTimeInMillis() > devicesRefreshDelay)) {
            refreshDevices();
        }

        return devicesIDs.get(id);
    }

    @Override
    public void refreshDevices() throws SQLException {
        List<Device> list = getDevices();
        devices = new HashMap<String, Device>();
        devicesIDs = new HashMap<Long, Device>();
        for (Device device: list) {
            devices.put(device.getImei(), device);
            devicesIDs.put(device.getId(), device);
        }
        devicesLastUpdate = Calendar.getInstance();

        return;
    }


    @Override
    public ArrayList<BluetoothDevice> selectBluetoothBinded(Long deviceId) throws SQLException {
        ArrayList<BluetoothDevice> list = new ArrayList<BluetoothDevice>();
        if (querySelectBluetoothBinded != null) {
            querySelectBluetoothBinded.prepare();
            querySelectBluetoothBinded.setLong("device_id", deviceId);

            ResultSet result = querySelectBluetoothBinded.executeQuery();
            while (result.next()) {
                BluetoothDevice d = new BluetoothDevice();
                d.setId(result.getLong("id"));
                d.setName(result.getString("name"));
                d.setMac(result.getString("mac"));
                list.add(d);
            }
        }
        return list;
    }



    @Override
    public void setDoSearchingBluetoothValue(Long deviceId, int value) throws SQLException {
        if (querySetDoSearchingBluetootValue != null) {
            querySetDoSearchingBluetootValue.prepare();
            querySetDoSearchingBluetootValue.setLong("device_id", deviceId);
            querySetDoSearchingBluetootValue.setInt("value", value);
            querySetDoSearchingBluetootValue.executeUpdate();
        }
    }


    @Override
    public void setDoBindingBluetoothValue(Long deviceId, int value) throws SQLException {
        if (querySetDoBindingBluetootValue != null) {
            querySetDoBindingBluetootValue.prepare();
            querySetDoBindingBluetootValue.setLong("device_id", deviceId);
            querySetDoBindingBluetootValue.setInt("value", value);
            querySetDoBindingBluetootValue.executeUpdate();
        }
    }

    @Override
    public void deleteBluetoothSearchResult(Long deviceId) throws SQLException {
        if (queryDeleteBluetoothSearchResult != null) {
            queryDeleteBluetoothSearchResult.prepare();
            queryDeleteBluetoothSearchResult.setLong("device_id", deviceId);
            queryDeleteBluetoothSearchResult.executeUpdate();
        }
    }

    @Override
    public void insertBluetoothSearchResult(Long deviceId, String name, String mac) throws SQLException {
        if (queryInsertBluetoothSearchResult != null) {
            queryInsertBluetoothSearchResult.prepare();
            queryInsertBluetoothSearchResult.setLong("device_id", deviceId);
            queryInsertBluetoothSearchResult.setString("name", name);
            queryInsertBluetoothSearchResult.setString("mac", mac);
            queryInsertBluetoothSearchResult.executeUpdate();
        }
    }




    @Override
    public synchronized Long addPosition(Position position) throws SQLException {

        if (queryAddPosition != null) {
            queryAddPosition.prepare(Statement.RETURN_GENERATED_KEYS);

            queryAddPosition.setLong("device_id", position.getDeviceId());
            queryAddPosition.setTimestamp("time", position.getTime());
            queryAddPosition.setBoolean("valid", position.getValid());
            queryAddPosition.setDouble("altitude", position.getAltitude());
            queryAddPosition.setDouble("latitude", position.getLatitude());
            queryAddPosition.setDouble("longitude", position.getLongitude());
            queryAddPosition.setDouble("speed", position.getSpeed());
            queryAddPosition.setDouble("course", position.getCourse());
            queryAddPosition.setString("address", position.getAddress());
            queryAddPosition.setString("extended_info", position.getExtendedInfo());
            
            // DELME: Temporary compatibility support
            XPath xpath = XPathFactory.newInstance().newXPath();
            try {
                InputSource source = new InputSource(new StringReader(position.getExtendedInfo()));
                String index = xpath.evaluate("/info/index", source);
                if (!index.isEmpty()) {
                    queryAddPosition.setLong("id", Long.valueOf(index));
                } else {
                    queryAddPosition.setLong("id", null);
                }
                source = new InputSource(new StringReader(position.getExtendedInfo()));
                String power = xpath.evaluate("/info/power", source);
                if (!power.isEmpty()) {
                    queryAddPosition.setDouble("power", Double.valueOf(power));
                } else {
                    queryAddPosition.setLong("power", null);
                }
            } catch (XPathExpressionException e) {
                Log.warning("Error in XML: " + position.getExtendedInfo(), e);
                queryAddPosition.setLong("id", null);
                queryAddPosition.setLong("power", null);
            }

            queryAddPosition.executeUpdate();

            ResultSet result = queryAddPosition.getGeneratedKeys();
            if (result != null && result.next()) {
                return result.getLong(1);
            }
        }

        return null;
    }

    @Override
    public void updateLatestPosition(Long deviceId, Long positionId) throws SQLException {
        
        if (queryUpdateLatestPosition != null) {
            queryUpdateLatestPosition.prepare();

            queryUpdateLatestPosition.setLong("device_id", deviceId);
            queryUpdateLatestPosition.setLong("id", positionId);

            queryUpdateLatestPosition.executeUpdate();
        }
    }

    @Override
    public synchronized Long addSig(String hex, String adds, String gps, String charge, String acc, String voltage, String signal) throws SQLException {
        String deviceID = null;
        String sentence = (String) hex;
        Pattern pattern = Pattern.compile("(id|imei)\\:(\\d+)");
        Matcher parser = pattern.matcher(sentence);
        if (!parser.matches()) {
            return null;
        }

        if (parser.group(1).compareTo("id")==0){
            deviceID = parser.group(2);
        }else if (parser.group(1).compareTo("imei")==0) {
            //���� �������� ��������� imei
            return null;
        }

        if (queryAddSig != null && deviceID!=null) {
            queryAddSig.prepare(Statement.RETURN_GENERATED_KEYS);
            queryAddSig.setString("device_id", deviceID);
            queryAddSig.setString("adds", adds);
            queryAddSig.setString("gps", gps);
            queryAddSig.setString("charge", charge);
            queryAddSig.setString("acc", acc);
            queryAddSig.setString("voltage", voltage);
            queryAddSig.setString("signal", signal);
            queryAddSig.executeUpdate();

            ResultSet result = queryAddSig.getGeneratedKeys();
            if (result != null && result.next()) {
                return result.getLong(1);
            }
        }
        return null;
    }

}

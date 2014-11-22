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

import org.traccar.SignalWatcher;
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
    private NamedParameterStatement querySetTimeZone;
    private NamedParameterStatement queryGetDevices;
    private NamedParameterStatement queryGetLastSignal;
    private NamedParameterStatement queryGetLastPosition;
    private NamedParameterStatement querySelectBluetoothBinded;
    private NamedParameterStatement querySetDefenceValue;
    private NamedParameterStatement querySetVersionValue;
    private NamedParameterStatement querySetDoUpdateVersionValue;
    private NamedParameterStatement querySetDoSearchingBluetoothValue;
    private NamedParameterStatement querySetDoBindingBluetoothValue;
    private NamedParameterStatement querySetDoSettingsUpdateValue;
    private NamedParameterStatement querySetCommandsValue;
    private NamedParameterStatement queryDeleteBluetoothSearchResult;
    private NamedParameterStatement queryInsertBluetoothSearchResult;
    private NamedParameterStatement queryAddPosition;
    private NamedParameterStatement queryUpdateLatestPosition;
    private NamedParameterStatement queryAddSig;


    public SignalWatcher signalWatcher;

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

        query = properties.getProperty("database.setTimeZone");
        if (query != null) {
            querySetTimeZone = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.selectDevice");
        if (query != null) {
            queryGetDevices = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.selectLastSignal");
        if (query != null) {
            queryGetLastSignal = new NamedParameterStatement(connection, query);
        }
        query = properties.getProperty("database.selectLastPosition");
        if (query != null) {
            queryGetLastPosition = new NamedParameterStatement(connection, query);
        }


        query = properties.getProperty("database.selectBluetoothBinded");
        if (query != null) {
            querySelectBluetoothBinded = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.setDefenceValue");
        if (query != null) {
            querySetDefenceValue = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.setVersionValue");
        if (query != null) {
            querySetVersionValue = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.setDoUpdateVersionValue");
        if (query != null) {
            querySetDoUpdateVersionValue = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.setDoSearchingBluetoothValue");
        if (query != null) {
            querySetDoSearchingBluetoothValue = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.setDoBindingBluetoothValue");
        if (query != null) {
            querySetDoBindingBluetoothValue = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.setDoSettingsUpdateValue");
        if (query != null) {
            querySetDoSettingsUpdateValue = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.setCommandsValue");
        if (query != null) {
            querySetCommandsValue = new NamedParameterStatement(connection, query);
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


        signalWatcher = new SignalWatcher(this);


    }

    @Override
    public synchronized List<Device> getDevices() throws SQLException {

        setTimeZone();

        List<Device> deviceList = new LinkedList<Device>();

        if (queryGetDevices != null) {
            queryGetDevices.prepare();
            ResultSet result = queryGetDevices.executeQuery();
            while (result.next()) {
                //Log.info("REFRESH DEVICES, ID: "+result.getLong("id")+", IMEI: "+result.getString("imei"));

                Device device = new Device();
                device.setId(result.getLong("id"));
                device.setImei(result.getString("imei"));
                device.setDoUpdateVersion(result.getString("do_update_version"));
                device.setDoSearchingBluetooth(result.getString("do_searching_bluetooth"));
                device.setDoBindingBluetooth(result.getString("do_binding_bluetooth"));
                device.do_settings_update = result.getInt("do_settings_update");
                device.setting_noise_volume_level = result.getDouble("setting_noise_volume_level");
                device.setting_incoming_numbers = result.getString("setting_incoming_numbers");
                device.setting_gsensor_level = result.getFloat("setting_gsensor_level");
                device.setting_orientsensor_level = result.getFloat("setting_orientsensor_level");
                device.setting_connect_timeout = result.getInt("setting_connect_timeout");
                device.setting_max_wait_time_to_change_sim = result.getInt("setting_max_wait_time_to_change_sim");
                device.setting_max_fail_time_in_defence_to_change_sim = result.getInt("setting_max_fail_time_in_defence_to_change_sim");
                device.setting_max_wait_time_in_defence_to_change_sim = result.getInt("setting_max_wait_time_in_defence_to_change_sim");
                device.setting_max_response_wait_time = result.getInt("setting_max_response_wait_time");
                device.setCommands(result.getString("commands"));
                device.defence = result.getInt("defence");
                device.setPhoneNumber(result.getString("notification_number"));

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
    private Long devicesLastUpdateTS;
    private Long devicesRefreshDelay;

    @Override
    public Device getDeviceByImei(String imei) throws SQLException {

        if ((devices == null) || (Calendar.getInstance().getTimeInMillis() - devicesLastUpdateTS > devicesRefreshDelay)) {
            refreshDevices();
        }

        return devices.get(imei);
    }

    @Override
    public Device getDeviceByID(Long id) throws SQLException {


        if ((devicesIDs == null) || (Calendar.getInstance().getTimeInMillis() - devicesLastUpdateTS > devicesRefreshDelay)) {
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
        devicesLastUpdateTS = Calendar.getInstance().getTimeInMillis();

        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(null);

        return;
    }


    @Override
    public Signal selectLastSignal(Long deviceId) throws SQLException {
        setTimeZone();
        if (queryGetLastSignal != null) {
            Signal signal = new Signal();

            queryGetLastSignal.prepare();
            queryGetLastSignal.setLong("device_id", deviceId);

            ResultSet result = queryGetLastSignal.executeQuery();
            result.next();

            signal.setAcc(result.getInt("acc"));
            signal.setCharge(result.getInt("charge"));
            signal.setGps(result.getInt("gps"));
            signal.setGSensor(result.getFloat("g_sensor"));
            signal.setNoiseValue(result.getDouble("noise_value"));
            signal.setTimeFromTimeStamp(result.getLong("datetime"));

            return signal;
        }
        return null;
    }

    @Override
    public Position selectLastPosition(Long deviceId) throws SQLException {
        setTimeZone();
        if (queryGetLastPosition != null) {
            Position position = new Position();

            queryGetLastPosition.prepare();
            queryGetLastPosition.setLong("device_id", deviceId);

            ResultSet result = queryGetLastPosition.executeQuery();
            result.next();

            position.setLatitude(result.getDouble("latitude"));
            position.setLongitude(result.getDouble("longitude"));
            position.setTimeFromTimeStamp(result.getLong("time"));

            return position;
        }
        return null;
    }


    @Override
    public ArrayList<BluetoothDevice> selectBluetoothBinded(Long deviceId) throws SQLException {
        setTimeZone();
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
        setTimeZone();
        if (querySetDoSearchingBluetoothValue != null) {
            querySetDoSearchingBluetoothValue.prepare();
            querySetDoSearchingBluetoothValue.setLong("device_id", deviceId);
            querySetDoSearchingBluetoothValue.setInt("value", value);
            querySetDoSearchingBluetoothValue.executeUpdate();
        }
    }

    @Override
    public void setDefenceValue(Long deviceId, int value) throws SQLException {
        setTimeZone();
        if (querySetDefenceValue != null) {
            querySetDefenceValue.prepare();
            querySetDefenceValue.setLong("device_id", deviceId);
            querySetDefenceValue.setInt("value", value);
            querySetDefenceValue.executeUpdate();
        }
    }

    @Override
    public void setVersionValue(Long deviceId, int value) throws SQLException {
        setTimeZone();
        if (querySetVersionValue != null) {
            querySetVersionValue.prepare();
            querySetVersionValue.setLong("device_id", deviceId);
            querySetVersionValue.setInt("value", value);
            querySetVersionValue.executeUpdate();
        }
    }

    @Override
    public void setDoUpdateVersionValue(Long deviceId, int value) throws SQLException {
        setTimeZone();
        if (querySetDoUpdateVersionValue != null) {
            querySetDoUpdateVersionValue.prepare();
            querySetDoUpdateVersionValue.setLong("device_id", deviceId);
            querySetDoUpdateVersionValue.setInt("value", value);
            querySetDoUpdateVersionValue.executeUpdate();
        }
    }

    @Override
    public void setDoBindingBluetoothValue(Long deviceId, int value) throws SQLException {
        setTimeZone();
        if (querySetDoBindingBluetoothValue != null) {
            querySetDoBindingBluetoothValue.prepare();
            querySetDoBindingBluetoothValue.setLong("device_id", deviceId);
            querySetDoBindingBluetoothValue.setInt("value", value);
            querySetDoBindingBluetoothValue.executeUpdate();
        }
    }

    @Override
    public void setDoSettingsUpdateValue(Long deviceId, int value) throws SQLException {
        setTimeZone();
        if (querySetDoSettingsUpdateValue != null) {
            querySetDoSettingsUpdateValue.prepare();
            querySetDoSettingsUpdateValue.setLong("device_id", deviceId);
            querySetDoSettingsUpdateValue.setInt("value", value);
            querySetDoSettingsUpdateValue.executeUpdate();
        }
    }

    @Override
    public void setCommandValue(Long deviceId, String value) throws SQLException {
        setTimeZone();
        if (querySetCommandsValue != null) {
            querySetCommandsValue.prepare();
            querySetCommandsValue.setLong("device_id", deviceId);
            querySetCommandsValue.setString("value", value);
            querySetCommandsValue.executeUpdate();
        }
    }

    @Override
    public void deleteBluetoothSearchResult(Long deviceId) throws SQLException {
        setTimeZone();
        if (queryDeleteBluetoothSearchResult != null) {
            queryDeleteBluetoothSearchResult.prepare();
            queryDeleteBluetoothSearchResult.setLong("device_id", deviceId);
            queryDeleteBluetoothSearchResult.executeUpdate();
        }
    }

    @Override
    public void insertBluetoothSearchResult(Long deviceId, String name, String mac) throws SQLException {
        setTimeZone();
        if (queryInsertBluetoothSearchResult != null) {
            queryInsertBluetoothSearchResult.prepare();
            queryInsertBluetoothSearchResult.setLong("device_id", deviceId);
            queryInsertBluetoothSearchResult.setString("name", name);
            queryInsertBluetoothSearchResult.setString("mac", mac);
            queryInsertBluetoothSearchResult.executeUpdate();
        }
    }


    @Override
    public void setTimeZone() throws SQLException {
        if (querySetTimeZone != null) {
            querySetTimeZone.prepare();
            querySetTimeZone.executeUpdate();
        }
    }



    @Override
    public synchronized Long addPosition(Position position) throws SQLException {
        setTimeZone();
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
        setTimeZone();
        if (queryUpdateLatestPosition != null) {
            queryUpdateLatestPosition.prepare();

            queryUpdateLatestPosition.setLong("device_id", deviceId);
            queryUpdateLatestPosition.setLong("id", positionId);

            queryUpdateLatestPosition.executeUpdate();
        }
    }

    @Override
    public synchronized Long addSig(String hex,
                                    int active_sim,
                                    String version,
                                    int defence,
                                    String adds,
                                    String gps,
                                    int satellites,
                                    int satellites_all,
                                    String charge,
                                    String acc,
                                    String voltage,
                                    double g_sensor,
                                    double orient_sensor,
                                    double noise_value,
                                    String cell1,
                                    String cell2,
                                    String signal,
                                    String bluetooth,
                                    int update_status) throws SQLException {

        setTimeZone();

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
            queryAddSig.setInt("active_sim", active_sim);
            queryAddSig.setInt("defence", defence);
            queryAddSig.setString("adds", adds);
            queryAddSig.setString("gps", gps);
            queryAddSig.setInt("satellites", satellites);
            queryAddSig.setInt("satellites_all", satellites_all);
            queryAddSig.setString("charge", charge);
            queryAddSig.setString("acc", acc);
            queryAddSig.setString("voltage", voltage);
            queryAddSig.setDouble("g_sensor", g_sensor);
            queryAddSig.setDouble("orient_sensor", orient_sensor);
            queryAddSig.setDouble("noise_value", noise_value);
            queryAddSig.setString("cell1", cell1);
            queryAddSig.setString("cell2", cell2);
            queryAddSig.setString("signal", signal);
            queryAddSig.setString("version", version);
            queryAddSig.setString("bluetooth", bluetooth);
            queryAddSig.setInt("update_status", update_status);
            queryAddSig.executeUpdate();

            ResultSet result = queryAddSig.getGeneratedKeys();
            if (result != null && result.next()) {
                return result.getLong(1);
            }
        }
        return null;
    }

}

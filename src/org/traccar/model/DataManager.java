/*
 * Copyright 2012 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.util.List;
import java.util.ArrayList;

/**
 * Data manager
 */
public interface DataManager {

    /**
     * Manage devices
     */
    public List<Device> getDevices() throws Exception;
    public void refreshDevices() throws Exception;
    public Device getDeviceByImei(String imei) throws Exception;
    public Device getDeviceByID(Long id) throws Exception;

    public ArrayList<BluetoothDevice> selectBluetoothBinded(Long deviceId) throws Exception;

    public void setDoSearchingBluetoothValue(Long deviceId, int value) throws Exception;
    public void setDoBindingBluetoothValue(Long deviceId, int value) throws Exception;
    public void setDoSettingsUpdateValue(Long deviceId, int value) throws Exception;

    public void deleteBluetoothSearchResult(Long deviceId) throws Exception;
    public void insertBluetoothSearchResult(Long deviceId, String name, String mac) throws Exception;

    public Long addSig(String hex, String adds, String gps, String charge, String acc, String voltage, String signal) throws Exception;

    /**
     * Manage positions
     */
    public Long addPosition(Position position) throws Exception;
    public void updateLatestPosition(Long deviceId, Long positionId) throws Exception;

}
